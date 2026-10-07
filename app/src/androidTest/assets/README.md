# Native inference regression fixture

These assets are maintained inputs for CompositionEngineTest: both detector modes must reproduce the 251 desktop scores and the same best crop on identical RGB inputs. They are packaged only in the instrumentation test APK.

Source: COCO val2017 image 567197, 500 × 407 pixels. COCO records license 7, “No known copyright restrictions” (Flickr Commons).

- COCO source: https://images.cocodataset.org/val2017/000000567197.jpg
- Original source: http://farm4.staticflickr.com/3044/3110606120_36aa6bdc13_z.jpg
- Source page: https://www.flickr.com/photo.gne?id=3110606120
- Rights statement: https://www.flickr.com/commons/usage/

scene.png is a lossless RGB decode of the source JPEG. scene-normalized.png is its OpenCV bilinear resize to 320 × 256, prior to float normalization. model-reference.json records ONNX Runtime CPU output for the model hashes documented in docs/model-assets.md. PNG fixtures keep the engine test independent of platform JPEG decoding and resampling differences.
