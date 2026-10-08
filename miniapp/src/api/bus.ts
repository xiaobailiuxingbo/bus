import { getEnvBaseUrl } from '@/utils'

export interface IStation {
  id: string
  station_id: string
  name: string
  address: string
  landmark: string
  instructions: string
  photo: string
  latitude: number | null
  longitude: number | null
  planned_at: string
  distance?: number
}
export interface IFare {
  from: string
  to: string
  cents: number
}
export interface ITrip {
  id: string
  depart_at: string
  capacity: number
  occupied: number
  status: string
  demo: boolean
  snapshot: {
    name: string
    origin: string
    destination: string
    vehicle_name: string
    plate: string
    stops: IStation[]
    fares: IFare[]
  }
}
export interface IRider {
  id: string
  name: string
  board_station: string
  alight_station: string
  board_at: string
  fare_cents: number
  status: string
  paid: boolean
}
export interface IBooking {
  id: string
  contact_name: string
  phone: string
  created_at: string
  trip: ITrip
  riders: IRider[]
}
export interface IMerchant {
  name: string
  phone: string
  demo: boolean
  demo_login: boolean
}
export const fareText = (cents: number) => (cents / 100).toFixed(2)
export function entry() {
  return String(uni.getStorageSync('bus-entry') || 'qinzhou-demo')
}
export function loggedIn() {
  return Boolean(uni.getStorageSync('bus-passenger-token'))
}
export function requestKey() {
  return `${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`
}
export async function busApi<T>(
  path: string,
  method: 'GET' | 'POST' = 'GET',
  data?: unknown,
): Promise<T> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${getEnvBaseUrl()}/app${path}`,
      method,
      data: data as any,
      header: {
        'X-Passenger-Token': uni.getStorageSync('bus-passenger-token') || '',
      },
      success: (response) => {
        const result = response.data as { code: number, msg: string, data: T }
        if (result.code === 200) {
          resolve(result.data)
        }
        else {
          if (result.code === 401) {
            uni.removeStorageSync('bus-passenger-token')
            uni.removeStorageSync('bus-passenger')
          }
          reject(new Error(result.msg || '服务暂不可用'))
        }
      },
      fail: () => reject(new Error('网络连接失败，请稍后重试')),
    })
  })
}
export function merchantApi() {
  return busApi<IMerchant>(`/bus/merchant?entry=${encodeURIComponent(entry())}`)
}
export function toast(error: unknown) {
  uni.showToast({
    title: error instanceof Error ? error.message : '操作未完成',
    icon: 'none',
  })
}
export async function loginPassenger(): Promise<boolean> {
  try {
    const merchant = await merchantApi()
    let result: {
      token: string
      passenger: { nickname: string }
      demo: boolean
    }
    if (merchant.demo_login) {
      const choice = await new Promise<number>((resolve, reject) =>
        uni.showActionSheet({
          itemList: ['演示乘客一', '演示乘客二', '演示乘客三'],
          success: r => resolve(r.tapIndex + 1),
          fail: reject,
        }),
      )
      result = await busApi('/auth/dev-login', 'POST', {
        entry: entry(),
        index: choice,
      })
    }
    else {
      // #ifdef MP-WEIXIN
      const wx = await new Promise<UniApp.LoginRes>((resolve, reject) =>
        uni.login({ provider: 'weixin', success: resolve, fail: reject }),
      )
      result = await busApi('/auth/wx-login', 'POST', {
        entry: entry(),
        code: wx.code,
      })
      // #endif
      // #ifndef MP-WEIXIN
      throw new Error('请在微信小程序中登录；H5仅用于开发演示')
      // #endif
    }
    uni.setStorageSync('bus-passenger-token', result.token)
    uni.setStorageSync('bus-passenger', result.passenger)
    uni.setStorageSync('bus-demo-session', result.demo)
    return true
  }
  catch (error) {
    toast(error)
    return false
  }
}
export async function ensurePassenger(): Promise<boolean> {
  return loggedIn() || loginPassenger()
}
export function openStation(station: IStation) {
  uni.navigateTo({
    url: `/pages/station/index?id=${station.station_id || station.id}`,
  })
}
export const riderStatus: Record<string, string> = {
  RESERVED: '待到站',
  ARRIVED: '已报到',
  BOARDED: '已上车',
  CANCELLED: '已取消',
  MISSED: '未乘车',
}
