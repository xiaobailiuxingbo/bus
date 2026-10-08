<script setup lang="ts">
import type { IBooking, IStation, ITrip } from '@/api/bus'
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import {
  busApi,
  ensurePassenger,
  entry,
  fareText,
  openStation,
  requestKey,
  toast,
} from '@/api/bus'
import { nearbyStations } from '@/utils/bus-map'

definePage({ style: { navigationBarTitleText: '预约这趟车' } })
const trip = ref<ITrip>()
const busy = ref(false)
const error = ref('')
const contact = ref('')
const phone = ref('')
const key = ref(requestKey())
const people = ref([{ name: '', fareIndex: 0 }])
const defaultFareIndex = ref(0)
const nearest = ref<IStation[]>([])
function stationName(id: string) {
  return (
    trip.value?.snapshot.stops.find(s => s.station_id === id)?.name || ''
  )
}
const fareLabels = computed(
  () =>
    trip.value?.snapshot.fares.map(
      f =>
        `${stationName(f.from)} → ${stationName(f.to)} · ¥${fareText(f.cents)}`,
    ) || [],
)
const total = computed(() =>
  people.value.reduce(
    (sum, p) => sum + (trip.value?.snapshot.fares[p.fareIndex]?.cents || 0),
    0,
  ),
)
async function load(id: string) {
  try {
    trip.value = await busApi(
      `/bus/trips/${id}?entry=${encodeURIComponent(entry())}`,
    )
    const stops = trip.value!.snapshot.stops
    defaultFareIndex.value = Math.max(
      0,
      trip.value!.snapshot.fares.findIndex(
        f =>
          f.from === stops[0].station_id
          && f.to === stops[stops.length - 1].station_id,
      ),
    )
    people.value[0].fareIndex = defaultFareIndex.value
  }
  catch (e) {
    error.value = (e as Error).message
  }
}
async function locate() {
  if (!trip.value)
    return
  nearest.value = await nearbyStations(
    trip.value.snapshot.stops.filter(s =>
      trip.value!.snapshot.fares.some(f => f.from === s.station_id),
    ),
  )
}
async function submit() {
  if (busy.value || !trip.value)
    return
  if (
    !contact.value.trim()
    || !/^1[3-9]\d{9}$/.test(phone.value)
    || people.value.some(p => !p.name.trim())
  ) {
    toast(new Error('请填写联系人、有效手机号和每位乘客姓名'))
    return
  }
  if (!(await ensurePassenger()))
    return
  busy.value = true
  try {
    const booking = await busApi<IBooking>('/bus/bookings', 'POST', {
      trip_id: trip.value.id,
      contact_name: contact.value,
      phone: phone.value,
      request_key: key.value,
      riders: people.value.map(p => ({
        name: p.name,
        board_station: trip.value!.snapshot.fares[p.fareIndex].from,
        alight_station: trip.value!.snapshot.fares[p.fareIndex].to,
      })),
    })
    uni.redirectTo({ url: `/pages/booking-detail/index?id=${booking.id}` })
  }
  catch (e) {
    toast(e)
  }
  finally {
    busy.value = false
  }
}
onLoad(query => query?.id && load(String(query.id)))
</script>

<template>
  <view class="bus-page">
    <view v-if="error" class="bus-empty">
      {{ error }}
    </view><template v-if="trip">
      <view class="bus-card">
        <text class="bus-eyebrow">这趟归途</text><view class="bus-title">
          {{ trip.snapshot.name }}
        </view><view class="bus-subtitle">
          {{ trip.depart_at.replace("T", " ").slice(0, 16) }} ·
          {{ trip.snapshot.vehicle_name }}
        </view><view class="bus-row" style="margin-top: 24rpx">
          <text class="bus-pill">
            余位 {{ trip.capacity - trip.occupied }} 人
          </text><text class="bus-link" @click="locate">查找附近上车点</text>
        </view>
      </view><view v-if="trip.demo" class="bus-demo">
        演示班次 · 非实际售票，勿据此乘车
      </view><view
        v-for="s in nearest"
        :key="s.station_id"
        class="bus-note-line"
        @click="openStation(s)"
      >
        {{ s.name
        }}<text class="bus-help">
          {{
            s.distance != null && Number.isFinite(s.distance)
              ? `直线距离 ${s.distance} 米`
              : "坐标待确认"
          }}
          →
        </text>
      </view><view class="bus-section">
        联系人信息
      </view><view class="bus-card">
        <text class="bus-help">
          用于班次通知和上车联系，手机号由你填写，尚未经过短信验证。
        </text><input
          v-model="contact"
          class="bus-input"
          maxlength="80"
          placeholder="联系人姓名"
        ><input
          v-model="phone"
          class="bus-input"
          type="number"
          maxlength="11"
          placeholder="联系人手机号"
        >
      </view><view class="bus-section">
        谁一起出发？<text class="bus-help">最多 6 人</text>
      </view><view v-for="(p, i) in people" :key="i" class="bus-card">
        <view class="bus-row">
          <text class="bus-pill">乘车人 {{ i + 1 }}</text><text
            v-if="people.length > 1"
            class="bus-link"
            @click="people.splice(i, 1)"
          >
            移除
          </text>
        </view><input
          v-model="p.name"
          class="bus-input"
          maxlength="80"
          placeholder="乘车人姓名"
        ><text class="bus-label">上下车地点 · 票价</text><wd-picker
          v-model="p.fareIndex"
          :columns="fareLabels.map((label, value) => ({ label, value }))"
          title="选择上下车区间"
        >
          <view class="bus-select">
            {{ fareLabels[p.fareIndex] }} ⌄
          </view>
        </wd-picker>
      </view><button
        class="bus-button secondary"
        :disabled="people.length >= 6"
        @click="people.push({ name: '', fareIndex: defaultFareIndex })"
      >
        ＋ 添加同行乘客
      </button><view class="bus-help" style="margin-top: 24rpx">
        预约成功后占用座位。请按上车点时间到站，现金或微信转账由工作人员现场收取。
      </view><view class="bus-fixed bus-row">
        <view>
          <text class="bus-help">{{ people.length }} 位乘客合计</text><view class="bus-price">
            ¥{{ fareText(total) }}
          </view>
        </view><button
          class="bus-button"
          :loading="busy"
          :disabled="
            busy
              || trip.status !== 'OPEN'
              || trip.capacity - trip.occupied < people.length
          "
          @click="submit"
        >
          确认预约
        </button>
      </view>
    </template>
  </view>
</template>
