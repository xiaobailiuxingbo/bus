<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { busGet, busPost, exportManifest, money, type Trip, type Rider, type Station, type Summary } from '@/api/bus';
const props = withDefaults(defineProps<{ mode?: string }>(), { mode: 'overview' });
const router = useRouter();
const busy = ref(false),
  loading = ref(true),
  error = ref('');
const trips = ref<Trip[]>([]),
  riders = ref<Rider[]>([]),
  payments = ref<any[]>([]);
const dashboard = ref<any>({ owner: false, merchant: { name: '归途客运' }, summary: {} });
const stations = ref<Station[]>([]),
  routes = ref<any[]>([]),
  vehicles = ref<any[]>([]),
  employees = ref<any[]>([]);
const selected = ref<Trip>(),
  search = ref(''),
  dateFilter = ref<string[] | null>(null),
  collectorFilter = ref('');
const catalogTab = ref('stations'),
  editVisible = ref(false),
  tripVisible = ref(false),
  bookingVisible = ref(false);
const form = ref<any>({}),
  tripForm = ref<any>({}),
  bookingForm = ref<any>({});
const showCancelled = ref(false);
const selectedIds = ref<string[]>([]),
  staffVisible = ref(false),
  staffAssignments = ref<any[]>([]);
const title = computed(
  () => ({ overview: '今日工作台', trips: '班次与乘客名单', catalog: '线路与站点', reports: '收款与对账' })[props.mode] || '今日工作台'
);
const isOwner = computed(() => dashboard.value.owner);
const status: Record<string, string> = {
  DRAFT: '草稿',
  OPEN: '预约中',
  CLOSED: '停止预约',
  FINISHED: '已结束',
  CANCELLED: '已取消',
  RESERVED: '待到站',
  ARRIVED: '已报到',
  BOARDED: '已上车',
  MISSED: '未乘车'
};
const visibleTrips = computed(() =>
  trips.value.filter((t) => !dateFilter.value || (t.depart_at.slice(0, 10) >= dateFilter.value[0] && t.depart_at.slice(0, 10) <= dateFilter.value[1]))
);
const filteredRiders = computed(() =>
  riders.value.filter(
    (r) => (showCancelled.value || r.status !== 'CANCELLED') && (!search.value || `${r.name}${r.phone}${r.contact_name}`.includes(search.value))
  )
);
const riderGroups = computed(() =>
  (selected.value?.snapshot.stops || [])
    .map((s) => ({ station: s, riders: filteredRiders.value.filter((r) => r.board_station === s.station_id) }))
    .filter((g) => g.riders.length)
);
const reportSummary = computed(() =>
  visibleTrips.value.reduce(
    (out, t) => {
      for (const k of Object.keys(out) as (keyof Summary)[]) out[k] += Number(t.summary[k] || 0);
      return out;
    },
    { reserved: 0, boarded: 0, cancelled: 0, missed: 0, due_cents: 0, paid_cents: 0, unpaid_cents: 0 }
  )
);
const shownPayments = computed(() => payments.value.filter((p) => !collectorFilter.value || String(p.actor_id) === collectorFilter.value));
const paymentCollectors = computed(() => [
  ...new Map(payments.value.map((p) => [String(p.actor_id), { id: String(p.actor_id), name: p.collector || `收款人 ${p.actor_id}` }])).values()
]);
const catalogRows = computed(() =>
  catalogTab.value === 'stations' ? stations.value : catalogTab.value === 'routes' ? routes.value : vehicles.value
);
const stationName = (id: string) =>
  selected.value?.snapshot.stops.find((s) => s.station_id === id)?.name || stations.value.find((s) => s.id === id)?.name || id;
const clock = (value: string) => value?.slice(11, 16);
const dateText = (value: string) => value?.slice(5, 10).replace('-', '月') + '日';
async function act(task: () => Promise<void>) {
  if (busy.value) return;
  busy.value = true;
  try {
    await task();
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e.message || '操作未完成，请稍后重试');
  } finally {
    busy.value = false;
  }
}
async function load() {
  loading.value = true;
  error.value = '';
  try {
    dashboard.value = await busGet('/dashboard');
    trips.value = await busGet('/trips');
    if (isOwner.value)
      [stations.value, routes.value, vehicles.value, employees.value] = await Promise.all([
        busGet('/catalog/stations'),
        busGet('/catalog/routes'),
        busGet('/catalog/vehicles'),
        busGet('/employees')
      ]);
    if (selected.value) {
      selected.value = trips.value.find((t) => t.id === selected.value?.id);
      if (selected.value) await selectTrip(selected.value);
    }
  } catch (e: any) {
    error.value = e.message || '数据加载失败，请检查后端连接';
  } finally {
    loading.value = false;
  }
}
async function selectTrip(t: Trip) {
  selected.value = t;
  selectedIds.value = [];
  riders.value = await busGet(`/trips/${t.id}/riders`);
  payments.value = isOwner.value ? await busGet(`/trips/${t.id}/payments`) : [];
}
function newCatalog(row?: any) {
  form.value = row
    ? JSON.parse(JSON.stringify(row))
    : {
        name: '',
        address: '',
        landmark: '',
        photo: '',
        instructions: '',
        latitude: null,
        longitude: null,
        enabled: true,
        plate: '',
        capacity: 35,
        origin: '',
        destination: '',
        stops: [
          { station_id: '', offset_minutes: 0 },
          { station_id: '', offset_minutes: 60 }
        ],
        fares: []
      };
  editVisible.value = true;
}
async function saveCatalog() {
  await act(async () => {
    await busPost(`/catalog/${catalogTab.value}`, form.value);
    editVisible.value = false;
    await load();
    ElMessage.success('资料已保存');
  });
}
function newTrip() {
  const future = new Date(Date.now() + 3600000);
  tripForm.value = {
    route_id: routes.value[0]?.id,
    vehicle_id: vehicles.value[0]?.id,
    depart_at: localTime(future),
    capacity: vehicles.value[0]?.capacity || 35,
    staff: [],
    demo: dashboard.value.merchant.demo
  };
  tripVisible.value = true;
}
function localTime(d: Date) {
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}
async function createTrip() {
  await act(async () => {
    await busPost('/trips', tripForm.value);
    tripVisible.value = false;
    await load();
    ElMessage.success('班次草稿已创建，发布后乘客才能预约');
  });
}
async function tripAction(t: Trip, action: string) {
  await act(async () => {
    let reason = '';
    if (action === 'cancel')
      reason = (
        await ElMessageBox.prompt('取消会释放所有未收款、未上车乘客的预约，请填写原因', '取消班次', {
          inputPattern: /\S+/,
          inputErrorMessage: '请输入原因'
        })
      ).value;
    else if (action === 'finish') await ElMessageBox.confirm('未上车乘客将标记为未乘车，确认结束这趟班次？', '结束班次');
    await busPost(`/trips/${t.id}/${action}`, { reason });
    await load();
  });
}
async function riderAction(r: Rider, action: string) {
  await act(async () => {
    let reason = '';
    if (action === 'cancel')
      reason = (await ElMessageBox.prompt('请填写取消或异常处理原因', '处理乘客', { inputPattern: /\S+/, inputErrorMessage: '请输入原因' })).value;
    await busPost(`/riders/${r.id}/${action}`, { reason });
    await load();
  });
}
async function collect(r: Rider, method: string) {
  await act(async () => {
    await ElMessageBox.confirm(
      `确认已实际收到 ${r.name} 的 ¥${money(r.fare_cents)} ${method === 'CASH' ? '现金' : '微信转账'}？系统仅登记收款。`,
      '登记收款'
    );
    await busPost(`/riders/${r.id}/collect`, { method, request_key: crypto.randomUUID() });
    await load();
    ElMessage.success('收款已登记');
  });
}
async function reverse(p: any) {
  await act(async () => {
    const { value } = await ElMessageBox.prompt('冲正保留原始流水。请说明原因；如需退还票款，请在线下实际处理。', '收款冲正', {
      inputPattern: /\S+/,
      inputErrorMessage: '请输入原因'
    });
    await busPost(`/payments/${p.id}/reverse`, { reason: value });
    await load();
  });
}
function newBooking() {
  if (!selected.value) return;
  const f = selected.value.snapshot.fares[0];
  bookingForm.value = {
    trip_id: selected.value.id,
    contact_name: '',
    phone: '',
    request_key: crypto.randomUUID(),
    riders: [{ name: '', board_station: f?.from, alight_station: f?.to }]
  };
  bookingVisible.value = true;
}
async function saveBooking() {
  await act(async () => {
    await busPost('/bookings', bookingForm.value);
    bookingVisible.value = false;
    await load();
    ElMessage.success('代录预约成功');
  });
}
function toggleSelected(id: string, checked: boolean) {
  selectedIds.value = checked ? [...selectedIds.value, id] : selectedIds.value.filter((x) => x !== id);
}
async function batchAction(action: string, method?: string) {
  await act(async () => {
    await ElMessageBox.confirm(
      `确认对选中的 ${selectedIds.value.length} 位乘客${action === 'board' ? '确认上车' : '登记已实际收到的票款'}？`,
      '批量处理'
    );
    await busPost(`/trips/${selected.value!.id}/batch`, { ids: selectedIds.value, action, method, request_key: crypto.randomUUID() });
    await load();
  });
}
function editStaff() {
  staffAssignments.value = JSON.parse(JSON.stringify(selected.value?.staff || [])).map((s: any) => ({ ...s, user_id: String(s.user_id) }));
  staffVisible.value = true;
}
async function saveStaff() {
  await act(async () => {
    await busPost(`/trips/${selected.value!.id}/staff`, { staff: staffAssignments.value });
    staffVisible.value = false;
    await load();
  });
}
onMounted(load);
</script>

<template>
  <div class="bus-console" v-loading="loading || busy">
    <header class="page-heading">
      <div>
        <span class="eyebrow">归途 · 客运运营</span>
        <h1>{{ title }}</h1>
        <p>把每一趟归途，安排妥当。</p>
      </div>
      <el-button round :loading="loading" @click="load">刷新数据</el-button>
    </header>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="notice" />
    <div v-if="dashboard.merchant.demo" class="demo-note">演示商家与演示线路 · 非实际售票服务，站点和票价需老板确认。</div>
    <template v-if="mode === 'overview'">
      <section class="journey-hero">
        <div>
          <span class="hero-label">{{ dashboard.today }} · {{ dashboard.merchant.name }}</span>
          <h2>今天，也让大家安心到达。</h2>
          <p>班次、乘客、票款，都有清楚的记录。</p>
          <el-button v-if="isOwner" color="#fff" round @click="newTrip">＋ 创建新班次</el-button
          ><el-button v-else color="#fff" round @click="router.push('/bus/trips')">查看负责班次</el-button>
        </div>
        <div class="route-art" aria-hidden="true">
          <div class="road"></div>
          <span class="road-stop a"></span><span class="road-stop b"></span>
          <div class="bus-illustration">归途<span>▥ ▥ ▥</span><i></i><i></i></div>
        </div>
      </section>
      <div class="metrics">
        <div class="metric">
          <span>今日预约</span><strong>{{ dashboard.summary.reserved || 0 }}<small>人</small></strong>
          <p>包含代录与自助预约</p>
        </div>
        <div class="metric">
          <span>已上车</span><strong>{{ dashboard.summary.boarded || 0 }}<small>人</small></strong>
          <p>由工作人员逐人确认</p>
        </div>
        <div class="metric">
          <span>已收票款</span><strong><small>¥</small>{{ money(dashboard.summary.paid_cents) }}</strong>
          <p>线下实际收款登记</p>
        </div>
        <div class="metric warm">
          <span>待收票款</span><strong><small>¥</small>{{ money(dashboard.summary.unpaid_cents) }}</strong>
          <p>请核实乘客与收款状态</p>
        </div>
      </div>
      <div class="section-title">
        <h2>
          今天的班次 <small>{{ dashboard.trips?.length || 0 }} 趟</small>
        </h2>
        <el-button text @click="router.push('/bus/trips')">查看全部 →</el-button>
      </div>
      <div class="trip-grid">
        <article v-for="t in dashboard.trips" :key="t.id" class="trip-card" @click="selectTrip(t)">
          <div class="trip-top">
            <span>{{ status[t.status] }}</span
            ><span>{{ t.snapshot.vehicle_name }}</span>
          </div>
          <div class="trip-route">
            <div>
              <strong>{{ clock(t.depart_at) }}</strong>
              <p>{{ t.snapshot.origin }}</p>
            </div>
            <div class="route-line">前往 →</div>
            <div>
              <strong>{{ t.snapshot.destination }}</strong>
              <p>{{ t.snapshot.plate }}</p>
            </div>
          </div>
          <div class="trip-bottom">
            <span>预约 {{ t.summary.reserved }} / {{ t.capacity }} 人</span><span>已上车 {{ t.summary.boarded }} 人</span
            ><button @click.stop="router.push('/bus/trips')">管理班次 →</button>
          </div>
        </article>
      </div>
      <el-empty v-if="!dashboard.trips?.length && !error" description="今天还没有班次，发布后就能开始预约" />
    </template>

    <template v-if="mode === 'trips' || mode === 'reports'">
      <div class="toolbar">
        <el-date-picker
          v-model="dateFilter"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          clearable
        /><el-button v-if="mode === 'trips' && isOwner" type="primary" round @click="newTrip">＋ 新建班次</el-button>
      </div>
      <div v-if="mode === 'reports'" class="metrics">
        <div class="metric">
          <span>应收票款</span><strong>¥{{ money(reportSummary.due_cents) }}</strong>
        </div>
        <div class="metric">
          <span>已收票款</span><strong>¥{{ money(reportSummary.paid_cents) }}</strong>
        </div>
        <div class="metric warm">
          <span>未收票款</span><strong>¥{{ money(reportSummary.unpaid_cents) }}</strong>
        </div>
        <div class="metric">
          <span>取消 / 未乘车</span><strong>{{ reportSummary.cancelled }} / {{ reportSummary.missed }}<small>人</small></strong>
        </div>
      </div>
      <div class="operations-layout">
        <aside class="trip-picker">
          <button v-for="t in visibleTrips" :key="t.id" :class="{ active: selected?.id === t.id }" @click="act(() => selectTrip(t))">
            <span class="picker-date">{{ dateText(t.depart_at) }} · {{ clock(t.depart_at) }}</span
            ><strong>{{ t.snapshot.name }}</strong
            ><span>{{ status[t.status] }} · {{ t.occupied }}/{{ t.capacity }} 人</span><span>待收 ¥{{ money(t.summary.unpaid_cents) }}</span></button
          ><el-empty v-if="!visibleTrips.length" description="暂无班次" :image-size="70" />
        </aside>
        <section v-if="selected" class="manifest-panel">
          <div class="manifest-heading">
            <div>
              <span class="eyebrow">{{ dateText(selected.depart_at) }} · {{ status[selected.status] }}</span>
              <h2>{{ selected.snapshot.name }}</h2>
              <p>{{ selected.snapshot.plate }} · 余位 {{ selected.capacity - selected.occupied }} 人</p>
            </div>
            <div class="action-row" v-if="isOwner">
              <el-button v-if="['DRAFT', 'CLOSED'].includes(selected.status)" type="primary" @click="tripAction(selected, 'publish')"
                >发布预约</el-button
              ><el-button v-if="selected.status === 'OPEN'" @click="tripAction(selected, 'close')">停止预约</el-button
              ><el-button v-if="selected.status === 'OPEN'" @click="newBooking">老板代录</el-button
              ><el-button v-if="['OPEN', 'CLOSED'].includes(selected.status)" @click="tripAction(selected, 'finish')">结束班次</el-button
              ><el-button v-if="!['FINISHED', 'CANCELLED'].includes(selected.status)" type="danger" plain @click="tripAction(selected, 'cancel')"
                >取消班次</el-button
              ><el-button v-if="!['FINISHED', 'CANCELLED'].includes(selected.status)" @click="editStaff">人员分配</el-button
              ><el-button
                @click="
                  act(async () => {
                    await exportManifest(selected!.id, mode === 'reports');
                  })
                "
                >{{ mode === 'reports' ? '导出收款流水' : '导出名单' }}</el-button
              >
            </div>
          </div>
          <div class="mini-summary">
            预约 {{ selected.summary.reserved }} 人　·　上车 {{ selected.summary.boarded }} 人　·　已收 ¥{{
              money(selected.summary.paid_cents)
            }}　·　未收 ¥{{ money(selected.summary.unpaid_cents) }}
          </div>
          <template v-if="mode === 'trips'"
            ><el-switch v-model="showCancelled" active-text="显示已取消的乘客" style="margin-bottom: 16px" /><el-input
              v-model="search"
              placeholder="搜索乘车人、联系人或手机号"
              clearable
              class="search" />
            <div v-if="selectedIds.length" class="mini-summary action-row">
              <span>已选 {{ selectedIds.length }} 人</span><el-button type="primary" @click="batchAction('board')">批量确认上车</el-button
              ><el-button @click="batchAction('collect', 'CASH')">批量收现金</el-button
              ><el-button @click="batchAction('collect', 'WECHAT')">批量微信已收</el-button>
            </div>
            <div v-for="group in riderGroups" :key="group.station.id" class="station-group">
              <h3>
                <span class="station-dot"></span>{{ group.station.name
                }}<small>{{ group.riders.length }} 人 · {{ clock(group.station.planned_at!) }}</small>
              </h3>
              <article v-for="r in group.riders" :key="r.id" class="rider-card">
                <div class="rider-main">
                  <el-checkbox
                    :model-value="selectedIds.includes(r.id)"
                    :disabled="['CANCELLED', 'MISSED'].includes(r.status)"
                    :aria-label="'选择乘客 ' + r.name"
                    @change="(v) => toggleSelected(r.id, Boolean(v))"
                  />
                  <div class="avatar">{{ r.name.slice(0, 1) }}</div>
                  <div>
                    <strong>{{ r.name }}</strong>
                    <p>
                      {{ r.contact_name }} · <a :href="`tel:${r.phone}`">{{ r.phone }}</a>
                    </p>
                    <p>{{ stationName(r.board_station) }} → {{ stationName(r.alight_station) }}</p>
                  </div>
                </div>
                <div class="rider-state">
                  <el-tag :type="r.status === 'BOARDED' ? 'success' : r.status === 'CANCELLED' ? 'info' : 'warning'">{{ status[r.status] }}</el-tag
                  ><span :class="{ unpaid: !r.paid }">¥{{ money(r.fare_cents) }} · {{ r.paid ? '已收款' : '未收款' }}</span>
                </div>
                <div class="action-row">
                  <el-button v-if="r.status === 'RESERVED' && !['FINISHED', 'CANCELLED'].includes(selected.status)" @click="riderAction(r, 'arrive')"
                    >确认到站</el-button
                  ><el-button
                    v-if="['RESERVED', 'ARRIVED'].includes(r.status) && !['FINISHED', 'CANCELLED'].includes(selected.status)"
                    type="primary"
                    @click="riderAction(r, 'board')"
                    >确认上车</el-button
                  ><template v-if="!r.paid && r.status !== 'CANCELLED'"
                    ><el-button @click="collect(r, 'CASH')">收现金</el-button><el-button @click="collect(r, 'WECHAT')">微信已收</el-button></template
                  ><el-button v-if="isOwner && !r.paid && r.status !== 'CANCELLED'" text @click="riderAction(r, 'cancel')">取消 / 异常</el-button>
                </div>
              </article>
            </div>
            <el-empty v-if="!filteredRiders.length" description="还没有符合条件的乘客"
          /></template>
          <template v-else
            ><div class="toolbar">
              <el-select v-model="collectorFilter" placeholder="按收款人筛选" clearable
                ><el-option v-for="c in paymentCollectors" :key="c.id" :label="c.name" :value="c.id" /></el-select
              ><span>流水合计 ¥{{ money(shownPayments.reduce((n, p) => n + p.amount_cents, 0)) }}</span>
            </div>
            <article v-for="p in shownPayments" :key="p.id" class="payment-row">
              <div>
                <strong>{{ p.name }}</strong>
                <p>{{ p.collector || p.actor_id }} · {{ p.method === 'CASH' ? '现金' : '微信转账' }} · {{ p.created_at?.replace('T', ' ') }}</p>
                <p v-if="p.reason">{{ p.reason }}</p>
              </div>
              <strong :class="p.amount_cents < 0 ? 'unpaid' : 'teal'"
                >{{ p.amount_cents < 0 ? '−' : '+' }}¥{{ money(Math.abs(p.amount_cents)) }}</strong
              ><el-button v-if="p.amount_cents > 0 && !payments.some((x) => x.reverses_id === p.id)" @click="reverse(p)">冲正</el-button
              ><span v-else>已冲正</span>
            </article>
            <el-empty v-if="!shownPayments.length" description="暂无收款流水"
          /></template>
        </section>
        <section v-else class="manifest-panel"><el-empty description="选择一趟班次，查看名单或收款记录" /></section>
      </div>
    </template>

    <template v-if="mode === 'catalog'"
      ><div class="catalog-header">
        <el-radio-group v-model="catalogTab"
          ><el-radio-button value="stations">候车站点</el-radio-button><el-radio-button value="routes">线路与票价</el-radio-button
          ><el-radio-button value="vehicles">车辆</el-radio-button></el-radio-group
        ><el-button type="primary" round @click="newCatalog()">＋ 新增</el-button>
      </div>
      <div class="catalog-grid">
        <article v-for="row in catalogRows" :key="row.id" class="catalog-card">
          <div class="catalog-card-top">
            <span class="eyebrow">{{ catalogTab === 'stations' ? '候车站点' : catalogTab === 'routes' ? '客运线路' : '运营车辆' }}</span
            ><el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
          </div>
          <h2>{{ row.name }}</h2>
          <template v-if="catalogTab === 'stations'"
            ><p>{{ row.address }}</p>
            <p>{{ row.landmark || '地标说明待补充' }}</p>
            <span class="helper">{{ row.latitude ? '已设置地图坐标' : '坐标待确认，暂不可导航' }}</span></template
          ><template v-else-if="catalogTab === 'routes'"
            ><p>{{ row.origin }} → {{ row.destination }}</p>
            <p>{{ row.stops.length }} 个站点 · {{ row.fares.length }} 个售票区间</p></template
          ><template v-else
            ><p>{{ row.plate }}</p>
            <p>核定 {{ row.capacity }} 人</p></template
          ><el-button plain @click="newCatalog(row)">编辑资料</el-button>
        </article>
      </div>
      <el-empty v-if="!catalogRows.length" description="先添加基础资料，再发布班次"
    /></template>

    <el-dialog class="bus-dialog" v-model="staffVisible" title="班次人员分配" width="min(600px,94vw)"
      ><div v-for="(s, i) in staffAssignments" :key="i" class="editor-row">
        <el-select v-model="s.user_id" placeholder="工作人员"
          ><el-option v-for="e in employees" :key="e.user_id" :label="e.nick_name || e.user_name" :value="String(e.user_id)" /></el-select
        ><el-select v-model="s.station_id"
          ><el-option label="整个班次（司机）" value="" /><el-option
            v-for="station in selected?.snapshot.stops"
            :key="station.id"
            :label="station.name"
            :value="station.station_id" /></el-select
        ><el-button text @click="staffAssignments.splice(i, 1)">移除</el-button>
      </div>
      <el-button @click="staffAssignments.push({ user_id: '', station_id: '' })">＋ 分配人员</el-button>
      <p class="helper">请同时在用户管理中授予工作人员“班次与名单”权限。</p>
      <template #footer
        ><el-button @click="staffVisible = false">返回</el-button
        ><el-button type="primary" :loading="busy" @click="saveStaff">保存分配</el-button></template
      ></el-dialog
    >
    <el-dialog class="bus-dialog" v-model="editVisible" :title="form.id ? '编辑基础资料' : '新增基础资料'" width="min(680px, 94vw)"
      ><el-form label-position="top"
        ><el-form-item label="名称"><el-input v-model="form.name" maxlength="80" /></el-form-item
        ><template v-if="catalogTab === 'stations'"
          ><el-form-item label="详细候车地址"><el-input v-model="form.address" /></el-form-item
          ><el-form-item label="附近地标"><el-input v-model="form.landmark" /></el-form-item
          ><el-form-item label="候车说明"><el-input v-model="form.instructions" type="textarea" /></el-form-item
          ><el-form-item label="站点照片地址（HTTPS）"><el-input v-model="form.photo" /></el-form-item>
          <div class="form-grid">
            <el-form-item label="纬度 · GCJ-02"><el-input v-model="form.latitude" placeholder="可暂留空" /></el-form-item
            ><el-form-item label="经度 · GCJ-02"><el-input v-model="form.longitude" placeholder="可暂留空" /></el-form-item>
          </div>
          <el-button disabled>地图搜索选点 · 待配置</el-button></template
        ><template v-if="catalogTab === 'vehicles'"
          ><el-form-item label="车牌"><el-input v-model="form.plate" /></el-form-item
          ><el-form-item label="核定载客人数"><el-input-number v-model="form.capacity" :min="1" :max="100" /></el-form-item></template
        ><template v-if="catalogTab === 'routes'"
          ><div class="form-grid">
            <el-form-item label="出发城市"><el-input v-model="form.origin" /></el-form-item
            ><el-form-item label="目的城市"><el-input v-model="form.destination" /></el-form-item>
          </div>
          <h3>依次停靠的站点</h3>
          <div v-for="(s, i) in form.stops" :key="i" class="editor-row">
            <el-select v-model="s.station_id" placeholder="选择站点"
              ><el-option v-for="station in stations" :key="station.id" :label="station.name" :value="station.id" /></el-select
            ><el-input-number v-model="s.offset_minutes" :min="0" :max="10080" /><span>发车后分钟</span
            ><el-button text @click="form.stops.splice(i, 1)">移除</el-button>
          </div>
          <el-button @click="form.stops.push({ station_id: '', offset_minutes: 60 })">＋ 添加站点</el-button>
          <h3>可预约区间与票价</h3>
          <div v-for="(f, i) in form.fares" :key="i" class="editor-row">
            <el-select v-model="f.from" placeholder="上车站"
              ><el-option
                v-for="s in stations.filter((x) => form.stops.some((p: any) => p.station_id === x.id))"
                :key="s.id"
                :label="s.name"
                :value="s.id" /></el-select
            ><el-select v-model="f.to" placeholder="下车站"
              ><el-option
                v-for="s in stations.filter((x) => form.stops.some((p: any) => p.station_id === x.id))"
                :key="s.id"
                :label="s.name"
                :value="s.id" /></el-select
            ><el-input-number
              :model-value="f.cents / 100"
              :min="0.01"
              :precision="2"
              @update:model-value="(v) => (f.cents = Math.round(Number(v) * 100))"
            /><span>元</span><el-button text @click="form.fares.splice(i, 1)">移除</el-button>
          </div>
          <el-button @click="form.fares.push({ from: '', to: '', cents: 5000 })">＋ 添加区间票价</el-button></template
        ><el-form-item label="允许用于新班次"><el-switch v-model="form.enabled" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="editVisible = false">返回</el-button
        ><el-button type="primary" :loading="busy" @click="saveCatalog">保存</el-button></template
      ></el-dialog
    >
    <el-dialog class="bus-dialog" v-model="tripVisible" title="安排一趟新班次" width="min(600px,94vw)"
      ><el-form label-position="top"
        ><el-form-item label="线路"
          ><el-select v-model="tripForm.route_id"
            ><el-option v-for="r in routes.filter((x) => x.enabled)" :key="r.id" :label="r.name" :value="r.id" /></el-select></el-form-item
        ><el-form-item label="车辆"
          ><el-select v-model="tripForm.vehicle_id" @change="(id) => (tripForm.capacity = vehicles.find((v) => v.id === id)?.capacity)"
            ><el-option
              v-for="v in vehicles.filter((x) => x.enabled)"
              :key="v.id"
              :label="`${v.name} · ${v.plate}`"
              :value="v.id" /></el-select></el-form-item
        ><el-form-item label="首站发车时间"
          ><el-date-picker v-model="tripForm.depart_at" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item
        ><el-form-item label="本班次可预约人数"
          ><el-input-number v-model="tripForm.capacity" :min="1" :max="vehicles.find((v) => v.id === tripForm.vehicle_id)?.capacity || 100"
        /></el-form-item>
        <h3>工作人员分配</h3>
        <div v-for="(s, i) in tripForm.staff" :key="i" class="editor-row">
          <el-select v-model="s.user_id" placeholder="工作人员"
            ><el-option v-for="e in employees" :key="e.user_id" :label="e.nick_name || e.user_name" :value="String(e.user_id)" /></el-select
          ><el-select v-model="s.station_id" placeholder="负责范围"
            ><el-option label="整个班次（司机）" value="" /><el-option
              v-for="s in stations.filter((x) => routes.find((r) => r.id === tripForm.route_id)?.stops.some((p: any) => p.station_id === x.id))"
              :key="s.id"
              :label="s.name"
              :value="s.id" /></el-select
          ><el-button text @click="tripForm.staff.splice(i, 1)">移除</el-button>
        </div>
        <el-button @click="tripForm.staff.push({ user_id: '', station_id: '' })">＋ 分配人员</el-button>
        <p class="helper">人员账号需在若依用户管理中创建，并授予“班次与名单”权限；站点人员仅能操作分配站点。</p></el-form
      ><template #footer
        ><el-button @click="tripVisible = false">返回</el-button
        ><el-button type="primary" :loading="busy" @click="createTrip">保存草稿</el-button></template
      ></el-dialog
    >
    <el-dialog class="bus-dialog" v-model="bookingVisible" title="替乘客登记预约" width="min(600px,94vw)"
      ><el-form label-position="top"
        ><div class="form-grid">
          <el-form-item label="联系人姓名"><el-input v-model="bookingForm.contact_name" /></el-form-item
          ><el-form-item label="联系电话"><el-input v-model="bookingForm.phone" maxlength="11" /></el-form-item>
        </div>
        <div v-for="(r, i) in bookingForm.riders" :key="i" class="booking-person">
          <el-input v-model="r.name" :placeholder="`第${i + 1}位乘车人姓名`" /><el-select
            :model-value="`${r.board_station}|${r.alight_station}`"
            @update:model-value="(v) => ([r.board_station, r.alight_station] = String(v).split('|'))"
            ><el-option
              v-for="f in selected?.snapshot.fares"
              :key="f.from + f.to"
              :label="`${stationName(f.from)} → ${stationName(f.to)} · ¥${money(f.cents)}`"
              :value="`${f.from}|${f.to}`" /></el-select
          ><el-button v-if="bookingForm.riders.length > 1" text @click="bookingForm.riders.splice(i, 1)">移除</el-button>
        </div>
        <el-button
          :disabled="bookingForm.riders?.length >= 6"
          @click="
            bookingForm.riders.push({ name: '', board_station: selected?.snapshot.fares[0]?.from, alight_station: selected?.snapshot.fares[0]?.to })
          "
          >＋ 同行乘客</el-button
        ></el-form
      ><template #footer
        ><el-button @click="bookingVisible = false">返回</el-button
        ><el-button type="primary" :loading="busy" @click="saveBooking">确认预约</el-button></template
      ></el-dialog
    >
  </div>
</template>

<style scoped>
.bus-console {
  --teal: #147d73;
  --cream: #f6f4ee;
  --ink: #243d39;
  --el-color-primary: #147d73;
  --el-bg-color: #fff;
  --el-fill-color-blank: #fff;
  --el-text-color-primary: #243d39;
  --el-text-color-regular: #53685e;
  --el-border-color: #dce4d7;
  --el-fill-color-light: #f1f5ed;
  background: var(--cream);
  min-height: calc(100vh - 90px);
  padding: 30px;
  color: var(--ink);
  font-family: Inter, 'PingFang SC', 'Microsoft YaHei', sans-serif;
}
.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}
.eyebrow {
  color: var(--teal);
  font-size: 12px;
  letter-spacing: 2px;
  font-weight: 600;
}
h1 {
  font-size: 28px;
  margin: 9px 0;
}
h2 {
  font-size: 20px;
  margin: 10px 0;
}
p {
  color: #71847d;
  line-height: 1.6;
  margin: 5px 0;
  font-size: 13px;
}
.demo-note {
  font-size: 12px;
  color: #8c6c36;
  background: #f1eadb;
  border-radius: 8px;
  padding: 10px 14px;
  margin-bottom: 20px;
}
.journey-hero {
  background: #147d73;
  border-radius: 20px;
  padding: 32px 36px;
  display: flex;
  justify-content: space-between;
  color: white;
  overflow: hidden;
  position: relative;
}
.journey-hero h2 {
  font-size: 30px;
  font-weight: 600;
  margin: 16px 0 8px;
}
.journey-hero p {
  color: #cde1d9;
  margin-bottom: 22px;
}
.hero-label {
  font-size: 12px;
  letter-spacing: 1px;
  color: #d2e8df;
}
.route-art {
  width: 250px;
  position: relative;
}
.road {
  position: absolute;
  width: 300px;
  height: 130px;
  border: 20px solid #ffffff18;
  border-radius: 80px;
  transform: rotate(-22deg);
  top: 25px;
  left: 0;
}
.road-stop {
  position: absolute;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #eecf8b;
  border: 4px solid #147d73;
}
.road-stop.a {
  left: 18px;
  top: 110px;
}
.road-stop.b {
  right: 0;
  top: 30px;
}
.bus-illustration {
  position: absolute;
  top: 60px;
  left: 70px;
  width: 142px;
  height: 68px;
  background: #e2eade;
  color: #147d73;
  border-radius: 14px 20px 7px 7px;
  padding: 8px 15px;
  font-size: 10px;
  transform: rotate(-8deg);
  box-shadow: 0 15px 30px #003e3940;
}
.bus-illustration span {
  display: block;
  font-size: 25px;
  letter-spacing: 8px;
}
.bus-illustration i {
  position: absolute;
  width: 18px;
  height: 18px;
  background: #254a45;
  border: 4px solid #a7bfb5;
  border-radius: 50%;
  bottom: -8px;
  left: 18px;
}
.bus-illustration i:last-child {
  left: 106px;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin: 22px 0;
}
.metric {
  background: white;
  border: 1px solid #e7e9df;
  padding: 22px;
  border-radius: 14px;
}
.metric > span {
  font-size: 13px;
  color: #6c7e76;
}
.metric strong {
  display: block;
  font-size: 30px;
  line-height: 1.5;
  margin: 9px 0;
  color: var(--teal);
  font-weight: 650;
}
.metric small {
  font-size: 13px;
  font-weight: 400;
  margin: 0 5px;
}
.metric p {
  font-size: 11px;
}
.metric.warm strong,
.unpaid {
  color: #b36a20 !important;
}
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 28px 0 16px;
}
.section-title small {
  font-size: 12px;
  color: #81938b;
  margin-left: 12px;
  font-weight: 400;
}
.trip-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}
.trip-card {
  background: white;
  border: 1px solid #e4e8dd;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
}
.trip-top,
.trip-bottom {
  display: flex;
  justify-content: space-between;
  padding: 16px 22px;
  font-size: 12px;
  color: #7e8d86;
}
.trip-top > span:first-child {
  color: var(--teal);
  background: #eaf3ee;
  padding: 4px 8px;
  border-radius: 5px;
}
.trip-route {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 24px 24px;
}
.trip-route strong {
  font-size: 25px;
}
.route-line {
  font-size: 12px;
  color: #8a9b92;
  border-bottom: 1px dashed #acbdb0;
  padding: 8px 24px;
}
.trip-bottom {
  border-top: 1px dashed #e2e7dc;
  align-items: center;
}
.trip-bottom button {
  border: 0;
  background: none;
  color: var(--teal);
  cursor: pointer;
}
.toolbar,
.catalog-header {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0;
}
.operations-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 20px;
}
.trip-picker {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.trip-picker button {
  border: 1px solid #e0e7da;
  background: white;
  text-align: left;
  padding: 20px;
  border-radius: 14px;
  cursor: pointer;
  color: #72887e;
  display: flex;
  flex-direction: column;
  gap: 9px;
  font-size: 12px;
}
.trip-picker button.active {
  border: 2px solid var(--teal);
  background: #f1f7f0;
}
.trip-picker strong {
  color: var(--ink);
  font-size: 16px;
}
.picker-date {
  color: var(--teal);
  font-weight: 600;
}
.manifest-panel {
  background: white;
  border-radius: 16px;
  padding: 24px;
  min-width: 0;
}
.manifest-heading {
  display: flex;
  justify-content: space-between;
  gap: 15px;
  flex-wrap: wrap;
}
.action-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
}
.action-row :deep(.el-button) {
  margin-left: 0;
  min-height: 44px;
}
.mini-summary {
  margin: 20px 0;
  padding: 14px;
  background: #f5f7f1;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.8;
}
.search {
  margin-bottom: 16px;
}
.station-group h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  margin: 24px 0 12px;
}
.station-group h3 small {
  color: #7d8c83;
  font-weight: 400;
  margin-left: auto;
  font-size: 12px;
}
.station-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--teal);
}
.rider-card {
  display: flex;
  align-items: center;
  gap: 15px;
  flex-wrap: wrap;
  padding: 16px 0;
  border-top: 1px solid #eef0e8;
}
.rider-main {
  display: flex;
  gap: 12px;
  flex: 1;
  min-width: 210px;
}
.rider-main a {
  color: #42685c;
}
.avatar {
  width: 40px;
  height: 40px;
  background: #eaf2e7;
  color: var(--teal);
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-weight: 600;
}
.rider-state {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 12px;
  align-items: flex-start;
}
.rider-card .action-row {
  width: 100%;
  padding-left: 52px;
}
.payment-row {
  display: flex;
  gap: 18px;
  align-items: center;
  border-bottom: 1px solid #eef0e8;
  padding: 18px 0;
}
.payment-row > div {
  flex: 1;
}
.teal {
  color: var(--teal);
}
.catalog-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}
.catalog-card {
  background: white;
  border: 1px solid #e5e9dd;
  border-radius: 14px;
  padding: 22px;
}
.catalog-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.catalog-card :deep(.el-button) {
  margin-top: 20px;
  width: 100%;
  min-height: 44px;
}
.helper {
  display: block;
  font-size: 12px;
  color: #8b968f;
  margin-top: 12px;
  line-height: 1.8;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 15px;
}
.editor-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 12px 0;
  flex-wrap: wrap;
}
.editor-row > .el-select {
  flex: 1;
  min-width: 130px;
}
.booking-person {
  background: #f6f7f1;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 12px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.booking-person > .el-select {
  width: 100%;
}
.bus-console :deep(.el-dialog) {
  border-radius: 16px;
}
.notice {
  margin-bottom: 15px;
}
@media (max-width: 1100px) {
  .metrics {
    grid-template-columns: repeat(2, 1fr);
  }
  .catalog-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .route-art {
    width: 180px;
  }
  .operations-layout {
    grid-template-columns: 220px 1fr;
  }
}
@media (max-width: 700px) {
  .bus-console {
    padding: 18px 12px;
  }
  .page-heading h1 {
    font-size: 23px;
  }
  .journey-hero {
    padding: 24px;
  }
  .journey-hero h2 {
    font-size: 23px;
  }
  .route-art {
    display: none;
  }
  .metrics {
    gap: 10px;
  }
  .metric {
    padding: 16px;
  }
  .metric strong {
    font-size: 24px;
  }
  .trip-grid,
  .catalog-grid {
    grid-template-columns: 1fr;
  }
  .operations-layout {
    grid-template-columns: 1fr;
  }
  .trip-picker {
    flex-direction: row;
    overflow-x: auto;
  }
  .trip-picker button {
    min-width: 210px;
  }
  .manifest-panel {
    padding: 16px;
  }
  .rider-card .action-row {
    padding-left: 0;
  }
  .form-grid {
    grid-template-columns: 1fr;
  }
  .toolbar,
  .catalog-header {
    flex-wrap: wrap;
  }
  .payment-row {
    flex-wrap: wrap;
  }
  .bus-console :deep(.el-button) {
    min-height: 44px;
  }
  .manifest-heading h2 {
    font-size: 22px;
  }
}
</style>

<style>
.bus-dialog {
  --el-color-primary: #147d73;
  --el-bg-color: #fff;
  --el-fill-color-blank: #fff;
  --el-text-color-primary: #243d39;
  --el-text-color-regular: #53685e;
  --el-border-color: #dce4d7;
  background: #fff !important;
  border-radius: 18px !important;
}
.bus-dialog .el-button {
  min-height: 44px;
}
</style>
