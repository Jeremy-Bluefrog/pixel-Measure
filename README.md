# 相機 AR 測量儀 (Pixel Measure) 

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose%20M3-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![ARCore](https://img.shields.io/badge/AR-Google%20ARCore%20%2B%20CameraX-orange.svg)](https://developers.google.com/ar)
[![Gemini](https://img.shields.io/badge/AI-Gemini%20Vision-blue.svg)](https://ai.google.dev)

基於 **Google ARCore**、**CameraX (60Hz)** 與 **Gemini AI** 打造的高精度空間測量與磁磚辨識 Android 應用程式。具備雙模架構、6DoF 空間追蹤、即時感測器融合校正與材料損耗估算功能。

---

## 🌟 核心特色功能

### 1. 🚀 60Hz 超流暢相機預覽 (High-Refresh Camera Mode)
- **硬體直出零拷貝**：採用 `PreviewView.ImplementationMode.PERFORMANCE` (SurfaceView)，直通 Android 硬體合成器 (SurfaceFlinger)。
- **Camera2Interop 幀率鎖定**：透過 `CONTROL_AE_TARGET_FPS_RANGE` 設定 `Range(60, 60)`，提供極致流暢、低延遲的測量取景體驗。
- **雙模相容機制**：具備 ARCore 60 FPS 模式與 CameraX 備援模式，無 Google Play 服務也能穩定流暢運作。

### 2. 🤖 Gemini AI 智慧磁磚偵測與 3D 空間自動跟蹤
- **視覺辨識磁磚與邊界**：結合 Gemini 多模態視覺分析，自動圈定地板與牆面磁磚。
- **Gemini 懸浮智慧膠囊 (Floating Intelligence Pill)**：
  - 經典 AI 四芒星（✦）霓虹漸層呼吸光暈。
  - 即時顯示磁磚材質與規格（如 `60×60 cm · 拋光石英磚`）。
- **動態流光光圈 (Organic Glowing Lasso)**：在磁磚輪廓周圍繪製柔和外發光與流動光弧線，搭配 4 角觸覺定位標記。
- **6DoF 空間投影追蹤**：相機移動旋轉時，AI 膠囊與光圈精確錨定在物理磁磚上，支援「一觸即測」自動計算面積與周長。

### 3. 📐 專業級多模態空間量測
- **點對點距離**：毫米級 3D 空間距離精確測量。
- **連續折線周長**：支援多點連續追蹤與即時累計長度。
- **多邊形面積**：自動投影至平面並計算平方公尺 ($m^2$) 與坪數。
- **3D 立體體積**：長寬高三維立方體體積運算 ($m^3$)。
- **直角三角測高 (Clinometer)**：透過傾角與基底距離自動計算建築或家具高度。
- **多軸水平儀與微米尺規**：高精確度螢幕實體刻度尺，支援公制 ($mm/cm$) 與英制 ($inch$)。

### 4. 🛰️ 多感應器硬體融合校正 (Sensor Fusion Engine)
- **雙軸陀螺儀水平防手震** (`Rotation Vector`)。
- **三軸加速度計抖動濾除** (`EMA Jitter Filter`)。
- **近接感測器接觸檢測** (`Proximity Distance`)。
- **氣壓高度計垂直校驗** (`Barometer Altimeter`)。

### 5. 🧱 磁磚鋪設施工備料與損耗計算
- 支援一鍵帶入測量面積，自訂磁磚規格 ($30\times30$, $60\times60$, $80\times80$, $60\times120$ cm 等)。
- 支援施工損耗率調整 ($3\% \sim 15\%$)，即時計算所需磁磚片數、總箱數與預估預算。

### 6. 💾 本地 Room 資料庫與圖表記錄
- 完整紀錄每次測量時間、模式、點位座標與材料備註。
- 支援截圖分享、CSV 匯出與歷史趨勢檢視。

---

## 🛠️ 技術架構與模組

```
app/src/main/java/com/example/
├── logic/
│   ├── ai/                # Gemini 磁磚視覺分析與本地幾何識別 (AiTileDetector.kt)
│   ├── ar/                # ARCore 空間引擎、OpenGL 著色器與空間投影數學 (ArMath.kt, ModernArEngine.kt)
│   └── sensor/            # 空間感測器融合、水平儀、抖動過濾演算法 (SensorCorrectionEngine.kt)
├── ui/
│   ├── components/        # AR 鏡頭預覽、Gemini 懸浮膠囊、流光光圈、尺規 (ModernArCameraView.kt, RulerComponent.kt)
│   ├── theme/             # Material Design 3 色彩系統與排版 (Theme.kt)
│   └── viewmodel/         # 測量狀態管理與響應式資料流 (MeasureViewModel.kt)
├── data/
│   └── db/                # Room 本地持久化資料庫 (MeasureHistoryDao.kt, AppDatabase.kt)
└── MainActivity.kt        # 應用程式入口與權限請求管理
```

---

## 📄 授權條款

本專案採用 [MIT License](LICENSE) 開源授權。

