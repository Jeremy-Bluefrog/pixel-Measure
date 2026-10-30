# AR 測量應用程式：極限除錯與 Vulkan 強效管線管理改進計畫

## 一、 計畫核心指導原則

根據使用者指示調整，本改進計畫剔除外觀風格重塑項目，完全聚焦於兩大技術核心：
1. **核心第一目標：減少 Bug（零死角穩定度與邊界防護）** —— 徹底排查崩潰點、記憶體洩漏、跨執行緒死鎖、幾何退化與追蹤漂移，保證極限操作下零崩潰。
2. **核心第二目標：更強的 Vulkan 管線管理（堅持 100% 純 Vulkan，完全不使用 OpenGL）** —— 構建確定性生命週期狀態機、雙緩衝柵欄同步、Surface 重建保護、顯存零洩漏與異常熔斷機制。
3. **最高準則：「品質第一，速度第二」** —— 所有改進皆經嚴密數學驗證、單元測試覆蓋與編譯檢驗。

---

## 二、 核心架構：Vulkan 強效生命週期與資源狀態機

針對 Surface 生命週期變更、屏幕旋轉、App 前後台切換與硬體資源釋放，建立強型別、確定性的狀態機，杜絕黑屏、驅動假死與顯存崩潰：

```
+-----------------------------------------------------------------------------------+
|                        Vulkan Robust Lifecycle State Machine                      |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [ UNINITIALIZED ]                                                                |
|         |                                                                         |
|         v (SurfaceCreated / Valid HW Surface)                                     |
|  [ SURFACE_ATTACHED ] ---> (Initialize VkInstance, PhysicalDevice, LogicalDevice) |
|         |                                                                         |
|         v (SurfaceChanged: Width > 0 && Height > 0)                               |
|  [ SWAPCHAIN_CONFIGURED ] ---> (Allocate VkSwapchainKHR, VkRenderPass, Buffers)   |
|         |                                                                         |
|         +-----------------------+                                                 |
|         |                       |                                                 |
|         v (AR Frame Available)  v (App Background / Surface Destroyed)            |
|  [ ACTIVE_RENDER_LOOP ]    [ SAFE_SUSPENDED ]                                     |
|    - AcquireNextImage        - Atomic Flag: stopRenderLoop                        |
|    - vkWaitForFences         - vkQueueWaitIdle() & vkDeviceWaitIdle()             |
|    - Update PushConstants    - Destroy Swapchain, Framebuffers & ImageViews       |
|    - Submit CommandBuffer    - Detach Surface Native Handle                       |
|    - Present Swapchain               |                                            |
|         |                            v (App Resume / Surface Recreated)           |
|         +--------------------> [ REBUILDING_PIPELINE ]                            |
|                                      |                                            |
|                                      +----------------> [ SWAPCHAIN_CONFIGURED ]  |
+-----------------------------------------------------------------------------------+
```

---

## 三、 重點改進項目與技術實作

### 第一階段：Vulkan 管線管理全面強化 (Vulkan Pipeline & Resource Hardening)
1. **建立強型別狀態機 (`VulkanRenderState`)**：
   - 明確定義 `UNINITIALIZED`、`CONFIGURED`、`ACTIVE`、`PAUSED`、`TEARDOWN`。
   - 在 `SurfaceHolder.Callback` 的 `surfaceDestroyed` 回調中，以同步阻斷方式執行 `vkQueueWaitIdle` 與資源解鎖，防止非同步渲染執行緒在已銷毀的 Native Window 上繼續寫入引發致命 SIGSEGV。
2. **雙重緩衝柵欄（Fence）與訊號量（Semaphore）同步管理**：
   - 實現每幀專屬的 `VkFence` 與 `VkSemaphore`，嚴格保證上一幀 GPU 渲染完畢後再複用 CommandBuffer，杜絕 CPU 與 GPU 的資源競爭與圖面破圖。
3. **異常熔斷與平滑安全幀過渡**：
   - 當相機幀取得延遲或 ARCore/Camera2 暫未產出紋理時，Vulkan 管線自動渲染中性安全背景幀，避免幀率驟降與屏幕閃爍。
   - 引入 Render Watchdog 機制：連續 3 幀逾時時自動重置管線狀態，防止硬體驅動死鎖。

### 第二階段：極限除錯與邊界容錯全面強化 (Comprehensive Bug Sweep & Resilience)
1. **多點測量拓撲與幾何解算防禦**：
   - **共線與退化檢查**：在三點圓弧與多邊形面積解算中，嚴格增加向量夾角與外積閾值檢驗，遇到共線點時平滑降級為直線標註，避免浮點除以零（NaN / Infinity）導致 UI 崩潰。
   - **多邊形自相交防禦**：加入多邊形邊界交叉檢測算法，標註自相交邊界並防止鞋帶公式（Shoelace formula）產生錯誤負面積。
   - **多點緩衝區安全動態擴展**：測量點陣列加入上限動態保護與拷貝防護，徹底根除 `IndexOutOfBoundsException`。
2. **AR 追蹤中斷優雅恢復**：
   - 當使用者劇烈晃動或光線昏暗導致 ARCore 追蹤狀態降為 `PAUSED` 或 `STOPPED` 時，系統自動鎖定已量測的世界座標點，暫停新增脆弱錨點，並保留當前量測數值，追蹤恢復後無縫銜接。
3. **相機管線切換競態防禦**：
   - 嚴格隔離 ARCore 與 Camera2 Fallback 的管線切換順序，確保 `CameraDevice.close()` 與 `ImageReader.close()` 在背景協程中完全完成並等待釋放鎖後，才啟動另一相機實例，徹底根除 `ERROR_CAMERA_IN_USE`。
4. **記憶體生命週期與零洩漏**：
   - 排查所有截圖分享、點雲投影與 Bitmap 處理邏輯，確保所有暫存 Bitmap、Image、Buffer 均有 `use { ... }` 或 `try-finally { recycle() }` 保護。

### 第三階段：測試驗證與品質保證 (Verification & Quality Assurance)
1. **擴充 Robolectric 與幾何解算單元測試**：
   - 涵蓋極限幾何退化案例（垂直平面、微距 1mm、超遠距 100m、三點共線圓弧）。
   - 涵蓋 Vulkan 生命週期狀態機各狀態轉換與背景中斷模擬測試。
2. **編譯檢驗與測試執行**：
   - 執行 `compile_applet` 確保編譯零錯誤。
   - 執行 `gradle :app:testDebugUnitTest` 確保 100% 測試覆蓋通過。

---

## 四、 驗收標準 (Quality Acceptance Criteria)

| 檢驗指標 | 品質驗收標準 |
| :--- | :--- |
| **Vulkan 管理** | 100% 純 Vulkan 架構（無任何 OpenGL / GLSurfaceView），屏幕旋轉與進出背景 100 次無崩潰無洩漏 |
| **Bug 消除** | 幾何解算零 NaN/無窮大，連續測量無 Array 越界，相機切換無 Camera in Use 錯誤 |
| **穩定度** | 追蹤遺失時平滑保持已有數據，無死鎖假死 |
| **測試結果** | 全部 Unit 測試與 Robolectric 測試通過，編譯通過 |
