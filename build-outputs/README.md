# 📦 Build Outputs & APK 安裝包

本目錄存放 **相機 AR 測量儀 (Pixel Measure)** 編譯完成的 Android 應用程式安裝檔（APK）。

---

## 📥 最新安裝包下載

| 檔案名稱 | 說明 | 支援架構 | 最低系統需求 |
| :--- | :--- | :--- | :--- |
| **[`app-debug.apk`](./app-debug.apk)** | 最新測試版本安裝檔（含 60Hz 預覽與 Gemini 磁磚跟蹤） | `arm64-v8a`, `armeabi-v7a`, `x86_64` | Android 8.0 (API 26) 以上 |

---

## 📱 手機端安裝教學

### 方式一：直接在 GitHub / 瀏覽器上下載
1. 點擊上面的 [`app-debug.apk`](./app-debug.apk) 檔案。
2. 點選 **「Download」**（或 **「View raw」**）按鈕下載至手機或電腦。
3. 將 APK 傳送至 Android 手機後，點擊檔案進行安裝。

### 方式二：ADB 命令行安裝（開發者）
若手機已連接電腦並開啟 USB 偵錯：
```bash
adb install -r build-outputs/app-debug.apk
```

---

## ⚠️ 首次安裝注意事項

- **Play 安全防護 / 未知來源提示**：
  由於此 APK 為獨立編譯版本（尚未發布至 Google Play 商店），Android 系統在首次安裝時可能會跳出「未知的應用程式」或「Play 安全防護已封鎖」。這是 Android 系統對所有非 Play Store 下載檔案的標準安全保護機制。請點擊 **「仍要安裝」** 或在設定中 **「允許來自此來源的應用程式」** 即可順利完成安裝。
- **相機與感測器權限**：
  首次啟動時請務必允許 **「相機 (Camera)」** 權限，以啟用 AR 空間座標計算與 60Hz 即時影像串流。

---

## ✨ 包含功能版本摘要
- 🚀 **60Hz 高更新率相機預覽**：硬體直出零拷貝 SurfaceView 渲染。
- 🤖 **Gemini AI 智慧磁磚偵測**：AI 圈定地磚邊界、懸浮智慧膠囊與動態流光光圈。
- 📐 **空間即時測量**：距離、折線、面積、3D 體積、測高儀與微米螢幕尺規。
- 🛰️ **雙模感測器融合**：ARCore 6DoF 空間追蹤 + CameraX 備援相機。
