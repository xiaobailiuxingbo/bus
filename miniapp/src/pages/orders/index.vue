<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { busApi, ensurePassenger, loggedIn } from '@/api/bus'

definePage({ style: { navigationBarTitleText: '我的预约' } })
const orders = ref<any[]>([])
const error = ref('')
const authenticated = ref(false)
const loading = ref(false)
async function load() {
  authenticated.value = loggedIn()
  if (!authenticated.value)
    return
  loading.value = true
  try {
    orders.value = await busApi('/bus/bookings')
    error.value = ''
  }
  catch (e) {
    error.value = (e as Error).message
    authenticated.value = loggedIn()
  }
  finally {
    loading.value = false
  }
}
async function login() {
  if (await ensurePassenger())
    await load()
}
onShow(load)
</script>

<template>
  <view class="bus-page">
    <view class="bus-eyebrow" style="margin-top: 30rpx">
      归途 · 我的行程
    </view><view class="bus-title">
      每一趟，都在这里。
    </view><view class="bus-subtitle">
      查看候车地点，报到和乘车状态。
    </view><view v-if="!authenticated" class="bus-card">
      <view class="bus-empty">
        <text class="bus-empty-mark">▤</text>登录后查看你的预约
      </view><button class="bus-button" @click="login">
        乘客登录
      </button>
    </view><template v-else>
      <view v-if="error" class="bus-card">
        <view class="bus-help">
          {{ error }}
        </view><button class="bus-button secondary" @click="load">
          重新加载
        </button>
      </view><view v-if="loading" class="bus-empty">
        正在查找你的行程…
      </view><view
        v-for="o in orders"
        :key="o.id"
        class="bus-ticket"
        @click="
          uni.navigateTo({ url: `/pages/booking-detail/index?id=${o.id}` })
        "
      >
        <view class="ticket-head">
          <text>{{ o.depart_at.replace("T", " ").slice(0, 16) }}</text><text class="bus-pill">
            {{
              o.trip_status === "CANCELLED"
                ? "班次已取消"
                : o.trip_status === "FINISHED"
                  ? "班次已结束"
                  : "查看预约"
            }}
          </text>
        </view><view class="ticket-main">
          <text class="ticket-big" style="font-size: 36rpx">
            {{
              o.snapshot.name
            }}
          </text>
        </view><view class="ticket-foot">
          <text class="bus-help">联系人 {{ o.contact_name }}</text><text class="bus-link">查看乘车详情 →</text>
        </view>
      </view><view v-if="!orders.length && !error && !loading" class="bus-empty">
        <text class="bus-empty-mark">▤</text>还没有预约，去选一趟合适的车吧。<button
          class="bus-button secondary"
          style="margin-top: 24rpx"
          @click="uni.switchTab({ url: '/pages/index/index' })"
        >
          去订车
        </button>
      </view>
    </template>
  </view>
</template>
