<script setup lang="ts">
import type { IMerchant, IStation } from '@/api/bus'
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { busApi, entry, merchantApi } from '@/api/bus'
import { navigateToStation } from '@/utils/bus-map'

definePage({ style: { navigationBarTitleText: '候车站点' } })
const station = ref<IStation>()
const merchant = ref<IMerchant>()
const error = ref('')
const photoFailed = ref(false)
onLoad(async (q) => {
  try {
    station.value = await busApi(
      `/bus/stations/${q?.id}?entry=${encodeURIComponent(entry())}`,
    )
    merchant.value = await merchantApi()
  }
  catch (e) {
    error.value = (e as Error).message
  }
})
</script>

<template>
  <view class="bus-page">
    <view v-if="error" class="bus-empty">
      {{ error }}
    </view><template v-if="station">
      <view class="bus-title">
        {{ station.name }}
      </view><view v-if="merchant?.demo" class="bus-demo">
        演示站点 · 非实际候车地点
      </view><image
        v-if="station.photo && !photoFailed"
        :src="station.photo"
        mode="widthFix"
        style="width: 100%; border-radius: 28rpx; margin: 24rpx 0"
        @error="photoFailed = true"
      /><view
        v-else
        class="bus-hero"
        style="text-align: center; padding: 60rpx 30rpx"
      >
        <view style="font-size: 70rpx">
          ⌖
        </view><view class="bus-subtitle">
          现场照片待老板补充
        </view>
      </view><view class="bus-card">
        <text class="bus-eyebrow">候车地址</text><view class="bus-note-line">
          {{ station.address }}
        </view><view class="bus-note-line">
          附近地标：{{ station.landmark || "待补充" }}
        </view><view class="bus-note-line">
          {{ station.instructions || "请提前到站，留意车辆并保持电话畅通。" }}
        </view>
      </view><button class="bus-button" @click="navigateToStation(station)">
        打开地图导航
      </button><view class="bus-actions">
        <button
          class="bus-button secondary"
          @click="uni.setClipboardData({ data: station.address })"
        >
          复制地址
        </button><button
          class="bus-button secondary"
          @click="
            merchant && uni.makePhoneCall({ phoneNumber: merchant.phone })
          "
        >
          联系老板
        </button>
      </view><view class="bus-help" style="margin-top: 28rpx">
        定位失败也可按地址、地标和现场说明找站点；导航不会自动报到。
      </view>
    </template>
  </view>
</template>
