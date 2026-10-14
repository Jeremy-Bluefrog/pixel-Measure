# Vulkan 渲染與幀率效能深度調優與智慧動態資源釋放計劃

本計劃依據「品質第一、速度第二」原則，針對您選定的三大重點：
1. **渲染與幀率效能（Vulkan 延遲與流暢度）**
2. **智慧動態釋放（離開頁面即時回收緩衝，顯著降低電池與 GPU 負擔）**
3. **維持現有量測功能完整性，專注底層演算法與渲染管線調優**

---

## 核心調優面向與技術方案

### 1. Vulkan 60FPS 渲染熱路徑「零配置（Zero-Allocation）」調優
- **問題分析**：在現有 `processGlFrame` / `processVulkanFrame` 幀循環中，每次 Choreographer 回呼都會執行 `capturedPoints.toList()`（觸發記憶體複製與分配）、動態建立 `Runnable` 閉包，以及在 3D 空間世界座標轉換螢幕投影時反覆建立 `Pair<Float, Float>` 物件，在高更新率螢幕（60Hz/90Hz/120Hz）下會產生顯著的 Android GC 壓力與微卡頓（Jank）。
- **優化方案**：
  - **快照快取機制（Cached Snapshot Pattern）**：將 `capturedPoints` 的拷貝時機限縮在點位真正變更時（新增點、刪除點、Undo、Redo），熱路徑每幀讀取純 volatile 指標，達成 $O(1)$ 零配置獲取。
  - **靜態單例 Runnable 任務循環**：在 `VulkanArSceneView` 建立可重複利用的 `frameProcessRunnable`，徹底消除每 16ms 產生的閉包實例化。
  - **投影運算與矩陣緩衝無配置優化**：在 `ArMath.projectWorldToScreen` 實作就地（In-Place）投影計算或復用緩衝，並重命名與優化 `vulkanViewMatrixBuffer` 及 `vulkanProjMatrixBuffer`。

### 2. 智慧動態釋放（Smart Dynamic Buffer Lifecycle）
- **問題分析**：ARCore、相機硬體通道及 `AHardwareBuffer` / `ImageReader` 佔用大量高階 GPU 記憶體與相機子系統供電。若使用者切換至歷史紀錄、設定或切到背景，未即時回收會導致高待機耗電及記憶體洩漏風險。
- **優化方案**：
  - **即時銷毀與回收管線（Explicit Teardown API）**：在 `VulkanArSceneView` 與 `VulkanHardwareBufferManager` 新增 `destroy()` 與 `release()` 嚴格動態釋放機制，關閉 `ImageReader`、釋放持有的 `HardwareBuffer` 並停止 `HandlerThread` 與 `Choreographer` 監聽。
  - **Compose 生命週期深度綁定**：在 `ModernArCameraView` 的 `DisposableEffect` 及 `AndroidView.onRelease` 中，保證離開 AR 頁面或切換標籤頁時第一時間即時釋放所有底層硬體緩衝與相機通道，回到頁面時再按需懶加載重建。

### 3. 底層演算法平滑化與 Compose 重組調優
- **演算法穩定性**：優化 `ArMath.findVertexSnap` 與磁吸力回饋計算，減少開根號與無效幾何交叉檢驗。
- **Compose 狀態隔離**：將即時準心、測量距離等高頻變更數值與外層 UI 容器有效解耦，避免高頻數值更新導致全屏不必要的重組（Recomposition），確保繪圖層與手勢層流暢度達到滿幀。

---

## 實作步驟

1. **`VulkanArSceneView.kt` 深度調優**：
   - 替換每幀 Lambda 為靜態 `Runnable`。
   - 增加 `destroy()` 與強韌的線程安全停止邏輯。
   - 確切落實 `onRelease` 與 `surfaceDestroyed` 狀態切換。

2. **`VulkanHardwareBufferManager.kt` 智慧釋放強化**：
   - 強化多線程互斥鎖與緩衝立即 `close()` 機制。
   - 增加動態釋放狀態檢查與即時 GC 友善清理。

3. **`MeasureViewModel.kt` 熱路徑降載與命名標準化**：
   - 矩陣緩衝升級為 `vulkanViewMatrixBuffer` / `vulkanProjMatrixBuffer`。
   - 實作帶快取的點位快照機制，熱路徑零分配。
   - 提供 `processVulkanFrame`，並於 ViewModel 銷毀/暫停時提供緩衝清理接口。

4. **`ModernArCameraView.kt` 生命週期與回收調控**：
   - 在 `DisposableEffect.onDispose` 與 `AndroidView.onRelease` 中確實觸發動態回收。
   - 針對重組範圍進行效能防禦。

5. **編譯檢驗與單元測試驗證**：
   - 運行 `compile_applet` 驗證編譯。
   - 執行單元測試驗證零拷貝計算與矩陣轉換精度。
