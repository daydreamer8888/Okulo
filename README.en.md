# Okulo

[简体中文](README.md)

An Android camera with framing assistance.

| Camera | Crop editor |
| --- | --- |
| <img src="docs/images/camera.png" alt="Camera viewfinder" width="280"> | <img src="docs/images/crop.png" alt="Photo crop editor" width="280"> |

- **Camera**: zoom and crop suggestions.
- **Crop editor**: import photos and get crop suggestions across aspect ratios or for a chosen ratio.

Sample photo: [Berenice Abbott / NYPL, public domain](https://commons.wikimedia.org/wiki/File:West_Washington_Market,_Washington_Street_and_Loew_Avenue,_Manhattan_(NYPL_b13668355-482678).jpg).

## Build

Requires JDK 17 and Android SDK 36. Supports Android 8.0 and later, with `arm64-v8a` packaged by default.

Prepare the models using the [model asset instructions](docs/model-assets.md), then run:

```sh
./gradlew :app:assembleDebug
```

On Windows, use `gradlew.bat`. APK: `app/build/outputs/apk/debug/app-debug.apk`.
