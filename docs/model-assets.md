# 模型资源

S2C 评分，YOLO26n 检测物体区域，检测输入为 320×320。

## 构建

需要 GitHub CLI 和 Python 3.11+ 版本。在仓库根目录执行：

```sh
gh release download model-assets-v1 \
  --repo daydreamer8888/Okulo \
  --pattern 's2c.onnx' --pattern 'yolo26n.onnx' \
  --dir /tmp/okulo-models --clobber
python3 tools/models/package.py \
  --scorer /tmp/okulo-models/s2c.onnx \
  --detector /tmp/okulo-models/yolo26n.onnx
gh release download runtime-assets-v1 \
  --repo daydreamer8888/Okulo \
  --pattern 'onnxruntime-android-1.30.0-okulo-v1.aar' \
  --dir /tmp/okulo-models --clobber
python3 tools/runtime/prepare.py \
  --source /tmp/okulo-models/onnxruntime-android-1.30.0-okulo-v1.aar
./gradlew :app:assembleDebug
```

Windows 使用 `gradlew.bat`，默认打包 `arm64-v8a`，其他架构通过 `-PokuloAbis` 指定，多个架构用逗号分隔。

## 更新

更新模型时，同步 Release、`tools/models/package.py` 与 `ModelAssets.kt` 中的哈希，以及模型测试基准，并同步 `tools/runtime/build.py` 的算子清单、重建运行库。更换 Release 标签时同步 CI 下载与缓存配置。

## 来源与许可

- S2C：[官方 S2CNet](https://github.com/suyukun666/S2CNet) 的 GAICv2 权重。
- YOLO26n：[官方权重](https://github.com/ultralytics/assets/releases/download/v8.4.0/yolo26n.pt)，采用 [AGPL-3.0](https://www.ultralytics.com/license)。使用 Ultralytics 8.4.175 导出 FP32 ONNX，`imgsz=640`、`dynamic=True`、`opset=17`、`simplify=False`、`nms=False`。
