import request, { globalHeaders } from '@/utils/request';

export interface Station {
  id: string;
  station_id?: string;
  name: string;
  address: string;
  landmark: string;
  photo: string;
  instructions: string;
  latitude?: number;
  longitude?: number;
  planned_at?: string;
  enabled: boolean;
}
export interface Fare {
  from: string;
  to: string;
  cents: number;
}
export interface Snapshot {
  name: string;
  origin: string;
  destination: string;
  vehicle_name: string;
  plate: string;
  stops: Station[];
  fares: Fare[];
}
export interface Summary {
  reserved: number;
  boarded: number;
  cancelled: number;
  missed: number;
  due_cents: number;
  paid_cents: number;
  unpaid_cents: number;
}
export interface Trip {
  id: string;
  depart_at: string;
  capacity: number;
  occupied: number;
  status: string;
  demo: boolean;
  snapshot: Snapshot;
  summary: Summary;
  staff: { user_id: string; station_id: string }[];
}
export interface Rider {
  id: string;
  booking_id: string;
  name: string;
  phone: string;
  contact_name: string;
  board_station: string;
  alight_station: string;
  status: string;
  paid: boolean;
  fare_cents: number;
}
export const money = (cents = 0) => (cents / 100).toFixed(2);
export const busGet = async <T = any>(path: string): Promise<T> => (await request({ url: '/bus' + path, method: 'get' })).data;
export const busPost = async <T = any>(path: string, data: unknown): Promise<T> =>
  (await request({ url: '/bus' + path, method: 'post', data, headers: { repeatSubmit: false } })).data;
export async function exportManifest(id: string, payments = false) {
  const response = await fetch(`${import.meta.env.VITE_APP_BASE_API}/bus/trips/${id}/${payments ? 'payments/export' : 'export'}`, {
    headers: globalHeaders()
  });
  if (!response.ok || !response.headers.get('content-type')?.includes('text/csv')) throw new Error('导出失败，请确认权限');
  const url = URL.createObjectURL(await response.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = payments ? '班次收款流水.csv' : '班次对账名单.csv';
  a.click();
  URL.revokeObjectURL(url);
}
