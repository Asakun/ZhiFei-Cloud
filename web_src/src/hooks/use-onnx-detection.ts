import { ref, Ref, shallowRef } from 'vue'
import * as ort from 'onnxruntime-web'
import { CURRENT_CONFIG } from '@/api/http/config'

// 模型配置定义
export const ONNX_MODEL_CONFIGS: Record<string, {
    name: string
    path: string
    inputSize: [number, number]
    labels: string[]
    ignoreLabels: string[]
}> = {
    '6': {
        name: '人员检测',
        path: '/models/6.onnx',
        inputSize: [640, 640],
        labels: ['人', '误报'],
        ignoreLabels: ['误报']
    },
    '9': {
        name: '火焰检测',
        path: '/models/9.onnx',
        inputSize: [640, 640],
        labels: ['fire'],
        ignoreLabels: []
    },
    '25': {
        name: '车辆检测',
        path: '/models/25.onnx',
        inputSize: [640, 640],
        labels: ['小车', '客车', '货车'],
        ignoreLabels: []
    },
    '26': {
        name: '工程车检测',
        path: '/models/工程车.onnx',
        inputSize: [640, 640],
        labels: ['工程车', '卡车', '误报'],
        ignoreLabels: ['误报']
    },
    '27': {
        name: '无人机工程车检测',
        path: '/models/无人机工程车.onnx',
        inputSize: [640, 640],
        labels: ['工程车', '卡车', '误报'],
        ignoreLabels: ['误报']
    },
    'biomass': {
        name: '生物质燃烧',
        path: '/models/best.onnx',
        inputSize: [640, 640],
        labels: ['白烟', '黑烟', '误报'],
        ignoreLabels: ['误报']
    }
}

export interface Detection {
    x1: number
    y1: number
    x2: number
    y2: number
    confidence: number
    classId: number
    label: string
    color: string
}

export interface OnnxDetectionOptions {
    confidenceThreshold?: number
    nmsThreshold?: number
    inferenceInterval?: number
}

/**
 * ONNX 视频检测 Composable
 * 用于在 WebRTC 视频流上进行实时目标检测
 */
export function useOnnxDetection(options: OnnxDetectionOptions = {}) {
    const INPUT_WIDTH = 640
    const INPUT_HEIGHT = 640

    // 状态
    const isRunning = ref(false)
    const isLoading = ref(false)
    const loadingMessage = ref('')
    const fps = ref(0)
    const inferenceTime = ref(0)
    const detectionCount = ref(0)
    const detections = ref<Detection[]>([])
    const currentBackend = ref('')
    const webgpuSupported = ref(false)

    // 配置
    const confidenceThreshold = ref(options.confidenceThreshold ?? 0.5)
    const nmsThreshold = ref(options.nmsThreshold ?? 0.45)
    const inferenceInterval = ref(options.inferenceInterval ?? 100)

    // Session
    let session: ort.InferenceSession | null = null
    let currentConfig = ONNX_MODEL_CONFIGS['6']
    let renderAnimationId: number | null = null
    let inferenceTimeoutId: ReturnType<typeof setTimeout> | null = null
    let lastTime = performance.now()
    let frameCount = 0
    let currentDetections: Detection[] = []
    let isInferencing = false

    // 动态生成颜色
    const getClassColor = (index: number, total: number): string => {
        const hue = Math.floor((360 / total) * index)
        return `hsl(${hue}, 80%, 60%)`
    }

    // 检测 WebGPU 支持
    const checkWebGPUSupport = async (): Promise<boolean> => {
        try {
            // 使用 any 类型避免 TypeScript 错误，因为 WebGPU API 类型声明可能不可用
            const nav = navigator as any
            if (!nav.gpu) return false
            const adapter = await nav.gpu.requestAdapter()
            if (!adapter) return false
            const device = await adapter.requestDevice()
            return !!device
        } catch {
            return false
        }
    }

    // 初始化标记
    let isInitialized = false

    // 初始化 ONNX Runtime
    const initOnnxRuntime = async () => {
        if (isInitialized) return
        try {
            // ort.env.wasm.wasmPaths = 'https://cdn.jsdelivr.net/npm/onnxruntime-web@1.23.2/dist/'
            ort.env.wasm.wasmPaths = `${CURRENT_CONFIG.minio_url}/cloud-api/onnxruntime/`

            ort.env.wasm.numThreads = 1
            webgpuSupported.value = await checkWebGPUSupport()
            isInitialized = true
            console.log('ONNX Runtime 初始化完成, WebGPU:', webgpuSupported.value)
        } catch (error) {
            console.error('ONNX Runtime 初始化失败:', error)
        }
    }

    // 加载模型
    const loadModel = async (modelKey: string): Promise<boolean> => {
        if (!ONNX_MODEL_CONFIGS[modelKey]) {
            console.error('未知的模型:', modelKey)
            return false
        }

        isLoading.value = true
        currentConfig = ONNX_MODEL_CONFIGS[modelKey]
        loadingMessage.value = `正在加载模型: ${currentConfig.name}...`

        try {
            const providers = webgpuSupported.value ? ['webgpu', 'wasm'] : ['wasm']
            const sessionOptions = {
                executionProviders: providers as Array<'webgpu' | 'wasm'>,
                graphOptimizationLevel: 'all' as const
            }

            // 添加超时控制
            const createSessionPromise = ort.InferenceSession.create(currentConfig.path, sessionOptions)
            const timeoutPromise = new Promise<never>((_, reject) =>
                setTimeout(() => reject(new Error('Model load timeout')), 30000)
            )

            // @ts-ignore
            session = await Promise.race([createSessionPromise, timeoutPromise])
            currentBackend.value = webgpuSupported.value ? 'webgpu' : 'wasm'

            console.log('模型加载成功:', currentConfig.name)
            console.log('执行后端:', currentBackend.value)
            return true
        } catch (error) {
            console.error('模型加载失败:', error)
            return false
        } finally {
            isLoading.value = false
        }
    }

    // 预处理图像
    const preprocessImage = (imageData: ImageData) => {
        const { width, height } = imageData
        const scale = Math.min(INPUT_WIDTH / width, INPUT_HEIGHT / height)
        const newWidth = Math.round(width * scale)
        const newHeight = Math.round(height * scale)
        const offsetX = Math.round((INPUT_WIDTH - newWidth) / 2)
        const offsetY = Math.round((INPUT_HEIGHT - newHeight) / 2)

        const srcCanvas = document.createElement('canvas')
        srcCanvas.width = width
        srcCanvas.height = height
        const srcCtx = srcCanvas.getContext('2d')!
        srcCtx.putImageData(imageData, 0, 0)

        const dstCanvas = document.createElement('canvas')
        dstCanvas.width = INPUT_WIDTH
        dstCanvas.height = INPUT_HEIGHT
        const dstCtx = dstCanvas.getContext('2d')!
        dstCtx.fillStyle = '#808080'
        dstCtx.fillRect(0, 0, INPUT_WIDTH, INPUT_HEIGHT)
        dstCtx.drawImage(srcCanvas, offsetX, offsetY, newWidth, newHeight)

        const scaledData = dstCtx.getImageData(0, 0, INPUT_WIDTH, INPUT_HEIGHT).data
        const float32Data = new Float32Array(1 * 3 * INPUT_HEIGHT * INPUT_WIDTH)

        for (let i = 0; i < INPUT_HEIGHT; i++) {
            for (let j = 0; j < INPUT_WIDTH; j++) {
                const pixelIndex = (i * INPUT_WIDTH + j) * 4
                float32Data[0 * INPUT_HEIGHT * INPUT_WIDTH + i * INPUT_WIDTH + j] = scaledData[pixelIndex] / 255.0
                float32Data[1 * INPUT_HEIGHT * INPUT_WIDTH + i * INPUT_WIDTH + j] = scaledData[pixelIndex + 1] / 255.0
                float32Data[2 * INPUT_HEIGHT * INPUT_WIDTH + i * INPUT_WIDTH + j] = scaledData[pixelIndex + 2] / 255.0
            }
        }

        return {
            tensor: new ort.Tensor('float32', float32Data, [1, 3, INPUT_HEIGHT, INPUT_WIDTH]),
            scale,
            offsetX,
            offsetY,
            originalWidth: width,
            originalHeight: height
        }
    }

    // 计算 IoU
    const calculateIoU = (box1: Detection, box2: Detection): number => {
        const x1 = Math.max(box1.x1, box2.x1)
        const y1 = Math.max(box1.y1, box2.y1)
        const x2 = Math.min(box1.x2, box2.x2)
        const y2 = Math.min(box1.y2, box2.y2)

        const intersection = Math.max(0, x2 - x1) * Math.max(0, y2 - y1)
        const area1 = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        const area2 = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        const union = area1 + area2 - intersection

        return intersection / union
    }

    // NMS 实现
    const applyNMS = (dets: Detection[]): Detection[] => {
        dets.sort((a, b) => b.confidence - a.confidence)
        const result: Detection[] = []
        const used = new Set<number>()

        for (let i = 0; i < dets.length; i++) {
            if (used.has(i)) continue
            result.push(dets[i])

            for (let j = i + 1; j < dets.length; j++) {
                if (used.has(j)) continue
                if (dets[i].classId === dets[j].classId) {
                    const iou = calculateIoU(dets[i], dets[j])
                    if (iou > nmsThreshold.value) {
                        used.add(j)
                    }
                }
            }
        }
        return result
    }

    // 后处理检测结果
    const postprocessDetections = (
        output: ort.Tensor,
        scale: number,
        offsetX: number,
        offsetY: number,
        originalWidth: number,
        originalHeight: number
    ): Detection[] => {
        const data = output.data as Float32Array
        const [, , numDetections] = output.dims

        const results: Detection[] = []
        const labels = currentConfig.labels
        const ignoreLabels = currentConfig.ignoreLabels
        const numClasses = labels.length

        for (let i = 0; i < numDetections; i++) {
            let cx = data[0 * numDetections + i]
            let cy = data[1 * numDetections + i]
            let bw = data[2 * numDetections + i]
            let bh = data[3 * numDetections + i]

            let maxScore = 0
            let maxClassId = 0

            for (let c = 0; c < numClasses; c++) {
                const score = data[(4 + c) * numDetections + i]
                if (score > maxScore) {
                    maxScore = score
                    maxClassId = c
                }
            }

            if (maxScore < confidenceThreshold.value) continue

            const label = labels[maxClassId] || `class_${maxClassId}`
            if (ignoreLabels.includes(label)) continue

            let x1_640 = cx - bw / 2
            let y1_640 = cy - bh / 2
            let x2_640 = cx + bw / 2
            let y2_640 = cy + bh / 2

            let x1_pixel = (x1_640 - offsetX) / scale
            let y1_pixel = (y1_640 - offsetY) / scale
            let x2_pixel = (x2_640 - offsetX) / scale
            let y2_pixel = (y2_640 - offsetY) / scale

            x1_pixel = Math.max(0, Math.min(x1_pixel, originalWidth))
            y1_pixel = Math.max(0, Math.min(y1_pixel, originalHeight))
            x2_pixel = Math.max(0, Math.min(x2_pixel, originalWidth))
            y2_pixel = Math.max(0, Math.min(y2_pixel, originalHeight))

            const x1 = x1_pixel / originalWidth
            const y1 = y1_pixel / originalHeight
            const x2 = x2_pixel / originalWidth
            const y2 = y2_pixel / originalHeight

            if (x2 <= x1 || y2 <= y1) continue

            results.push({
                x1, y1, x2, y2,
                confidence: maxScore,
                classId: maxClassId,
                label,
                color: getClassColor(maxClassId, numClasses)
            })
        }

        return applyNMS(results)
    }

    // 绘制检测结果
    const drawDetections = (ctx: CanvasRenderingContext2D, results: Detection[]) => {
        const canvasWidth = ctx.canvas.width
        const canvasHeight = ctx.canvas.height

        results.forEach((det) => {
            const { label, confidence, color } = det
            const x1 = det.x1 * canvasWidth
            const y1 = det.y1 * canvasHeight
            const x2 = det.x2 * canvasWidth
            const y2 = det.y2 * canvasHeight
            const boxWidth = x2 - x1
            const boxHeight = y2 - y1

            if (boxWidth <= 0 || boxHeight <= 0) return

            // 绘制外发光效果
            ctx.shadowColor = color
            ctx.shadowBlur = 10

            // 绘制边界框
            ctx.strokeStyle = color
            ctx.lineWidth = 3
            ctx.beginPath()
            const radius = Math.min(8, boxWidth * 0.1, boxHeight * 0.1)
            ctx.moveTo(x1 + radius, y1)
            ctx.lineTo(x2 - radius, y1)
            ctx.quadraticCurveTo(x2, y1, x2, y1 + radius)
            ctx.lineTo(x2, y2 - radius)
            ctx.quadraticCurveTo(x2, y2, x2 - radius, y2)
            ctx.lineTo(x1 + radius, y2)
            ctx.quadraticCurveTo(x1, y2, x1, y2 - radius)
            ctx.lineTo(x1, y1 + radius)
            ctx.quadraticCurveTo(x1, y1, x1 + radius, y1)
            ctx.closePath()
            ctx.stroke()

            ctx.shadowBlur = 0

            // 绘制半透明填充
            ctx.fillStyle = color.replace(')', ', 0.15)').replace('hsl', 'hsla')
            ctx.fill()

            // 绘制角标记
            const cornerLength = Math.min(20, boxWidth * 0.2, boxHeight * 0.2)
            ctx.strokeStyle = '#ffffff'
            ctx.lineWidth = 2

            // 左上角
            ctx.beginPath()
            ctx.moveTo(x1, y1 + cornerLength)
            ctx.lineTo(x1, y1)
            ctx.lineTo(x1 + cornerLength, y1)
            ctx.stroke()

            // 右上角
            ctx.beginPath()
            ctx.moveTo(x2 - cornerLength, y1)
            ctx.lineTo(x2, y1)
            ctx.lineTo(x2, y1 + cornerLength)
            ctx.stroke()

            // 左下角
            ctx.beginPath()
            ctx.moveTo(x1, y2 - cornerLength)
            ctx.lineTo(x1, y2)
            ctx.lineTo(x1 + cornerLength, y2)
            ctx.stroke()

            // 右下角
            ctx.beginPath()
            ctx.moveTo(x2 - cornerLength, y2)
            ctx.lineTo(x2, y2)
            ctx.lineTo(x2, y2 - cornerLength)
            ctx.stroke()

            // 绘制标签
            const text = `${label} ${(confidence * 100).toFixed(0)}%`
            ctx.font = 'bold 14px Inter, Arial, sans-serif'
            const textMetrics = ctx.measureText(text)
            const textWidth = textMetrics.width + 16
            const textHeight = 24
            const padding = 8

            let labelX = x1
            let labelY = y1 - textHeight - 4
            if (labelY < 0) labelY = y1 + 4
            if (labelX + textWidth > canvasWidth) labelX = canvasWidth - textWidth

            // 标签背景
            ctx.shadowColor = 'rgba(0, 0, 0, 0.5)'
            ctx.shadowBlur = 4
            ctx.shadowOffsetY = 2

            const gradient = ctx.createLinearGradient(labelX, labelY, labelX + textWidth, labelY)
            gradient.addColorStop(0, color)
            gradient.addColorStop(1, color.replace(/50%\)/, '40%)'))

            ctx.fillStyle = gradient
            ctx.beginPath()
            ctx.roundRect(labelX, labelY, textWidth, textHeight, 4)
            ctx.fill()

            ctx.shadowBlur = 0
            ctx.shadowOffsetY = 0

            // 标签文字
            ctx.fillStyle = '#ffffff'
            ctx.textBaseline = 'middle'
            ctx.fillText(text, labelX + padding, labelY + textHeight / 2)
        })
    }

    // 推理循环
    const inferenceLoop = async (video: HTMLVideoElement, canvas: HTMLCanvasElement) => {
        if (!isRunning.value || !session || isInferencing) {
            if (isRunning.value) {
                inferenceTimeoutId = setTimeout(() => inferenceLoop(video, canvas), 10)
            }
            return
        }

        isInferencing = true

        if (video.readyState < 2) {
            isInferencing = false
            if (isRunning.value) {
                inferenceTimeoutId = setTimeout(() => inferenceLoop(video, canvas), 10)
            }
            return
        }

        try {
            const tempCanvas = document.createElement('canvas')
            tempCanvas.width = video.videoWidth
            tempCanvas.height = video.videoHeight
            const tempCtx = tempCanvas.getContext('2d')!
            tempCtx.drawImage(video, 0, 0)

            const imageData = tempCtx.getImageData(0, 0, tempCanvas.width, tempCanvas.height)
            const { tensor, scale, offsetX, offsetY, originalWidth, originalHeight } = preprocessImage(imageData)

            const startTime = performance.now()
            const feeds = { [session.inputNames[0]]: tensor }
            const results = await session.run(feeds)
            const output = results[session.outputNames[0]]

            const dets = postprocessDetections(output, scale, offsetX, offsetY, originalWidth, originalHeight)

            inferenceTime.value = performance.now() - startTime
            currentDetections = dets
            detections.value = dets
            detectionCount.value = dets.length

        } catch (error) {
            console.error('推理错误:', error)
        }

        isInferencing = false

        if (isRunning.value) {
            const delay = Math.max(0, inferenceInterval.value)
            inferenceTimeoutId = setTimeout(() => inferenceLoop(video, canvas), delay)
        }
    }

    // 渲染循环
    const renderLoop = (canvas: HTMLCanvasElement) => {
        if (!isRunning.value) return

        const ctx = canvas.getContext('2d')
        if (!ctx) {
            renderAnimationId = requestAnimationFrame(() => renderLoop(canvas))
            return
        }

        // 清空 canvas
        ctx.clearRect(0, 0, canvas.width, canvas.height)

        // 绘制检测结果
        if (currentDetections.length > 0) {
            drawDetections(ctx, currentDetections)
        }

        // 计算 FPS
        frameCount++
        const now = performance.now()
        if (now - lastTime >= 1000) {
            fps.value = frameCount * 1000 / (now - lastTime)
            frameCount = 0
            lastTime = now
        }

        renderAnimationId = requestAnimationFrame(() => renderLoop(canvas))
    }

    // 开始检测
    const startDetection = async (
        modelKey: string,
        videoElement: HTMLVideoElement,
        overlayCanvas: HTMLCanvasElement
    ): Promise<boolean> => {
        if (isRunning.value) return false

        isLoading.value = true
        loadingMessage.value = '正在初始化...'

        try {
            // 初始化 ONNX Runtime
            await initOnnxRuntime()

            // 加载模型
            const success = await loadModel(modelKey)
            if (!success) {
                isLoading.value = false
                return false
            }

            // 设置 canvas 尺寸
            overlayCanvas.width = videoElement.videoWidth || 1920
            overlayCanvas.height = videoElement.videoHeight || 1080

            // 开始循环
            isRunning.value = true
            currentDetections = []
            lastTime = performance.now()
            frameCount = 0

            renderLoop(overlayCanvas)
            inferenceLoop(videoElement, overlayCanvas)

            isLoading.value = false
            return true

        } catch (error) {
            console.error('启动检测失败:', error)
            isLoading.value = false
            return false
        }
    }

    // 停止检测
    const stopDetection = () => {
        isRunning.value = false

        if (renderAnimationId) {
            cancelAnimationFrame(renderAnimationId)
            renderAnimationId = null
        }

        if (inferenceTimeoutId) {
            clearTimeout(inferenceTimeoutId)
            inferenceTimeoutId = null
        }

        isInferencing = false
        currentDetections = []
        detections.value = []
        detectionCount.value = 0
        fps.value = 0
        inferenceTime.value = 0
    }

    // 释放资源
    const dispose = () => {
        stopDetection()
        if (session) {
            session.release()
            session = null
        }
    }

    return {
        // 状态
        isRunning,
        isLoading,
        loadingMessage,
        fps,
        inferenceTime,
        detectionCount,
        detections,
        currentBackend,

        // 配置
        confidenceThreshold,
        nmsThreshold,
        inferenceInterval,

        // 方法
        startDetection,
        stopDetection,
        dispose,

        // 模型配置
        MODEL_CONFIGS: ONNX_MODEL_CONFIGS
    }
}
