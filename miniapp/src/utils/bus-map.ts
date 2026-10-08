import type { IStation } from '@/api/bus'
import { toast } from '@/api/bus'

/** Map boundary: GCJ-02 throughout. Navigation never changes arrival status. */
export function navigateToStation(station: IStation): void {
  if (station.latitude == null || station.longitude == null) {
    toast(new Error('站点坐标待老板确认，请查看候车说明或联系老板'))
    return
  }
  // #ifdef MP-WEIXIN
  uni.openLocation({
    latitude: Number(station.latitude),
    longitude: Number(station.longitude),
    name: station.name,
    address: station.address,
    fail: () => toast(new Error('地图打开失败，可复制地址或联系老板')),
  })
  // #endif
  // #ifndef MP-WEIXIN
  toast(new Error('请在微信小程序中打开地图导航；可以先复制候车地址'))
  // #endif
}
export function distanceMeters(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number,
): number {
  const rad = (n: number) => (n * Math.PI) / 180
  const a
    = Math.sin(rad(lat2 - lat1) / 2) ** 2
      + Math.cos(rad(lat1))
      * Math.cos(rad(lat2))
      * Math.sin(rad(lng2 - lng1) / 2) ** 2
  return Math.round(6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, a))))
}
export async function nearbyStations(
  stations: IStation[],
): Promise<IStation[]> {
  const location = await new Promise<UniApp.GetLocationSuccess>(
    (resolve, reject) =>
      uni.getLocation({ type: 'gcj02', success: resolve, fail: reject }),
  ).catch(() => null)
  if (!location) {
    toast(new Error('未获取定位，你仍可手动选择上车点'))
    return stations
  }
  return stations
    .map(s => ({
      ...s,
      distance:
        s.latitude == null || s.longitude == null
          ? Infinity
          : distanceMeters(
              location.latitude,
              location.longitude,
              Number(s.latitude),
              Number(s.longitude),
            ),
    }))
    .sort((a, b) => a.distance - b.distance)
}
