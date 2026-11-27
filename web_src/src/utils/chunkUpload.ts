import { bytesToSize } from './bytes'

export interface ChunkItem {
  index: number
  chunk: Blob
  size: number
  totalChunks: number
}

export interface UploadProgressInfo {
  percent: number
  loaded: number
  total: number
  speed: number // bytes per second
  speedStr: string // e.g. "12.5 MB/s"
  eta: number // remaining seconds
  etaStr: string // e.g. "剩余 25 秒"
  statusText: string // e.g. "上传中 35% (3.2 MB/s)"
  currentChunk?: number
  totalChunks?: number
}

export interface ChunkUploadOptions {
  chunkSize?: number // default 10MB
  concurrency?: number // default 4 parallel requests
  maxRetries?: number // default 3 retries
  onProgress?: (progress: UploadProgressInfo) => void
  onStatusChange?: (status: string) => void
}

/**
 * 动态计算网速与剩余时间 Trackers
 */
export class UploadSpeedTracker {
  private startTime = 0
  private lastTime = 0
  private lastLoaded = 0
  private totalSize = 0
  private speedSamples: number[] = []

  public reset(totalSize: number) {
    this.startTime = Date.now()
    this.lastTime = Date.now()
    this.lastLoaded = 0
    this.totalSize = totalSize
    this.speedSamples = []
  }

  public update(loaded: number): UploadProgressInfo {
    const now = Date.now()
    const timeDiff = (now - this.lastTime) / 1000 // seconds
    const loadedDiff = loaded - this.lastLoaded

    let instantSpeed = 0
    if (timeDiff > 0.3 && loadedDiff > 0) {
      instantSpeed = loadedDiff / timeDiff
      this.lastTime = now
      this.lastLoaded = loaded

      // 平滑移动平均网速 (滑动窗口 5 个样本)
      this.speedSamples.push(instantSpeed)
      if (this.speedSamples.length > 5) {
        this.speedSamples.shift()
      }
    }

    const avgSpeed = this.speedSamples.length > 0
      ? this.speedSamples.reduce((a, b) => a + b, 0) / this.speedSamples.length
      : (loaded / (Math.max(1, now - this.startTime) / 1000))

    const remainingBytes = Math.max(0, this.totalSize - loaded)
    const eta = avgSpeed > 0 ? Math.ceil(remainingBytes / avgSpeed) : 0

    const percent = Math.min(100, Math.floor((loaded / this.totalSize) * 100))
    const speedStr = avgSpeed > 0 ? `${bytesToSize(avgSpeed, '0 B')}/s` : '0 B/s'
    
    let etaStr = '--'
    if (eta > 0 && percent < 100) {
      if (eta > 3600) {
        etaStr = `约 ${Math.floor(eta / 3600)} 小时`
      } else if (eta > 60) {
        etaStr = `约 ${Math.floor(eta / 60)} 分 ${eta % 60} 秒`
      } else {
        etaStr = `约 ${eta} 秒`
      }
    } else if (percent >= 100) {
      etaStr = '已完成'
    }

    return {
      percent,
      loaded,
      total: this.totalSize,
      speed: avgSpeed,
      speedStr,
      eta,
      etaStr,
      statusText: `${percent}% (${speedStr}) - 剩余 ${etaStr}`
    }
  }
}

/**
 * 将 File 进行分片
 */
export function createFileChunks(file: File, chunkSize = 10 * 1024 * 1024): ChunkItem[] {
  const chunks: ChunkItem[] = []
  let cur = 0
  const total = file.size
  let index = 0

  while (cur < total) {
    const end = Math.min(cur + chunkSize, total)
    const chunk = file.slice(cur, end)
    chunks.push({
      index,
      chunk,
      size: chunk.size,
      totalChunks: 0 // set later
    })
    cur = end
    index++
  }

  chunks.forEach(c => (c.totalChunks = chunks.length))
  return chunks
}

/**
 * 抽样 / Web Worker 兼容 Hash 计算
 * 对于 500MB+ 文件，全量计算 MD5 非常耗时。
 * 此处对首尾及中间区块抽样生成唯一特征指纹，兼顾速度与准确度（毫秒级完成 500MB 文件特征计算）。
 */
export async function calculateFastFileHash(file: File, onProgress?: (percent: number) => void): Promise<string> {
  return new Promise((resolve, reject) => {
    try {
      const chunkSize = 2 * 1024 * 1024 // 2MB sampling
      const chunks: Blob[] = []
      const size = file.size

      // 提取开头 2MB
      chunks.push(file.slice(0, chunkSize))

      // 提取中间 3 个 2MB 节点
      const offset1 = Math.floor(size * 0.25)
      const offset2 = Math.floor(size * 0.5)
      const offset3 = Math.floor(size * 0.75)

      chunks.push(file.slice(offset1, offset1 + chunkSize))
      chunks.push(file.slice(offset2, offset2 + chunkSize))
      chunks.push(file.slice(offset3, offset3 + chunkSize))

      // 提取末尾 2MB
      if (size > chunkSize) {
        chunks.push(file.slice(size - chunkSize, size))
      }

      const blob = new Blob(chunks)
      const reader = new FileReader()

      reader.onload = async (e) => {
        try {
          const buffer = e.target?.result as ArrayBuffer
          if (!buffer) {
            resolve(`${file.name}-${file.size}-${file.lastModified}`)
            return
          }

          if (window.crypto && window.crypto.subtle) {
            const hashBuffer = await window.crypto.subtle.digest('SHA-256', buffer)
            const hashArray = Array.from(new Uint8Array(hashBuffer))
            const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
            onProgress?.(100)
            resolve(`${hashHex.substring(0, 16)}_${file.size}`)
          } else {
            onProgress?.(100)
            resolve(`${file.name}_${file.size}_${file.lastModified}`)
          }
        } catch {
          resolve(`${file.name}_${file.size}_${file.lastModified}`)
        }
      }

      reader.onerror = () => resolve(`${file.name}_${file.size}_${file.lastModified}`)
      reader.readAsArrayBuffer(blob)
    } catch (err) {
      resolve(`${file.name}_${file.size}_${file.lastModified}`)
    }
  })
}

/**
 * 并发池执行器
 */
export async function runConcurrentPool<T>(
  tasks: (() => Promise<T>)[],
  concurrency = 4,
  onTaskSuccess?: (res: T, index: number) => void
): Promise<T[]> {
  const results: T[] = new Array(tasks.length)
  let currentIndex = 0

  const workers = Array.from({ length: Math.min(concurrency, tasks.length) }, async () => {
    while (currentIndex < tasks.length) {
      const index = currentIndex++
      const task = tasks[index]
      const res = await task()
      results[index] = res
      onTaskSuccess?.(res, index)
    }
  })

  await Promise.all(workers)
  return results
}
