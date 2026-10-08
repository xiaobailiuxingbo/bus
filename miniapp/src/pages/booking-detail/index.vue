<script setup lang="ts">
import type { IBooking, IMerchant } from '@/api/bus'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import {
  busApi,
  fareText,
  merchantApi,
  openStation,
  riderStatus,
  toast,
} from '@/api/bus'
import { navigateToStation } from '@/utils/bus-map'

definePage({ style: { navigationBarTitleText: '我的乘车预约' } })
const id = ref('')
const booking = ref<IBooking>()
const merchant = ref<IMerchant>()
const error = ref('')
const busy = ref(false)
function stop(station: string) {
  return booking.value?.trip.snapshot.stops.find(s => s.station_id === station)
}
async function load() {
  if (!id.value)
    return
  try {
    booking.value = await busApi(`/bus/bookings/${id.value}`)
    merchant.value = await merchantApi()
    error.value = ''
  }
  catch (e) {
    error.value = (e as Error).message
  }
}
async function action(rider: string, kind: string) {
  if (busy.value)
    return
  if (kind === 'cancel') {
    const confirmed = await new Promise<boolean>(resolve =>
      uni.showModal({
        title: '取消这位乘客的预约？',
        content: '只取消选中的乘车人，其他同行人的预约不受影响。',
        success: r => resolve(r.confirm),
      }),
    )
    if (!confirmed)
      return
  }
  busy.value = true
  try {
    await busApi(`/bus/riders/${rider}/${kind}`, 'POST', {})
    await load()
    uni.showToast({
      title: kind === 'arrive' ? '已报到，等候上车' : '已取消',
      icon: 'none',
    })
  }
  catch (e) {
    toast(e)
  }
  finally {
    busy.value = false
  }
}
onLoad((q) => {
  id.value = String(q?.id || '')
  load()
})
onShow(load)
</script>

<template>
  <view class="bus-page">
    <view v-if="error" class="bus-card">
      <view class="bus-empty">
        {{ error }}
      </view><button class="bus-button secondary" @click="load">
        重新加载
      </button>
    </view><template v-if="booking">
      <view class="bus-hero">
        <text class="bus-eyebrow">
          预约已记录 ·
          {{
            booking.trip.status === "CANCELLED"
              ? "班次已取消"
              : booking.trip.status === "FINISHED"
                ? "班次已结束"
                : "请按时到站"
          }}
        </text><view class="bus-title">
          {{ booking.trip.snapshot.name }}
        </view><view class="bus-subtitle">
          {{ booking.trip.depart_at.replace("T", " ").slice(0, 16) }}
        </view><view class="bus-subtitle">
          {{ booking.trip.snapshot.plate }}
        </view>
      </view><view v-if="booking.trip.demo" class="bus-demo">
        演示预约 · 非实际车票
      </view><view class="bus-card">
        <view class="bus-row">
          <text>联系人 {{ booking.contact_name }}</text><text class="bus-help">{{ booking.phone }}</text>
        </view><view class="bus-help" style="margin-top: 16rpx">
          预约编号 {{ booking.id.slice(-8).toUpperCase() }}
        </view>
      </view><view class="bus-section">
        同行乘车人<text class="bus-help">上车与付款分别记录</text>
      </view><view v-for="r in booking.riders" :key="r.id" class="bus-card">
        <view class="bus-row">
          <text style="font-size: 34rpx; font-weight: 600">{{ r.name }}</text><text
            class="bus-pill"
            :class="[
              r.status === 'BOARDED'
                ? ''
                : r.status === 'CANCELLED'
                  ? 'muted'
                  : 'warm',
            ]"
          >
            {{ riderStatus[r.status] }}
          </text>
        </view><view
          class="bus-note-line"
          @click="stop(r.board_station) && openStation(stop(r.board_station)!)"
        >
          <view class="bus-row">
            <text>
              {{ stop(r.board_station)?.name }} →
              {{ stop(r.alight_station)?.name }}
            </text><text class="bus-link">详情 →</text>
          </view><view class="bus-help">
            {{ r.board_at.replace("T", " ").slice(0, 16) }} 到站
          </view><view class="bus-help">
            {{ stop(r.board_station)?.address }}
          </view>
        </view><view class="bus-row" style="margin-top: 24rpx">
          <text class="bus-price">¥{{ fareText(r.fare_cents) }}</text><text class="bus-pill" :class="{ warm: !r.paid }">
            {{
              r.paid ? "已登记收款" : "现场付款 · 未收"
            }}
          </text>
        </view><view
          v-if="!['CANCELLED', 'MISSED'].includes(r.status)"
          class="bus-actions"
        >
          <button
            class="bus-button secondary"
            @click="
              stop(r.board_station) && navigateToStation(stop(r.board_station)!)
            "
          >
            导航到站点
          </button><button
            v-if="
              r.status === 'RESERVED'
                && !['FINISHED', 'CANCELLED'].includes(booking.trip.status)
            "
            class="bus-button"
            :disabled="busy"
            @click="action(r.id, 'arrive')"
          >
            我已到站
          </button>
        </view><view
          v-if="['RESERVED', 'ARRIVED'].includes(r.status) && !r.paid"
          class="bus-link"
          style="margin-top: 24rpx; text-align: center"
          @click="action(r.id, 'cancel')"
        >
          取消这位乘客的预约
        </view>
      </view><view class="bus-help">
        报到表示你已到站，上车需要工作人员确认。地图导航不会自动报到。
      </view><button
        class="bus-button secondary"
        style="margin-top: 28rpx"
        @click="merchant && uni.makePhoneCall({ phoneNumber: merchant.phone })"
      >
        联系老板
      </button>
    </template>
  </view>
</template>
