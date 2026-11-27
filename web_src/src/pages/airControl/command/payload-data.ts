/**
 * 负载浮窗下行数据解析
 *
 * 兼容三种格式：
 * 1) 键值串：t:1,j:105.478,w:29.304,...
 * 2) JSON 串：{"temp":"25.3",...}
 * 3) 带单位分节的纯文本（Sniffer4D 大气监测等）：
 *    ug/m3:
 *    PM10: 55.0     PM2.5: 33.0
 *
 * 解析结果同时给出每个通道的单位，供图表与表格展示。
 */
export interface ParsedPayload {
  values: Record<string, number>
  units: Record<string, string>
}

export const emptyPayload = (): ParsedPayload => ({ values: {}, units: {} })

export const parseKvString = (raw: string): ParsedPayload => {
  const values: Record<string, number> = {}
  raw.split(',').forEach(pair => {
    const idx = pair.indexOf(':')
    if (idx < 1) return
    const key = pair.slice(0, idx).trim()
    const val = parseFloat(pair.slice(idx + 1).trim())
    if (key && !isNaN(val)) values[key] = val
  })
  return { values, units: {} }
}

/** 逐行解析 "单位:" 小节标题 + "通道: 数值"；单位对小节内所有通道生效 */
export const parseTextPayload = (raw: string): ParsedPayload => {
  const values: Record<string, number> = {}
  const units: Record<string, string> = {}
  let unit = ''

  // 兼容后端/数据库以 JSON 字面量形式存储的转义序列
  const normalized = raw
    .replace(/\\r\\n/g, '\n')
    .replace(/\\r/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t')

  normalized.split(/\r?\n/).forEach(line => {
    // Chamber 节头行（"Chamber:" 本身）直接跳过
    if (/Chamber\s*[:：]/i.test(line)) return

    // Chamber 环境数据行：{数值}℃,{数值}%RH,{数值}kPa
    // 解析为 Temp / RH / Press 三个独立通道
    if (line.includes('℃') || line.includes('%RH') || line.includes('kPa')) {
      const nameMap: Record<string, string> = { '℃': 'Temp', '%RH': 'RH', 'kPa': 'Press' }
      const re2 = /(-?\d+(?:\.\d+)?)(℃|%RH|kPa)/g
      let mc: RegExpExecArray | null
      while ((mc = re2.exec(line))) {
        const name = nameMap[mc[2]]
        if (name) { values[name] = parseFloat(mc[1]); units[name] = mc[2] }
      }
      return
    }

    // 单位行：整行仅包含 "单位名称:"，如 "ug/m3:" 或 "ppm:"
    const unitLine = line.match(/^\s*([^\s:：\d][^:：]*?)\s*[:：]\s*$/)
    if (unitLine) {
      unit = unitLine[1].trim()
      return
    }

    // 数据行：一行可包含多个 "通道: 数值" 对，支持制表符或空格分隔
    const re = /([^\s:：,\t]+)\s*[:：]\s*(-?\d+(?:\.\d+)?)/g
    let m: RegExpExecArray | null
    while ((m = re.exec(line))) {
      const key = m[1].trim()
      const val = parseFloat(m[2])
      if (!key || isNaN(val)) continue
      values[key] = val
      if (unit) units[key] = unit
    }
  })
  return { values, units }
}

/** 解析单条浮窗数据的 value 字段（可能是字符串，也可能是包了一层的对象） */
export const parsePayload = (value: any): ParsedPayload => {
  const fromString = (s: string): ParsedPayload => {
    const str = s.trim()
    if (!str) return emptyPayload()
    if (str.startsWith('{')) {
      try {
        const p = JSON.parse(str)
        const values: Record<string, number> = {}
        for (const [k, v] of Object.entries(p)) {
          const n = parseFloat(v as string)
          if (!isNaN(n)) values[k] = n
        }
        if (Object.keys(values).length) return { values, units: {} }
      } catch (_) {
        /* 不是合法 JSON，继续按其他格式尝试 */
      }
    }
    if (str.includes(':') || str.includes('：')) {
      const r = parseTextPayload(str)
      if (Object.keys(r.values).length) return r
    }
    if (str.includes(':') && str.includes(',')) {
      const r = parseKvString(str)
      if (Object.keys(r.values).length) return r
    }
    return emptyPayload()
  }
  if (value == null) return emptyPayload()
  if (typeof value === 'string') return fromString(value)
  if (typeof value === 'object' && !Array.isArray(value)) {
    for (const val of Object.values(value)) {
      if (typeof val !== 'string') continue
      const r = fromString(val)
      if (Object.keys(r.values).length) return r
    }
  }
  return emptyPayload()
}

/** 这批浮窗数据是否存在至少一条可展示的记录（用于决定按钮是否显示） */
export const hasPayloadPoints = (points: any[] | null | undefined): boolean => {
  if (!Array.isArray(points) || points.length === 0) return false
  return points.some(
    item =>
      item &&
      item.latitude &&
      item.longitude &&
      Object.keys(parsePayload(item.value).values).length > 0
  )
}
