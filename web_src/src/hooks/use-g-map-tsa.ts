import store from '@/store'
import { getRoot } from '@/root'
import { ELocalStorageKey, EDeviceTypeName } from '@/types'
import { getDeviceBySn } from '@/api/manage'
import { message } from 'ant-design-vue'
import dockIcon from '@/assets/icons/dock.png'
import rcIcon from '@/assets/icons/rc.png'
import droneIcon from '@/assets/icons/drone.png'

const fetchingSn: Record<string, boolean> = {}

export function deviceTsaUpdate () {
  const root = getRoot()
  let AMap = root.$aMap

  const icons = new Map([
    [EDeviceTypeName.Aircraft, droneIcon],
    [EDeviceTypeName.Gateway, rcIcon],
    [EDeviceTypeName.Dock, dockIcon]
  ])
  const markers = store.state.markerInfo.coverMap
  const paths = store.state.markerInfo.pathMap

  let trackLine = null as any
  function getTrackLineInstance () {
    if (!trackLine) {
      trackLine = new AMap.Polyline({
        map: root.$map,
        strokeColor: '#939393' // 线颜色
      })
    }
    return trackLine
  }

  function initIcon (type: number) {
    return new AMap.Icon({
      image: icons.get(type),
      imageSize: new AMap.Size(40, 40),
      size: new AMap.Size(40, 40)
    })
  }

  function initMarker (type: number, name: string, sn: string, lng?: number, lat?: number) {
    if (markers[sn]) {
      return
    }
    if (root.$aMap === undefined) {
      return
    }
    AMap = root.$aMap
    markers[sn] = new AMap.Marker({
      position: new AMap.LngLat(lng || 113.943225499, lat || 22.577673716),
      icon: initIcon(type),
      title: name,
      anchor: 'top-center',
      offset: [0, -20],
    })
    root.$map.add(markers[sn])
    // markers[sn].on('moving', function (e: any) {
    //   let path = paths[sn]
    //   if (!path) {
    //     paths[sn] = e.passedPath
    //     return
    //   }
    //   path.push(e.passedPath[0])
    //   path.push(e.passedPath[1])
    //   getTrackLineInstance().setPath(path)
    // })
  }

  function removeMarker (sn: string) {
    if (!markers[sn]) {
      return
    }
    root.$map.remove(markers[sn])
    getTrackLineInstance().setPath([])
    delete markers[sn]
    delete paths[sn]
    delete fetchingSn[sn]
  }

  function addMarker (sn: string, lng?: number, lat?: number) {
    if (fetchingSn[sn]) return // 拦截高频重复请求
    fetchingSn[sn] = true
    getDeviceBySn(localStorage.getItem(ELocalStorageKey.WorkspaceId)!, sn)
      .then(data => {
        if (data.code !== 0) {
          fetchingSn[sn] = false // 允许重试
          message.error(data.message)
          return
        }
        initMarker(data.data.domain, data.data.nickname, sn, lng, lat)
      }).catch(() => {
        fetchingSn[sn] = false // 允许重试
      })
  }

  function moveTo (sn: string, lng: number, lat: number) {
    let marker = markers[sn]
    if (!marker) {
      addMarker(sn, lng, lat)
      marker = markers[sn]
      return
    }
    // 使用 setPosition 瞬间同步高频更新位置，彻底规避 AMAP marker.moveTo 在高频 OSD 下积累海量动画计时器造成的内存持续激增
    if (typeof marker.setPosition === 'function') {
      marker.setPosition([lng, lat])
    } else {
      marker.moveTo([lng, lat], {
        duration: 100,
        autoRotation: false
      })
    }
  }

  return {
    marker: markers,
    initMarker,
    removeMarker,
    moveTo
  }
}
