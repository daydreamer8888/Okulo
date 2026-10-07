# 模型资源

应用使用 S2C 评估裁剪构图，Faster R-CNN MobileNetV3 Large FPN 为其提供五个检测框。快速与标准分析共用 S2C，分别使用短边 320、800 的检测器变体。

## 下载与构建

在仓库根目录执行以下命令，需要 GitHub CLI 和 Python 3.11 或更新版本：

```sh
gh release download model-assets-v1 \
  --repo daydreamer8888/Okulo \
  --pattern '*.onnx' \
  --dir /tmp/okulo-models
python3 tools/models/package.py \
  --scorer /tmp/okulo-models/s2c.onnx \
  --standard-detector /tmp/okulo-models/detector-800.onnx \
  --fast-detector /tmp/okulo-models/detector-320.onnx
./gradlew :app:assembleDebug
```

打包工具校验 SHA-256 后将三个文件放到 `app/model-assets/models/`，构建时打包到 APK 的 `assets/models/`。CI 使用同一 Release 和工具，并缓存下载文件。

默认打包 `arm64-v8a` 运行库。通过 `-PokuloAbis=x86_64` 为 CI 模拟器构建，多个架构以逗号分隔。

使用其他模型资源目录时，给打包工具传入 `--destination /absolute/path/to/assets/models`，并给 Gradle 传入 `-PokuloModelAssets=/absolute/path/to/assets`。

## 校验与更新

| 文件 | SHA-256 |
| --- | --- |
| s2c.onnx | e9d5b6518a878f6873f00482bd48c96c82980ef22e7715fb0cb73a066156348a |
| detector-800.onnx | c71ccb64fd0221b9308d10f45e5b66182de09e96f214ffcfacf4e2a42578bdef |
| detector-320.onnx | d5c15586b8c10bfd2b1f729766af1a754eb6b3fe1475d12ed5efd8b87439f339 |

首次准备模型时，应用校验 APK 中的资源，并将其复制到不参与备份的应用专用目录。后续直接复用以哈希命名的文件。

更新模型时，同步更新 Release 版本、CI 下载与缓存配置、`tools/models/package.py` 和 `ModelAssets.kt` 中的哈希、本表及模型测试基准。

## 来源与输入契约

S2C 使用 [官方 S2CNet](https://github.com/suyukun666/S2CNet) 的 GAICv2 权重。检测器使用 [torchvision COCO_V1 权重](https://download.pytorch.org/models/fasterrcnn_mobilenet_v3_large_fpn-fb6a3cc7.pth)，替代 S2C 原实现中的 Visual Genome 检测器。

- S2C 图像输入：RGB、`1×3×H×W`，像素值除以 256，再按 ImageNet 均值与标准差归一化。按短边 256、最长边 2048 的限制缩放，尺寸取最近的 32 倍数。
- 裁剪输入：`N×5`，每行为 `[0, left, top, right, bottom]`，坐标对应 S2C 图像像素。
- 检测器输入：RGB、`1×3×H×W`，像素值除以 255。
- 检测框输入：按置信度排序，执行 IoU 阈值 0.5 的类别无关 NMS，选取五个框。坐标映射到 S2C 图像后，左上角向下取整，右下角向上取整。少于五个框时分析失败。

同一照片和分析模式的后续评分复用图像张量与检测框。切换模式时清除缓存并释放旧检测会话。评分仅用于比较同一照片的裁剪方案。
