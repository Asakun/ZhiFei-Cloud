/**
 * 智能优先级分级 OSD 数据节流器 (OsdThrottle)
 *
 * 调度策略：
 * 1. 优先级别 1 (最高)：正在飞行的无人机 (Flying Drones) -> 100ms 刷新 (~10 FPS)
 * 2. 优先级别 2 (中级)：当前 UI 列表页/可视范围内的设备 (Visible Page Devices) -> 150ms 刷新
 * 3. 优先级别 3 (普通)：在后台其他分页静止待机的设备 (Hidden / Off-page Devices) -> 1000ms 低频批量刷新
 */

type CommitFn = (data: any) => void

interface OsdBuffer {
  latest: any            // 该 SN 最新帧数据对象
  dirty: boolean         // 是否有新帧等待提交
  lastCommitTime: number // 上次提交给 store.commit 的时间戳
  targetInterval: number // 当前帧按设备状态计算出的刷新间隔
}

export class OsdThrottle {
  private _buffers: Map<string, OsdBuffer> = new Map()
  private _commitFn: CommitFn
  private _timer: ReturnType<typeof setInterval> | null = null
  private _visibleSns: Set<string> = new Set() // 当前 UI 可视范围内的 SN 集合

  constructor(commitFn: CommitFn) {
    this._commitFn = commitFn
    this._start()
  }

  /** 更新当前 UI 界面可视范围内的设备 SN 列表 */
  setVisibleSns(sns: string[]) {
    this._visibleSns = new Set(sns)
  }

  /**
   * 压入 OSD 数据，并根据数据类型与运行状态动态计算优先级间隔
   * @param sn           设备序列号
   * @param data         WebSocket payload.data 原始对象
   * @param isDeviceOsd  是否为无人机 DeviceOsd
   */
  push(sn: string, data: any, isDeviceOsd = false) {
    if (!sn) return

    // 性能与资源优选策略：当处于虚拟座舱活动页面时，只处理这一个机场与对应无人机的消息数据，拦截并弃置其他设备高频OSD
    const activeCockpit = typeof window !== 'undefined' ? (window as any)._activeCockpitSn : null
    if (activeCockpit && (activeCockpit.dockSn || activeCockpit.deviceSn)) {
      if (sn !== activeCockpit.dockSn && sn !== activeCockpit.deviceSn && sn !== activeCockpit.gatewaySn) {
        return
      }
    }

    let buf = this._buffers.get(sn)
    if (!buf) {
      buf = { latest: null, dirty: false, lastCommitTime: 0, targetInterval: 1000 }
      this._buffers.set(sn, buf)
    }

    buf.latest = data
    buf.dirty = true

    // 判断是否处于飞行状态 (高度 > 0.5m 或 速度 > 0.2m/s 或 工作模式非离线/待机)
    const isFlying = isDeviceOsd && (
      (data?.mode_code !== undefined && data.mode_code !== 0 && data.mode_code !== 14) ||
      Number(data?.elevation || 0) > 0.5 ||
      Number(data?.height || 0) > 0.5 ||
      Number(data?.horizontal_speed || 0) > 0.2
    )

    // 确定目标刷新间隔
    let targetInterval = 1000 // 默认非可视设备：1000ms 低频

    if (isFlying) {
      targetInterval = 100   // 飞行无人机：100ms (10 FPS 顺畅实时显示，极大幅降低内存开销和 Vue 深度渲染奔溃风险)
    } else if (this._visibleSns.has(sn)) {
      targetInterval = 150   // 当前页可视设备：150ms 刷新
    }
    buf.targetInterval = targetInterval

    // 若已经达到/超过刷新间隔，立即提交并重置时间
    const now = Date.now()
    if (buf.dirty && (now - buf.lastCommitTime >= targetInterval)) {
      this._commitSingle(buf, now)
    }
  }

  private _commitSingle(buf: OsdBuffer, now: number) {
    try {
      this._commitFn(buf.latest)
      buf.lastCommitTime = now
    } catch (e) {
      // 静默捕获异常
    }
    buf.dirty = false
  }

  /** 定时轮询调度器 (50ms 基频轮询，减缓 CPU 轮询和主线程开销) */
  private _start() {
    this._timer = setInterval(() => {
      const now = Date.now()
      this._buffers.forEach((buf) => {
        if (!buf.dirty) return

        if (now - buf.lastCommitTime >= buf.targetInterval) {
          this._commitSingle(buf, now)
        }
      })
    }, 50)
  }

  destroy() {
    if (this._timer !== null) {
      clearInterval(this._timer)
      this._timer = null
    }
    this._buffers.clear()
    this._visibleSns.clear()
  }
}
