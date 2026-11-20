package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.util.Log
import java.util.Locale
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.MeasureDatabase
import com.example.data.model.MeasureRecord
import com.example.data.repository.MeasureRepository
import com.example.logic.MeasureFormatter
import com.example.logic.PixelPerformanceMonitor
import com.example.logic.PixelThermalPolicy
import com.example.logic.ThermalTier
import com.example.logic.TranslationManager
import com.example.logic.ar.*
import com.example.logic.sensor.SensorCorrectionTelemetry
import com.example.logic.sensor.SensorFusionCorrectionEngine
import com.example.logic.vulkan.PixelGpuProfile
import com.example.logic.vulkan.PixelGpuRenderPolicy
import com.example.logic.vulkan.VulkanArGraphicsPipeline
import com.google.ar.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

enum class HapticType {
    SNAP,   // Crisp light tick for magnetic snapping / 0° alignment
    CLICK,  // Standard click for UI controls
    HEAVY,  // Heavy click for point addition & shutter
    DOUBLE  // Double pulse for save success
}

/**
 * Modern, clean ViewModel managing AR measurements, sensor fusion,
 * persistence, unit conversions, and UI state.
 */
class MeasureViewModel(private val app: Application) : AndroidViewModel(app) {

    private val prefs: SharedPreferences =
        app.getSharedPreferences("measure_app_prefs", Context.MODE_PRIVATE)

    private val database = MeasureDatabase.getDatabase(app)
    private val repository = MeasureRepository(database.measureDao())

    // Language state
    private val _currentLanguage = MutableStateFlow(resolveInitialLanguage())
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    private fun resolveInitialLanguage(): String {
        // 1. Check if user configured a language in system Settings on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = app.getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
                val appLocales = localeManager?.applicationLocales
                if (appLocales != null && !appLocales.isEmpty) {
                    val primary = appLocales.get(0)
                    val matched = TranslationManager.matchLanguageCode(primary.toLanguageTag())
                    prefs.edit().putString("selected_language", matched).apply()
                    return matched
                }
            } catch (e: Exception) {
                // Ignore fallback to prefs
            }
        }

        // 2. Check saved preference
        val savedLang = prefs.getString("selected_language", null)
        if (!savedLang.isNullOrBlank()) {
            return savedLang
        }

        // 3. Fallback to device system locale
        return try {
            val locales = app.resources.configuration.locales
            val systemLocale = if (!locales.isEmpty) locales.get(0) else Locale.getDefault()
            val matched = TranslationManager.matchLanguageCode(systemLocale.toLanguageTag())
            prefs.edit().putString("selected_language", matched).apply()
            matched
        } catch (e: Exception) {
            "zh-TW"
        }
    }

    /**
     * Synchronizes app language with system per-app locale settings.
     * Called on app startup, onResume, and configuration changes.
     */
    fun syncWithSystemLocale() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = getApplication<Application>().getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
                val appLocales = localeManager?.applicationLocales
                if (appLocales != null && !appLocales.isEmpty) {
                    val primary = appLocales.get(0)
                    val matched = TranslationManager.matchLanguageCode(primary.toLanguageTag())
                    if (matched != _currentLanguage.value) {
                        _currentLanguage.value = matched
                        prefs.edit().putString("selected_language", matched).apply()
                        modernArEngine.currentLangCode = matched
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun setLanguage(langCode: String) {
        _currentLanguage.value = langCode
        prefs.edit().putString("selected_language", langCode).apply()
        modernArEngine.currentLangCode = langCode
        if (modernArEngine.session == null) {
            _surfaceTypeAtCenter.value = tr("vm_surface_searching")
        }

        // Synchronize to Android System LocaleManager on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = getApplication<Application>().getSystemService(Context.LOCALE_SERVICE) as? LocaleManager
                val targetLocale = Locale.forLanguageTag(langCode)
                localeManager?.applicationLocales = LocaleList(targetLocale)
            } catch (e: Exception) {
                // Safe fallback
            }
        }
    }

    /**
     * Launches the Android System Settings app for this application's language preferences.
     * On Android 13+ (API 33+), directly opens the App Locale Settings screen.
     * On older Android versions, opens the App Details screen.
     */
    fun openSystemLanguageSettings(context: Context) {
        val addNewTaskIfNeeded: (Intent) -> Intent = { intent ->
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            intent
        }
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        try {
            context.startActivity(addNewTaskIfNeeded(intent))
        } catch (e: Exception) {
            try {
                context.startActivity(addNewTaskIfNeeded(Intent(Settings.ACTION_LOCALE_SETTINGS)))
            } catch (e2: Exception) {
                try {
                    context.startActivity(addNewTaskIfNeeded(Intent(Settings.ACTION_SETTINGS)))
                } catch (_: Exception) {
                    _toastMessage.tryEmit(tr("vm_cannot_open_lang_settings"))
                }
            }
        }
    }

    private fun tr(key: String): String = TranslationManager.getString(key, _currentLanguage.value)

    private fun trFmt(key: String, vararg args: Any): String =
        TranslationManager.formatString(key, _currentLanguage.value, *args)

    fun getString(key: String): String {
        return TranslationManager.getString(key, _currentLanguage.value)
    }

    // Top-level Tool Navigation Mode: 0 = Camera AR, 1 = Screen Ruler
    private val _currentMode = MutableStateFlow(0)
    val currentMode: StateFlow<Int> = _currentMode.asStateFlow()

    fun setMode(mode: Int) {
        _currentMode.value = mode
    }

    // Camera SubMode:
    // 0 = Auto-Detect (Smart Geometry), 1 = Area, 2 = Height, 3 = 3D Box Volume, 4 = Circle/Diameter, 5 = Angle
    // In Auto-Detect (mode 0), the app automatically determines whether you're measuring a straight distance, closed polygon area, vertical height, or circle based on points and spatial gestures.
    private val _cameraSubMode = MutableStateFlow(0)
    val cameraSubMode: StateFlow<Int> = _cameraSubMode.asStateFlow()

    // Auto-detected geometry classification: "DISTANCE", "AREA", "HEIGHT", "CIRCLE", "ANGLE"
    private val _autoDetectedType = MutableStateFlow("DISTANCE")
    val autoDetectedType: StateFlow<String> = _autoDetectedType.asStateFlow()

    fun setCameraSubMode(mode: Int) {
        _cameraSubMode.value = mode
        clearActivePoints()
    }

    // Unit Selection: "cm", "m", "in", "ft", "yd"
    private val _selectedUnit = MutableStateFlow(
        prefs.getString("selected_unit", "cm") ?: "cm"
    )
    val selectedUnit: StateFlow<String> = _selectedUnit.asStateFlow()

    fun setSelectedUnit(unit: String) {
        _selectedUnit.value = unit
        prefs.edit().putString("selected_unit", unit).apply()
    }

    // Welcome & Onboarding Guide Screen State
    private val _showWelcomeScreen = MutableStateFlow(!prefs.getBoolean("has_seen_welcome_screen", false))
    val showWelcomeScreen: StateFlow<Boolean> = _showWelcomeScreen.asStateFlow()

    fun openWelcomeScreen() {
        _showWelcomeScreen.value = true
        triggerHapticFeedback(HapticType.CLICK)
    }

    fun dismissWelcomeScreen(neverShowAgain: Boolean = true) {
        _showWelcomeScreen.value = false
        if (neverShowAgain) {
            prefs.edit().putBoolean("has_seen_welcome_screen", true).apply()
        }
        triggerHapticFeedback(HapticType.CLICK)
    }

    val refreshRateMode = MutableStateFlow(prefs.getString("refresh_rate_mode", "120Hz") ?: "120Hz")

    private fun refreshRateToTargetFps(mode: String = refreshRateMode.value): Int = when (mode) {
        "120Hz" -> 120
        "60Hz" -> 60
        else -> 30
    }

    // Pixel Thermal & Sustained Performance Monitor
    val pixelPerformanceMonitor = PixelPerformanceMonitor(app)
    val thermalPolicy: StateFlow<PixelThermalPolicy> = pixelPerformanceMonitor.policy
    private val _effectiveTargetFps = MutableStateFlow(
        if (prefs.getBoolean("high_fps_mode_enabled", true)) refreshRateToTargetFps(refreshRateMode.value) else 30
    )
    val effectiveTargetFps: StateFlow<Int> = _effectiveTargetFps.asStateFlow()

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Modern AR Engine & Session
    val modernArEngine = ModernArEngine(app)
    val arSession: Session? get() = modernArEngine.session
    val arCoreStatus = modernArEngine.arCoreStatus
    val arCoreStatusMessage = modernArEngine.arCoreStatusMessage

    private var hasCheckedArCore = false
    private val arInitMutex = kotlinx.coroutines.sync.Mutex()

    private fun isCameraPermissionGranted(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun isOpticalFallbackMode(): Boolean {
        return modernArEngine.session == null || _isFrontCamera.value || _isCameraSimulationActive.value
    }

    fun updateFallbackCameraMatrices() {
        if (!isOpticalFallbackMode()) return
        val w = displayWidth.coerceAtLeast(1)
        val h = displayHeight.coerceAtLeast(1)
        val aspect = w.toFloat() / h.toFloat()
        val view = FloatArray(16)
        val proj = FloatArray(16)
        com.example.logic.vulkan.VulkanMatrix4.setIdentity(view, 0)
        com.example.logic.vulkan.VulkanMatrix4.perspective(proj, 0, 60f, aspect, 0.05f, 50f)
        if (!view.contentEquals(_viewMatrix.value)) _viewMatrix.value = view
        if (!proj.contentEquals(_projectionMatrix.value)) _projectionMatrix.value = proj
    }

    suspend fun ensureArCoreInitialized(activity: Activity? = null, forceRetry: Boolean = false): Session? {
        if (!isCameraPermissionGranted()) {
            return null
        }
        modernArEngine.session?.let { existing ->
            return existing
        }
        return arInitMutex.withLock {
            modernArEngine.session?.let { return@withLock it }
            if (hasCheckedArCore && !forceRetry && modernArEngine.arCoreStatus.value == ArCoreEngineStatus.ACTIVE) {
                return@withLock null
            }
            hasCheckedArCore = true
            val installReady = if (activity != null) {
                withContext(Dispatchers.Main) {
                    modernArEngine.ensureInstallReadyOnMainThread(activity, explicitUserAction = false)
                }
            } else {
                true
            }
            if (!installReady) {
                withContext(Dispatchers.Main) {
                    _isMeasurementAvailable.value = false
                    updateFallbackCameraMatrices()
                    Log.w("MeasureViewModel", "ARCore install/availability check returned not ready (status=${modernArEngine.arCoreStatus.value}); measurement gated.")
                }
                return@withLock null
            }
            val created = withContext(Dispatchers.IO) {
                modernArEngine.checkAndCreateSession(activity, skipInstallPrompt = true)
            }
            withContext(Dispatchers.Main) {
                if (created != null) {
                    val sc = modernArEngine.sharedCamera
                    val camMgr = highSpeedCamera2Manager
                    if (sc != null && camMgr != null && !_isFrontCamera.value) {
                        camMgr.sharedCamera = sc
                        camMgr.preferredCameraId = modernArEngine.selectedCameraId
                        camMgr.preferredAspectRatio = modernArEngine.selectedCameraImageSize?.let {
                            if (it.height > 0) it.width.toFloat() / it.height.toFloat() else null
                        }
                        camMgr.preferredFpsRange = modernArEngine.selectedCameraFpsRange
                    }
                    // Keep _isMeasurementAvailable driven truthfully by tracked surface hits once frames arrive
                    Log.i("MeasureViewModel", "Google ARCore session initialized (camId=${modernArEngine.selectedCameraId}, fps=${modernArEngine.selectedCameraFpsRange}).")
                } else {
                    _isMeasurementAvailable.value = false
                    updateFallbackCameraMatrices()
                    Log.w("MeasureViewModel", "ARCore session unavailable (status=${modernArEngine.arCoreStatus.value}); measurement gated until ARCore is ready.")
                }
            }
            created
        }
    }

    fun warmUpArCoreAvailability() {
        modernArEngine.warmUpAvailability()
    }

    fun isArCoreInstalled(): Boolean = ModernArEngine.isArCorePackageInstalled(app)

    fun checkAndInitArCore(activity: Activity? = null, forceRetry: Boolean = false) {
        if (!isCameraPermissionGranted()) return
        if (modernArEngine.session != null) return
        if (hasCheckedArCore && !forceRetry && modernArEngine.arCoreStatus.value == ArCoreEngineStatus.ACTIVE) return
        viewModelScope.launch {
            val hadSessionBefore = modernArEngine.session != null
            val created = ensureArCoreInitialized(activity, forceRetry)
            if (created != null && !hadSessionBefore) {
                val sc = modernArEngine.sharedCamera
                val camMgr = highSpeedCamera2Manager
                if (sc != null && camMgr != null && !_isFrontCamera.value) {
                    camMgr.reconnectWithSharedCamera(
                        sharedCam = sc,
                        cameraId = modernArEngine.selectedCameraId,
                        imageSize = modernArEngine.selectedCameraImageSize,
                        fpsRange = modernArEngine.selectedCameraFpsRange,
                        onBeforeCloseCamera = { modernArEngine.pause() }
                    )
                }
            }
        }
    }

    fun requestInstallArCore(activity: Activity) {
        val installed = modernArEngine.requestInstallArCore(activity)
        if (installed) {
            checkAndInitArCore(activity, forceRetry = true)
        }
    }

    private val _arTrackingState = MutableStateFlow(TrackingState.STOPPED)
    val arTrackingState: StateFlow<TrackingState> = _arTrackingState.asStateFlow()

    private val _trackingFailureReason = MutableStateFlow(TrackingFailureReason.NONE)
    val trackingFailureReason: StateFlow<TrackingFailureReason> = _trackingFailureReason.asStateFlow()

    // Real-time AR Plane & Spatial Tracking Stability (Confidence Score & Feature Point Health)
    private val _trackingStability = MutableStateFlow(ArTrackingStability.default())
    val trackingStability: StateFlow<ArTrackingStability> = _trackingStability.asStateFlow()

    private val _isDepthAvailable = MutableStateFlow(false)
    val isDepthAvailable: StateFlow<Boolean> = _isDepthAvailable.asStateFlow()

    private val _arPlanesCount = MutableStateFlow(0)
    val arPlanesCount: StateFlow<Int> = _arPlanesCount.asStateFlow()

    private val _detectedPlanes = MutableStateFlow<List<DetectedPlaneInfo>>(emptyList())
    val detectedPlanes: StateFlow<List<DetectedPlaneInfo>> = _detectedPlanes.asStateFlow()

    private val _surfaceTypeAtCenter = MutableStateFlow(TranslationManager.getString("vm_surface_searching", _currentLanguage.value))
    val surfaceTypeAtCenter: StateFlow<String> = _surfaceTypeAtCenter.asStateFlow()

    private val _isMeasurementAvailable = MutableStateFlow(false)
    val isMeasurementAvailable: StateFlow<Boolean> = _isMeasurementAvailable.asStateFlow()

    private val _lightIntensity = MutableStateFlow(1.0f)
    val lightIntensity: StateFlow<Float> = _lightIntensity.asStateFlow()

    private val _viewMatrix = MutableStateFlow(FloatArray(16))
    val viewMatrix: StateFlow<FloatArray> = _viewMatrix.asStateFlow()

    private val _projectionMatrix = MutableStateFlow(FloatArray(16))
    val projectionMatrix: StateFlow<FloatArray> = _projectionMatrix.asStateFlow()

    private val vulkanViewMatrixBuffer = FloatArray(16)
    private val vulkanProjMatrixBuffer = FloatArray(16)
    private val vulkanCircleBasis = DoubleArray(9)
    private val vulkanCircleX = DoubleArray(48)
    private val vulkanCircleY = DoubleArray(48)
    private val vulkanCircleZ = DoubleArray(48)
    private val vulkanCircleFloats = FloatArray(48 * 3)
    private val surfaceNormalScratch = floatArrayOf(0f, 1f, 0f)
    private val normalEmitPool = arrayOf(FloatArray(3), FloatArray(3))
    private var normalEmitIdx = 0
    private var lastTapTimestampMs = 0L
    private var lastDriftWarningTimestampMs = 0L
    private val screenPosBuffer = FloatArray(2)

    private val _liveTargetScreenPos = MutableStateFlow<androidx.compose.ui.geometry.Offset?>(null)
    val liveTargetScreenPos: StateFlow<androidx.compose.ui.geometry.Offset?> = _liveTargetScreenPos.asStateFlow()
    private val _liveSurfaceNormal = MutableStateFlow(floatArrayOf(0f, 1f, 0f))
    val liveSurfaceNormal: StateFlow<FloatArray> = _liveSurfaceNormal.asStateFlow()
    private val _isSurfaceNormalLocked = MutableStateFlow(false)
    val isSurfaceNormalLocked: StateFlow<Boolean> = _isSurfaceNormalLocked.asStateFlow()
    private var lastValidHitTimestampMs: Long = 0L

    // Screen dimensions & display rotation for zero-cost Vulkan Pre-Rotation
    @Volatile
    var displayRotation: Int = android.view.Surface.ROTATION_0
        private set
    var displayWidth: Int = 1080
        private set
    var displayHeight: Int = 2400
        private set

    fun updateDisplayGeometry(rotation: Int, width: Int, height: Int) {
        displayRotation = rotation
        displayWidth = width
        displayHeight = height
        if (isOpticalFallbackMode()) {
            updateFallbackCameraMatrices()
        }
    }

    // Active placed points in 3D space with concurrency lock
    private val pointsLock = Any()
    val capturedPoints = mutableStateListOf<Point3D>()
    private val undoStack = mutableListOf<List<Point3D>>()
    @Volatile
    private var capturedPointsSnapshotCache: List<Point3D> = emptyList()

    private fun updateCapturedPointsSnapshotCache() {
        synchronized(pointsLock) {
            capturedPointsSnapshotCache = capturedPoints.toList()
        }
    }

    /**
     * Ultra-fast zero-allocation snapshot for 60/90/120 FPS Vulkan render loop.
     * Guaranteed immutable and non-blocking O(1) reference fetch.
     */
    fun getCapturedPointsSnapshot(): List<Point3D> {
        return capturedPointsSnapshotCache
    }

    // Real-time live targeting preview from current reticle position
    private val _liveTargetPoint = MutableStateFlow<Point3D?>(null)
    val liveTargetPoint: StateFlow<Point3D?> = _liveTargetPoint.asStateFlow()

    private val _liveDistanceMeters = MutableStateFlow<Double?>(null)
    val liveDistanceMeters: StateFlow<Double?> = _liveDistanceMeters.asStateFlow()

    private val _liveAngleDegrees = MutableStateFlow<Double?>(null)
    val liveAngleDegrees: StateFlow<Double?> = _liveAngleDegrees.asStateFlow()

    private val _isSnapped = MutableStateFlow(false)
    val isSnapped: StateFlow<Boolean> = _isSnapped.asStateFlow()

    // Torch / Flashlight state & Brightness control
    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isTakingPhoto = MutableStateFlow(false)
    val isTakingPhoto: StateFlow<Boolean> = _isTakingPhoto.asStateFlow()

    fun setIsTakingPhoto(taking: Boolean) {
        _isTakingPhoto.value = taking
    }

    private val _torchBrightness = MutableStateFlow(prefs.getFloat("torch_brightness", 1.0f))
    val torchBrightness: StateFlow<Float> = _torchBrightness.asStateFlow()

    // Toast / Feedback event channel
    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessage = _toastMessage.asSharedFlow()

    @Volatile
    private var suppressSettingToasts = false

    private fun emitSettingToast(message: String) {
        if (!suppressSettingToasts) {
            _toastMessage.tryEmit(message)
        }
    }

    fun showToast(message: String) {
        _toastMessage.tryEmit(message)
    }

    // Haptic feedback channel with HapticType
    private val _hapticEvent = MutableSharedFlow<HapticType>(extraBufferCapacity = 1)
    val hapticEvent = _hapticEvent.asSharedFlow()

    fun triggerHapticFeedback(type: HapticType = HapticType.CLICK) {
        if (vibrateOnAlignment.value) {
            _hapticEvent.tryEmit(type)
        }
    }

    // Database records
    val savedRecords: StateFlow<List<MeasureRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val vibrateOnAlignment = MutableStateFlow(prefs.getBoolean("vibrate_align", true))
    fun setVibrateOnAlignment(enabled: Boolean) {
        vibrateOnAlignment.value = enabled
        prefs.edit().putBoolean("vibrate_align", enabled).apply()
    }

    // Dynamic color preference (Forced enabled for Material You Dynamic Color)
    private val _dynamicColorEnabled = MutableStateFlow(true)
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    // Battery & Power Saver Automated Strategy State
    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isPowerSaveMode = MutableStateFlow(false)
    val isPowerSaveMode: StateFlow<Boolean> = _isPowerSaveMode.asStateFlow()

    private val _autoBatteryProfileEnabled = MutableStateFlow(prefs.getBoolean("auto_battery_profile", true))
    val autoBatteryProfileEnabled: StateFlow<Boolean> = _autoBatteryProfileEnabled.asStateFlow()

    private val _currentAppliedProfile = MutableStateFlow(prefs.getString("current_applied_profile", "BALANCED") ?: "BALANCED")
    val currentAppliedProfile: StateFlow<String> = _currentAppliedProfile.asStateFlow()

    fun setAutoBatteryProfileEnabled(enabled: Boolean) {
        _autoBatteryProfileEnabled.value = enabled
        prefs.edit().putBoolean("auto_battery_profile", enabled).apply()
        if (enabled) {
            updateBatteryStatusAndEvaluate(showToast = true)
        } else {
            _toastMessage.tryEmit(tr("vm_battery_opt_off"))
        }
        triggerHapticFeedback()
    }

    /**
     * 根據當前電量與省電模式評估應採用的情境模式
     * 
     * 規則：
     * 1. 當未開啟省電模式且電量高於59：使用專業工程極限精密 (PRO_PRECISION)
     * 2. 當已開啟省電模式且電量高於59：使用日常智慧平衡 (BALANCED)
     * 3. 當電量低於59且高於45，且未開啟省電模式：使用日常智慧平衡 (BALANCED)
     * 4. 當電量低於45：使用省電長效極速 (POWER_SAVER)
     */
    fun evaluateBatteryProfile(level: Int, powerSave: Boolean): String {
        return when {
            level < 45 -> "POWER_SAVER"
            !powerSave && level > 59 -> "PRO_PRECISION"
            else -> "BALANCED"
        }
    }

    fun updateBatteryStatusAndEvaluate(showToast: Boolean = false) {
        try {
            val context = getApplication<Application>().applicationContext
            
            // Read system battery status
            val batteryFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatusIntent = context.registerReceiver(null, batteryFilter)
            val level = batteryStatusIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatusIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 100
            _batteryLevel.value = pct

            // Read system power save mode status
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val powerSave = powerManager?.isPowerSaveMode ?: false
            _isPowerSaveMode.value = powerSave

            if (_autoBatteryProfileEnabled.value) {
                val targetProfile = evaluateBatteryProfile(pct, powerSave)
                if (targetProfile != _currentAppliedProfile.value || showToast) {
                    applyMeasurementProfile(targetProfile, isAutoTriggered = true)
                }
            }
        } catch (e: Exception) {
            Log.e("MeasureViewModel", "Failed to update battery status", e)
        }
    }

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryStatusAndEvaluate(showToast = false)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean = true) {
        _dynamicColorEnabled.value = true
        prefs.edit().putBoolean("dynamic_color", true).apply()
        triggerHapticFeedback()
        _toastMessage.tryEmit(tr("vm_dynamic_color_forced"))
    }

    // Day/Night Theme Mode: "SYSTEM" (Follow system), "LIGHT" (Force Day), "DARK" (Force Night)
    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
        triggerHapticFeedback()
        val label = when (mode) {
            "LIGHT" -> tr("set_theme_light_short")
            "DARK" -> tr("set_theme_dark_short")
            else -> tr("set_theme_system_short")
        }
        emitSettingToast(trFmt("vm_toast_theme_fmt", label))
    }

    // UI Customization Preferences (3D Reticle Styles: DOUBLE_RING, PRECISION_CROSSHAIR, MINIMAL_DOT, HOLOGRAPHIC_RADAR)
    private val _reticleStyle = MutableStateFlow(
        prefs.getString("reticle_style", "DOUBLE_RING") ?: "DOUBLE_RING"
    )
    val reticleStyle: StateFlow<String> = _reticleStyle.asStateFlow()

    fun setReticleStyle(style: String) {
        _reticleStyle.value = style
        prefs.edit().putString("reticle_style", style).apply()
        triggerHapticFeedback()
        val label = when (style) {
            "PRECISION_CROSSHAIR" -> tr("vm_reticle_crosshair")
            "MINIMAL_DOT" -> tr("vm_reticle_dot")
            "HOLOGRAPHIC_RADAR" -> tr("vm_reticle_radar")
            else -> tr("vm_reticle_double")
        }
        emitSettingToast(trFmt("vm_toast_reticle_fmt", label))
    }

    // AR Accent Color Palette: DYNAMIC, PIXEL_BLUE, NEON_LIME, AMBER_GOLD, CORAL_ROSE
    private val _arAccentPalette = MutableStateFlow(
        prefs.getString("ar_accent_palette", "DYNAMIC") ?: "DYNAMIC"
    )
    val arAccentPalette: StateFlow<String> = _arAccentPalette.asStateFlow()

    fun setArAccentPalette(palette: String) {
        _arAccentPalette.value = palette
        prefs.edit().putString("ar_accent_palette", palette).apply()
        triggerHapticFeedback()
        val label = when (palette) {
            "PIXEL_BLUE" -> tr("vm_accent_blue")
            "NEON_LIME" -> tr("vm_accent_lime")
            "AMBER_GOLD" -> tr("vm_accent_amber")
            "CORAL_ROSE" -> tr("vm_accent_coral")
            else -> tr("vm_accent_dynamic")
        }
        emitSettingToast(trFmt("vm_toast_accent_fmt", label))
    }

    // Magnetic Vertex Snap Sensitivity in cm (0f = Off, 2.5f = Precision, 5.0f = Strong)
    private val _vertexSnapThresholdCm = MutableStateFlow(
        prefs.getFloat("vertex_snap_cm", 2.5f).coerceIn(0f, 6.0f)
    )
    val vertexSnapThresholdCm: StateFlow<Float> = _vertexSnapThresholdCm.asStateFlow()

    fun setVertexSnapThresholdCm(cm: Float) {
        val clamped = cm.coerceIn(0f, 6.0f)
        _vertexSnapThresholdCm.value = clamped
        prefs.edit().putFloat("vertex_snap_cm", clamped).apply()
        triggerHapticFeedback()
    }

    // Keep Screen On While Measuring
    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean("keep_screen_on", true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
        triggerHapticFeedback()
        emitSettingToast(tr(if (enabled) "vm_toast_keep_on" else "vm_toast_keep_off"))
    }

    // Auto-Copy Measurement Value on Line Completion
    private val _autoCopyOnComplete = MutableStateFlow(prefs.getBoolean("auto_copy_on_complete", false))
    val autoCopyOnComplete: StateFlow<Boolean> = _autoCopyOnComplete.asStateFlow()

    fun setAutoCopyOnComplete(enabled: Boolean) {
        _autoCopyOnComplete.value = enabled
        prefs.edit().putBoolean("auto_copy_on_complete", enabled).apply()
        triggerHapticFeedback()
        emitSettingToast(tr(if (enabled) "vm_toast_autocopy_on" else "vm_toast_autocopy_off"))
    }

    // Ruler Dual-Unit Edge Display (false = cm on both sides, true = cm on left / inches on right)
    private val _rulerDualUnit = MutableStateFlow(prefs.getBoolean("ruler_dual_unit", false))
    val rulerDualUnit: StateFlow<Boolean> = _rulerDualUnit.asStateFlow()

    fun setRulerDualUnit(enabled: Boolean) {
        _rulerDualUnit.value = enabled
        prefs.edit().putBoolean("ruler_dual_unit", enabled).apply()
        triggerHapticFeedback()
        emitSettingToast(tr(if (enabled) "vm_toast_dual_in" else "vm_toast_dual_cm"))
    }

    // Spirit Level Tolerance (degrees: 0.2f, 0.5f, 1.0f) & Acoustic Cue Default
    private val _levelToleranceDeg = MutableStateFlow(
        prefs.getFloat("level_tolerance_deg", 0.5f).coerceIn(0.1f, 3.0f)
    )
    val levelToleranceDeg: StateFlow<Float> = _levelToleranceDeg.asStateFlow()

    fun setLevelToleranceDeg(deg: Float) {
        val clamped = deg.coerceIn(0.1f, 3.0f)
        _levelToleranceDeg.value = clamped
        prefs.edit().putFloat("level_tolerance_deg", clamped).apply()
        triggerHapticFeedback()
    }

    private val _levelAudioEnabled = MutableStateFlow(prefs.getBoolean("level_audio_enabled", true))
    val levelAudioEnabled: StateFlow<Boolean> = _levelAudioEnabled.asStateFlow()

    fun setLevelAudioEnabled(enabled: Boolean) {
        _levelAudioEnabled.value = enabled
        prefs.edit().putBoolean("level_audio_enabled", enabled).apply()
        triggerHapticFeedback()
    }

    private val _lineThickness = MutableStateFlow(prefs.getFloat("line_thickness", 3.5f))
    val lineThickness: StateFlow<Float> = _lineThickness.asStateFlow()

    fun setLineThickness(thickness: Float) {
        _lineThickness.value = thickness
        prefs.edit().putFloat("line_thickness", thickness).apply()
        triggerHapticFeedback()
    }

    private val _hudStyle = MutableStateFlow(prefs.getString("hud_style", "GLASS") ?: "GLASS")
    val hudStyle: StateFlow<String> = _hudStyle.asStateFlow()

    fun setHudStyle(style: String) {
        _hudStyle.value = style
        prefs.edit().putString("hud_style", style).apply()
        triggerHapticFeedback()
        emitSettingToast(tr("vm_toast_hud_applied"))
    }

    // Extended Custom UI Preferences
    private val _arFontSize = MutableStateFlow(prefs.getString("ar_font_size", "STANDARD") ?: "STANDARD")
    val arFontSize: StateFlow<String> = _arFontSize.asStateFlow()

    fun setArFontSize(size: String) {
        _arFontSize.value = size
        prefs.edit().putString("ar_font_size", size).apply()
        triggerHapticFeedback()
        val label = when (size) {
            "COMPACT" -> tr("vm_font_compact")
            "LARGE" -> tr("vm_font_large")
            else -> tr("vm_font_standard")
        }
        emitSettingToast(trFmt("vm_toast_font_fmt", label))
    }

    private val _rulerTheme = MutableStateFlow(prefs.getString("ruler_theme", "MATERIAL_YOU") ?: "MATERIAL_YOU")
    val rulerTheme: StateFlow<String> = _rulerTheme.asStateFlow()

    fun setRulerTheme(theme: String) {
        _rulerTheme.value = theme
        prefs.edit().putString("ruler_theme", theme).apply()
        triggerHapticFeedback()
        val label = when (theme) {
            "STEEL" -> tr("vm_ruler_steel")
            "NEON_CYAN" -> tr("vm_ruler_cyan")
            "HIGH_CONTRAST" -> tr("vm_ruler_contrast")
            else -> tr("vm_ruler_dynamic")
        }
        emitSettingToast(trFmt("vm_toast_ruler_theme_fmt", label))
    }

    private val _uiCornerStyle = MutableStateFlow(prefs.getString("ui_corner_style", "ROUNDED") ?: "ROUNDED")
    val uiCornerStyle: StateFlow<String> = _uiCornerStyle.asStateFlow()

    fun setUiCornerStyle(style: String) {
        _uiCornerStyle.value = style
        prefs.edit().putString("ui_corner_style", style).apply()
        triggerHapticFeedback()
        val label = when (style) {
            "PILL" -> tr("vm_corner_pill")
            "ANGULAR" -> tr("vm_corner_angular")
            else -> tr("vm_corner_rounded")
        }
        emitSettingToast(trFmt("vm_toast_corner_fmt", label))
    }

    private val _badgeOpacity = MutableStateFlow(prefs.getString("badge_opacity", "GLASS") ?: "GLASS")
    val badgeOpacity: StateFlow<String> = _badgeOpacity.asStateFlow()

    fun setBadgeOpacity(opacity: String) {
        _badgeOpacity.value = opacity
        prefs.edit().putString("badge_opacity", opacity).apply()
        triggerHapticFeedback()
        val label = when (opacity) {
            "SOLID" -> tr("vm_opacity_solid")
            "CLEAR" -> tr("vm_opacity_clear")
            else -> tr("vm_opacity_glass")
        }
        emitSettingToast(trFmt("vm_toast_opacity_fmt", label))
    }

    private val _gridOverlayStyle = MutableStateFlow(prefs.getString("grid_overlay_style", "OFF") ?: "OFF")
    val gridOverlayStyle: StateFlow<String> = _gridOverlayStyle.asStateFlow()

    fun setGridOverlayStyle(style: String) {
        _gridOverlayStyle.value = style
        prefs.edit().putString("grid_overlay_style", style).apply()
        triggerHapticFeedback()
        val label = when (style) {
            "DOT_MATRIX" -> tr("vm_grid_dot_matrix")
            "OFF" -> tr("vm_grid_off")
            else -> tr("vm_grid_perspective")
        }
        emitSettingToast(trFmt("vm_toast_grid_fmt", label))
    }

    // Customizable UI Button Size Scale (0.85f, 1.0f, 1.15f, 1.30f)
    private val _uiButtonScale = MutableStateFlow(prefs.getFloat("ui_button_scale", 1.0f))
    val uiButtonScale: StateFlow<Float> = _uiButtonScale.asStateFlow()

    fun setUiButtonScale(scale: Float) {
        _uiButtonScale.value = scale
        prefs.edit().putFloat("ui_button_scale", scale).apply()
        triggerHapticFeedback()
        val label = when {
            scale <= 0.88f -> tr("vm_btn_scale_compact")
            scale in 0.95f..1.05f -> tr("vm_btn_scale_standard")
            scale in 1.10f..1.20f -> tr("vm_btn_scale_large")
            else -> tr("vm_btn_scale_xl")
        }
        emitSettingToast(trFmt("vm_toast_btn_scale_fmt", label))
    }

    // Camera Aspect Ratio (4:3, 16:9, 1:1, FULL) - Default to FULL so no mask covers screen
    private val _cameraAspectRatio = MutableStateFlow(
        if (prefs.getBoolean("camera_aspect_ratio_user_selected", false)) {
            prefs.getString("camera_aspect_ratio", "FULL") ?: "FULL"
        } else {
            "FULL"
        }
    )
    val cameraAspectRatio: StateFlow<String> = _cameraAspectRatio.asStateFlow()

    fun setCameraAspectRatio(ratio: String) {
        _cameraAspectRatio.value = ratio
        prefs.edit()
            .putString("camera_aspect_ratio", ratio)
            .putBoolean("camera_aspect_ratio_user_selected", true)
            .apply()
        triggerHapticFeedback()
        val label = when (ratio) {
            "16_9" -> tr("vm_aspect_16_9")
            "1_1" -> tr("vm_aspect_1_1")
            "4_3" -> tr("vm_aspect_4_3")
            else -> tr("vm_aspect_full")
        }
        emitSettingToast(trFmt("vm_toast_aspect_fmt", label))
    }

    fun cycleCameraAspectRatio() {
        val next = when (_cameraAspectRatio.value) {
            "FULL" -> "16_9"
            "16_9" -> "4_3"
            "4_3" -> "1_1"
            else -> "FULL"
        }
        setCameraAspectRatio(next)
    }

    // Camera Capture Mode: Photo (false) vs Video (true)
    private val _isCameraVideoMode = MutableStateFlow(false)
    val isCameraVideoMode: StateFlow<Boolean> = _isCameraVideoMode.asStateFlow()

    fun setCameraVideoMode(isVideo: Boolean) {
        _isCameraVideoMode.value = isVideo
        triggerHapticFeedback()
        _toastMessage.tryEmit(tr(if (isVideo) "vm_toast_video_mode" else "vm_toast_photo_mode"))
    }

    fun toggleCameraVideoMode() {
        setCameraVideoMode(!_isCameraVideoMode.value)
    }

    // Display P3 Wide Color Gamut Profile
    private val _useDisplayP3ColorSpace = MutableStateFlow(prefs.getBoolean("use_display_p3", true))
    val useDisplayP3ColorSpace: StateFlow<Boolean> = _useDisplayP3ColorSpace.asStateFlow()

    fun setUseDisplayP3ColorSpace(enabled: Boolean) {
        _useDisplayP3ColorSpace.value = enabled
        prefs.edit().putBoolean("use_display_p3", enabled).apply()
        triggerHapticFeedback()
        emitSettingToast(tr(if (enabled) "vm_toast_p3_on" else "vm_toast_p3_off"))
    }

    // Camera Lens Dirt & Smudge Detection Warning
    private val _isLensDirtWarningEnabled = MutableStateFlow(prefs.getBoolean("lens_dirt_warning", true))
    val isLensDirtWarningEnabled: StateFlow<Boolean> = _isLensDirtWarningEnabled.asStateFlow()

    fun setLensDirtWarningEnabled(enabled: Boolean) {
        _isLensDirtWarningEnabled.value = enabled
        prefs.edit().putBoolean("lens_dirt_warning", enabled).apply()
        if (!enabled) _isLensSmudged.value = false
        triggerHapticFeedback()
        emitSettingToast(tr(if (enabled) "vm_toast_dirt_on" else "vm_toast_dirt_off"))
    }

    private val _isLensSmudged = MutableStateFlow(false)
    val isLensSmudged: StateFlow<Boolean> = _isLensSmudged.asStateFlow()

    private var lastLensCheckTime = 0L
    private val isAnalyzingLensDirt = java.util.concurrent.atomic.AtomicBoolean(false)
    private val lensCheckPixels = IntArray(48 * 48)

    /**
     * Background offloaded lens smudge & cleanliness analyzer.
     * Throttled to max 1 sample per 2.5~3.0 seconds, dispatched strictly to background thread pool.
     */
    fun analyzeLensCleanliness(bitmap: Bitmap) {
        if (!_isLensDirtWarningEnabled.value) return
        val now = System.currentTimeMillis()
        if (now - lastLensCheckTime < 2500L) return
        if (!isAnalyzingLensDirt.compareAndSet(false, true)) return

        lastLensCheckTime = now

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val scaled = Bitmap.createScaledBitmap(bitmap, 48, 48, false)
                var totalEdgeVariance = 0.0
                var pixelCount = 0
                val pixels = lensCheckPixels
                scaled.getPixels(pixels, 0, 48, 0, 0, 48, 48)
                if (scaled != bitmap) {
                    try { scaled.recycle() } catch (_: Throwable) {}
                }

                for (y in 1 until 47) {
                    val rowIdx = y * 48
                    for (x in 1 until 47) {
                        val idx = rowIdx + x
                        val centerL = (Color.red(pixels[idx]) * 0.299 + Color.green(pixels[idx]) * 0.587 + Color.blue(pixels[idx]) * 0.114)
                        val rightL = (Color.red(pixels[idx + 1]) * 0.299 + Color.green(pixels[idx + 1]) * 0.587 + Color.blue(pixels[idx + 1]) * 0.114)
                        val downL = (Color.red(pixels[idx + 48]) * 0.299 + Color.green(pixels[idx + 48]) * 0.587 + Color.blue(pixels[idx + 48]) * 0.114)

                        val dx = centerL - rightL
                        val dy = centerL - downL
                        totalEdgeVariance += (dx * dx + dy * dy)
                        pixelCount++
                    }
                }

                val avgEdge = if (pixelCount > 0) totalEdgeVariance / pixelCount else 100.0
                // Very low high-frequency variance indicates fingerprint/grease smudging fog
                val detectedSmudge = avgEdge < 14.0
                if (detectedSmudge != _isLensSmudged.value) {
                    _isLensSmudged.value = detectedSmudge
                }
            } catch (e: Throwable) {
                // Ignore frame analysis errors
            } finally {
                isAnalyzingLensDirt.set(false)
            }
        }
    }

    fun dismissLensDirtWarning() {
        _isLensSmudged.value = false
        triggerHapticFeedback()
    }

    private val _dismissedLowLight = MutableStateFlow(false)
    val dismissedLowLight: StateFlow<Boolean> = _dismissedLowLight.asStateFlow()

    private val _isCameraSimulationActive = MutableStateFlow(false)
    val isCameraSimulationActive: StateFlow<Boolean> = _isCameraSimulationActive.asStateFlow()

    fun setCameraSimulationActive(active: Boolean) {
        _isCameraSimulationActive.value = active
    }

    private val _simulatedAlert = MutableStateFlow<String?>(null)
    val simulatedAlert: StateFlow<String?> = _simulatedAlert.asStateFlow()

    fun dismissLowLightWarning() {
        _dismissedLowLight.value = true
        _simulatedAlert.value = null
        triggerHapticFeedback()
    }

    fun triggerLensDirtAlert() {
        _isLensSmudged.value = true
        _simulatedAlert.value = null
        triggerHapticFeedback()
        _toastMessage.tryEmit(tr("vm_toast_alert_lens"))
    }

    fun triggerLowLightAlert() {
        _dismissedLowLight.value = false
        _simulatedAlert.value = "LOW_LIGHT"
        triggerHapticFeedback()
        _toastMessage.tryEmit(tr("vm_toast_alert_light"))
    }

    fun triggerFastMovementAlert() {
        _simulatedAlert.value = "MOTION_EXCESSIVE"
        triggerHapticFeedback()
        _toastMessage.tryEmit(tr("vm_toast_alert_motion"))
    }

    fun triggerLowFeatureAlert() {
        _simulatedAlert.value = "FEATURE_DEFICIENT"
        triggerHapticFeedback()
        _toastMessage.tryEmit(tr("vm_toast_alert_feature"))
    }

    fun dismissMovementWarning() {
        if (_simulatedAlert.value == "MOTION_EXCESSIVE") {
            _simulatedAlert.value = null
        }
        triggerHapticFeedback()
    }

    fun dismissFeatureDeficientWarning() {
        if (_simulatedAlert.value == "FEATURE_DEFICIENT") {
            _simulatedAlert.value = null
        }
        triggerHapticFeedback()
    }

    fun clearSimulatedAlert() {
        _simulatedAlert.value = null
    }

    // Scanning Feature Point Cloud preference
    private val _showPointCloud = MutableStateFlow(prefs.getBoolean("show_point_cloud", true))
    val showPointCloud: StateFlow<Boolean> = _showPointCloud.asStateFlow()

    fun setShowPointCloud(enabled: Boolean) {
        _showPointCloud.value = enabled
        prefs.edit().putBoolean("show_point_cloud", enabled).apply()
    }

    val sensorCorrectionEngine = SensorFusionCorrectionEngine(app)
    val sensorTelemetry: StateFlow<SensorCorrectionTelemetry> = sensorCorrectionEngine.telemetry
    val multiSampleProgress: StateFlow<Float> = sensorCorrectionEngine.multiSampleProgress
    val isMultiSampleLocked: StateFlow<Boolean> = sensorCorrectionEngine.isMultiSampleLocked

    val sensorCorrectionEnabled = MutableStateFlow(prefs.getBoolean("sensor_correction_enabled", true))
    val antiJitterEnabled = MutableStateFlow(prefs.getBoolean("sensor_antijitter_enabled", true))
    val gravityAlignmentEnabled = MutableStateFlow(prefs.getBoolean("sensor_gravity_align_enabled", true))
    val barometerFusionEnabled = MutableStateFlow(prefs.getBoolean("sensor_barometer_fusion_enabled", true))
    val jerkRejectionEnabled = MutableStateFlow(prefs.getBoolean("sensor_jerk_rejection_enabled", true))
    val proximityContactEnabled = MutableStateFlow(prefs.getBoolean("sensor_proximity_enabled", true))
    val stereoParallaxEnabled = MutableStateFlow(prefs.getBoolean("sensor_stereo_enabled", true))
    val multiSampleAveragingEnabled = MutableStateFlow(prefs.getBoolean("sensor_multisample_enabled", true))
    val coplanarProjectionEnabled = MutableStateFlow(prefs.getBoolean("sensor_coplanar_enabled", true))
    val orthogonalSnapEnabled = MutableStateFlow(prefs.getBoolean("sensor_ortho_snap_enabled", true))
    val rawDepthConfidenceEnabled = MutableStateFlow(prefs.getBoolean("raw_depth_confidence_enabled", true))
    val rawDepthConfidenceThreshold = MutableStateFlow(prefs.getInt("raw_depth_confidence_threshold", 45))
    val scaleCalibrationFactor = MutableStateFlow(prefs.getFloat("scale_calibration_factor", 1.0000f))

    val highFpsModeEnabled = MutableStateFlow(prefs.getBoolean("high_fps_mode_enabled", true))
    val highDefinitionQualityEnabled = MutableStateFlow(prefs.getBoolean("high_definition_quality_enabled", true))
    val lowLightBoostEnabled = MutableStateFlow(true)

    fun setLowLightBoostEnabled(enabled: Boolean = true) {
        lowLightBoostEnabled.value = true
        prefs.edit().putBoolean("low_light_boost_enabled", true).apply()
        highSpeedCamera2Manager?.setLowLightBoostEnabled(true)
    }

    fun setRefreshRateMode(mode: String) {
        refreshRateMode.value = mode
        prefs.edit().putString("refresh_rate_mode", mode).apply()
        val targetFps = refreshRateToTargetFps(mode)
        val highFps = targetFps >= 60
        highFpsModeEnabled.value = highFps
        prefs.edit().putBoolean("high_fps_mode_enabled", highFps).apply()
        modernArEngine.preferHighFps = highFps
        pixelPerformanceMonitor.updateUserPreferences(highFps, highDefinitionQualityEnabled.value, targetFps)
        val policy = pixelPerformanceMonitor.policy.value
        _effectiveTargetFps.value = policy.targetFps
        vulkanGraphicsPipeline.updateGpuPolicy(policy.tier, policy.targetFps)
        _gpuRenderPolicy.value = vulkanGraphicsPipeline.currentRenderPolicy
        highSpeedCamera2Manager?.updatePerformanceConfig(
            highFps = highFps,
            highDef = highDefinitionQualityEnabled.value,
            maxFps = policy.targetFps
        )
        triggerHapticFeedback()
        emitSettingToast(when (mode) {
            "120Hz" -> tr("vm_toast_fps_120")
            "60Hz" -> tr("vm_toast_fps_60")
            else -> tr("vm_toast_fps_30")
        })
    }

    fun setHighFpsModeEnabled(enabled: Boolean) {
        setRefreshRateMode(if (enabled) "120Hz" else "30Hz")
    }

    fun setHighDefinitionQualityEnabled(enabled: Boolean) {
        highDefinitionQualityEnabled.value = enabled
        prefs.edit().putBoolean("high_definition_quality_enabled", enabled).apply()
        modernArEngine.preferHighDef = enabled
        pixelPerformanceMonitor.updateUserPreferences(highFpsModeEnabled.value, enabled, refreshRateToTargetFps())
        val policy = pixelPerformanceMonitor.policy.value
        highSpeedCamera2Manager?.updatePerformanceConfig(
            highFps = highFpsModeEnabled.value,
            highDef = enabled,
            maxFps = policy.targetFps
        )
        emitSettingToast(tr(if (enabled) "vm_toast_hq_on" else "vm_toast_hq_off"))
    }

    // Vulkan 1.4 AR Hardware Accelerated Graphics Engine
    val vulkanGraphicsPipeline = VulkanArGraphicsPipeline(app)
    val gpuProfile: PixelGpuProfile = vulkanGraphicsPipeline.gpuProfile
    private val _gpuRenderPolicy = MutableStateFlow(vulkanGraphicsPipeline.currentRenderPolicy)
    val gpuRenderPolicy: StateFlow<PixelGpuRenderPolicy> = _gpuRenderPolicy.asStateFlow()

    val isVulkanGraphicsEnabled = MutableStateFlow(prefs.getBoolean("vulkan_graphics_enabled", true))

    fun setVulkanGraphicsEnabled(enabled: Boolean) {
        isVulkanGraphicsEnabled.value = enabled
        prefs.edit().putBoolean("vulkan_graphics_enabled", enabled).apply()
        emitSettingToast(tr(if (enabled) "vm_toast_vulkan_on" else "vm_toast_vulkan_off"))
    }

    fun setSensorCorrectionEnabled(enabled: Boolean) {
        sensorCorrectionEnabled.value = enabled
        sensorCorrectionEngine.isSensorCorrectionEnabled = enabled
        prefs.edit().putBoolean("sensor_correction_enabled", enabled).apply()
        emitSettingToast(tr(if (enabled) "vm_toast_sensor_on" else "vm_toast_sensor_off"))
    }

    fun setAntiJitterEnabled(enabled: Boolean) {
        antiJitterEnabled.value = enabled
        sensorCorrectionEngine.isAntiJitterEnabled = enabled
        prefs.edit().putBoolean("sensor_antijitter_enabled", enabled).apply()
    }

    fun setGravityAlignmentEnabled(enabled: Boolean) {
        gravityAlignmentEnabled.value = enabled
        sensorCorrectionEngine.isGravityAlignmentEnabled = enabled
        prefs.edit().putBoolean("sensor_gravity_align_enabled", enabled).apply()
    }

    fun setBarometerFusionEnabled(enabled: Boolean) {
        barometerFusionEnabled.value = enabled
        sensorCorrectionEngine.isBarometerFusionEnabled = enabled
        prefs.edit().putBoolean("sensor_barometer_fusion_enabled", enabled).apply()
    }

    fun setJerkRejectionEnabled(enabled: Boolean) {
        jerkRejectionEnabled.value = enabled
        sensorCorrectionEngine.isJerkRejectionEnabled = enabled
        prefs.edit().putBoolean("sensor_jerk_rejection_enabled", enabled).apply()
    }

    fun setProximityContactEnabled(enabled: Boolean) {
        proximityContactEnabled.value = enabled
        sensorCorrectionEngine.isProximityZeroContactEnabled = enabled
        prefs.edit().putBoolean("sensor_proximity_enabled", enabled).apply()
    }

    fun setStereoParallaxEnabled(enabled: Boolean) {
        stereoParallaxEnabled.value = enabled
        sensorCorrectionEngine.isStereoParallaxScaleEnabled = enabled
        prefs.edit().putBoolean("sensor_stereo_enabled", enabled).apply()
    }

    fun setMultiSampleAveragingEnabled(enabled: Boolean) {
        multiSampleAveragingEnabled.value = enabled
        sensorCorrectionEngine.isMultiSampleAveragingEnabled = enabled
        prefs.edit().putBoolean("sensor_multisample_enabled", enabled).apply()
    }

    fun setCoplanarProjectionEnabled(enabled: Boolean) {
        coplanarProjectionEnabled.value = enabled
        sensorCorrectionEngine.isCoplanarProjectionEnabled = enabled
        prefs.edit().putBoolean("sensor_coplanar_enabled", enabled).apply()
    }

    fun setOrthogonalSnapEnabled(enabled: Boolean) {
        orthogonalSnapEnabled.value = enabled
        sensorCorrectionEngine.isOrthogonalSnapEnabled = enabled
        prefs.edit().putBoolean("sensor_ortho_snap_enabled", enabled).apply()
    }

    fun setRawDepthConfidenceEnabled(enabled: Boolean) {
        rawDepthConfidenceEnabled.value = enabled
        modernArEngine.isRawDepthConfidenceFilterEnabled = enabled
        prefs.edit().putBoolean("raw_depth_confidence_enabled", enabled).apply()
        emitSettingToast(tr(if (enabled) "vm_toast_raw_depth_on" else "vm_toast_raw_depth_off"))
    }

    fun setRawDepthConfidenceThreshold(threshold: Int) {
        val clamped = threshold.coerceIn(10, 95)
        rawDepthConfidenceThreshold.value = clamped
        modernArEngine.rawDepthConfidenceThreshold = clamped
        prefs.edit().putInt("raw_depth_confidence_threshold", clamped).apply()
    }

    fun setScaleCalibrationFactor(factor: Float) {
        val clamped = factor.coerceIn(0.8500f, 1.1500f)
        scaleCalibrationFactor.value = clamped
        sensorCorrectionEngine.userScaleCalibrationFactor = clamped
        prefs.edit().putFloat("scale_calibration_factor", clamped).apply()
        emitSettingToast(trFmt("vm_toast_scale_fmt", "%.4f".format(Locale.US, clamped)))
    }


    fun calibrateSensors() {
        sensorCorrectionEngine.resetBarometerBase()
        triggerHapticFeedback()
        _toastMessage.tryEmit(tr("vm_toast_alt_reset"))
    }

    /**
     * 一鍵套用特定情境模式 (Pro Presets)
     */
    fun applyMeasurementProfile(profileKey: String, isAutoTriggered: Boolean = false) {
        _currentAppliedProfile.value = profileKey
        prefs.edit().putString("current_applied_profile", profileKey).apply()

        suppressSettingToasts = true
        try {
            when (profileKey) {
                "PRO_PRECISION" -> {
                    setSelectedUnit("mm")
                    setHighFpsModeEnabled(true)
                    setHighDefinitionQualityEnabled(true)
                    setAntiJitterEnabled(true)
                    setMultiSampleAveragingEnabled(true)
                    setOrthogonalSnapEnabled(true)
                    setReticleStyle("DOUBLE_RING")
                    setVibrateOnAlignment(true)
                    setRawDepthConfidenceEnabled(true)
                    setRawDepthConfidenceThreshold(55)
                    setShowPointCloud(true)
                }
                "POWER_SAVER" -> {
                    setSelectedUnit("cm")
                    setHighFpsModeEnabled(false)
                    setHighDefinitionQualityEnabled(false)
                    setAntiJitterEnabled(false)
                    setMultiSampleAveragingEnabled(false)
                    setOrthogonalSnapEnabled(false)
                    setReticleStyle("DOUBLE_RING")
                    setGridOverlayStyle("OFF")
                    setRawDepthConfidenceEnabled(false)
                    setShowPointCloud(false)
                }
                else -> { // "BALANCED"
                    setSelectedUnit("cm")
                    setHighFpsModeEnabled(true)
                    setHighDefinitionQualityEnabled(true)
                    setAntiJitterEnabled(true)
                    setMultiSampleAveragingEnabled(true)
                    setOrthogonalSnapEnabled(true)
                    setReticleStyle("DOUBLE_RING")
                    setVibrateOnAlignment(true)
                    setGridOverlayStyle("OFF")
                    setRawDepthConfidenceEnabled(true)
                    setRawDepthConfidenceThreshold(45)
                    setShowPointCloud(true)
                }
            }
        } finally {
            suppressSettingToasts = false
        }

        val profileLabel = when (profileKey) {
            "PRO_PRECISION" -> tr("vm_profile_pro")
            "POWER_SAVER" -> tr("vm_profile_saver")
            else -> tr("vm_profile_balanced")
        }
        val toastKey = if (isAutoTriggered) "vm_toast_profile_auto_fmt" else "vm_toast_profile_fmt"
        _toastMessage.tryEmit(trFmt(toastKey, profileLabel))
        triggerHapticFeedback()
    }

    /**
     * 恢復原廠預設設定
     */
    fun resetAllSettingsToDefault() {
        suppressSettingToasts = true
        try {
            setSelectedUnit("cm")
            setReticleStyle("DOUBLE_RING")
            setArAccentPalette("DYNAMIC")
            setLineThickness(3.5f)
            setArFontSize("STANDARD")
            setBadgeOpacity("GLASS")
            setHudStyle("GLASS")
            setGridOverlayStyle("OFF")
            setUiButtonScale(1.0f)
            setUiCornerStyle("ROUNDED")
            setKeepScreenOn(true)
            setAutoCopyOnComplete(false)
            setVertexSnapThresholdCm(2.5f)
            setRulerTheme("MATERIAL_YOU")
            setRulerDualUnit(false)
            setLevelToleranceDeg(0.5f)
            setLevelAudioEnabled(true)
            setCameraAspectRatio("FULL")
            setUseDisplayP3ColorSpace(true)
            setLensDirtWarningEnabled(true)
            setHighFpsModeEnabled(true)
            setHighDefinitionQualityEnabled(true)
            setAntiJitterEnabled(true)
            setSensorCorrectionEnabled(true)
            setGravityAlignmentEnabled(true)
            setBarometerFusionEnabled(true)
            setJerkRejectionEnabled(true)
            setProximityContactEnabled(true)
            setStereoParallaxEnabled(true)
            setMultiSampleAveragingEnabled(true)
            setOrthogonalSnapEnabled(true)
            setRawDepthConfidenceEnabled(true)
            setRawDepthConfidenceThreshold(45)
            setScaleCalibrationFactor(1.0000f)
            setShowPointCloud(true)
            setVibrateOnAlignment(true)
            updateRulerCalibration(1.0f, true)
            updateRulerFontScale(1.0f, true)
        } finally {
            suppressSettingToasts = false
        }
        triggerHapticFeedback()
        _toastMessage.tryEmit(tr("vm_toast_defaults_restored"))
    }

    /**
     * 產生當前設定參數報告 (供複製或分享備份)
     */
    fun getSettingsSummaryReport(): String {
        val onStr = tr("common_on")
        val offStr = tr("common_off")
        val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        return buildString {
            appendLine(tr("vm_rpt_header"))
            appendLine(trFmt("vm_rpt_unit", _selectedUnit.value.uppercase()))
            appendLine(trFmt("vm_rpt_reticle", _reticleStyle.value))
            appendLine(trFmt("vm_rpt_palette", _arAccentPalette.value))
            appendLine(trFmt("vm_rpt_thickness", _lineThickness.value.toString()))
            appendLine(trFmt("vm_rpt_font", _arFontSize.value))
            appendLine(trFmt("vm_rpt_snap", _vertexSnapThresholdCm.value.toString()))
            appendLine(trFmt("vm_rpt_ratio", _cameraAspectRatio.value))
            appendLine(trFmt("vm_rpt_fps60", if (highFpsModeEnabled.value) onStr else offStr))
            appendLine(trFmt("vm_rpt_uhd", if (highDefinitionQualityEnabled.value) onStr else offStr))
            appendLine(trFmt("vm_rpt_antijitter", if (antiJitterEnabled.value) onStr else offStr))
            appendLine(trFmt("vm_rpt_scale", String.format(java.util.Locale.US, "%.4fx", scaleCalibrationFactor.value)))
            appendLine(trFmt("vm_rpt_ruler", _rulerTheme.value, String.format(java.util.Locale.US, "%.3fx", _rulerCalibration.value)))
            appendLine(trFmt("vm_rpt_ortho", if (orthogonalSnapEnabled.value) onStr else offStr))
            appendLine(trFmt("vm_rpt_pointcloud", if (_showPointCloud.value) onStr else offStr))
            appendLine(trFmt("vm_rpt_vibrate", if (vibrateOnAlignment.value) onStr else offStr))
            appendLine(trFmt("vm_rpt_generated", nowStr))
        }
    }

    init {
        modernArEngine.currentLangCode = _currentLanguage.value
        modernArEngine.reportRuntimeError(tr("arcore_checking"))
        sensorCorrectionEngine.isSensorCorrectionEnabled = sensorCorrectionEnabled.value
        sensorCorrectionEngine.isAntiJitterEnabled = antiJitterEnabled.value
        sensorCorrectionEngine.isGravityAlignmentEnabled = gravityAlignmentEnabled.value
        sensorCorrectionEngine.isBarometerFusionEnabled = barometerFusionEnabled.value
        sensorCorrectionEngine.isJerkRejectionEnabled = jerkRejectionEnabled.value
        sensorCorrectionEngine.isProximityZeroContactEnabled = proximityContactEnabled.value
        sensorCorrectionEngine.isStereoParallaxScaleEnabled = stereoParallaxEnabled.value
        sensorCorrectionEngine.isMultiSampleAveragingEnabled = multiSampleAveragingEnabled.value
        sensorCorrectionEngine.isCoplanarProjectionEnabled = coplanarProjectionEnabled.value
        sensorCorrectionEngine.isOrthogonalSnapEnabled = orthogonalSnapEnabled.value
        sensorCorrectionEngine.userScaleCalibrationFactor = scaleCalibrationFactor.value
        modernArEngine.isRawDepthConfidenceFilterEnabled = rawDepthConfidenceEnabled.value
        modernArEngine.rawDepthConfidenceThreshold = rawDepthConfidenceThreshold.value
        modernArEngine.preferHighFps = highFpsModeEnabled.value
        modernArEngine.preferHighDef = highDefinitionQualityEnabled.value
        sensorCorrectionEngine.startListening()

        pixelPerformanceMonitor.updateUserPreferences(
            highFps = highFpsModeEnabled.value,
            highDef = highDefinitionQualityEnabled.value,
            targetRefreshRate = refreshRateToTargetFps()
        )
        pixelPerformanceMonitor.onPolicyChanged = { policy ->
            applyPixelThermalPolicy(policy)
        }
        pixelPerformanceMonitor.startMonitoring()
        applyPixelThermalPolicy(pixelPerformanceMonitor.policy.value)

        try {
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_BATTERY_CHANGED)
                addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(android.content.Intent.ACTION_POWER_CONNECTED)
                addAction(android.content.Intent.ACTION_POWER_DISCONNECTED)
            }
            app.registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            Log.e("MeasureViewModel", "Failed to register batteryReceiver", e)
        }
        updateBatteryStatusAndEvaluate(showToast = false)
    }

    private fun applyPixelThermalPolicy(policy: PixelThermalPolicy) {
        _effectiveTargetFps.value = policy.targetFps
        vulkanGraphicsPipeline.updateGpuPolicy(policy.tier, policy.targetFps)
        _gpuRenderPolicy.value = vulkanGraphicsPipeline.currentRenderPolicy
        modernArEngine.allowDepthMode = policy.allowDepth
        modernArEngine.applyRuntimePerformanceConfig(policy.allowDepth)
        highSpeedCamera2Manager?.updatePerformanceConfig(
            highFps = highFpsModeEnabled.value,
            highDef = highDefinitionQualityEnabled.value,
            maxFps = policy.targetFps
        )
        if (policy.forceDisableTorch && _isTorchOn.value) {
            mainHandler.post {
                turnOffTorch(app)
                _toastMessage.tryEmit(tr("vm_toast_thermal_30fps"))
            }
        } else if (policy.tier == ThermalTier.THROTTLED) {
            Log.i("MeasureViewModel", "Pixel thermal throttle active: 30fps, depth off")
        }
    }

    // Ruler calibration, font scale & physical screen scale
    private val _rulerCalibration = MutableStateFlow(prefs.getFloat("ruler_calibration", 1.0f))
    val rulerCalibration: StateFlow<Float> = _rulerCalibration.asStateFlow()

    private val _rulerFontScale = MutableStateFlow(prefs.getFloat("ruler_font_scale", 1.0f).coerceIn(0.8f, 1.8f))
    val rulerFontScale: StateFlow<Float> = _rulerFontScale.asStateFlow()

    private val _isRulerCalibrationActive = MutableStateFlow(false)
    val isRulerCalibrationActive: StateFlow<Boolean> = _isRulerCalibrationActive.asStateFlow()

    fun setRulerCalibrationActive(active: Boolean) {
        _isRulerCalibrationActive.value = active
    }

    fun updateRulerCalibration(factor: Float, persistImmediately: Boolean = true) {
        _rulerCalibration.value = factor
        if (persistImmediately) {
            prefs.edit().putFloat("ruler_calibration", factor).apply()
        }
    }

    fun updateRulerFontScale(scale: Float, persistImmediately: Boolean = true) {
        val clamped = scale.coerceIn(0.8f, 1.8f)
        _rulerFontScale.value = clamped
        if (persistImmediately) {
            prefs.edit().putFloat("ruler_font_scale", clamped).apply()
        }
    }

    fun persistRulerCalibration() {
        prefs.edit().putFloat("ruler_calibration", _rulerCalibration.value).apply()
    }

    @Volatile
    private var lastSurfaceNormalTimestampMs: Long = 0L

    @Volatile
    private var activeAreaPlanePose: Pose? = null

    // Queue for hit-testing requested from UI touches
    private val pendingHitTestQueue = AtomicReference<Pair<Float, Float>?>()
    private var lastHapticMeterMark = 0

    fun hasPendingTapRequest(): Boolean = pendingHitTestQueue.get() != null

    private fun checkMeterMilestoneHaptic(distMeters: Double) {
        if (_trackingStability.value.isDriftRisk) return
        val meterMark = distMeters.toInt()
        if (meterMark > 0 && meterMark != lastHapticMeterMark) {
            lastHapticMeterMark = meterMark
            triggerHapticFeedback(HapticType.SNAP)
        }
    }

    // AR Frame Processing on Vulkan Thread (Zero Allocation Hot Path)
    fun processVulkanFrame(frame: Frame, engine: ModernArEngine): HitTestResult? {
        // Synchronously extract frame camera matrices for hit testing and projection
        val camera = frame.camera
        camera.getViewMatrix(vulkanViewMatrixBuffer, 0)
        camera.getProjectionMatrix(vulkanProjMatrixBuffer, 0, 0.05f, 50.0f)

        // 1. Process any pending tap hit-test (creates persistent anchor only on tap)
        val tapRequest = pendingHitTestQueue.getAndSet(null)
        val isTap = tapRequest != null
        val hitX = tapRequest?.first ?: (displayWidth / 2f)
        val hitY = tapRequest?.second ?: (displayHeight / 2f)

        val hitResult = engine.performHitTest(frame, hitX, hitY, createAnchor = isTap)

        // 2. Real-time live targeting preview at screen center (no anchor creation to avoid GC pauses)
        val centerHit = if (!isTap) hitResult else engine.performHitTest(frame, displayWidth / 2f, displayHeight / 2f, createAnchor = false)

        // 3. Synchronously extract frame data (view/projection matrices, detected planes, light, stability)
        val frameData = engine.extractFrameData(frame, centerHit)
        onArFrameUpdated(frameData)

        if (isVulkanGraphicsEnabled.value) {
            val snapshotPts = getCapturedPointsSnapshot()
            val liveTarget = _liveTargetPoint.value
            val vulkanCircleVertsCount = prepareVulkanCircleVertices(snapshotPts, liveTarget)
            vulkanGraphicsPipeline.recordAndExecuteDrawPass(
                viewMatrix = vulkanViewMatrixBuffer,
                projectionMatrix = vulkanProjMatrixBuffer,
                capturedPoints = snapshotPts,
                liveTarget = liveTarget,
                boxCorners = null,
                circleRingVertices = if (vulkanCircleVertsCount > 0) vulkanCircleFloats else null,
                circleVertexCount = vulkanCircleVertsCount,
                displayRotation = displayRotation
            )
        }

        if (centerHit != null) {
            val isMetricHit = !centerHit.isApproximatePlacement
            if (isMetricHit) {
                lastValidHitTimestampMs = System.currentTimeMillis()
            }
            sensorCorrectionEngine.updateSurfaceUncertaintyContext(
                depthConfidencePercent = centerHit.depthConfidencePercent,
                surfaceDistanceMeters = centerHit.distance
            )
            val pose = centerHit.pose
            val rawPoint = Point3D(
                x = pose.tx().toDouble(),
                y = pose.ty().toDouble(),
                z = pose.tz().toDouble(),
                isArPrecision = isMetricHit
            )

            // 1. Plane Coplanar Projection (projects onto shared Area plane or current detected Plane)
            val targetPlanePose = if (_cameraSubMode.value == 1) {
                activeAreaPlanePose ?: centerHit.planeCenterPose
            } else if (centerHit.hitType == com.example.logic.ar.HitType.PLANE_POLYGON) {
                centerHit.planeCenterPose
            } else null

            val planeProjectedPoint = if (targetPlanePose != null) {
                sensorCorrectionEngine.projectPointToPlane(rawPoint, targetPlanePose)
            } else {
                rawPoint
            }

            // 2. Single-stage 3D Adaptive Kalman Filter + MAD burst averaging
            val sensorCorrectedPoint = sensorCorrectionEngine.correctPointWithSensorFusion(
                rawPoint = planeProjectedPoint,
                previousPoint = _liveTargetPoint.value
            )

            val pointsSnapshot = getCapturedPointsSnapshot()
            val subModeNow = _cameraSubMode.value
            val isActivelyDrawingLine = when (subModeNow) {
                1 -> pointsSnapshot.isNotEmpty()
                4, 5 -> pointsSnapshot.size == 1 || pointsSnapshot.size == 2
                else -> pointsSnapshot.size % 2 == 1
            }
            val lastConfirmedPoint = pointsSnapshot.lastOrNull()

            // 3. Magnetic Snapping check (Configurable 0..6cm sensitivity, default 2.5cm)
            val snapMeters = (_vertexSnapThresholdCm.value / 100.0).coerceIn(0.0, 0.06)
            val snappedVertex = if (snapMeters > 0.001) {
                ArMath.findVertexSnap(
                    livePoint = sensorCorrectedPoint,
                    existingPoints = pointsSnapshot,
                    snapThresholdMeters = snapMeters,
                    isCurrentlySnapped = _isSnapped.value
                )
            } else null
            val finalTargetPoint: Point3D
            val isFeatureSnapped = centerHit.isSnappedToFeature || (snappedVertex != null)

            if (snappedVertex != null) {
                val snapDist = ArMath.distance(sensorCorrectedPoint, snappedVertex)
                val pullDenom = (snapMeters * 1.12).coerceAtLeast(0.01)
                val pullFactor = (1.0 - (snapDist / pullDenom).coerceIn(0.0, 1.0)).let { it * it * (3.0 - 2.0 * it) }
                finalTargetPoint = Point3D(
                    x = sensorCorrectedPoint.x * (1.0 - pullFactor) + snappedVertex.x * pullFactor,
                    y = sensorCorrectedPoint.y * (1.0 - pullFactor) + snappedVertex.y * pullFactor,
                    z = sensorCorrectedPoint.z * (1.0 - pullFactor) + snappedVertex.z * pullFactor,
                    isArPrecision = isMetricHit,
                    label = snappedVertex.label
                )
            } else {
                finalTargetPoint = sensorCorrectedPoint
            }

            var snapHapticEmittedThisFrame = false
            if (isFeatureSnapped) {
                if (!_isSnapped.value) {
                    _isSnapped.value = true
                    triggerHapticFeedback(HapticType.SNAP)
                    snapHapticEmittedThisFrame = true
                }
            } else {
                _isSnapped.value = false
            }

            val prevTarget = _liveTargetPoint.value
            if (prevTarget == null || ArMath.distance(prevTarget, finalTargetPoint) > 0.0005) {
                _liveTargetPoint.value = finalTargetPoint
            }
            updateSmoothedSurfaceNormal(centerHit)
            if (displayWidth > 0 && displayHeight > 0) {
                if (ArMath.projectWorldToScreenInPlace(
                        worldPoint = finalTargetPoint,
                        viewMatrix = vulkanViewMatrixBuffer,
                        projectionMatrix = vulkanProjMatrixBuffer,
                        screenWidth = displayWidth,
                        screenHeight = displayHeight,
                        outScreenPos = screenPosBuffer,
                        outOffset = 0,
                        isHorizontallyMirrored = _isHorizontallyMirrored.value
                    )
                ) {
                    val px = screenPosBuffer[0]
                    val py = screenPosBuffer[1]
                    val current = _liveTargetScreenPos.value
                    if (current == null || kotlin.math.abs(current.x - px) > 0.25f || kotlin.math.abs(current.y - py) > 0.25f) {
                        _liveTargetScreenPos.value = androidx.compose.ui.geometry.Offset(px, py)
                    }
                }
            }

            // Require real metric plane/depth tracking before allowing measurement point commit
            if (isMetricHit) {
                if (!_isMeasurementAvailable.value) {
                    _isMeasurementAvailable.value = true
                    if (!snapHapticEmittedThisFrame) {
                        triggerHapticFeedback(HapticType.SNAP)
                    }
                }
            } else if (_isMeasurementAvailable.value) {
                _isMeasurementAvailable.value = false
            }

            // 4. Calculate live real-time distance & angle with single application of scaleCalibrationFactor
            val userScale = scaleCalibrationFactor.value.toDouble()
            if (subModeNow == 5 && pointsSnapshot.size == 2) {
                val angleLive = ArMath.calculateAngleDegrees(pointsSnapshot[0], pointsSnapshot[1], finalTargetPoint)
                _liveAngleDegrees.value = if (!angleLive.isNaN() && !angleLive.isInfinite()) angleLive else null
            } else if (_liveAngleDegrees.value != null) {
                _liveAngleDegrees.value = null
            }

            val circleLiveDist = if (subModeNow == 4 && pointsSnapshot.size == 2) {
                ArMath.fitCircle3Points(pointsSnapshot[0], pointsSnapshot[1], finalTargetPoint)?.diameter
                    ?: ArMath.distance(pointsSnapshot[0], pointsSnapshot[1])
            } else null

            if (circleLiveDist != null) {
                val scaled = sensorCorrectionEngine.correctDistanceWithStereoParallax(circleLiveDist) * userScale
                _liveDistanceMeters.value = smoothLiveDistance(scaled)
                checkMeterMilestoneHaptic(scaled)
            } else if (isActivelyDrawingLine && lastConfirmedPoint != null) {
                val dist = if (subModeNow == 2) {
                    sensorCorrectionEngine.correctVerticalHeightWithGravity(lastConfirmedPoint, finalTargetPoint) * userScale
                } else {
                    val rawD = ArMath.distance(lastConfirmedPoint, finalTargetPoint)
                    sensorCorrectionEngine.correctDistanceWithStereoParallax(rawD) * userScale
                }
                _liveDistanceMeters.value = smoothLiveDistance(dist)
                checkMeterMilestoneHaptic(dist)
            } else {
                lastHapticMeterMark = 0
                val centerDist = sensorCorrectionEngine.correctDistanceWithStereoParallax(centerHit.distance.toDouble()) * userScale
                _liveDistanceMeters.value = smoothLiveDistance(centerDist)
            }
        } else {
            // Surface tracking grace window: hold previous live target for up to 350ms during fast pan
            val now = System.currentTimeMillis()
            if (now - lastValidHitTimestampMs > 350L) {
                if (_isMeasurementAvailable.value) {
                    _isMeasurementAvailable.value = false
                }
            }
        }

        // 6. If a tap was requested, commit the exact ARCore hit point (fusing locked MAD burst average for center reticle taps)
        if (tapRequest != null) {
            if (hitResult != null && !hitResult.isApproximatePlacement) {
                val pose = hitResult.pose
                var rawX = pose.tx().toDouble()
                var rawY = pose.ty().toDouble()
                var rawZ = pose.tz().toDouble()

                val isCenterReticleTap = kotlin.math.abs(hitX - (displayWidth / 2f)) < 16f &&
                    kotlin.math.abs(hitY - (displayHeight / 2f)) < 16f
                if (isCenterReticleTap && multiSampleAveragingEnabled.value) {
                    val lockedBurst = sensorCorrectionEngine.getLockedBurstPointIfFresh()
                    if (lockedBurst != null) {
                        val dx = lockedBurst.x - rawX
                        val dy = lockedBurst.y - rawY
                        val dz = lockedBurst.z - rawZ
                        if (dx * dx + dy * dy + dz * dz <= 0.025 * 0.025) {
                            rawX = lockedBurst.x
                            rawY = lockedBurst.y
                            rawZ = lockedBurst.z
                        }
                    }
                }

                val rawP = Point3D(
                    x = rawX,
                    y = rawY,
                    z = rawZ,
                    isArPrecision = true,
                    anchor = hitResult.anchor
                )
                val pointsSnapshot = getCapturedPointsSnapshot()
                if (_cameraSubMode.value == 1 && pointsSnapshot.isEmpty() && hitResult.planeCenterPose != null) {
                    activeAreaPlanePose = hitResult.planeCenterPose
                }
                val tapPlanePose = if (_cameraSubMode.value == 1) {
                    activeAreaPlanePose ?: hitResult.planeCenterPose
                } else if (hitResult.hitType == com.example.logic.ar.HitType.PLANE_POLYGON) {
                    hitResult.planeCenterPose
                } else null

                val planarP = if (tapPlanePose != null) {
                    sensorCorrectionEngine.projectPointToPlane(rawP, tapPlanePose)
                } else {
                    rawP
                }
                val tapSnapMeters = (_vertexSnapThresholdCm.value / 100.0).coerceIn(0.0, 0.06)
                val snappedTap = if (tapSnapMeters > 0.001) {
                    ArMath.findVertexSnap(
                        livePoint = planarP,
                        existingPoints = pointsSnapshot,
                        snapThresholdMeters = tapSnapMeters,
                        isCurrentlySnapped = _isSnapped.value
                    )
                } else null
                val correctedP = if (snappedTap != null) {
                    val snapAnchor = snappedTap.anchor ?: engine.createAnchorAtWorldPoint(snappedTap)
                    // Detach unaligned hit anchor only after resolving snapped vertex or SLAM anchor
                    try { hitResult.anchor?.detach() } catch (_: Throwable) {}
                    snappedTap.copy(anchor = snapAnchor)
                } else {
                    planarP
                }

                val nextIndex = pointsSnapshot.size
                val charCode = ('A'.code + (nextIndex % 26)).toChar()
                val (labelText, toastText, isClosing) = buildPointPlacementMeta(_cameraSubMode.value, nextIndex, charCode)
                val namedP = correctedP.copy(label = labelText)

                mainHandler.post {
                    appendMeasuredPointOnMainThread(
                        point = namedP,
                        isClosingLine = isClosing,
                        successToast = toastText,
                        overflowToast = tr("vm_toast_max_pts_overflow"),
                        onOverflow = { try { namedP.anchor?.detach() } catch (_: Throwable) {} }
                    )
                }
            } else {
                if (hitResult?.anchor != null) {
                    try { hitResult.anchor.detach() } catch (_: Throwable) {}
                }
                val livePt = _liveTargetPoint.value
                val hasRecentMetricHit = (System.currentTimeMillis() - lastValidHitTimestampMs) <= 250L
                if (hitResult == null && livePt != null && camera.trackingState == TrackingState.TRACKING && _isMeasurementAvailable.value && hasRecentMetricHit) {
                    val pointsSnapshot = getCapturedPointsSnapshot()
                    val nextIndex = pointsSnapshot.size
                    val charCode = ('A'.code + (nextIndex % 26)).toChar()
                    val (labelText, toastText, isClosing) = buildPointPlacementMeta(_cameraSubMode.value, nextIndex, charCode)
                    val namedP = livePt.copy(label = labelText)

                    mainHandler.post {
                        appendMeasuredPointOnMainThread(
                            point = namedP,
                            isClosingLine = isClosing,
                            successToast = toastText,
                            overflowToast = tr("vm_toast_max_pts")
                        )
                    }
                } else {
                    mainHandler.post {
                        _toastMessage.tryEmit(tr("vm_toast_no_plane"))
                    }
                }
            }
        }
        return centerHit
    }

    private fun updateSmoothedSurfaceNormal(centerHit: HitTestResult) {
        val now = System.currentTimeMillis()
        if (!centerHit.hasSurfaceNormal) {
            // Brief grace window prevents 3D reticle flicker on transient hit-test gaps
            if (_isSurfaceNormalLocked.value && (now - lastSurfaceNormalTimestampMs > 450L)) {
                _isSurfaceNormalLocked.value = false
            }
            return
        }
        lastSurfaceNormalTimestampMs = now
        val tx = centerHit.normalX
        val ty = centerHit.normalY
        val tz = centerHit.normalZ
        val tLen = kotlin.math.sqrt(tx * tx + ty * ty + tz * tz)
        if (tLen < 1e-5f || tLen.isNaN()) return
        val ntx = tx / tLen
        val nty = ty / tLen
        val ntz = tz / tLen

        if (!_isSurfaceNormalLocked.value) {
            _isSurfaceNormalLocked.value = true
            surfaceNormalScratch[0] = ntx
            surfaceNormalScratch[1] = nty
            surfaceNormalScratch[2] = ntz
            normalEmitIdx = normalEmitIdx xor 1
            val out = normalEmitPool[normalEmitIdx]
            out[0] = ntx
            out[1] = nty
            out[2] = ntz
            _liveSurfaceNormal.value = out
            return
        }

        val cx = surfaceNormalScratch[0]
        val cy = surfaceNormalScratch[1]
        val cz = surfaceNormalScratch[2]
        val dot = (cx * ntx + cy * nty + cz * ntz).coerceIn(-1f, 1f)
        if (dot > 0.9996f) return

        // Adaptive angular smoothing without sudden jump threshold
        val alpha = (0.20f + (1f - dot) * 0.40f).coerceIn(0.20f, 0.52f)
        var sx = cx + (ntx - cx) * alpha
        var sy = cy + (nty - cy) * alpha
        var sz = cz + (ntz - cz) * alpha
        val sLen = kotlin.math.sqrt(sx * sx + sy * sy + sz * sz)
        if (sLen > 1e-5f) {
            sx /= sLen
            sy /= sLen
            sz /= sLen
            surfaceNormalScratch[0] = sx
            surfaceNormalScratch[1] = sy
            surfaceNormalScratch[2] = sz
            normalEmitIdx = normalEmitIdx xor 1
            val out = normalEmitPool[normalEmitIdx]
            out[0] = sx
            out[1] = sy
            out[2] = sz
            _liveSurfaceNormal.value = out
        }
    }

    private fun prepareVulkanCircleVertices(
        snapshotPts: List<Point3D>,
        liveTarget: Point3D?
    ): Int {
        val segments = 48
        if (_cameraSubMode.value == 4) {
            if (snapshotPts.size >= 3) {
                val c = ArMath.fitCircle3Points(snapshotPts[0], snapshotPts[1], snapshotPts[2])
                if (c != null && ArMath.computePlaneNormal3Points(snapshotPts[0], snapshotPts[1], snapshotPts[2], vulkanCircleBasis)) {
                    val count = ArMath.sampleCircle3DWorldCoords(
                        c.center.x, c.center.y, c.center.z, c.radius,
                        vulkanCircleBasis, segments, vulkanCircleX, vulkanCircleY, vulkanCircleZ
                    )
                    for (i in 0 until count) {
                        val base = i * 3
                        vulkanCircleFloats[base] = vulkanCircleX[i].toFloat()
                        vulkanCircleFloats[base + 1] = vulkanCircleY[i].toFloat()
                        vulkanCircleFloats[base + 2] = vulkanCircleZ[i].toFloat()
                    }
                    return count
                }
            } else if (snapshotPts.size == 2 && liveTarget != null) {
                val c = ArMath.fitCircle3Points(snapshotPts[0], snapshotPts[1], liveTarget)
                if (c != null && ArMath.computePlaneNormal3Points(snapshotPts[0], snapshotPts[1], liveTarget, vulkanCircleBasis)) {
                    val count = ArMath.sampleCircle3DWorldCoords(
                        c.center.x, c.center.y, c.center.z, c.radius,
                        vulkanCircleBasis, segments, vulkanCircleX, vulkanCircleY, vulkanCircleZ
                    )
                    for (i in 0 until count) {
                        val base = i * 3
                        vulkanCircleFloats[base] = vulkanCircleX[i].toFloat()
                        vulkanCircleFloats[base + 1] = vulkanCircleY[i].toFloat()
                        vulkanCircleFloats[base + 2] = vulkanCircleZ[i].toFloat()
                    }
                    return count
                }
            } else if (snapshotPts.size == 1 && liveTarget != null) {
                val c = ArMath.fitCircle2Points(snapshotPts[0], liveTarget)
                if (c != null) {
                    val norm = surfaceNormalScratch
                    ArMath.buildOrthonormalBasis(norm[0].toDouble(), norm[1].toDouble(), norm[2].toDouble(), vulkanCircleBasis)
                    val count = ArMath.sampleCircle3DWorldCoords(
                        c.center.x, c.center.y, c.center.z, c.radius,
                        vulkanCircleBasis, segments, vulkanCircleX, vulkanCircleY, vulkanCircleZ
                    )
                    for (i in 0 until count) {
                        val base = i * 3
                        vulkanCircleFloats[base] = vulkanCircleX[i].toFloat()
                        vulkanCircleFloats[base + 1] = vulkanCircleY[i].toFloat()
                        vulkanCircleFloats[base + 2] = vulkanCircleZ[i].toFloat()
                    }
                    return count
                }
            }
        }
        if (liveTarget != null) {
            val norm = surfaceNormalScratch
            ArMath.buildOrthonormalBasis(norm[0].toDouble(), norm[1].toDouble(), norm[2].toDouble(), vulkanCircleBasis)
            val reticleRadius = ((_liveDistanceMeters.value ?: 1.0) * 0.045).coerceIn(0.018, 0.25)
            val count = ArMath.sampleCircle3DWorldCoords(
                liveTarget.x, liveTarget.y, liveTarget.z, reticleRadius,
                vulkanCircleBasis, segments, vulkanCircleX, vulkanCircleY, vulkanCircleZ
            )
            for (i in 0 until count) {
                val base = i * 3
                vulkanCircleFloats[base] = vulkanCircleX[i].toFloat()
                vulkanCircleFloats[base + 1] = vulkanCircleY[i].toFloat()
                vulkanCircleFloats[base + 2] = vulkanCircleZ[i].toFloat()
            }
            return count
        }
        return 0
    }

    private fun appendMeasuredPointOnMainThread(
        point: Point3D,
        isClosingLine: Boolean,
        successToast: String,
        overflowToast: String,
        onOverflow: (() -> Unit)? = null
    ) {
        synchronized(pointsLock) {
            if (capturedPoints.size >= 256) {
                onOverflow?.invoke()
                _toastMessage.tryEmit(overflowToast)
                return
            }
            saveUndoState()
            capturedPoints.add(point)
            updateCapturedPointsSnapshotCache()
        }
        updateAutoDetectedGeometry()
        triggerHapticFeedback(if (isClosingLine) HapticType.HEAVY else HapticType.CLICK)
        if (isClosingLine && _autoCopyOnComplete.value) {
            val formatted = getPrimaryMeasurementDisplayString()
            try {
                val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Measure Result", formatted))
                _toastMessage.tryEmit(trFmt("vm_toast_autocopy_append_fmt", successToast, formatted))
            } catch (_: Throwable) {
                _toastMessage.tryEmit(successToast)
            }
        } else {
            _toastMessage.tryEmit(successToast)
        }
    }




    private fun smoothLiveDistance(targetDist: Double): Double {
        val prev = _liveDistanceMeters.value ?: return targetDist
        val diff = abs(targetDist - prev)
        if (diff.isNaN() || diff.isInfinite()) return targetDist

        // Responsive 1-Euro style distance filter with smoothstep curve and 0.8mm deadband
        val minAlpha = 0.25
        val maxAlpha = 0.95
        val t = (diff / 0.02).coerceIn(0.0, 1.0)
        val alphaScale = t * t * (3.0 - 2.0 * t)
        val alpha = minAlpha + (maxAlpha - minAlpha) * alphaScale

        val filtered = prev * (1.0 - alpha) + targetDist * alpha
        return if (abs(filtered - prev) < 0.0008) prev else filtered
    }

    private fun updateAutoDetectedGeometry() {
        if (_cameraSubMode.value != 0) return // Manual mode override
        val points = getCapturedPointsSnapshot()
        when (points.size) {
            0, 1 -> _autoDetectedType.value = "DISTANCE"
            2 -> {
                // Check if vertical height
                val p1 = points[0]
                val p2 = points[1]
                val dy = abs(p2.y - p1.y)
                val dxz = sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.z - p1.z) * (p2.z - p1.z))
                if (dy > 0.35 && dy > dxz * 2.2) {
                    _autoDetectedType.value = "HEIGHT"
                } else {
                    _autoDetectedType.value = "DISTANCE"
                }
            }
            else -> {
                // 兩點成一線，不要有共用的點：預設距離模式下保持為獨立線段
                _autoDetectedType.value = "DISTANCE"
            }
        }
    }

    fun onArFrameUpdated(data: ModernArFrame) {
        if (_arTrackingState.value != data.trackingState) _arTrackingState.value = data.trackingState
        if (_trackingFailureReason.value != data.trackingFailureReason) _trackingFailureReason.value = data.trackingFailureReason
        if (!data.viewMatrix.contentEquals(_viewMatrix.value)) {
            _viewMatrix.value = data.viewMatrix
        }
        if (!data.projectionMatrix.contentEquals(_projectionMatrix.value)) {
            _projectionMatrix.value = data.projectionMatrix
        }
        if (_isDepthAvailable.value != data.isDepthAvailable) _isDepthAvailable.value = data.isDepthAvailable
        if (_detectedPlanes.value !== data.planes) {
            _detectedPlanes.value = data.planes
            val activePlanes = data.planes.count { it.isTracking }
            if (_arPlanesCount.value != activePlanes) {
                _arPlanesCount.value = activePlanes
            }
        }
        if (_surfaceTypeAtCenter.value != data.surfaceTypeAtCenter) _surfaceTypeAtCenter.value = data.surfaceTypeAtCenter
        _lightIntensity.value = data.lightIntensity
        
        // Filter tracking stability updates: prevent high-frequency StateFlow emissions on subtle float jitter
        val oldStab = _trackingStability.value
        val newStab = data.stability
        if (oldStab.level != newStab.level ||
            oldStab.isDriftRisk != newStab.isDriftRisk ||
            oldStab.isFeatureDeficient != newStab.isFeatureDeficient ||
            oldStab.isMotionExcessive != newStab.isMotionExcessive ||
            oldStab.warningMessage != newStab.warningMessage ||
            oldStab.trackingPlanesCount != newStab.trackingPlanesCount ||
            kotlin.math.abs(oldStab.confidenceScore - newStab.confidenceScore) > 0.08f ||
            kotlin.math.abs(oldStab.cameraSpeedMps - newStab.cameraSpeedMps) > 0.20f) {
            _trackingStability.value = newStab
        }

        // Update active anchor positions to eliminate world drift with threshold to prevent SnapshotStateList churning
        val snapshot = capturedPointsSnapshotCache
        if (snapshot.isNotEmpty() && data.trackingState == TrackingState.TRACKING) {
            var driftUpdates: MutableList<Pair<Int, Point3D>>? = null
            for (i in snapshot.indices) {
                val pt = snapshot[i]
                val anchor = pt.anchor ?: continue
                try {
                    if (anchor.trackingState == TrackingState.TRACKING) {
                        val pose = anchor.pose
                        val newX = pose.tx().toDouble()
                        val newY = pose.ty().toDouble()
                        val newZ = pose.tz().toDouble()
                        val dx = abs(newX - pt.x)
                        val dy = abs(newY - pt.y)
                        val dz = abs(newZ - pt.z)
                        val distSq = dx * dx + dy * dy + dz * dz
                        if (distSq > 0.002 * 0.002) {
                            val candidate = pt.copy(x = newX, y = newY, z = newZ)
                            if (ArMath.isPointValid(candidate)) {
                                val list = driftUpdates ?: mutableListOf<Pair<Int, Point3D>>().also { driftUpdates = it }
                                list.add(Pair(i, candidate))
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // Anchor was detached or session invalidated concurrently; ignore safely
                }
            }
            val updates = driftUpdates
            if (updates != null) {
                mainHandler.post {
                    synchronized(pointsLock) {
                        for ((idx, cand) in updates) {
                            if (idx in capturedPoints.indices && capturedPoints[idx].anchor === snapshot.getOrNull(idx)?.anchor) {
                                capturedPoints[idx] = cand
                            }
                        }
                        updateCapturedPointsSnapshotCache()
                    }
                }
            }
        }
    }


    fun requestHitTest(pixelX: Float? = null, pixelY: Float? = null) {
        val context = getApplication<Application>()
        if (!ModernArEngine.isArCorePackageInstalled(context)) {
            _toastMessage.tryEmit(tr("vm_toast_install_arcore"))
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastTapTimestampMs < 180L) {
            return // Debounce rapid double taps to avoid duplicate points
        }
        lastTapTimestampMs = now

        val x = pixelX ?: (displayWidth / 2f)
        val y = pixelY ?: (displayHeight / 2f)

        if (_isCameraSimulationActive.value) {
            updateFallbackCameraMatrices()
            processOpticalFallbackTap(x, y)
            return
        }

        if (_isFrontCamera.value) {
            _toastMessage.tryEmit(tr("vm_toast_back_cam_only"))
            return
        }

        if (modernArEngine.session == null) {
            _isMeasurementAvailable.value = false
            _toastMessage.tryEmit(tr("vm_toast_arcore_not_ready"))
            return
        }

        // Proactive warning when AR tracking feature points are deficient or camera moves too fast (throttled)
        val stability = _trackingStability.value
        if (stability.isDriftRisk && (now - lastDriftWarningTimestampMs >= 3000L)) {
            lastDriftWarningTimestampMs = now
            val warn = stability.warningMessage ?: tr("stab_banner_default")
            _toastMessage.tryEmit(trFmt("vm_toast_stab_warn_fmt", warn))
        }
        if (!modernArEngine.isSessionResumed) {
            _toastMessage.tryEmit(tr("vm_toast_ar_init_wait"))
            return
        }
        pendingHitTestQueue.set(Pair(x, y))
    }

    private fun processOpticalFallbackTap(x: Float, y: Float) {
        val w = displayWidth.coerceAtLeast(1)
        val h = displayHeight.coerceAtLeast(1)
        val ndcX = (x / w) * 2f - 1f
        val ndcY = 1f - (y / h) * 2f

        val telemetry = sensorCorrectionEngine.telemetry.value
        val pitch = telemetry.pitchDeg
        val angleRad = Math.toRadians(pitch.toDouble())
        val estimatedDist = if (pitch in 10.0..80.0) {
            (1.2 / kotlin.math.sin(angleRad)).coerceIn(0.3, 5.0)
        } else {
            1.2
        }

        val aspect = w.toFloat() / h.toFloat()
        val fovYRad = Math.toRadians(60.0)
        val tanHalfFovY = kotlin.math.tan(fovYRad / 2.0)
        val tanHalfFovX = tanHalfFovY * aspect

        val pX = ndcX * tanHalfFovX * estimatedDist
        val pY = ndcY * tanHalfFovY * estimatedDist
        val pZ = -estimatedDist

        val rawPoint = Point3D(pX, pY, pZ, isArPrecision = false)
        val finalPoint = sensorCorrectionEngine.correctPointWithSensorFusion(rawPoint, _liveTargetPoint.value)

        var toastToEmit: String? = null
        var isClosingPoint = false
        synchronized(pointsLock) {
            if (capturedPoints.size >= 256) {
                _toastMessage.tryEmit(tr("vm_toast_max_pts"))
                return
            }
            saveUndoState()
            val nextIndex = capturedPoints.size
            val charCode = ('A'.code + (nextIndex % 26)).toChar()
            val (labelText, toastText, isClosing) = buildPointPlacementMeta(_cameraSubMode.value, nextIndex, charCode)
            isClosingPoint = isClosing
            toastToEmit = trFmt("vm_toast_optical_assist_fmt", toastText)
            capturedPoints.add(finalPoint.copy(label = labelText))
            updateCapturedPointsSnapshotCache()
        }
        updateAutoDetectedGeometry()
        triggerHapticFeedback(if (isClosingPoint) HapticType.HEAVY else HapticType.CLICK)
        if (isClosingPoint && _autoCopyOnComplete.value) {
            val formatted = getPrimaryMeasurementDisplayString()
            try {
                val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Measure Result", formatted))
                _toastMessage.tryEmit(trFmt("vm_toast_autocopy_append_fmt", toastToEmit ?: "", formatted))
            } catch (_: Throwable) {
                toastToEmit?.let { _toastMessage.tryEmit(it) }
            }
        } else {
            toastToEmit?.let { _toastMessage.tryEmit(it) }
        }
        _isMeasurementAvailable.value = true
    }

    private fun saveUndoState(stripAnchors: Boolean = false) {
        val snapshot = if (stripAnchors) {
            capturedPoints.map { it.copy(anchor = null) }
        } else {
            capturedPoints.toList()
        }
        undoStack.add(snapshot)
        if (undoStack.size > 20) {
            val evicted = undoStack.removeAt(0)
            val stillReferenced = HashSet<Anchor>().apply {
                capturedPoints.forEach { it.anchor?.let(::add) }
                undoStack.forEach { frame -> frame.forEach { it.anchor?.let(::add) } }
            }
            evicted.forEach { pt ->
                val anchor = pt.anchor
                if (anchor != null && anchor !in stillReferenced) {
                    try { anchor.detach() } catch (_: Throwable) {}
                }
            }
        }
    }

    fun undo() {
        synchronized(pointsLock) {
            if (undoStack.isNotEmpty()) {
                val previous = undoStack.removeAt(undoStack.size - 1)
                val keptAnchors = previous.mapNotNull { it.anchor }.toSet()
                capturedPoints.forEach { pt ->
                    val anchor = pt.anchor
                    if (anchor != null && anchor !in keptAnchors) {
                        try { anchor.detach() } catch (_: Throwable) {}
                    }
                }
                capturedPoints.clear()
                capturedPoints.addAll(previous)
                updateCapturedPointsSnapshotCache()
                triggerHapticFeedback()
                updateAutoDetectedGeometry()
            } else if (capturedPoints.isNotEmpty()) {
                val removed = capturedPoints.removeAt(capturedPoints.size - 1)
                try { removed.anchor?.detach() } catch (_: Throwable) {}
                updateCapturedPointsSnapshotCache()
                triggerHapticFeedback()
                updateAutoDetectedGeometry()
            }
        }
    }

    private fun buildPointPlacementMeta(subMode: Int, nextIndex: Int, charCode: Char): Triple<String, String, Boolean> {
        val charStr = charCode.toString()
        return when (subMode) {
            1 -> {
                val label = trFmt("vm_pt_vertex_fmt", nextIndex + 1, charStr)
                val toast = when (nextIndex) {
                    0 -> trFmt("vm_toast_area_p1_fmt", charStr)
                    1 -> trFmt("vm_toast_area_p2_fmt", charStr)
                    else -> trFmt("vm_toast_area_pn_fmt", nextIndex + 1, charStr)
                }
                Triple(label, toast, nextIndex >= 2)
            }
            2 -> {
                val isClosing = nextIndex % 2 == 1
                val label = trFmt(if (isClosing) "vm_pt_height_top_fmt" else "vm_pt_height_base_fmt", charStr)
                val toast = if (isClosing) tr("vm_toast_height_done") else trFmt("vm_toast_height_base_fmt", charStr)
                Triple(label, toast, isClosing)
            }
            3 -> {
                val isClosing = nextIndex % 2 == 1
                val label = trFmt(if (isClosing) "vm_pt_box_end_fmt" else "vm_pt_box_start_fmt", charStr)
                val toast = if (isClosing) tr("vm_toast_box_done") else trFmt("vm_toast_box_start_fmt", charStr)
                Triple(label, toast, isClosing)
            }
            4 -> {
                val label = trFmt("vm_pt_circle_fmt", nextIndex + 1, charStr)
                val toast = when (nextIndex) {
                    0 -> trFmt("vm_toast_circle_p1_fmt", charStr)
                    1 -> trFmt("vm_toast_circle_p2_fmt", charStr)
                    else -> tr("vm_toast_circle_done")
                }
                Triple(label, toast, nextIndex >= 2)
            }
            5 -> {
                val label = when (nextIndex) {
                    0 -> trFmt("vm_pt_angle_leg1_fmt", charStr)
                    1 -> trFmt("vm_pt_angle_vertex_fmt", charStr)
                    else -> trFmt("vm_pt_angle_leg2_fmt", charStr)
                }
                val toast = when (nextIndex) {
                    0 -> trFmt("vm_toast_angle_p1_fmt", charStr)
                    1 -> trFmt("vm_toast_angle_p2_fmt", charStr)
                    else -> tr("vm_toast_angle_done")
                }
                Triple(label, toast, nextIndex >= 2)
            }
            else -> {
                val lineNum = (nextIndex / 2) + 1
                val isClosing = nextIndex % 2 == 1
                val role = tr(if (isClosing) "vm_role_end" else "vm_role_start")
                val label = trFmt("vm_pt_line_fmt", lineNum, role, charStr)
                val toast = if (isClosing) trFmt("vm_toast_line_done_fmt", lineNum) else trFmt("vm_toast_line_start_fmt", lineNum, charStr)
                Triple(label, toast, isClosing)
            }
        }
    }

    fun clearActivePoints() {
        lastHapticMeterMark = 0
        activeAreaPlanePose = null
        synchronized(pointsLock) {
            if (capturedPoints.isNotEmpty()) {
                // Clear anchor handles on undo snapshot before detaching to prevent IllegalStateException after undo()
                saveUndoState(stripAnchors = true)
                capturedPoints.forEach { pt ->
                    try { pt.anchor?.detach() } catch (_: Throwable) {}
                }
                capturedPoints.clear()
                updateCapturedPointsSnapshotCache()
            }
        }
        _liveDistanceMeters.value = null
        _liveAngleDegrees.value = null
        _autoDetectedType.value = "DISTANCE"
        triggerHapticFeedback()
    }

    fun updatePointLabel(index: Int, newLabel: String) {
        synchronized(pointsLock) {
            if (index in capturedPoints.indices) {
                capturedPoints[index] = capturedPoints[index].copy(label = newLabel)
                updateCapturedPointsSnapshotCache()
            }
        }
    }

    /**
     * Smart dynamic release of Vulkan rendering pipeline buffers and temporary state
     * when navigating away from the AR camera view.
     */
    fun releaseVulkanPipelineBuffers() {
        pendingHitTestQueue.set(null)
        _liveTargetPoint.value = null
        _liveDistanceMeters.value = null
        _liveAngleDegrees.value = null
        _liveTargetScreenPos.value = null
        _isSnapped.value = false
        _isMeasurementAvailable.value = false
        _isSurfaceNormalLocked.value = false
        surfaceNormalScratch[0] = 0f
        surfaceNormalScratch[1] = 1f
        surfaceNormalScratch[2] = 0f
        _liveSurfaceNormal.value = floatArrayOf(0f, 1f, 0f)
    }

    // Active CameraX camera control reference (when in CameraX mode)
    private var cameraControl: androidx.camera.core.CameraControl? = null
    // Active High-Speed Camera2 Manager reference (when in Direct Camera2 High-Speed mode)
    private var highSpeedCamera2Manager: com.example.logic.camera.HighSpeedCamera2Manager? = null

    fun setCameraControl(control: androidx.camera.core.CameraControl?) {
        cameraControl = control
        if (_isTorchOn.value && control != null) {
            try {
                control.enableTorch(true)
            } catch (e: Exception) {
                Log.w("MeasureViewModel", "Failed to sync torch state with CameraControl: ${e.message}")
            }
        }
    }

    private val _isFrontCamera = MutableStateFlow(false)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _isHorizontallyMirrored = MutableStateFlow(false)
    val isHorizontallyMirrored: StateFlow<Boolean> = _isHorizontallyMirrored.asStateFlow()

    fun setHighSpeedCamera2Manager(manager: com.example.logic.camera.HighSpeedCamera2Manager?) {
        highSpeedCamera2Manager = manager
        if (manager != null) {
            manager.onCameraAvailable = {
                if (_isCameraSimulationActive.value) {
                    _isCameraSimulationActive.value = false
                    Log.i("MeasureViewModel", "Hardware camera recovered; exited simulation fallback.")
                }
            }
            manager.onCameraUnavailable = {
                _isCameraSimulationActive.value = false
                _isMeasurementAvailable.value = false
                if (!ModernArEngine.isArCorePackageInstalled(app)) {
                    _toastMessage.tryEmit(tr("arcore_not_installed"))
                } else {
                    _toastMessage.tryEmit(tr("vm_toast_camera_start_fail"))
                }
                Log.w("MeasureViewModel", "Camera unavailable callback invoked; strict ARCore gating active.")
            }
            manager.onArSessionReadyToResume = { captureCallback, bgHandler ->
                if (!_isFrontCamera.value && !_isCameraSimulationActive.value) {
                    modernArEngine.resume(captureCallback, bgHandler)
                }
            }
            manager.onBeforeCameraSwitch = {
                modernArEngine.pause()
            }
            manager.isArCoreResumedChecker = { modernArEngine.isSessionResumed }
            val sc = modernArEngine.sharedCamera
            if (sc != null) {
                manager.sharedCamera = sc
                manager.preferredCameraId = modernArEngine.selectedCameraId
                manager.preferredPreviewSize = modernArEngine.selectedCameraImageSize
                manager.preferredAspectRatio = modernArEngine.selectedCameraImageSize?.let {
                    if (it.height > 0) it.width.toFloat() / it.height.toFloat() else null
                }
                manager.preferredFpsRange = modernArEngine.selectedCameraFpsRange
            }
            _isFrontCamera.value = manager.isFrontFacing
            _isHorizontallyMirrored.value = manager.isHorizontallyMirrored || manager.isFrontFacing
            val policy = pixelPerformanceMonitor.policy.value
            manager.updatePerformanceConfig(
                highFps = highFpsModeEnabled.value,
                highDef = highDefinitionQualityEnabled.value,
                maxFps = policy.targetFps
            )
            if (_isTorchOn.value) {
                manager.toggleTorch(true)
            }
        }
    }



    fun pauseArAndCamera(manager: com.example.logic.camera.HighSpeedCamera2Manager? = highSpeedCamera2Manager) {
        modernArEngine.pause()
        try {
            manager?.closeCamera()
        } catch (_: Exception) {}
    }

    fun toggleHorizontalMirror(textureView: android.view.TextureView?) {
        val manager = highSpeedCamera2Manager ?: return
        val newMirrored = !_isHorizontallyMirrored.value
        manager.setHorizontallyMirrored(newMirrored, textureView)
        _isHorizontallyMirrored.value = newMirrored
    }

    // Set Torch Brightness (0.1f ~ 1.0f) and apply to hardware if supported
    fun setTorchBrightness(context: Context, brightness: Float) {
        val clamped = brightness.coerceIn(0.1f, 1.0f)
        _torchBrightness.value = clamped
        prefs.edit().putFloat("torch_brightness", clamped).apply()

        if (_isTorchOn.value) {
            applyTorchWithBrightness(context, true, clamped)
        }
    }

    // Toggle Torch (Flashlight) with multi-engine support (ARCore, Camera2 High-Speed, CameraX, CameraManager)
    fun toggleTorch(context: Context) {
        val newState = !_isTorchOn.value
        if (newState) {
            applyTorchWithBrightness(context, true, _torchBrightness.value)
        } else {
            turnOffTorch(context)
        }
    }

    private fun applyTorchWithBrightness(context: Context, newState: Boolean, brightness: Float) {
        var success = false
        val isArSharedActive = highSpeedCamera2Manager?.isSharedCameraArActive() == true ||
            (modernArEngine.session != null && modernArEngine.isSessionResumed && !_isFrontCamera.value)

        // 0. When ARCore SharedCamera owns the repeating CaptureSession, route directly through ARCore flashMode
        if (isArSharedActive && modernArEngine.session != null && modernArEngine.isSessionResumed && !_isFrontCamera.value) {
            success = modernArEngine.setTorchMode(newState)
        }

        // 1. Standalone HighSpeedCamera2Manager CaptureRequest repeating update
        if (!success && highSpeedCamera2Manager != null) {
            try {
                success = highSpeedCamera2Manager?.toggleTorch(newState) == true
            } catch (e: Exception) {
                Log.w("MeasureViewModel", "HighSpeedCamera2Manager toggleTorch error: ${e.message}")
            }
        }

        // 2. Android 13+ (API 33+) Hardware Torch Strength Level via CameraManager (when camera is idle / not owned by ARCore)
        if (!success && !isArSharedActive && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val targetCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val isBack = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                    hasFlash && isBack
                } ?: cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }

                if (targetCameraId != null) {
                    val chars = cameraManager.getCameraCharacteristics(targetCameraId)
                    val maxLevel = chars.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                    if (newState && maxLevel > 1) {
                        val targetLevel = (brightness * maxLevel).toInt().coerceIn(1, maxLevel)
                        cameraManager.turnOnTorchWithStrengthLevel(targetCameraId, targetLevel)
                        success = true
                    } else {
                        cameraManager.setTorchMode(targetCameraId, newState)
                        success = true
                    }
                }
            } catch (e: Throwable) {
                Log.w("MeasureViewModel", "CameraManager torch strength fallback: ${e.message}")
            }
        }

        // 3. Standard CameraManager setTorchMode fallback
        if (!success && !isArSharedActive) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val targetCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    val isBack = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                    hasFlash && isBack
                } ?: cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }

                if (targetCameraId != null) {
                    cameraManager.setTorchMode(targetCameraId, newState)
                    success = true
                }
            } catch (e: Exception) {
                Log.w("MeasureViewModel", "CameraManager setTorchMode error: ${e.message}")
            }
        }

        // 4. CameraX fallback
        if (!success && cameraControl != null) {
            try {
                cameraControl?.enableTorch(newState)
                success = true
            } catch (e: Exception) {
                Log.w("MeasureViewModel", "CameraX enableTorch failed: ${e.message}")
            }
        }

        // 5. Final ARCore flashMode attempt
        if (!success && modernArEngine.session != null && modernArEngine.isSessionResumed && !_isFrontCamera.value) {
            success = modernArEngine.setTorchMode(newState)
        }

        if (!newState || success) {
            _isTorchOn.value = newState
            triggerHapticFeedback()
            val percent = (brightness * 100).toInt()
            _toastMessage.tryEmit(if (newState) trFmt("vm_toast_torch_on_fmt", percent) else tr("vm_toast_torch_off"))
        } else {
            _isTorchOn.value = false
            _toastMessage.tryEmit(tr("vm_toast_torch_unsupported"))
        }
    }

    fun turnOffTorch(context: Context? = null) {
        if (!_isTorchOn.value) return
        _isTorchOn.value = false
        modernArEngine.setTorchMode(false)
        try {
            highSpeedCamera2Manager?.toggleTorch(false)
        } catch (_: Exception) {}
        try {
            cameraControl?.enableTorch(false)
        } catch (e: Exception) {}
        if (context != null) {
            try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val targetCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                    cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                if (targetCameraId != null) {
                    cameraManager.setTorchMode(targetCameraId, false)
                }
            } catch (e: Exception) {}
        }
    }

    // Measurement Calculations (兩點成一線，不共用點；scaleCalibrationFactor 僅在此套用一次)
    fun calculateTotalDistance(): Double {
        val points = getCapturedPointsSnapshot()
        if (points.size < 2) return 0.0
        var raw = 0.0
        for (i in 0 until points.size - 1 step 2) {
            raw += ArMath.distance(points[i], points[i + 1])
        }
        val corrected = sensorCorrectionEngine.correctDistanceWithStereoParallax(raw)
        return corrected * scaleCalibrationFactor.value.toDouble()
    }

    fun calculatePolygonArea(): Double {
        val points = getCapturedPointsSnapshot()
        val rawArea = ArMath.polygonArea(points)
        val scale = sensorCorrectionEngine.correctDistanceWithStereoParallax(1.0) * scaleCalibrationFactor.value.toDouble()
        return rawArea * (scale * scale)
    }

    fun calculateBoundingBox(): BoundingBoxResult {
        val rawBox = ArMath.calculateBoundingBox(getCapturedPointsSnapshot())
        val scale = scaleCalibrationFactor.value.toDouble()
        if (abs(scale - 1.0) < 1e-6) return rawBox
        return rawBox.copy(
            lengthX = rawBox.lengthX * scale,
            heightY = rawBox.heightY * scale,
            widthZ = rawBox.widthZ * scale,
            volume = rawBox.volume * scale * scale * scale,
            surfaceArea = rawBox.surfaceArea * scale * scale
        )
    }

    fun calculateVerticalHeight(): Double {
        val points = getCapturedPointsSnapshot()
        return if (points.size >= 2) {
            sensorCorrectionEngine.correctVerticalHeightWithGravity(points.first(), points.last()) * scaleCalibrationFactor.value.toDouble()
        } else {
            0.0
        }
    }

    fun calculateCircle(): CircleResult? {
        val points = getCapturedPointsSnapshot()
        val raw = when {
            points.size >= 3 -> ArMath.fitCircle3Points(points[0], points[1], points[2])
            points.size == 2 -> ArMath.fitCircle2Points(points[0], points[1])
            else -> null
        } ?: return null
        val scale = scaleCalibrationFactor.value.toDouble()
        return raw.copy(
            radius = raw.radius * scale,
            diameter = raw.diameter * scale,
            circumference = raw.circumference * scale,
            area = raw.area * (scale * scale)
        )
    }

    fun calculateAngle(): Double {
        val points = getCapturedPointsSnapshot()
        return if (points.size >= 3) {
            ArMath.calculateAngleDegrees(points[0], points[1], points[2])
        } else {
            0.0
        }
    }

    // Formatting utilities with precise and adaptive decimal places
    fun formatLength(meters: Double, unit: String = _selectedUnit.value): String =
        MeasureFormatter.formatLength(meters, unit)

    fun formatArea(sqMeters: Double, unit: String = _selectedUnit.value): String =
        MeasureFormatter.formatArea(sqMeters, unit)

    fun formatVolume(cuMeters: Double, unit: String = _selectedUnit.value): String =
        MeasureFormatter.formatVolume(cuMeters, unit)

    // Last saved record for quick share affordance
    private val _lastSavedRecord = MutableStateFlow<MeasureRecord?>(null)
    val lastSavedRecord: StateFlow<MeasureRecord?> = _lastSavedRecord.asStateFlow()

    fun clearLastSavedRecord() {
        _lastSavedRecord.value = null
    }

    // Save record to Room database automatically with screenshot support
    fun saveMeasurementRecord(
        imagePath: String? = null,
        videoPath: String? = null,
        customTitle: String? = null,
        customNotes: String? = null,
        onSaved: ((MeasureRecord) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val subMode = _cameraSubMode.value
            val unit = _selectedUnit.value
            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

            val effectiveType = if (subMode == 0) _autoDetectedType.value else when (subMode) {
                1 -> "AREA"
                2 -> "HEIGHT"
                3 -> "VOLUME"
                4 -> "CIRCLE"
                5 -> "ANGLE"
                else -> "DISTANCE"
            }

            val (value, typeStr, autoTitle) = when (effectiveType) {
                "AREA" -> {
                    val area = calculatePolygonArea()
                    Triple(area, "AREA", trFmt("vm_rec_area_fmt", formatArea(area, unit), timeStr))
                }
                "HEIGHT" -> {
                    val height = calculateVerticalHeight()
                    Triple(height, "HEIGHT", trFmt("vm_rec_height_fmt", formatLength(height, unit), timeStr))
                }
                "VOLUME" -> {
                    val box = calculateBoundingBox()
                    Triple(box.volume, "VOLUME", trFmt("vm_rec_vol_fmt", formatVolume(box.volume, unit), timeStr))
                }
                "CIRCLE" -> {
                    val circle = calculateCircle()
                    val d = circle?.diameter ?: 0.0
                    Triple(d, "CIRCLE", trFmt("vm_rec_circle_fmt", formatLength(d, unit), timeStr))
                }
                "ANGLE" -> {
                    val angle = calculateAngle()
                    Triple(angle, "ANGLE", trFmt("vm_rec_angle_fmt", Math.round(angle), timeStr))
                }
                else -> {
                    val dist = calculateTotalDistance()
                    val lineCount = getCapturedPointsSnapshot().size / 2
                    val titleDesc = when {
                        lineCount > 1 -> trFmt("vm_rec_lines_fmt", lineCount, formatLength(dist, unit), timeStr)
                        dist > 0.0 -> trFmt("vm_rec_line_fmt", formatLength(dist, unit), timeStr)
                        !videoPath.isNullOrBlank() -> trFmt("vm_rec_video_fmt", timeStr)
                        else -> trFmt("vm_rec_photo_fmt", timeStr)
                    }
                    Triple(dist, "CAM", titleDesc)
                }
            }

            val hasMediaAttachment = !imagePath.isNullOrBlank() || !videoPath.isNullOrBlank()
            if (value > 0.0 || hasMediaAttachment) {
                val title = if (!customTitle.isNullOrBlank()) customTitle else autoTitle
                val serializedPoints = getCapturedPointsSnapshot().serializePoints()
                val record = MeasureRecord(
                    title = title,
                    value = value,
                    unit = if (effectiveType == "ANGLE") "°" else unit,
                    type = typeStr,
                    notes = customNotes,
                    pointsData = serializedPoints,
                    imagePath = imagePath,
                    videoPath = videoPath
                )

                try {
                    withContext(Dispatchers.IO) {
                        repository.insert(record)
                    }
                    _lastSavedRecord.value = record
                    _toastMessage.tryEmit(trFmt("vm_toast_auto_saved_fmt", title))
                    onSaved?.invoke(record)
                    if (value > 0.0) {
                        clearActivePoints()
                    }
                    triggerHapticFeedback()
                } catch (e: Exception) {
                    _toastMessage.tryEmit(trFmt("vm_toast_save_failed_fmt", e.localizedMessage ?: ""))
                }
            }
        }
    }

    fun getPrimaryMeasurementDisplayString(): String {
        val subMode = _cameraSubMode.value
        val unit = _selectedUnit.value
        val effectiveType = if (subMode == 0) _autoDetectedType.value else when (subMode) {
            1 -> "AREA"
            2 -> "HEIGHT"
            3 -> "VOLUME"
            4 -> "CIRCLE"
            5 -> "ANGLE"
            else -> "DISTANCE"
        }
        return when (effectiveType) {
            "AREA" -> formatArea(calculatePolygonArea(), unit)
            "HEIGHT" -> formatLength(calculateVerticalHeight(), unit)
            "VOLUME" -> formatVolume(calculateBoundingBox().volume, unit)
            "CIRCLE" -> trFmt("ar_summary_diam_fmt", formatLength(calculateCircle()?.diameter ?: 0.0, unit))
            "ANGLE" -> "${Math.round(calculateAngle())}°"
            else -> formatLength(calculateTotalDistance(), unit)
        }
    }

    fun saveRulerRecord(
        cmVal: Double,
        imagePath: String? = null,
        customTitle: String? = null,
        customNotes: String? = null,
        onSaved: ((MeasureRecord) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val meters = cmVal / 100.0
            val formatted = String.format(java.util.Locale.US, "%.2f cm", cmVal)
            val title = if (!customTitle.isNullOrBlank()) customTitle else trFmt("vm_rec_ruler_fmt", formatted, timeStr)
            val record = MeasureRecord(
                title = title,
                value = meters,
                unit = "cm",
                type = "RULER",
                notes = customNotes,
                imagePath = imagePath
            )
            try {
                withContext(Dispatchers.IO) {
                    repository.insert(record)
                }
                _lastSavedRecord.value = record
                _toastMessage.tryEmit(trFmt("vm_toast_auto_saved_fmt", title))
                onSaved?.invoke(record)
                triggerHapticFeedback(HapticType.DOUBLE)
            } catch (e: Exception) {
                _toastMessage.tryEmit(trFmt("vm_toast_save_failed_fmt", e.localizedMessage ?: ""))
            }
        }
    }

    fun saveLevelRecord(
        pitchDeg: Float,
        rollDeg: Float,
        modeName: String,
        toleranceDeg: Float,
        customNotes: String? = null,
        onSaved: ((MeasureRecord) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val dev = kotlin.math.sqrt((pitchDeg * pitchDeg + rollDeg * rollDeg).toDouble())
            val isLevel = dev <= toleranceDeg
            val statusStr = if (isLevel) {
                trFmt("vm_rec_level_ok_fmt", toleranceDeg.toString())
            } else {
                trFmt("vm_rec_level_dev_fmt", dev)
            }
            val title = trFmt("vm_rec_level_title_fmt", modeName, statusStr, timeStr)
            val notes = customNotes ?: trFmt("vm_rec_level_notes_fmt", toleranceDeg.toString())
            val record = MeasureRecord(
                title = title,
                value = dev,
                unit = "°",
                type = "LEVEL",
                notes = notes,
                imagePath = null
            )
            try {
                withContext(Dispatchers.IO) {
                    repository.insert(record)
                }
                _lastSavedRecord.value = record
                _toastMessage.tryEmit(trFmt("vm_toast_saved_fmt", title))
                onSaved?.invoke(record)
                triggerHapticFeedback(HapticType.DOUBLE)
            } catch (e: Exception) {
                _toastMessage.tryEmit(trFmt("vm_toast_save_failed_fmt", e.localizedMessage ?: ""))
            }
        }
    }

    fun updateRecordNotes(record: MeasureRecord, newNotes: String) {
        viewModelScope.launch {
            try {
                val updated = record.copy(notes = newNotes)
                withContext(Dispatchers.IO) {
                    repository.insert(updated)
                }
                _toastMessage.tryEmit(tr("vm_toast_notes_updated"))
            } catch (e: Exception) {
                _toastMessage.tryEmit(trFmt("vm_toast_notes_failed_fmt", e.localizedMessage ?: ""))
            }
        }
    }

    fun closeHighSpeedCamera() {
        try {
            highSpeedCamera2Manager?.closeCamera()
        } catch (_: Exception) {}
        highSpeedCamera2Manager = null
    }

    val isArCoreSessionActive: Boolean
        get() = modernArEngine.session != null

    fun deleteRecord(record: MeasureRecord) {
        viewModelScope.launch {
            try {
                record.imagePath?.let { path ->
                    try {
                        val file = java.io.File(path)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                }
                record.videoPath?.let { path ->
                    try {
                        val file = java.io.File(path)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                }
                withContext(Dispatchers.IO) {
                    repository.delete(record)
                }
            } catch (e: Exception) {}
        }
    }

    fun deleteRecordById(id: Int) {
        viewModelScope.launch {
            try {
                val record = savedRecords.value.firstOrNull { it.id == id }
                record?.imagePath?.let { path ->
                    try {
                        val file = java.io.File(path)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                }
                record?.videoPath?.let { path ->
                    try {
                        val file = java.io.File(path)
                        if (file.exists()) file.delete()
                    } catch (_: Exception) {}
                }
                withContext(Dispatchers.IO) {
                    repository.deleteById(id)
                }
            } catch (e: Exception) {}
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            try {
                savedRecords.value.forEach { record ->
                    record.imagePath?.let { path ->
                        try {
                            val file = java.io.File(path)
                            if (file.exists()) file.delete()
                        } catch (_: Exception) {}
                    }
                    record.videoPath?.let { path ->
                        try {
                            val file = java.io.File(path)
                            if (file.exists()) file.delete()
                        } catch (_: Exception) {}
                    }
                }
                withContext(Dispatchers.IO) {
                    repository.clearAll()
                }
            } catch (e: Exception) {}
        }
    }

    fun onResume() {
        syncWithSystemLocale()
        // ARCore SharedCamera resume is strictly triggered once Camera2 CaptureSession becomes active
        pixelPerformanceMonitor.startMonitoring()
        sensorCorrectionEngine.startListening()
        updateBatteryStatusAndEvaluate(showToast = false)
    }

    fun onPause() {
        turnOffTorch(getApplication())
        pauseArAndCamera()
        pixelPerformanceMonitor.stopMonitoring()
        sensorCorrectionEngine.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        mainHandler.removeCallbacksAndMessages(null)
        try {
            getApplication<Application>().unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        turnOffTorch(getApplication())
        synchronized(pointsLock) {
            val detached = HashSet<Anchor>()
            capturedPoints.forEach { pt ->
                pt.anchor?.let { a ->
                    if (detached.add(a)) {
                        try { a.detach() } catch (_: Throwable) {}
                    }
                }
            }
            undoStack.forEach { frame ->
                frame.forEach { pt ->
                    pt.anchor?.let { a ->
                        if (detached.add(a)) {
                            try { a.detach() } catch (_: Throwable) {}
                        }
                    }
                }
            }
            capturedPoints.clear()
            undoStack.clear()
            updateCapturedPointsSnapshotCache()
        }
        vulkanGraphicsPipeline.release()
        modernArEngine.pause()
        try {
            highSpeedCamera2Manager?.closeCamera()
        } catch (_: Exception) {}
        modernArEngine.destroy()
        highSpeedCamera2Manager = null
        pixelPerformanceMonitor.stopMonitoring()
        sensorCorrectionEngine.stopListening()
    }
}
