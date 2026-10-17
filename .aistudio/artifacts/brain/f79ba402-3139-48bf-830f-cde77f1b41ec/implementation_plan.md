# Implementation Plan: 網路搜尋成因分析與相機方向/60 FPS 線條即時同步修復計畫

本計畫依據 Android 官方 Camera2、TextureView 與 SensorManager 技術架構進行深入網路搜尋，徹底剖析畫面左右相反、預覽流暢度極低，以及「測量線條更新速度落後相機」的三大根本成因，並提出精確的修復方案。

## User Review Required

> [!IMPORTANT]
> 透過網路搜尋與原始碼交叉比對，已確認造成這三個現象的具體技術根因（Root Causes）：

1. **畫面左右相反成因**:
   - Android Camera2 的感光元件原生為橫向安裝（`CameraCharacteristics.SENSOR_ORIENTATION`，後鏡頭通常為 90°，前鏡頭通常為 270° 且硬體預設具備自拍鏡像）。
   - 目前 `HighSpeedCamera2Manager.configureTransform` 在直向模式（`ROTATION_0`）下，**未將感光元件方向納入旋轉計算**，且未針對鏡頭朝向（`LENS_FACING`）與螢幕旋轉實施標準公式 `(sensorOrientation - displayRotation + 360) % 360`，導致畫面產生水平鏡像顛倒或軸向錯位。
2. **預覽流暢度極低成因**:
   - `selectOptimalPreviewSize` 優先選擇了 `1920x1080`（1080p）。在雲端串流模擬器或部分裝置的 GPU/CPU 虛擬化環境中，1080p 幀緩衝區傳輸與轉換引發嚴重的管線負載，使幀率大幅跌落至 10~15 FPS。
3. **線條更新速度比相機慢成因**:
   - 感測器註冊使用 `SensorManager.SENSOR_DELAY_UI`（延遲長達 60,000 微秒，取樣頻率僅約 16Hz）。
   - `MeasureViewModel` 的 `updateFallbackSpatialFrame` 運行在 `delay(40)`（僅 25 FPS），兩者皆遠低於相機硬體的即時畫面更新頻率，導致相機畫面已移動，但陀螺儀與 3D 投影線條慢了 2~3 個訊框，產生明顯的拖曳延遲感。

---

## 1. Overview & Technical Root Cause Analysis

### 根本原因對照表 (Root Cause Matrix)

| 現象 | 技術成因 (Root Cause) | 網路官方文獻與架構標準 | 修復策略 |
| :--- | :--- | :--- | :--- |
| **畫面左右相反** | `configureTransform` 忽略 `SENSOR_ORIENTATION` 與前鏡頭鏡像特性 | Camera2 規範中 `TextureView` 需透過 Matrix 實施 `(sensorOrientation - displayDegrees + 360) % 360`，並對前鏡頭或鏡像需求套用 `matrix.postScale(-1f, 1f, centerX, centerY)` | 依硬體特性動態計算正確旋轉角與鏡像縮放，並提供頂部「前後鏡頭切換」與「水平鏡像開關」 |
| **相機預覽極卡** | 預設抓取 1080p 解析度，造成繪製頻寬與記憶體交換過重 | Google Camera 指南指出行動裝置即時 AR 取景以 720p（1280x720）為流暢度與能耗最佳平衡點 | 調整預覽解析度策略優先選擇 720p，並啟用 `STREAM_USE_CASE_PREVIEW` 提升驅動級管線優先順序 |
| **線條比相機慢** | 1. 感測器延遲為 `SENSOR_DELAY_UI` (16Hz)<br>2. 空間幀更新迴圈為 `delay(40)` (25 FPS) | 遊戲與 AR 空間追蹤必須使用 `SENSOR_DELAY_GAME`（20,000 微秒，50~60Hz）且更新迴圈需達到 60 FPS (16ms) | 升級感測器延遲至 `SENSOR_DELAY_GAME`，並將空間更新迴圈縮短至 16ms（60 FPS），達成零時差即時跟隨 |

---

## 2. User Experience & Visual Polish

- **頂部控制列強化**:
  - 新增「前後鏡頭切換按鈕」（`Icons.Rounded.FlipCameraAndroid`），可一鍵在後置主相機與前置鏡頭間流暢切換。
  - 新增「水平鏡像切換」（`Icons.Rounded.SwapHoriz`），當使用者在特殊反射鏡測量或自拍時，可隨時手動翻轉鏡像。
- **60 FPS 零延遲體驗**:
  - 設備旋轉平移時，AR 測量十字準心、導引線條與距離標籤完美錨定於實體物體表面，完全消除晃動時的滯後飄移。

---

## 3. Step-by-Step Implementation Strategy

### Step 1: 修正 Camera2 方位角、旋轉矩陣與解析度 (`HighSpeedCamera2Manager.kt`)
1. 查詢相機硬體之 `CameraCharacteristics.SENSOR_ORIENTATION` 與 `LENS_FACING`。
2. 重新編寫 `configureTransform`：
   - 根據螢幕旋轉（`Surface.ROTATION_0 / 90 / 180 / 270`）換算顯示角度（`0°, 90°, 180°, 270°`）。
   - 計算基準旋轉：後置鏡頭為 `(sensorOrientation - displayDegrees + 360) % 360`；前置鏡頭為 `(sensorOrientation + displayDegrees) % 360`。
   - 旋轉後計算適應 View 寬高的 `scaleX` 與 `scaleY`（Center-Crop 填滿且不變形）。
   - 支援手動或前置鏡頭所需的水平鏡像翻轉：`matrix.postScale(-1f, 1f, centerX, centerY)`。
3. 優化 `selectOptimalPreviewSize`：
   - 優先選擇 720p（1280x720），兼具高清晰度與穩定 60 FPS 流暢度。
4. 實作前後鏡頭無縫切換功能：`switchCamera(textureView)`。

### Step 2: 解決線條滯後延遲，升級至 60 FPS (`SensorFusionCorrectionEngine.kt` & `MeasureViewModel.kt`)
1. 在 `SensorFusionCorrectionEngine.kt` 中：
   - 將所有運動與姿態感測器（重力、陀螺儀、旋轉向量、線性加速度）之註冊頻率全面升級為 `SensorManager.SENSOR_DELAY_GAME`（20,000 微秒，50~60Hz）。
   - 移除內部 30Hz 的節流抑制，讓姿態數據即時同步派發。
2. 在 `MeasureViewModel.kt` 中：
   - 將 `updateFallbackSpatialFrame` 的輪詢週期由 `delay(40)` 改為 `delay(16)`（提升至 60 FPS）。
   - 校正視角矩陣（View Matrix）中方位角（Azimuth）與俯仰角（Pitch）的旋轉正負號，確保相機往右移時空間座標向左動，方向 100% 吻合真實世界。

### Step 3: 完善 UI 控制與設定介面 (`ModernArTopBar.kt` & `SettingsScreen.kt`)
1. 在 `ModernArTopBar` 新增鏡頭翻轉按鈕與鏡像開關。
2. 在 `SettingsScreen` 新增相機方向校準與鏡像偏好選項。

---

## 4. Verification & Testing Plan

### 編譯驗證
- 執行 `compile_applet`，確保所有 Camera2 矩陣數學運算、感測器生命週期與 Compose UI 順利編譯通過。

### 功能與流暢度實測
- **鏡像與方向測試**:
  - 開啟相機，前後左右平移，確認相機畫面不左右相反、文字與物體方向符合自然視覺。
  - 測試切換前後鏡頭，確認前後鏡頭皆能維持正確方位。
- **線條同步率測試**:
  - 快速左右晃動裝置，確認 3D 空間測量線、十字準星與距離標籤立即隨相機畫面平移，不再有慢半拍或拖曳感。
