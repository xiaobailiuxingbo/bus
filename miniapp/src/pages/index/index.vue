<script setup lang="ts">
import type { IMerchant, ITrip } from '@/api/bus'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { busApi, entry, fareText, merchantApi } from '@/api/bus'

definePage({
  type: 'home',
  style: { navigationStyle: 'custom', navigationBarTitleText: '归途订车' },
})
const calendarDate = ref(Date.now())
const merchant = ref<IMerchant>()
const trips = ref<ITrip[]>([])
const error = ref('')
const loading = ref(false)
const date = ref(localDate(new Date()))
function chooseDate(value: number | number[] | null) {
  if (typeof value === 'number') {
    date.value = localDate(new Date(value))
    load()
  }
}
const origin = ref('')
const destination = ref('')
function localDate(d: Date) {
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 10)
}
const days = computed(() =>
  Array.from({ length: 5 }, (_, i) => {
    const d = new Date()
    d.setDate(d.getDate() + i)
    return {
      value: localDate(d),
      label:
        i === 0
          ? '今天'
          : i === 1
            ? '明天'
            : `${d.getMonth() + 1}月${d.getDate()}日`,
    }
  }),
)
const shown = computed(() =>
  trips.value.filter(
    t =>
      (!origin.value || t.snapshot.origin.includes(origin.value))
      && (!destination.value
        || t.snapshot.destination.includes(destination.value)),
  ),
)
function minimumFare(t: ITrip) {
  return Math.min(...t.snapshot.fares.map(f => f.cents))
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    merchant.value = await merchantApi()
    trips.value = await busApi(
      `/bus/trips?entry=${encodeURIComponent(entry())}&date=${date.value}`,
    )
  }
  catch (e) {
    error.value = (e as Error).message
  }
  finally {
    loading.value = false
  }
}
onLoad((q) => {
  if (q?.entry && String(q.entry) !== entry()) {
    uni.setStorageSync('bus-entry', String(q.entry))
    uni.removeStorageSync('bus-passenger-token')
    uni.removeStorageSync('bus-passenger')
  }
})
onShow(load)
</script>

<template>
  <view class="bus-page home-page">
    <view class="bus-brand">
      <view class="brand-mark">
        ▥
      </view><view>
        {{ merchant?.name || "归途客运"
        }}<view class="bus-help" style="font-weight: 400; font-size: 20rpx">
          家乡出发，安心到达
        </view>
      </view>
    </view><view class="bus-hero">
      <text class="bus-eyebrow">熟悉的路 · 方便的预约</text><view class="bus-title">
        出发之前，<br>先把座位安排好。
      </view><view class="bus-subtitle">
        选班次、找站点，出行少一点麻烦。
      </view>
    </view><view class="bus-card search-card">
      <view class="bus-row">
        <input v-model="origin" class="city-input" placeholder="出发地"><text
          class="bus-link"
        >
          →
        </text><input
          v-model="destination"
          class="city-input"
          placeholder="目的地"
        >
      </view><view class="date-picker">
        <view
          v-for="d in days"
          :key="d.value"
          class="date-chip"
          :class="[{ chosen: date === d.value }]"
          @click="
            date = d.value;
            load();
          "
        >
          {{ d.label }}
        </view><wd-calendar
          v-model="calendarDate"
          type="date"
          title="选择乘车日期"
          :min-date="new Date(new Date().setHours(0, 0, 0, 0)).getTime()"
          @confirm="chooseDate($event.value)"
        >
          <view class="date-chip">
            选日期
          </view>
        </wd-calendar>
      </view>
    </view><view v-if="merchant?.demo" class="bus-demo">
      演示线路与班次 · 非实际售票服务
    </view><view class="bus-section">
      <text>合适的班次</text><text class="bus-help">{{ date }} · {{ shown.length }} 趟</text>
    </view><view v-if="loading" class="bus-empty">
      正在查找班次…
    </view><view v-if="error" class="bus-card">
      <view class="bus-help">
        {{ error }}
      </view><button
        class="bus-button secondary"
        style="margin-top: 20rpx"
        @click="load"
      >
        重新加载
      </button>
    </view><view v-for="t in shown" :key="t.id" class="bus-ticket">
      <view class="ticket-head">
        <text class="bus-pill">{{ t.snapshot.vehicle_name }}</text><text>{{ t.snapshot.stops.length }} 个停靠点</text>
      </view><view class="ticket-main">
        <view>
          <view class="ticket-big">
            {{ t.depart_at.slice(11, 16) }}
          </view><view class="ticket-city">
            {{ t.snapshot.origin }}
          </view>
        </view><text class="ticket-arrow">──── →</text><view style="text-align: right">
          <view class="ticket-big" style="font-size: 38rpx">
            {{ t.snapshot.destination }}
          </view><view class="ticket-city">
            {{ t.snapshot.stops.slice(-1)[0]?.planned_at.slice(11, 16) }}
            预计到达
          </view>
        </view>
      </view><view class="ticket-foot">
        <view>
          <text class="bus-price">¥{{ fareText(minimumFare(t)) }}</text><text class="bus-help"> 起</text><view class="bus-help" style="margin-top: 6rpx">
            余位 {{ t.capacity - t.occupied }} 人 · 现场付款
          </view>
        </view><button
          class="bus-button"
          :disabled="t.capacity <= t.occupied"
          @click="uni.navigateTo({ url: `/pages/booking/index?id=${t.id}` })"
        >
          {{ t.capacity <= t.occupied ? "已满员" : "预约这趟车" }}
        </button>
      </view>
    </view><view
      v-if="!loading && !error && !shown.length"
      class="bus-card bus-empty"
    >
      <text class="bus-empty-mark">⌁</text>这个日期暂无合适班次<br><text
        class="bus-help"
      >
        换个日期看看，或联系老板确认发车安排。
      </text><button
        class="bus-button secondary"
        style="margin-top: 28rpx"
        @click="merchant && uni.makePhoneCall({ phoneNumber: merchant.phone })"
      >
        联系老板
      </button>
    </view><view class="bus-help" style="text-align: center; margin-top: 36rpx">
      多人同行可以一起预约，到站后逐人确认上车。
    </view>
  </view>
</template>

<style scoped>
.home-page {
  padding-top: calc(24rpx + env(safe-area-inset-top));
}
.search-card {
  margin-top: -8rpx;
}
.city-input {
  width: 42%;
  font-size: 32rpx;
  font-weight: 600;
  height: 80rpx;
}
.date-picker {
  display: flex;
  gap: 8rpx;
  margin-top: 24rpx;
  flex-wrap: wrap;
}
.date-chip {
  font-size: 22rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  background: #f4f5ef;
  color: #7f8e7b;
  min-height: 44rpx;
  line-height: 44rpx;
}
.date-chip.chosen {
  background: #e5f1e5;
  color: #147d73;
  font-weight: 600;
}
</style>
