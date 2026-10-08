// Optional black-box check against the explicitly configured local demonstration merchant.
// Creates only demo bookings and cancels them afterwards. Never use with a real merchant.
import assert from 'node:assert/strict';
const base = process.env.BUS_TEST_URL || 'http://127.0.0.1:8088';
let token = '';
async function api(path, body, overrideToken = token) {
  const response = await fetch(base + '/app' + path, { method: body ? 'POST' : 'GET', headers: { 'Content-Type': 'application/json', 'X-Passenger-Token': overrideToken }, body: body ? JSON.stringify(body) : undefined });
  return response.json();
}
const merchant = (await api('/bus/merchant')).data;
assert.equal(merchant.demo, true, 'Only run against demo merchant');
assert.equal(merchant.demo_login, true, 'Requires local development demo login');
token = (await api('/auth/dev-login', { entry: 'qinzhou-demo', index: 1 })).data.token;
const otherToken = (await api('/auth/dev-login', { entry: 'qinzhou-demo', index: 2 })).data.token;
const today = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10);
const trips = (await api('/bus/trips?date=' + today)).data;
const trip = trips.find(t => t.demo && t.occupied === 0 && t.capacity >= 3 && t.capacity <= 10);
assert.ok(trip, 'Publish an empty demo trip with 3–10 seats first');
const fare = trip.snapshot.fares[0], bookings = [];
const body = (count, key) => ({ trip_id: trip.id, contact_name: '自动验收 · 演示', phone: '13800000000', request_key: key, riders: Array.from({ length: count }, (_, i) => ({ name: '验收乘客' + i, board_station: fare.from, alight_station: fare.to })) });
try {
  const request = body(2, crypto.randomUUID());
  const first = await api('/bus/bookings', request); assert.equal(first.code, 200); bookings.push(first.data);
  const duplicate = await api('/bus/bookings', request); assert.equal(duplicate.data.id, first.data.id);
  assert.equal((await api('/bus/bookings/' + first.data.id, null, otherToken)).code, 403);
  assert.equal((await api('/bus/bookings/' + first.data.id, null, '')).code, 401);
  assert.equal((await api(`/bus/riders/${first.data.riders[0].id}/board`, {})).code, 403);
  const results = await Promise.all(Array.from({ length: trip.capacity + 4 }, () => api('/bus/bookings', body(1, crypto.randomUUID()))));
  bookings.push(...results.filter(r => r.code === 200).map(r => r.data));
  assert.equal(results.filter(r => r.code === 200).length, trip.capacity - 2);
  assert.ok(results.filter(r => r.code !== 200).every(r => r.code === 409));
  assert.equal((await api('/bus/trips/' + trip.id)).data.occupied, trip.capacity);
  const rider = first.data.riders[0].id;
  assert.equal((await api(`/bus/riders/${rider}/arrive`, {})).code, 200);
  const refreshed = (await api('/bus/bookings/' + first.data.id)).data;
  assert.equal(refreshed.riders.find(r => r.id === rider).status, 'ARRIVED');
  assert.equal((await api(`/bus/riders/${rider}/cancel`, {})).code, 200);
  assert.equal((await api('/bus/trips/' + trip.id)).data.occupied, trip.capacity - 1);
  console.log('PASS: MySQL concurrency, idempotency, passenger isolation, auth, arrival and partial cancellation');
} finally {
  for (const booking of bookings) for (const rider of booking.riders) assert.equal((await api(`/bus/riders/${rider.id}/cancel`, {})).code, 200);
  assert.equal((await api('/bus/trips/' + trip.id)).data.occupied, 0);
  console.log('Demo seat inventory restored; cancelled booking history retained.');
}
