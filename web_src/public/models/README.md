# ONNX 模型目录

请将 ONNX 格式的 YOLOv8 模型文件放置在此目录中。

## 下载模型

### 方式一：使用 ultralytics 导出

```bash
# 安装 ultralytics
pip install ultralytics

# 导出 YOLOv8n 模型
python -c "from ultralytics import YOLO; model = YOLO('yolov8n.pt'); model.export(format='onnx', opset=12)"
```

导出后的模型文件 `yolov8n.onnx` 放到此目录即可。

### 方式二：直接下载预转换模型

可从以下链接下载已转换的 ONNX 模型：

- YOLOv8n: https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n.pt (需要自行转换)
- ONNX Model Zoo: https://github.com/onnx/models

## 模型命名

请确保模型文件命名与代码中一致：
- `yolov8n.onnx` - YOLOv8 Nano 版本
- `yolov8s.onnx` - YOLOv8 Small 版本

## 自定义模型

如果使用自定义训练的模型，请确保：
1. 输入格式：`[1, 3, 640, 640]` (NCHW)
2. 输出格式：`[1, 84, 8400]` (batch, features, detections)
3. 类别数量与 COCO_CLASSES 匹配（或修改代码中的类别列表）
