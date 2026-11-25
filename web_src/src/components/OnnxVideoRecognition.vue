<template>
    <div class="onnx-video-recognition">
        <!-- Header -->
        <div class="header">
            <h2>
                <span class="icon">🎯</span>
                ONNX 实时视频识别
            </h2>
            <div class="status-badge" :class="{ active: isRunning, loading: isLoading }">
                <span class="status-dot"></span>
                {{ statusText }}
            </div>
        </div>

        <!-- 双视频对比布局 -->
        <!-- 单视频布局 (只显示识别结果) -->
        <div class="video-container">
            <div class="video-wrapper">
                <!-- 原始视频元素 (隐藏，但用于提供图像源) -->
                <!-- 注意：不能使用 v-if 因为我们需要 ref，也不能 display:none 因为可能影响推流播放 -->
                <!-- 使用 visibility: hidden 和绝对定位将其移出视觉区域 -->
                <video ref="videoRef" autoplay playsinline muted class="source-video" crossorigin="anonymous"></video>

                <!-- 识别结果 Canvas -->
                <canvas ref="canvasRef" class="detection-canvas"></canvas>

                <!-- Loading Overlay -->
                <div v-if="isLoading" class="loading-overlay">
                    <div class="spinner"></div>
                    <p>{{ loadingMessage }}</p>
                </div>

                <!-- Detection Info Overlay -->
                <div class="info-overlay" v-if="isRunning">
                    <div class="info-item">
                        <span class="label">FPS</span>
                        <span class="value">{{ fps.toFixed(1) }}</span>
                    </div>
                    <div class="info-item">
                        <span class="label">推理</span>
                        <span class="value">{{ inferenceTime.toFixed(0) }}ms</span>
                    </div>
                    <div class="info-item">
                        <span class="label">目标</span>
                        <span class="value highlight">{{ detectionCount }}</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- Controls -->
        <div class="controls">
            <div class="control-group">
                <label>视频源</label>
                <select v-model="videoSource" :disabled="isRunning">
                    <option value="camera">摄像头</option>
                    <option value="file">视频文件</option>
                    <option value="stream">RTSP/HLS 流</option>
                </select>
            </div>

            <div class="control-group" v-if="videoSource === 'file'">
                <label>选择视频</label>
                <input type="file" accept="video/*" @change="handleVideoFile" :disabled="isRunning" />
            </div>

            <div class="control-group" v-if="videoSource === 'stream'">
                <label>流地址</label>
                <input type="text" v-model="streamUrl" placeholder="输入 HLS/DASH 流地址" :disabled="isRunning" />
            </div>

            <div class="control-group">
                <label>模型</label>
                <select v-model="selectedModel" :disabled="isRunning">
                    <option v-for="(config, key) in MODEL_CONFIGS" :key="key" :value="key">
                        {{ config.name }}
                    </option>
                    <option value="custom">自定义模型</option>
                </select>
            </div>

            <div class="control-group" v-if="selectedModel === 'custom'">
                <label>上传模型</label>
                <input type="file" accept=".onnx" @change="handleModelFile" :disabled="isRunning" />
            </div>

            <div class="control-group">
                <label>置信度阈值: {{ confidenceThreshold.toFixed(2) }}</label>
                <input type="range" v-model.number="confidenceThreshold" min="0.1" max="0.9" step="0.05" />
            </div>

            <div class="control-group">
                <label>识别间隔: {{ inferenceInterval }} ms</label>
                <input type="range" v-model.number="inferenceInterval" min="0" max="2000" step="50" />
                <div style="font-size: 12px; color: #94a3b8; margin-top: 4px;">
                    (0 = 最快速度, 越大越省资源)
                </div>
            </div>

            <div class="control-group">
                <label>NMS 阈值: {{ nmsThreshold.toFixed(2) }}</label>
                <input type="range" v-model.number="nmsThreshold" min="0.1" max="0.9" step="0.05" />
            </div>

            <div class="control-group">
                <label>执行后端</label>
                <select v-model="executionBackend" :disabled="isRunning">
                    <option value="webgpu">WebGPU (GPU加速)</option>
                    <option value="wasm">WASM (CPU)</option>
                </select>
                <span class="backend-status" :class="backendStatusClass">{{ backendStatusText }}</span>
            </div>
        </div>

        <!-- Action Buttons -->
        <div class="actions">
            <button class="btn-primary" @click="startDetection" :disabled="isRunning || isLoading">
                <span class="btn-icon">▶</span>
                开始检测
            </button>
            <button class="btn-secondary" @click="stopDetection" :disabled="!isRunning">
                <span class="btn-icon">⏹</span>
                停止检测
            </button>
            <button class="btn-outline" @click="captureFrame" :disabled="!isRunning">
                <span class="btn-icon">📷</span>
                截图
            </button>
        </div>

        <!-- Detection Results -->
        <div class="results-panel" v-if="detections.length > 0">
            <h3>检测结果</h3>
            <div class="results-list">
                <div v-for="(detection, index) in detections" :key="index" class="result-item"
                    :style="{ borderLeftColor: detection.color }">
                    <span class="result-label">{{ detection.label }}</span>
                    <span class="result-confidence">{{ (detection.confidence * 100).toFixed(1) }}%</span>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import * as ort from 'onnxruntime-web'

// 模型配置定义
const MODEL_CONFIGS = {
    '6': {
        name: '人员检测 (6.onnx)',
        path: '/models/6.onnx',
        inputSize: [640, 640],
        labels: ['人', '误报'],
        ignoreLabels: ['误报'] // 不显示的类别
    },
    '9': {
        name: '火焰检测 (9.onnx)',
        path: '/models/9.onnx',
        inputSize: [640, 640],
        labels: ['fire'],
        ignoreLabels: []
    },
    '25': {
        name: '车辆检测 (25.onnx)',
        path: '/models/25.onnx',
        inputSize: [640, 640],
        labels: ['小车', '客车', '货车'],
        ignoreLabels: []
    },
    '无人机生物质燃烧': {
        name: '无人机生物质燃烧 (best.onnx)',
        path: '/models/best.onnx',
        inputSize: [640, 640],
        labels: ['白烟', '黑烟', '误报'],
        ignoreLabels: ['误报']
    },
    'yolov8n': { // 保留 YOLOv8n 用于通用检测
        name: '通用检测 (YOLOv8n)',
        path: '/models/yolov8n.onnx',
        inputSize: [640, 640],
        labels: [
            'person', 'bicycle', 'car', 'motorcycle', 'airplane', 'bus', 'train', 'truck', 'boat',
            'traffic light', 'fire hydrant', 'stop sign', 'parking meter', 'bench', 'bird', 'cat',
            'dog', 'horse', 'sheep', 'cow', 'elephant', 'bear', 'zebra', 'giraffe', 'backpack',
            'umbrella', 'handbag', 'tie', 'suitcase', 'frisbee', 'skis', 'snowboard', 'sports ball',
            'kite', 'baseball bat', 'baseball glove', 'skateboard', 'surfboard', 'tennis racket',
            'bottle', 'wine glass', 'cup', 'fork', 'knife', 'spoon', 'bowl', 'banana', 'apple',
            'sandwich', 'orange', 'broccoli', 'carrot', 'hot dog', 'pizza', 'donut', 'cake', 'chair',
            'couch', 'potted plant', 'bed', 'dining table', 'toilet', 'tv', 'laptop', 'mouse',
            'remote', 'keyboard', 'cell phone', 'microwave', 'oven', 'toaster', 'sink', 'refrigerator',
            'book', 'clock', 'vase', 'scissors', 'teddy bear', 'hair drier', 'toothbrush'
        ],
        ignoreLabels: []
    }
}

// 当前使用的配置
const currentConfig = ref(MODEL_CONFIGS['6'])

// 动态生成颜色
const getClassColor = (index, total) => {
    const hue = Math.floor((360 / total) * index)
    return `hsl(${hue}, 80%, 60%)`
}

// Refs
const videoRef = ref(null)
const canvasRef = ref(null)

// State
const isRunning = ref(false)
const isLoading = ref(false)
const loadingMessage = ref('')
const showVideo = ref(true)
const videoSource = ref('camera')
const streamUrl = ref('')
const selectedModel = ref('6')
const confidenceThreshold = ref(0.5)
const nmsThreshold = ref(0.45)
const fps = ref(0)
const inferenceTime = ref(0)
const detectionCount = ref(0)
const detections = ref([])
const executionBackend = ref('webgpu')
const webgpuSupported = ref(false)
const currentBackend = ref('')
const inferenceInterval = ref(0) // 推理间隔 (毫秒)

// 监听模型选择变化，重置 session
import { watch } from 'vue'
watch(selectedModel, () => {
    if (session) {
        session = null
        loadingMessage.value = '模型已切换，请重新开始检测'
    }
})

// ONNX Session
let session = null
let animationId = null
let lastTime = performance.now()
let frameCount = 0
let mediaStream = null

// Model input size
const INPUT_WIDTH = 640
const INPUT_HEIGHT = 640

// Computed
const statusText = computed(() => {
    if (isLoading.value) return '加载中...'
    if (isRunning.value) return '运行中'
    return '就绪'
})

const backendStatusClass = computed(() => ({
    'gpu-active': currentBackend.value === 'webgpu',
    'cpu-active': currentBackend.value === 'wasm'
}))

const backendStatusText = computed(() => {
    if (!webgpuSupported.value && executionBackend.value === 'webgpu') {
        return '⚠️ WebGPU 不支持，将使用 WASM'
    }
    if (currentBackend.value === 'webgpu') {
        return '🚀 GPU 加速中'
    }
    if (currentBackend.value === 'wasm') {
        return '💻 CPU 运行中'
    }
    return webgpuSupported.value ? '✓ GPU 可用' : '⚠️ 仅 CPU'
})

// 检测 WebGPU 支持
const checkWebGPUSupport = async () => {
    try {
        if (!navigator.gpu) {
            console.log('WebGPU 不支持: navigator.gpu 不存在')
            return false
        }
        const adapter = await navigator.gpu.requestAdapter()
        if (!adapter) {
            console.log('WebGPU 不支持: 无法获取 adapter')
            return false
        }
        const device = await adapter.requestDevice()
        if (!device) {
            console.log('WebGPU 不支持: 无法获取 device')
            return false
        }
        console.log('WebGPU 支持! Adapter:', adapter)
        return true
    } catch (error) {
        console.log('WebGPU 检测失败:', error)
        return false
    }
}

// 初始化 ONNX Runtime
const initOnnxRuntime = async () => {
    try {
        // 使用 CDN 路径加载 WASM 文件
        // ort.env.wasm.wasmPaths = 'https://cdn.jsdelivr.net/npm/onnxruntime-web@1.23.2/dist/'
        ort.env.wasm.wasmPaths = `${CURRENT_CONFIG.minio_url}/cloud-api/onnxruntime/`


        // 使用单线程模式，避免 SharedArrayBuffer 相关问题
        ort.env.wasm.numThreads = 1

        // 检测 WebGPU 支持
        webgpuSupported.value = await checkWebGPUSupport()

        if (webgpuSupported.value) {
            console.log('ONNX Runtime 初始化完成 - WebGPU 可用')
        } else {
            console.log('ONNX Runtime 初始化完成 - 仅 WASM 可用')
            executionBackend.value = 'wasm'
        }
    } catch (error) {
        console.error('ONNX Runtime 初始化失败:', error)
    }
}

// 加载模型
const loadModel = async (modelPath) => {
    isLoading.value = true
    loadingMessage.value = '正在加载模型...'

    try {
        // 确定使用的执行后端
        let providers = ['wasm']

        if (executionBackend.value === 'webgpu' && webgpuSupported.value) {
            providers = ['webgpu', 'wasm'] // WebGPU 优先，WASM 作为后备
            loadingMessage.value = '正在加载模型 (GPU加速)...'
        } else {
            loadingMessage.value = '正在加载模型 (CPU)...'
        }

        // 创建 session 选项
        const sessionOptions = {
            executionProviders: providers,
            graphOptimizationLevel: 'all'
        }

        if (typeof modelPath === 'string') {
            // 从 URL 加载模型
            session = await ort.InferenceSession.create(modelPath, sessionOptions)
        } else {
            // 从 ArrayBuffer 加载模型
            session = await ort.InferenceSession.create(modelPath, sessionOptions)
        }

        // 获取实际使用的执行后端
        currentBackend.value = executionBackend.value === 'webgpu' && webgpuSupported.value ? 'webgpu' : 'wasm'

        console.log('模型加载成功')
        console.log('执行后端:', currentBackend.value)
        console.log('输入:', session.inputNames)
        console.log('输出:', session.outputNames)

        return true
    } catch (error) {
        console.error('模型加载失败:', error)
        alert('模型加载失败: ' + error.message)
        return false
    } finally {
        isLoading.value = false
    }
}

// 获取摄像头流
const getCameraStream = async () => {
    try {
        mediaStream = await navigator.mediaDevices.getUserMedia({
            video: {
                width: { ideal: 1280 },
                height: { ideal: 720 },
                facingMode: 'environment'
            },
            audio: false
        })
        videoRef.value.srcObject = mediaStream
        await videoRef.value.play()
        return true
    } catch (error) {
        console.error('无法获取摄像头:', error)
        alert('无法访问摄像头: ' + error.message)
        return false
    }
}

// 处理视频文件
const handleVideoFile = (event) => {
    const file = event.target.files[0]
    if (file) {
        const url = URL.createObjectURL(file)
        videoRef.value.src = url
        videoRef.value.loop = true
    }
}

// 处理模型文件
const handleModelFile = async (event) => {
    const file = event.target.files[0]
    if (file) {
        const arrayBuffer = await file.arrayBuffer()
        await loadModel(arrayBuffer)
    }
}

// 预处理图像
const preprocessImage = (imageData) => {
    const { width, height } = imageData

    // 计算缩放比例（保持宽高比，缩放到 640x640 内）
    const scale = Math.min(INPUT_WIDTH / width, INPUT_HEIGHT / height)
    const newWidth = Math.round(width * scale)
    const newHeight = Math.round(height * scale)

    // 计算偏移（居中放置）
    const offsetX = Math.round((INPUT_WIDTH - newWidth) / 2)
    const offsetY = Math.round((INPUT_HEIGHT - newHeight) / 2)

    // 创建源 canvas 放置原始图像数据
    const srcCanvas = document.createElement('canvas')
    srcCanvas.width = width
    srcCanvas.height = height
    const srcCtx = srcCanvas.getContext('2d')
    srcCtx.putImageData(imageData, 0, 0)

    // 创建目标 canvas (640x640)
    const dstCanvas = document.createElement('canvas')
    dstCanvas.width = INPUT_WIDTH
    dstCanvas.height = INPUT_HEIGHT
    const dstCtx = dstCanvas.getContext('2d')

    // 填充灰色背景 (letterbox padding)
    dstCtx.fillStyle = '#808080'
    dstCtx.fillRect(0, 0, INPUT_WIDTH, INPUT_HEIGHT)

    // 将原图像缩放并绘制到目标位置
    // drawImage(image, dx, dy, dWidth, dHeight) - 将整个源图像绘制到目标矩形
    dstCtx.drawImage(srcCanvas, offsetX, offsetY, newWidth, newHeight)

    // 获取缩放后的图像数据
    const scaledData = dstCtx.getImageData(0, 0, INPUT_WIDTH, INPUT_HEIGHT).data

    // 创建 Float32Array 用于存储预处理后的数据
    // NCHW 格式: [1, 3, INPUT_HEIGHT, INPUT_WIDTH]
    const float32Data = new Float32Array(1 * 3 * INPUT_HEIGHT * INPUT_WIDTH)

    // 归一化并转换为 NCHW 格式
    for (let i = 0; i < INPUT_HEIGHT; i++) {
        for (let j = 0; j < INPUT_WIDTH; j++) {
            const pixelIndex = (i * INPUT_WIDTH + j) * 4
            // RGB 通道，归一化到 [0, 1]
            float32Data[0 * INPUT_HEIGHT * INPUT_WIDTH + i * INPUT_WIDTH + j] = scaledData[pixelIndex] / 255.0     // R
            float32Data[1 * INPUT_HEIGHT * INPUT_WIDTH + i * INPUT_WIDTH + j] = scaledData[pixelIndex + 1] / 255.0 // G
            float32Data[2 * INPUT_HEIGHT * INPUT_WIDTH + i * INPUT_WIDTH + j] = scaledData[pixelIndex + 2] / 255.0 // B
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

// 后处理检测结果 (YOLOv8 格式)
// 输出归一化坐标 (0~1)，绘制时再乘以实际尺寸
const postprocessDetections = (output, scale, offsetX, offsetY, originalWidth, originalHeight) => {
    const data = output.data
    const [batch, features, numDetections] = output.dims

    const results = []

    // YOLOv8 输出格式: [1, 84, 8400] 
    // 84 = 4 (bbox) + 80 (classes)
    // 8400 = number of detections

    for (let i = 0; i < numDetections; i++) {
        // 获取边界框坐标 (cx, cy, w, h)
        //根据调试日志确认：模型输出的直接是 640x640 空间的像素坐标
        let cx = data[0 * numDetections + i]
        let cy = data[1 * numDetections + i]
        let bw = data[2 * numDetections + i]
        let bh = data[3 * numDetections + i]

        // 找到最大类别置信度
        let maxScore = 0
        let maxClassId = 0

        // 使用当前配置的标签
        const labels = currentConfig.value.labels
        const ignoreLabels = currentConfig.value.ignoreLabels || []
        const numClasses = labels.length

        for (let c = 0; c < numClasses; c++) {
            const score = data[(4 + c) * numDetections + i]
            if (score > maxScore) {
                maxScore = score
                maxClassId = c
            }
        }

        // 过滤低置信度检测
        if (maxScore < confidenceThreshold.value) continue

        // 过滤被忽略的类别
        const label = labels[maxClassId] || `class_${maxClassId}`
        if (ignoreLabels.includes(label)) continue

        // 计算在 640x640 空间中的边界框
        let x1_640 = cx - bw / 2
        let y1_640 = cy - bh / 2
        let x2_640 = cx + bw / 2
        let y2_640 = cy + bh / 2

        // 从 640x640 空间转换回原始图像空间
        // 需要减去 letterbox 的偏移，再除以缩放比例
        let x1_pixel = (x1_640 - offsetX) / scale
        let y1_pixel = (y1_640 - offsetY) / scale
        let x2_pixel = (x2_640 - offsetX) / scale
        let y2_pixel = (y2_640 - offsetY) / scale

        // 调试日志（只打印前3个检测）
        if (results.length < 3) {
            console.log('检测坐标调试:', {
                pixel_640: { cx, cy, bw, bh },
                box_640: { x1_640, y1_640, x2_640, y2_640 },
                transform: { scale, offsetX, offsetY },
                pixel_orig: { x1_pixel, y1_pixel, x2_pixel, y2_pixel },
                original: { originalWidth, originalHeight }
            })
        }

        // 裁剪到图像边界
        x1_pixel = Math.max(0, Math.min(x1_pixel, originalWidth))
        y1_pixel = Math.max(0, Math.min(y1_pixel, originalHeight))
        x2_pixel = Math.max(0, Math.min(x2_pixel, originalWidth))
        y2_pixel = Math.max(0, Math.min(y2_pixel, originalHeight))

        // 转换为归一化坐标 (0~1)
        const x1 = x1_pixel / originalWidth
        const y1 = y1_pixel / originalHeight
        const x2 = x2_pixel / originalWidth
        const y2 = y2_pixel / originalHeight

        // 跳过无效的检测框（归一化后宽高为0或负数）
        if (x2 <= x1 || y2 <= y1) continue

        results.push({
            // 归一化坐标 (0~1)
            x1, y1, x2, y2,
            confidence: maxScore,
            classId: maxClassId,
            label: label,
            color: getClassColor(maxClassId, numClasses)
        })
    }

    // 应用 NMS
    return applyNMS(results)
}

// NMS 实现
const applyNMS = (detections) => {
    // 按置信度排序
    detections.sort((a, b) => b.confidence - a.confidence)

    const result = []
    const used = new Set()

    for (let i = 0; i < detections.length; i++) {
        if (used.has(i)) continue

        result.push(detections[i])

        for (let j = i + 1; j < detections.length; j++) {
            if (used.has(j)) continue

            if (detections[i].classId === detections[j].classId) {
                const iou = calculateIoU(detections[i], detections[j])
                if (iou > nmsThreshold.value) {
                    used.add(j)
                }
            }
        }
    }

    return result
}

// 计算 IoU
const calculateIoU = (box1, box2) => {
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

// 绘制检测结果
// 输入的坐标是归一化坐标 (0~1)，绘制时乘以 Canvas 实际尺寸
const drawDetections = (ctx, results) => {
    // 获取 Canvas 实际尺寸（像素）
    const canvasWidth = ctx.canvas.width
    const canvasHeight = ctx.canvas.height

    results.forEach((det, index) => {
        const { label, confidence, color } = det

        // 将归一化坐标 (0~1) 转换为实际像素坐标
        const x1 = det.x1 * canvasWidth
        const y1 = det.y1 * canvasHeight
        const x2 = det.x2 * canvasWidth
        const y2 = det.y2 * canvasHeight
        const boxWidth = x2 - x1
        const boxHeight = y2 - y1

        // 跳过无效的检测框
        if (boxWidth <= 0 || boxHeight <= 0) return

        // 绘制外发光效果
        ctx.shadowColor = color
        ctx.shadowBlur = 10

        // 绘制边界框 (使用圆角效果)
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

        // 重置阴影
        ctx.shadowBlur = 0

        // 绘制半透明填充
        ctx.fillStyle = color.replace(')', ', 0.15)').replace('hsl', 'hsla')
        ctx.fill()

        // 绘制角标记 (增强视觉效果)
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

        // 准备标签文字
        const text = `${label} ${(confidence * 100).toFixed(0)}%`
        ctx.font = 'bold 14px Inter, Arial, sans-serif'
        const textMetrics = ctx.measureText(text)
        const textWidth = textMetrics.width + 16
        const textHeight = 24
        const padding = 8

        // 计算标签位置 (确保不超出画布)
        let labelX = x1
        let labelY = y1 - textHeight - 4

        if (labelY < 0) {
            labelY = y1 + 4
        }
        if (labelX + textWidth > canvasWidth) {
            labelX = canvasWidth - textWidth
        }

        // 绘制标签背景 (带圆角和渐变)
        ctx.shadowColor = 'rgba(0, 0, 0, 0.5)'
        ctx.shadowBlur = 4
        ctx.shadowOffsetY = 2

        // 创建渐变背景
        const gradient = ctx.createLinearGradient(labelX, labelY, labelX + textWidth, labelY)
        gradient.addColorStop(0, color)
        gradient.addColorStop(1, color.replace(/50%\)/, '40%)'))

        ctx.fillStyle = gradient
        ctx.beginPath()
        ctx.roundRect(labelX, labelY, textWidth, textHeight, 4)
        ctx.fill()

        // 重置阴影
        ctx.shadowBlur = 0
        ctx.shadowOffsetY = 0

        // 绘制标签文字
        ctx.fillStyle = '#ffffff'
        ctx.textBaseline = 'middle'
        ctx.fillText(text, labelX + padding, labelY + textHeight / 2)

        // 绘制置信度条
        const barWidth = textWidth - 16
        const barHeight = 3
        const barX = labelX + 8
        const barY = labelY + textHeight - 6

        // 背景条
        ctx.fillStyle = 'rgba(255, 255, 255, 0.3)'
        ctx.fillRect(barX, barY, barWidth, barHeight)

        // 置信度条
        ctx.fillStyle = '#ffffff'
        ctx.fillRect(barX, barY, barWidth * confidence, barHeight)
    })
}

// ==================== 双循环架构 ====================
// 渲染循环：高频运行 (60 FPS)，负责绘制视频帧和检测框
// 推理循环：独立运行，负责 AI 推理，更新检测结果

// 当前检测结果（由推理循环更新，渲染循环读取）
let currentDetections = []
let isInferencing = false
let renderAnimationId = null
let inferenceTimeoutId = null

// 渲染循环 - 高频运行，保证画面流畅
const renderLoop = () => {
    if (!isRunning.value) return

    const video = videoRef.value
    const canvas = canvasRef.value

    if (!canvas || !video) {
        renderAnimationId = requestAnimationFrame(renderLoop)
        return
    }

    const ctx = canvas.getContext('2d')

    // 确保视频已准备好
    if (video.readyState < 2) {
        renderAnimationId = requestAnimationFrame(renderLoop)
        return
    }

    // 只在尺寸变化时更新 canvas 尺寸
    if (canvas.width !== video.videoWidth || canvas.height !== video.videoHeight) {
        canvas.width = video.videoWidth
        canvas.height = video.videoHeight
    }

    // 清空并绘制当前视频帧
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height)

    // 绘制当前检测结果（即使推理还在进行，也显示上一次的结果）
    if (currentDetections.length > 0) {
        drawDetections(ctx, currentDetections)
    }

    // 计算渲染 FPS
    frameCount++
    const now = performance.now()
    if (now - lastTime >= 1000) {
        fps.value = frameCount * 1000 / (now - lastTime)
        frameCount = 0
        lastTime = now
    }

    // 继续下一帧
    renderAnimationId = requestAnimationFrame(renderLoop)
}

// 推理循环 - 独立运行，进行 AI 推理
const inferenceLoop = async () => {
    if (!isRunning.value || !session || isInferencing) {
        // 如果还在运行，继续安排下次推理
        if (isRunning.value) {
            inferenceTimeoutId = setTimeout(inferenceLoop, 10)
        }
        return
    }

    isInferencing = true

    const video = videoRef.value
    const canvas = canvasRef.value

    if (!canvas || !video || video.readyState < 2) {
        isInferencing = false
        if (isRunning.value) {
            inferenceTimeoutId = setTimeout(inferenceLoop, 10)
        }
        return
    }

    try {
        // 创建临时 canvas 获取图像数据（不影响显示的 canvas）
        const tempCanvas = document.createElement('canvas')
        tempCanvas.width = video.videoWidth
        tempCanvas.height = video.videoHeight
        const tempCtx = tempCanvas.getContext('2d')
        tempCtx.drawImage(video, 0, 0)

        // 获取图像数据
        const imageData = tempCtx.getImageData(0, 0, tempCanvas.width, tempCanvas.height)

        // 预处理
        const { tensor, scale, offsetX, offsetY, originalWidth, originalHeight } = preprocessImage(imageData)

        // 推理计时
        const startTime = performance.now()

        const feeds = { [session.inputNames[0]]: tensor }
        const results = await session.run(feeds)
        const output = results[session.outputNames[0]]

        // 后处理 - 使用原始图像尺寸进行坐标转换
        const dets = postprocessDetections(
            output,
            scale,
            offsetX,
            offsetY,
            originalWidth,
            originalHeight
        )

        // 更新推理时间
        inferenceTime.value = performance.now() - startTime

        // 更新检测结果（渲染循环会读取这个变量）
        currentDetections = dets
        detections.value = dets
        detectionCount.value = dets.length

    } catch (error) {
        console.error('推理错误:', error)
    }

    isInferencing = false

    // 继续下次推理
    if (isRunning.value) {
        // 使用用户设置的间隔，如果是 0 则使用最小延迟
        const delay = Math.max(0, inferenceInterval.value)
        inferenceTimeoutId = setTimeout(inferenceLoop, delay)
    }
}

// 启动双循环
const startLoops = () => {
    // 重置状态
    currentDetections = []
    isInferencing = false
    lastTime = performance.now()
    frameCount = 0

    // 启动渲染循环
    renderLoop()

    // 启动推理循环
    inferenceLoop()
}

// 停止双循环
const stopLoops = () => {
    if (renderAnimationId) {
        cancelAnimationFrame(renderAnimationId)
        renderAnimationId = null
    }

    if (inferenceTimeoutId) {
        clearTimeout(inferenceTimeoutId)
        inferenceTimeoutId = null
    }

    isInferencing = false
}

// 开始检测
const startDetection = async () => {
    isLoading.value = true
    loadingMessage.value = '正在初始化...'

    try {
        // 初始化视频源
        if (videoSource.value === 'camera') {
            const success = await getCameraStream()
            if (!success) {
                isLoading.value = false
                return
            }
        } else if (videoSource.value === 'stream') {
            if (!streamUrl.value) {
                alert('请输入流地址')
                isLoading.value = false
                return
            }
            videoRef.value.src = streamUrl.value
            await videoRef.value.play()
        } else if (videoSource.value === 'file') {
            if (!videoRef.value.src) {
                alert('请选择视频文件')
                isLoading.value = false
                return
            }
            await videoRef.value.play()
        }

        // 设置当前模型配置
        if (selectedModel.value !== 'custom') {
            currentConfig.value = MODEL_CONFIGS[selectedModel.value]
        }

        // 加载模型（如果尚未加载或模型已更改）
        // 注意：这里简化了逻辑，实际上应该检查是否需要重新加载
        // 为了简单起见，如果 session 存在但 modelPath 不同才重新加载
        // 目前逻辑是每次 startDetection 如果 session 为空才加载
        // 建议在 selectedModel 变化时清空 session

        if (!session) {
            let modelPath
            if (selectedModel.value === 'custom') {
                // 自定义模型逻辑已经有 handleModelFile 处理加载，这里假设已加载
                if (!customModelBuffer) {
                    // 如果是自定义但没上传，提示
                    alert('请先上传自定义模型')
                    isLoading.value = false
                    return
                }
                // 自定义模型通常在 handleModelFile 加载了，这里可能不需要做
            } else {
                modelPath = currentConfig.value.path
                const success = await loadModel(modelPath)
                if (!success) {
                    isLoading.value = false
                    return
                }
            }
        }

        // 开始双循环
        isRunning.value = true
        isLoading.value = false

        startLoops()

    } catch (error) {
        console.error('启动检测失败:', error)
        alert('启动检测失败: ' + error.message)
        isLoading.value = false
    }
}

// 停止检测
const stopDetection = () => {
    isRunning.value = false

    // 停止双循环
    stopLoops()

    // 停止摄像头流
    if (mediaStream) {
        mediaStream.getTracks().forEach(track => track.stop())
        mediaStream = null
    }

    // 停止视频播放
    if (videoRef.value) {
        videoRef.value.pause()
        if (videoRef.value.srcObject) {
            videoRef.value.srcObject = null
        }
    }

    // 清空检测结果
    currentDetections = []
    detections.value = []
    detectionCount.value = 0
    fps.value = 0
    inferenceTime.value = 0
}

// 截图
const captureFrame = () => {
    const canvas = canvasRef.value
    if (!canvas) return

    const link = document.createElement('a')
    link.download = `detection_${Date.now()}.png`
    link.href = canvas.toDataURL('image/png')
    link.click()
}

// 生命周期
onMounted(async () => {
    await initOnnxRuntime()
})

onUnmounted(() => {
    stopDetection()
    if (session) {
        session.release()
        session = null
    }
})
</script>

<style scoped>
.onnx-video-recognition {
    background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid rgba(255, 255, 255, 0.1);
    box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}

.header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
}

.header h2 {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 1.5rem;
    font-weight: 700;
    background: linear-gradient(135deg, #38bdf8, #818cf8);
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
    margin: 0;
}

.icon {
    font-size: 1.8rem;
}

.status-badge {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 16px;
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.1);
    font-size: 0.875rem;
    font-weight: 500;
    color: #94a3b8;
}

.status-badge.active {
    background: rgba(34, 197, 94, 0.2);
    color: #22c55e;
}

.status-badge.loading {
    background: rgba(234, 179, 8, 0.2);
    color: #eab308;
}

.status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: currentColor;
    animation: pulse 2s infinite;
}

@keyframes pulse {

    0%,
    100% {
        opacity: 1;
    }

    50% {
        opacity: 0.5;
    }
}

/* CSS 已清理 */

/* 单视频容器样式 */
.video-container {
    position: relative;
    width: 100%;
    max-width: 1200px;
    margin: 0 auto 30px;
    border-radius: 16px;
    overflow: hidden;
    box-shadow: 0 20px 50px rgba(0, 0, 0, 0.5);
    border: 1px solid rgba(255, 255, 255, 0.1);
}

.video-wrapper {
    position: relative;
    aspect-ratio: 16/9;
    background: #000;
}

/* 隐藏原始视频源，但保持其在文档流中（使用 absolute 移出可视区更为稳妥，或者 visibility: hidden） */
.source-video {
    position: absolute;
    width: 1px;
    /* 设置极小尺寸 */
    height: 1px;
    opacity: 0;
    pointer-events: none;
    z-index: -1;
}

.detection-canvas {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    object-fit: contain;
    z-index: 1;
    /* 确保在视频上方 */
}

.loading-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.8);
    z-index: 10;
}

.spinner {
    width: 50px;
    height: 50px;
    border: 4px solid rgba(255, 255, 255, 0.1);
    border-left-color: #38bdf8;
    border-radius: 50%;
    animation: spin 1s linear infinite;
    margin-bottom: 16px;
}

@keyframes spin {
    to {
        transform: rotate(360deg);
    }
}

.loading-overlay p {
    color: #94a3b8;
    font-size: 0.875rem;
}

.info-overlay {
    position: absolute;
    top: 12px;
    left: 12px;
    display: flex;
    gap: 12px;
    z-index: 5;
}

.info-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    background: rgba(0, 0, 0, 0.7);
    backdrop-filter: blur(8px);
    padding: 8px 12px;
    border-radius: 8px;
    border: 1px solid rgba(255, 255, 255, 0.1);
}

.info-item .label {
    font-size: 0.7rem;
    color: #94a3b8;
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

.info-item .value {
    font-size: 1.1rem;
    font-weight: 700;
    color: #38bdf8;
}

.controls {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 16px;
    margin-bottom: 20px;
}

.control-group {
    display: flex;
    flex-direction: column;
    gap: 6px;
}

.backend-status {
    font-size: 0.75rem;
    padding: 4px 8px;
    border-radius: 4px;
    margin-top: 4px;
    text-align: center;
    background: rgba(255, 255, 255, 0.05);
    color: #94a3b8;
}

.backend-status.gpu-active {
    background: linear-gradient(135deg, rgba(34, 197, 94, 0.2), rgba(16, 185, 129, 0.2));
    color: #22c55e;
    border: 1px solid rgba(34, 197, 94, 0.3);
    animation: gpu-glow 2s ease-in-out infinite;
}

.backend-status.cpu-active {
    background: rgba(56, 189, 248, 0.1);
    color: #38bdf8;
    border: 1px solid rgba(56, 189, 248, 0.2);
}

@keyframes gpu-glow {

    0%,
    100% {
        box-shadow: 0 0 5px rgba(34, 197, 94, 0.3);
    }

    50% {
        box-shadow: 0 0 15px rgba(34, 197, 94, 0.5);
    }
}

.control-group label {
    font-size: 0.875rem;
    color: #94a3b8;
    font-weight: 500;
}

.control-group select,
.control-group input[type="text"] {
    background: rgba(30, 41, 59, 1);
    /* 使用不透明的深色背景 */
    border: 1px solid rgba(255, 255, 255, 0.2);
    border-radius: 8px;
    padding: 10px 12px;
    color: #f8fafc;
    /* 浅色文字 */
    font-size: 0.875rem;
    transition: all 0.2s;
}

/* 修复下拉选项在白色背景下文字不可见的问题 */
.control-group select option {
    background-color: #1e293b;
    /* 深色背景 */
    color: #f8fafc;
    /* 浅色文字 */
}

.control-group select:focus,
.control-group input[type="text"]:focus {
    outline: none;
    border-color: #38bdf8;
    box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.2);
}

.control-group select:disabled,
.control-group input:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.control-group input[type="file"] {
    color: #94a3b8;
    font-size: 0.8rem;
}

.control-group input[type="file"]::file-selector-button {
    background: rgba(56, 189, 248, 0.2);
    color: #38bdf8;
    border: none;
    border-radius: 6px;
    padding: 6px 12px;
    cursor: pointer;
    font-weight: 500;
    margin-right: 10px;
    transition: all 0.2s;
}

.control-group input[type="file"]::file-selector-button:hover {
    background: rgba(56, 189, 248, 0.3);
}

.control-group input[type="range"] {
    width: 100%;
    accent-color: #38bdf8;
}

.actions {
    display: flex;
    gap: 12px;
    margin-bottom: 20px;
}

.actions button {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 12px 20px;
    border-radius: 10px;
    font-weight: 600;
    font-size: 0.9rem;
    transition: all 0.3s;
    cursor: pointer;
}

.btn-icon {
    font-size: 1rem;
}

.btn-primary {
    background: linear-gradient(135deg, #22c55e, #16a34a);
    color: white;
    border: none;
}

.btn-primary:hover:not(:disabled) {
    background: linear-gradient(135deg, #16a34a, #15803d);
    transform: translateY(-2px);
    box-shadow: 0 10px 20px rgba(34, 197, 94, 0.3);
}

.btn-secondary {
    background: linear-gradient(135deg, #ef4444, #dc2626);
    color: white;
    border: none;
}

.btn-secondary:hover:not(:disabled) {
    background: linear-gradient(135deg, #dc2626, #b91c1c);
    transform: translateY(-2px);
    box-shadow: 0 10px 20px rgba(239, 68, 68, 0.3);
}

.btn-outline {
    background: transparent;
    border: 2px solid #38bdf8;
    color: #38bdf8;
}

.btn-outline:hover:not(:disabled) {
    background: rgba(56, 189, 248, 0.1);
    transform: translateY(-2px);
}

.actions button:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none !important;
}

.results-panel {
    background: rgba(255, 255, 255, 0.03);
    border-radius: 12px;
    padding: 16px;
    border: 1px solid rgba(255, 255, 255, 0.05);
}

.results-panel h3 {
    font-size: 1rem;
    color: #f8fafc;
    margin: 0 0 12px 0;
}

.results-list {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
}

.result-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    background: rgba(255, 255, 255, 0.05);
    padding: 8px 12px;
    border-radius: 8px;
    border-left: 3px solid;
    min-width: 150px;
}

.result-label {
    font-size: 0.875rem;
    color: #f8fafc;
    font-weight: 500;
}

.result-confidence {
    font-size: 0.75rem;
    color: #38bdf8;
    font-weight: 600;
}

/* 响应式设计 */
@media (max-width: 640px) {
    .onnx-video-recognition {
        padding: 16px;
        border-radius: 12px;
    }

    .header {
        flex-direction: column;
        gap: 12px;
        align-items: flex-start;
    }

    .header h2 {
        font-size: 1.25rem;
    }

    .controls {
        grid-template-columns: 1fr;
    }

    .actions {
        flex-direction: column;
    }

    .info-overlay {
        flex-wrap: wrap;
        gap: 6px;
    }

    .info-item {
        padding: 6px 10px;
    }
}
</style>
