<script setup lang="ts">
import type { IMerchant } from '@/api/bus'
import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import {
  busApi,
  loggedIn,
  loginPassenger,
  merchantApi,
  toast,
} from '@/api/bus'

definePage({ style: { navigationBarTitleText: '我的' } })
const merchant = ref<IMerchant>()
const name = ref('')
const demo = ref(false)
async function refresh() {
  name.value = uni.getStorageSync('bus-passenger')?.nickname || ''
  demo.value = Boolean(uni.getStorageSync('bus-demo-session'))
  try {
    merchant.value = await merchantApi()
  }
  catch (e) {
    toast(e)
  }
}
async function login() {
  if (await loginPassenger())
    refresh()
}
async function logout() {
  try {
    await busApi('/auth/logout', 'POST', {})
  }
  catch {}
  uni.removeStorageSync('bus-passenger-token')
  uni.removeStorageSync('bus-passenger')
  uni.removeStorageSync('bus-demo-session')
  refresh()
}
onShow(refresh)
</script>

<template>
  <view class="bus-page">
    <view class="bus-hero">
      <text class="bus-eyebrow">归途 · 乘客服务</text><view class="bus-title">
        {{ name || "你好，准备出发吗？" }}
      </view><view class="bus-subtitle">
        {{
          name
            ? "你的预约和乘车安排，都在这里。"
            : "登录后预约座位，查看你的行程。"
        }}
      </view>
    </view><view v-if="demo" class="bus-demo">
      当前为开发演示身份，仅用于本地联调。
    </view><button v-if="!loggedIn()" class="bus-button" @click="login">
      乘客登录
    </button><view class="bus-card">
      <view
        class="bus-note-line bus-row"
        @click="uni.switchTab({ url: '/pages/orders/index' })"
      >
        <text>我的预约</text><text class="bus-link">查看 →</text>
      </view><view
        class="bus-note-line bus-row"
        @click="merchant && uni.makePhoneCall({ phoneNumber: merchant.phone })"
      >
        <text>联系老板</text><text class="bus-link">{{ merchant?.phone }}</text>
      </view><view class="bus-note-line">
        <text>乘车须知</text><view class="bus-help">
          提前10分钟到上车点，保持电话畅通。报到不代表已上车，工作人员会逐人确认。
        </view>
      </view><view class="bus-note-line">
        <text>付款说明</text><view class="bus-help">
          第一版使用现金或微信转账现场付款。工作人员登记后，预约详情显示“已收款”。
        </view>
      </view><view class="bus-note-line">
        <text>信息使用说明</text><view class="bus-help">
          姓名和联系电话仅用于预约、通知与乘车核对；定位只用于查找附近上车点，不存储你的行踪。
        </view>
      </view>
    </view><view v-if="name" class="bus-actions">
      <button
        v-if="merchant?.demo_login"
        class="bus-button secondary"
        @click="login"
      >
        切换演示乘客
      </button><button class="bus-button secondary" @click="logout">
        退出登录
      </button>
    </view>
  </view>
</template>
