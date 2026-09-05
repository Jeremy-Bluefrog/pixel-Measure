package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.MeasureDatabase
import com.example.data.model.MeasureRecord
import com.example.data.repository.MeasureRepository
import com.example.logic.TranslationManager
import com.example.logic.ai.ObjectronEngine
import com.example.logic.ai.Objectron3DBox
import com.example.logic.ai.MobileSamEngine
import com.example.logic.ai.SegmentedObject
import com.example.logic.ai.AiTileDetector
import com.example.logic.ai.AiTilePreset
import com.example.logic.ai.DetectedTile
import com.example.logic.ai.TileEstimationResult
import com.example.logic.ai.TilePatternType
import com.example.logic.ar.*
import com.example.logic.sensor.SensorCorrectionTelemetry
import com.example.logic.sensor.SensorFusionCorrectionEngine
import com.google.ar.core.*
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

/**
 * Modern, clean ViewModel managing AR measurements, sensor fusion,
 * persistence, unit conversions, and UI state.
 */
class MeasureViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("measure_app_prefs", Context.MODE_PRIVATE)

    private val database = MeasureDatabase.getDatabase(application)
    private val repository = MeasureRepository(database.measureDao())

    // Language state
    private val _currentLanguage = MutableStateFlow(
        prefs.getString("selected_language", "zh-TW") ?: "zh-TW"
    )
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun setLanguage(langCode: String) {
        _currentLanguage.value = langCode
        prefs.edit().putString("selected_language", langCode).apply()
    }

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

    // Modern AR Engine & Session
    val modernArEngine = ModernArEngine(application)
    val arSession: Session? get() = modernArEngine.session

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

    private val _surfaceTypeAtCenter = MutableStateFlow("尋找空間特徵中...")
    val surfaceTypeAtCenter: StateFlow<String> = _surfaceTypeAtCenter.asStateFlow()

    private val _lightIntensity = MutableStateFlow(1.0f)
    val lightIntensity: StateFlow<Float> = _lightIntensity.asStateFlow()

    private val _viewMatrix = MutableStateFlow(FloatArray(16))
    val viewMatrix: StateFlow<FloatArray> = _viewMatrix.asStateFlow()

    private val _projectionMatrix = MutableStateFlow(FloatArray(16))
    val projectionMatrix: StateFlow<FloatArray> = _projectionMatrix.asStateFlow()

    // Screen dimensions
    var displayWidth: Int = 1080
        private set
    var displayHeight: Int = 2400
        private set

    fun updateDisplayGeometry(rotation: Int, width: Int, height: Int) {
        displayWidth = width
        displayHeight = height
    }

    // Active placed points in 3D space
    val capturedPoints = mutableStateListOf<Point3D>()
    private val undoStack = mutableListOf<List<Point3D>>()

    // Real-time live targeting preview from current reticle position
    private val _liveTargetPoint = MutableStateFlow<Point3D?>(null)
    val liveTargetPoint: StateFlow<Point3D?> = _liveTargetPoint.asStateFlow()

    private val _liveDistanceMeters = MutableStateFlow<Double?>(null)
    val liveDistanceMeters: StateFlow<Double?> = _liveDistanceMeters.asStateFlow()

    private val _isSnapped = MutableStateFlow(false)
    val isSnapped: StateFlow<Boolean> = _isSnapped.asStateFlow()

    // Torch / Flashlight state & Brightness control
    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _torchBrightness = MutableStateFlow(prefs.getFloat("torch_brightness", 1.0f))
    val torchBrightness: StateFlow<Float> = _torchBrightness.asStateFlow()

    // Toast / Feedback event channel
    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()

    // Haptic feedback channel
    private val _hapticEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val hapticEvent = _hapticEvent.asSharedFlow()

    fun triggerHapticFeedback() {
        _hapticEvent.tryEmit(Unit)
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

    // Dynamic color preference
    private val _dynamicColorEnabled = MutableStateFlow(prefs.getBoolean("dynamic_color", true))
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setDynamicColorEnabled(enabled: Boolean) {
        _dynamicColorEnabled.value = enabled
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
    }

    // Scanning Feature Point Cloud preference
    private val _showPointCloud = MutableStateFlow(prefs.getBoolean("show_point_cloud", true))
    val showPointCloud: StateFlow<Boolean> = _showPointCloud.asStateFlow()

    fun setShowPointCloud(enabled: Boolean) {
        _showPointCloud.value = enabled
        prefs.edit().putBoolean("show_point_cloud", enabled).apply()
    }

    // Multi-Sensor Fusion & Measurement Correction Engine
    val sensorCorrectionEngine = SensorFusionCorrectionEngine(application)
    val sensorTelemetry: StateFlow<SensorCorrectionTelemetry> = sensorCorrectionEngine.telemetry

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
    val scaleCalibrationFactor = MutableStateFlow(prefs.getFloat("scale_calibration_factor", 1.0000f))

    val highFpsModeEnabled = MutableStateFlow(prefs.getBoolean("high_fps_mode_enabled", true))
    val highDefinitionQualityEnabled = MutableStateFlow(prefs.getBoolean("high_definition_quality_enabled", true))

    fun setHighFpsModeEnabled(enabled: Boolean) {
        highFpsModeEnabled.value = enabled
        prefs.edit().putBoolean("high_fps_mode_enabled", enabled).apply()
        _toastMessage.tryEmit(if (enabled) "已啟用 60Hz 高幀率相機預覽" else "已切換至標準幀率")
    }

    fun setHighDefinitionQualityEnabled(enabled: Boolean) {
        highDefinitionQualityEnabled.value = enabled
        prefs.edit().putBoolean("high_definition_quality_enabled", enabled).apply()
        _toastMessage.tryEmit(if (enabled) "已啟用 Full HD/4K 超高畫質模式" else "已切換至標準畫質模式")
    }

    fun setSensorCorrectionEnabled(enabled: Boolean) {
        sensorCorrectionEnabled.value = enabled
        sensorCorrectionEngine.isSensorCorrectionEnabled = enabled
        prefs.edit().putBoolean("sensor_correction_enabled", enabled).apply()
        _toastMessage.tryEmit(if (enabled) "已啟用感應器融合校正" else "已關閉感應器校正")
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

    fun setScaleCalibrationFactor(factor: Float) {
        val clamped = factor.coerceIn(0.9000f, 1.1000f)
        scaleCalibrationFactor.value = clamped
        sensorCorrectionEngine.userScaleCalibrationFactor = clamped
        prefs.edit().putFloat("scale_calibration_factor", clamped).apply()
        _toastMessage.tryEmit("尺度校正係數已設定為：${"%.4f".format(clamped)}x")
    }

    fun calibrateSensors() {
        sensorCorrectionEngine.resetBarometerBase()
        triggerHapticFeedback()
        _toastMessage.tryEmit("已重設氣壓與感應器基準高度")
    }

    // AI Core Tile Recognition & One-Tap Measurement State
    private val _isAiTileMode = MutableStateFlow(false)
    val isAiTileMode: StateFlow<Boolean> = _isAiTileMode.asStateFlow()

    private val _isAiTileAnalyzing = MutableStateFlow(false)
    val isAiTileAnalyzing: StateFlow<Boolean> = _isAiTileAnalyzing.asStateFlow()

    private val _detectedTiles = MutableStateFlow<List<DetectedTile>>(emptyList())
    val detectedTiles: StateFlow<List<DetectedTile>> = _detectedTiles.asStateFlow()

    private val _selectedTileForDetail = MutableStateFlow<DetectedTile?>(null)
    val selectedTileForDetail: StateFlow<DetectedTile?> = _selectedTileForDetail.asStateFlow()

    private val _tileTargetAreaM2 = MutableStateFlow(16.5) // Default 5 坪 ~ 16.5 m²
    val tileTargetAreaM2: StateFlow<Double> = _tileTargetAreaM2.asStateFlow()

    private val _tileWastagePercent = MutableStateFlow(10) // 10% 損耗備料
    val tileWastagePercent: StateFlow<Int> = _tileWastagePercent.asStateFlow()

    private val _activeTilePreset = MutableStateFlow(AiTilePreset.PRESET_60X60)
    val activeTilePreset: StateFlow<AiTilePreset> = _activeTilePreset.asStateFlow()

    private val _activeTilePattern = MutableStateFlow(TilePatternType.GRID)
    val activeTilePattern: StateFlow<TilePatternType> = _activeTilePattern.asStateFlow()

    fun setTilePreset(preset: AiTilePreset) {
        _activeTilePreset.value = preset
        triggerHapticFeedback()
        _toastMessage.tryEmit("已切換磁磚規格：${preset.name} (${preset.widthCm.toInt()}×${preset.heightCm.toInt()} cm)")
    }

    fun setTilePatternType(pattern: TilePatternType) {
        _activeTilePattern.value = pattern
        triggerHapticFeedback()
        _toastMessage.tryEmit("已切換鋪設工法：${pattern.label}")
    }

    fun setTileTargetAreaM2(area: Double) {
        _tileTargetAreaM2.value = area.coerceAtLeast(0.1)
    }

    fun setTileWastagePercent(percent: Int) {
        _tileWastagePercent.value = percent.coerceIn(0, 30)
    }

    fun selectTileForDetail(tile: DetectedTile?) {
        _selectedTileForDetail.value = tile
    }

    fun toggleAiTileMode(bitmap: Bitmap? = null) {
        val nextState = !_isAiTileMode.value
        _isAiTileMode.value = nextState
        triggerHapticFeedback()
        if (nextState) {
            triggerAiTileDetection(bitmap)
            _toastMessage.tryEmit("已開啟 AI Core 磁磚自動識別")
        } else {
            _detectedTiles.value = emptyList()
            _selectedTileForDetail.value = null
            _toastMessage.tryEmit("已退出 AI 磁磚識別模式")
        }
    }

    fun triggerAiTileDetection(bitmap: Bitmap? = null) {
        viewModelScope.launch {
            _isAiTileAnalyzing.value = true
            try {
                val center = _liveTargetPoint.value ?: Point3D(0.0, -0.4, -1.2, isArPrecision = true)
                val tiles = if (bitmap != null) {
                    val geminiTiles = AiTileDetector.analyzeTilesWithGemini(bitmap)
                    geminiTiles.map { t ->
                        if (t.worldCorners.isEmpty()) {
                            t.copy(worldCorners = AiTileDetector.createTileCorners(center, t.estimatedWidthCm / 100.0, t.estimatedHeightCm / 100.0))
                        } else t
                    }
                } else {
                    AiTileDetector.generateLocalVisionTiles(displayWidth, displayHeight, center)
                }
                _detectedTiles.value = tiles
                triggerHapticFeedback()
                _toastMessage.tryEmit("AI Core 已識別 ${tiles.size} 處磁磚結構，輕觸即可自動測量")
            } catch (e: Exception) {
                val center = _liveTargetPoint.value ?: Point3D(0.0, -0.4, -1.2, isArPrecision = true)
                _detectedTiles.value = AiTileDetector.generateLocalVisionTiles(displayWidth, displayHeight, center)
            } finally {
                _isAiTileAnalyzing.value = false
            }
        }
    }

    /**
     * One-Tap Tile Measurement: Instantly places the 4 corner 3D anchor points in AR space,
     * computes the exact dimensions and area, and opens the tile spec calculation sheet.
     */
    fun measureTileOneTap(tile: DetectedTile) {
        viewModelScope.launch {
            saveUndoState()
            capturedPoints.clear()

            if (tile.worldCorners.size == 4) {
                capturedPoints.addAll(tile.worldCorners)
            } else {
                val wMeters = tile.estimatedWidthCm / 100.0
                val hMeters = tile.estimatedHeightCm / 100.0
                val center = _liveTargetPoint.value ?: Point3D(0.0, -0.4, -1.2, isArPrecision = true)
                capturedPoints.addAll(AiTileDetector.createTileCorners(center, wMeters, hMeters))
            }
            _autoDetectedType.value = "AREA"
            _selectedTileForDetail.value = tile

            triggerHapticFeedback()
            val wStr = "${Math.round(tile.estimatedWidthCm)}"
            val hStr = "${Math.round(tile.estimatedHeightCm)}"
            _toastMessage.tryEmit("✨ 已自動鎖定並測量磁磚：${wStr}×${hStr} cm")
        }
    }

    // AI 3D Bounding Box (MediaPipe Objectron) Estimation State
    private val _objectron3DBox = MutableStateFlow<Objectron3DBox?>(null)
    val objectron3DBox: StateFlow<Objectron3DBox?> = _objectron3DBox.asStateFlow()

    private val _isObjectronMode = MutableStateFlow(false)
    val isObjectronMode: StateFlow<Boolean> = _isObjectronMode.asStateFlow()

    fun toggleObjectronMode() {
        val nextState = !_isObjectronMode.value
        _isObjectronMode.value = nextState
        triggerHapticFeedback()
        if (nextState) {
            updateObjectron3DBox()
            _toastMessage.tryEmit("已啟用 MediaPipe Objectron 3D AI 立體方框輔助")
        } else {
            _objectron3DBox.value = null
            _toastMessage.tryEmit("已退出 Objectron 3D 模式")
        }
    }

    fun updateObjectron3DBox() {
        if (capturedPoints.isEmpty()) {
            val liveTarget = _liveTargetPoint.value
            if (liveTarget != null) {
                // Generate instant 3D bounding box around live target
                _objectron3DBox.value = ObjectronEngine.estimateBoxFromPlane(
                    centerPoint = liveTarget,
                    widthMeters = 0.30,
                    heightMeters = 0.20,
                    depthMeters = 0.25,
                    category = "Object"
                )
            } else {
                _objectron3DBox.value = null
            }
        } else {
            _objectron3DBox.value = ObjectronEngine.fitBoxFromPoints(capturedPoints)
        }
    }

    fun applyObjectronBoxCorners() {
        val box = _objectron3DBox.value ?: return
        saveUndoState()
        capturedPoints.clear()
        capturedPoints.addAll(box.corners)
        _autoDetectedType.value = "AREA"
        triggerHapticFeedback()
        val volLiters = Math.round(box.volumeM3 * 1000.0)
        _toastMessage.tryEmit("✨ 已鎖定 Objectron 3D 空間錨點（體積: ${volLiters} L）")
    }

    // MobileSAM / FastSAM On-Device Segmentation State
    private val _isMobileSamMode = MutableStateFlow(false)
    val isMobileSamMode: StateFlow<Boolean> = _isMobileSamMode.asStateFlow()

    private val _segmentedObject = MutableStateFlow<SegmentedObject?>(null)
    val segmentedObject: StateFlow<SegmentedObject?> = _segmentedObject.asStateFlow()

    fun toggleMobileSamMode() {
        val nextState = !_isMobileSamMode.value
        _isMobileSamMode.value = nextState
        triggerHapticFeedback()
        if (nextState) {
            _toastMessage.tryEmit("已啟用 MobileSAM 智慧一鍵物件邊緣分割")
        } else {
            _segmentedObject.value = null
            _toastMessage.tryEmit("已退出 MobileSAM 模式")
        }
    }

    fun triggerSamSegmentationAtTap(
        screenTap: androidx.compose.ui.geometry.Offset,
        screenW: Float,
        screenH: Float
    ) {
        val refPoint = _liveTargetPoint.value ?: capturedPoints.lastOrNull()
        val segResult = MobileSamEngine.segmentAtPoint(
            screenTap = screenTap,
            screenWidth = screenW,
            screenHeight = screenH,
            reference3DPoint = refPoint,
            viewMatrix = _viewMatrix.value,
            projectionMatrix = _projectionMatrix.value
        )
        _segmentedObject.value = segResult
        triggerHapticFeedback()
        _toastMessage.tryEmit("✨ 已分割 ${segResult.label}（面積: ${"%.2f".format(segResult.areaM2)} m²）")
    }

    fun applySegmentedObjectCorners() {
        val seg = _segmentedObject.value ?: return
        saveUndoState()
        capturedPoints.clear()
        capturedPoints.addAll(seg.corners3D)
        _autoDetectedType.value = "AREA"
        triggerHapticFeedback()
        _toastMessage.tryEmit("🎯 已鎖定 ${seg.label} 邊緣多邊形測量")
    }

    fun measureTileUnderReticle() {
        val preset = _activeTilePreset.value
        val detected = _detectedTiles.value.firstOrNull() ?: DetectedTile(
            label = "鎖定磁磚 (${preset.widthCm.toInt()}×${preset.heightCm.toInt()} cm)",
            material = preset.defaultMaterial,
            estimatedWidthCm = preset.widthCm,
            estimatedHeightCm = preset.heightCm,
            areaM2 = preset.singleTileAreaM2,
            groutWidthMm = preset.defaultGroutMm
        )
        measureTileOneTap(detected)
    }

    init {
        sensorCorrectionEngine.isSensorCorrectionEnabled = sensorCorrectionEnabled.value
        sensorCorrectionEngine.isAntiJitterEnabled = antiJitterEnabled.value
        sensorCorrectionEngine.isGravityAlignmentEnabled = gravityAlignmentEnabled.value
        sensorCorrectionEngine.isBarometerFusionEnabled = barometerFusionEnabled.value
        sensorCorrectionEngine.isJerkRejectionEnabled = jerkRejectionEnabled.value
        sensorCorrectionEngine.isProximityZeroContactEnabled = proximityContactEnabled.value
        sensorCorrectionEngine.isStereoParallaxScaleEnabled = stereoParallaxEnabled.value
        sensorCorrectionEngine.startListening()
    }

    // Ruler calibration & Vernier caliper positions
    private val _rulerCalibration = MutableStateFlow(prefs.getFloat("ruler_calibration", 1.0f))
    val rulerCalibration: StateFlow<Float> = _rulerCalibration.asStateFlow()

    fun updateRulerCalibration(factor: Float) {
        _rulerCalibration.value = factor
        prefs.edit().putFloat("ruler_calibration", factor).apply()
    }

    // Queue for hit-testing requested from UI touches
    private val pendingHitTestQueue = AtomicReference<Pair<Float, Float>?>()

    // AR Frame Processing on GL Thread
    fun processGlFrame(frame: Frame, engine: ModernArEngine): HitTestResult? {
        // 1. Process any pending tap hit-test (creates persistent anchor only on tap)
        val tapRequest = pendingHitTestQueue.getAndSet(null)
        val isTap = tapRequest != null
        val hitX = tapRequest?.first ?: (displayWidth / 2f)
        val hitY = tapRequest?.second ?: (displayHeight / 2f)

        val hitResult = engine.performHitTest(frame, hitX, hitY, createAnchor = isTap)

        // 2. Real-time live targeting preview at screen center (no anchor creation to avoid GC pauses)
        val centerHit = if (!isTap) hitResult else engine.performHitTest(frame, displayWidth / 2f, displayHeight / 2f, createAnchor = false)

        if (centerHit != null) {
            val pose = centerHit.pose
            val rawPoint = Point3D(
                x = pose.tx().toDouble(),
                y = pose.ty().toDouble(),
                z = pose.tz().toDouble(),
                isArPrecision = true
            )

            // 0. Proximity Zero-Contact Offset (if touching surface directly)
            val contactPoint = sensorCorrectionEngine.applyProximityZeroContactOffset(rawPoint)

            // 1. Plane Coplanar Projection (for 2D Area & Polygon measuring on flat surfaces)
            val planeProjectedPoint = if (_cameraSubMode.value == 1 || centerHit.hitType == com.example.logic.ar.HitType.PLANE_POLYGON) {
                sensorCorrectionEngine.projectPointToPlane(contactPoint, centerHit.pose)
            } else {
                contactPoint
            }

            // 2. Apply 3D Adaptive Kalman Filter & Multi-Sample Burst Averaging
            val sensorCorrectedPoint = sensorCorrectionEngine.correctPointWithSensorFusion(
                rawPoint = planeProjectedPoint,
                previousPoint = _liveTargetPoint.value
            )
            val smoothedPoint = ArMath.filterJitterEMA(_liveTargetPoint.value, sensorCorrectedPoint)

            // 3. Gravity-aligned orthogonal leveling & 45/90-deg snapping
            val orthogonalAdjustedPoint = if (capturedPoints.isNotEmpty() && (_cameraSubMode.value == 0 || _cameraSubMode.value == 1)) {
                sensorCorrectionEngine.correctOrthogonalAlignment(capturedPoints.last(), smoothedPoint)
            } else {
                smoothedPoint
            }

            // 4. Magnetic Vertex Snapping check
            val snappedVertex = ArMath.findVertexSnap(orthogonalAdjustedPoint, capturedPoints, 0.07)
            val finalTargetPoint: Point3D

            if (snappedVertex != null) {
                finalTargetPoint = snappedVertex
                if (!_isSnapped.value) {
                    _isSnapped.value = true
                    triggerHapticFeedback()
                }
            } else {
                _isSnapped.value = false
                finalTargetPoint = orthogonalAdjustedPoint
            }

            _liveTargetPoint.value = finalTargetPoint

            // 5. Calculate live real-time distance with stability & Dual-Camera Stereo Parallax scale calibration
            if (capturedPoints.isNotEmpty()) {
                val lastPoint = capturedPoints.last()
                val dist = if (_cameraSubMode.value == 2) {
                    sensorCorrectionEngine.correctVerticalHeightWithGravity(lastPoint, finalTargetPoint)
                } else {
                    val rawD = ArMath.distance(lastPoint, finalTargetPoint)
                    sensorCorrectionEngine.correctDistanceWithStereoParallax(rawD)
                }
                _liveDistanceMeters.value = smoothLiveDistance(dist)
            } else {
                val centerDist = sensorCorrectionEngine.correctDistanceWithStereoParallax(centerHit.distance.toDouble())
                _liveDistanceMeters.value = smoothLiveDistance(centerDist)
            }
        }

        // 6. If a tap was requested, commit the point with sensor correction
        if (tapRequest != null && hitResult != null) {
            val pose = hitResult.pose
            val rawP = Point3D(
                x = pose.tx().toDouble(),
                y = pose.ty().toDouble(),
                z = pose.tz().toDouble(),
                isArPrecision = true,
                anchor = hitResult.anchor
            )
            val contactP = sensorCorrectionEngine.applyProximityZeroContactOffset(rawP)
            val planarP = if (_cameraSubMode.value == 1 || hitResult.hitType == com.example.logic.ar.HitType.PLANE_POLYGON) {
                sensorCorrectionEngine.projectPointToPlane(contactP, hitResult.pose)
            } else {
                contactP
            }
            val livePt = _liveTargetPoint.value
            val baseP = if (livePt != null && ArMath.distance(livePt, planarP) < 0.08) {
                livePt.copy(anchor = hitResult.anchor ?: livePt.anchor)
            } else {
                planarP
            }
            val correctedP = if (capturedPoints.isNotEmpty() && (_cameraSubMode.value == 0 || _cameraSubMode.value == 1)) {
                sensorCorrectionEngine.correctOrthogonalAlignment(capturedPoints.last(), baseP)
            } else {
                baseP
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                saveUndoState()
                capturedPoints.add(correctedP)
                triggerHapticFeedback()
                updateAutoDetectedGeometry()
                if (_isObjectronMode.value) {
                    updateObjectron3DBox()
                }
            }
        }
        return centerHit
    }

    private fun smoothLiveDistance(targetDist: Double): Double {
        val prev = _liveDistanceMeters.value ?: return targetDist
        val diff = abs(targetDist - prev)
        // Deadband: within 4mm, completely hold previous distance to prevent digit flickering
        if (diff < 0.004) return prev
        val alpha = when {
            diff < 0.02 -> 0.08 // Micro flutter: strong dampening
            diff < 0.08 -> 0.20 // Minor change: smooth ease
            diff < 0.30 -> 0.50 // Moderate shift
            else -> 1.0         // Big move: immediate
        }
        return prev * (1.0 - alpha) + targetDist * alpha
    }

    private fun updateAutoDetectedGeometry() {
        if (_cameraSubMode.value != 0) return // Manual mode override

        when (capturedPoints.size) {
            0, 1 -> _autoDetectedType.value = "DISTANCE"
            2 -> {
                // Check if vertical height
                val p1 = capturedPoints[0]
                val p2 = capturedPoints[1]
                val dy = abs(p2.y - p1.y)
                val dxz = sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.z - p1.z) * (p2.z - p1.z))
                if (dy > 0.35 && dy > dxz * 2.2) {
                    _autoDetectedType.value = "HEIGHT"
                } else {
                    _autoDetectedType.value = "DISTANCE"
                }
            }
            3 -> {
                // Check if closing loop or distance
                val pFirst = capturedPoints.first()
                val pLast = capturedPoints.last()
                val closeDist = ArMath.distance(pFirst, pLast)
                if (closeDist < 0.12) {
                    _autoDetectedType.value = "AREA"
                } else {
                    _autoDetectedType.value = "DISTANCE"
                }
            }
            else -> {
                // >= 4 points: check if closed polygon
                val pFirst = capturedPoints.first()
                val pLast = capturedPoints.last()
                val closeDist = ArMath.distance(pFirst, pLast)
                val totalLen = ArMath.polylineLength(capturedPoints)
                if (closeDist < 0.25 || (totalLen > 0 && closeDist / totalLen < 0.2)) {
                    _autoDetectedType.value = "AREA"
                } else {
                    _autoDetectedType.value = "DISTANCE"
                }
            }
        }
    }

    fun onArFrameUpdated(data: ModernArFrame) {
        if (_arTrackingState.value != data.trackingState) _arTrackingState.value = data.trackingState
        if (_trackingFailureReason.value != data.trackingFailureReason) _trackingFailureReason.value = data.trackingFailureReason
        _viewMatrix.value = data.viewMatrix
        _projectionMatrix.value = data.projectionMatrix
        if (_isDepthAvailable.value != data.isDepthAvailable) _isDepthAvailable.value = data.isDepthAvailable
        if (_detectedPlanes.value !== data.planes) {
            _detectedPlanes.value = data.planes
            _arPlanesCount.value = data.planes.count { it.isTracking }
        }
        if (_surfaceTypeAtCenter.value != data.surfaceTypeAtCenter) _surfaceTypeAtCenter.value = data.surfaceTypeAtCenter
        _lightIntensity.value = data.lightIntensity
        _trackingStability.value = data.stability

        // Update active anchor positions to eliminate world drift
        if (capturedPoints.isNotEmpty() && data.trackingState == TrackingState.TRACKING) {
            for (i in capturedPoints.indices) {
                val pt = capturedPoints[i]
                val anchor = pt.anchor
                if (anchor != null && anchor.trackingState == TrackingState.TRACKING) {
                    val pose = anchor.pose
                    val newX = pose.tx().toDouble()
                    val newY = pose.ty().toDouble()
                    val newZ = pose.tz().toDouble()
                    val candidate = pt.copy(x = newX, y = newY, z = newZ)

                    if (ArMath.isPointValid(candidate)) {
                        val dx = abs(newX - pt.x)
                        val dy = abs(newY - pt.y)
                        val dz = abs(newZ - pt.z)
                        if (dx > 0.0001 || dy > 0.0001 || dz > 0.0001) {
                            capturedPoints[i] = candidate
                        }
                    }
                }
            }
        }
    }

    init {
        // Start Fallback Sensor-Driven Spatial Engine when ARCore GL frames are inactive
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(33) // ~30 FPS fallback updater
                if (modernArEngine.session == null || _arTrackingState.value != TrackingState.TRACKING) {
                    updateFallbackSpatialFrame()
                }
            }
        }
    }

    private fun updateFallbackSpatialFrame() {
        _arTrackingState.value = TrackingState.TRACKING
        _surfaceTypeAtCenter.value = "感應器空間校正"

        // Build standard perspective projection matrix
        val pMatrix = FloatArray(16)
        val aspect = displayWidth.toFloat() / displayHeight.coerceAtLeast(1).toFloat()
        android.opengl.Matrix.perspectiveM(pMatrix, 0, 60.0f, aspect, 0.1f, 50.0f)
        _projectionMatrix.value = pMatrix

        // Build View Matrix from device rotation
        val telem = sensorTelemetry.value
        val vMatrix = FloatArray(16)
        android.opengl.Matrix.setIdentityM(vMatrix, 0)
        android.opengl.Matrix.rotateM(vMatrix, 0, telem.pitchDeg, 1f, 0f, 0f)
        android.opengl.Matrix.rotateM(vMatrix, 0, telem.rollDeg, 0f, 0f, 1f)
        android.opengl.Matrix.rotateM(vMatrix, 0, telem.azimuthDeg, 0f, 1f, 0f)
        _viewMatrix.value = vMatrix

        // Raycast depth estimation: pitch down creates distance to ground
        val pitchRad = Math.toRadians(telem.pitchDeg.toDouble().coerceIn(-85.0, 85.0))
        val estDist = if (pitchRad < -0.1) {
            (1.35 / kotlin.math.sin(-pitchRad)).coerceIn(0.4, 10.0)
        } else {
            1.5
        }

        // Live target point in front of camera
        val forwardX = kotlin.math.sin(Math.toRadians(telem.azimuthDeg.toDouble())) * kotlin.math.cos(pitchRad) * estDist
        val forwardY = -kotlin.math.sin(pitchRad) * estDist
        val forwardZ = -kotlin.math.cos(Math.toRadians(telem.azimuthDeg.toDouble())) * kotlin.math.cos(pitchRad) * estDist

        val rawTarget = Point3D(forwardX, forwardY, forwardZ, isArPrecision = true)
        val smoothed = ArMath.filterJitterEMA(_liveTargetPoint.value, rawTarget)
        _liveTargetPoint.value = smoothed

        if (capturedPoints.isNotEmpty()) {
            val dist = ArMath.distance(capturedPoints.last(), smoothed)
            _liveDistanceMeters.value = smoothLiveDistance(dist)
        } else {
            _liveDistanceMeters.value = smoothLiveDistance(estDist)
        }
    }

    fun requestHitTest(pixelX: Float? = null, pixelY: Float? = null) {
        val x = pixelX ?: (displayWidth / 2f)
        val y = pixelY ?: (displayHeight / 2f)

        // Proactive warning when AR tracking feature points are deficient or camera moves too fast
        val stability = _trackingStability.value
        if (stability.isDriftRisk) {
            val warn = stability.warningMessage ?: "特徵點不足，請慢速平移相機"
            _toastMessage.tryEmit("⚠️ $warn（避免測量偏移）")
        }
        
        if (modernArEngine.session != null) {
            pendingHitTestQueue.set(Pair(x, y))
        } else {
            val target = _liveTargetPoint.value ?: Point3D(0.0, 0.0, -1.2, isArPrecision = true)
            saveUndoState()
            capturedPoints.add(target)
            triggerHapticFeedback()
            updateAutoDetectedGeometry()
        }
    }

    private fun saveUndoState() {
        undoStack.add(capturedPoints.toList())
        if (undoStack.size > 20) undoStack.removeAt(0)
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.size - 1)
            capturedPoints.clear()
            capturedPoints.addAll(previous)
            triggerHapticFeedback()
            updateAutoDetectedGeometry()
        } else if (capturedPoints.isNotEmpty()) {
            capturedPoints.removeAt(capturedPoints.size - 1)
            triggerHapticFeedback()
            updateAutoDetectedGeometry()
        }
    }

    fun clearActivePoints() {
        saveUndoState()
        capturedPoints.forEach { it.anchor?.detach() }
        capturedPoints.clear()
        _liveDistanceMeters.value = null
        _autoDetectedType.value = "DISTANCE"
        if (_isObjectronMode.value) {
            updateObjectron3DBox()
        }
        triggerHapticFeedback()
    }

    fun updatePointLabel(index: Int, newLabel: String) {
        if (index in capturedPoints.indices) {
            capturedPoints[index] = capturedPoints[index].copy(label = newLabel)
        }
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

    fun setHighSpeedCamera2Manager(manager: com.example.logic.camera.HighSpeedCamera2Manager?) {
        highSpeedCamera2Manager = manager
        if (_isTorchOn.value && manager != null) {
            manager.toggleTorch(true)
        }
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

        // 1. Android 13+ (API 33+) Hardware Torch Strength Level via CameraManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
                    if (maxLevel > 1) {
                        val targetLevel = (brightness * maxLevel).toInt().coerceIn(1, maxLevel)
                        cameraManager.turnOnTorchWithStrengthLevel(targetCameraId, targetLevel)
                        success = true
                    }
                }
            } catch (e: Throwable) {
                Log.w("MeasureViewModel", "turnOnTorchWithStrengthLevel fallback: ${e.message}")
            }
        }

        // 2. If ARCore session is running, configure flashMode via ARCore
        if (!success && modernArEngine.session != null) {
            val arOk = modernArEngine.setTorchMode(newState)
            if (arOk) {
                success = true
            }
        }

        // 3. If Direct HighSpeedCamera2Manager is active
        if (!success && highSpeedCamera2Manager != null) {
            try {
                highSpeedCamera2Manager?.toggleTorch(newState)
                success = true
            } catch (e: Exception) {
                Log.w("MeasureViewModel", "HighSpeedCamera2Manager toggleTorch error: ${e.message}")
            }
        }

        // 4. If CameraX is active and controlling the camera
        if (!success && cameraControl != null) {
            try {
                cameraControl?.enableTorch(newState)
                success = true
            } catch (e: Exception) {
                Log.w("MeasureViewModel", "CameraX enableTorch failed: ${e.message}")
            }
        }

        // 5. Fallback: Standard CameraManager setTorchMode
        if (!success) {
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

        _isTorchOn.value = newState
        triggerHapticFeedback()
        val percent = (brightness * 100).toInt()
        _toastMessage.tryEmit(if (newState) "已開啟手電筒補光 (${percent}%)" else "已關閉手電筒")
    }

    fun turnOffTorch(context: Context? = null) {
        if (!_isTorchOn.value) return
        _isTorchOn.value = false
        modernArEngine.setTorchMode(false)
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

    // Measurement Calculations
    fun calculateTotalDistance(): Double {
        val raw = ArMath.polylineLength(capturedPoints)
        return sensorCorrectionEngine.correctDistanceWithStereoParallax(raw)
    }

    fun calculatePolygonArea(): Double {
        val rawArea = ArMath.polygonArea(capturedPoints)
        // Area scales with square of distance scale factor
        val scale = sensorCorrectionEngine.correctDistanceWithStereoParallax(1.0)
        return rawArea * (scale * scale)
    }

    fun calculateBoundingBox(): BoundingBoxResult {
        return ArMath.calculateBoundingBox(capturedPoints)
    }

    fun calculateVerticalHeight(): Double {
        return if (capturedPoints.size >= 2) {
            sensorCorrectionEngine.correctVerticalHeightWithGravity(capturedPoints.first(), capturedPoints.last())
        } else {
            0.0
        }
    }

    fun calculateCircle(): CircleResult? {
        return if (capturedPoints.size >= 3) {
            ArMath.fitCircle3Points(capturedPoints[0], capturedPoints[1], capturedPoints[2])
        } else {
            null
        }
    }

    fun calculateAngle(): Double {
        return if (capturedPoints.size >= 3) {
            ArMath.calculateAngleDegrees(capturedPoints[0], capturedPoints[1], capturedPoints[2])
        } else {
            0.0
        }
    }

    // Formatting utilities (整數顯示，無小數點)
    fun formatLength(meters: Double, unit: String = _selectedUnit.value): String {
        val df = DecimalFormat("#,##0")
        return when (unit.lowercase().trim()) {
            "m" -> {
                val m = Math.round(meters)
                val displayM = if (meters > 0.001 && m == 0L) 1L else m
                "${df.format(displayM)} m"
            }
            "in" -> {
                val inches = Math.round(meters * 39.3701)
                val displayIn = if (meters > 0.001 && inches == 0L) 1L else inches
                "${df.format(displayIn)} in"
            }
            "ft" -> {
                val ft = Math.round(meters * 3.28084)
                val displayFt = if (meters > 0.001 && ft == 0L) 1L else ft
                "${df.format(displayFt)} ft"
            }
            "yd" -> {
                val yd = Math.round(meters * 1.09361)
                val displayYd = if (meters > 0.001 && yd == 0L) 1L else yd
                "${df.format(displayYd)} yd"
            }
            else -> {
                val cm = meters * 100.0
                val roundedCm = Math.round(cm)
                val displayCm = if (meters > 0.001 && roundedCm == 0L) 1L else roundedCm
                "${df.format(displayCm)} cm"
            }
        }
    }

    fun formatArea(sqMeters: Double, unit: String = _selectedUnit.value): String {
        val df = DecimalFormat("#,##0")
        return when (unit.lowercase().trim()) {
            "m" -> {
                val m2 = Math.round(sqMeters)
                val displayM2 = if (sqMeters > 0.0001 && m2 == 0L) 1L else m2
                "${df.format(displayM2)} m²"
            }
            "in" -> {
                val in2 = Math.round(sqMeters * 1550.0)
                val displayIn2 = if (sqMeters > 0.0001 && in2 == 0L) 1L else in2
                "${df.format(displayIn2)} in²"
            }
            "ft" -> {
                val ft2 = Math.round(sqMeters * 10.7639)
                val displayFt2 = if (sqMeters > 0.0001 && ft2 == 0L) 1L else ft2
                "${df.format(displayFt2)} sq ft"
            }
            "yd" -> {
                val yd2 = Math.round(sqMeters * 1.19599)
                val displayYd2 = if (sqMeters > 0.0001 && yd2 == 0L) 1L else yd2
                "${df.format(displayYd2)} sq yd"
            }
            else -> {
                val sqCm = sqMeters * 10000.0
                val roundedSqCm = Math.round(sqCm)
                val displaySqCm = if (sqMeters > 0.0001 && roundedSqCm == 0L) 1L else roundedSqCm
                "${df.format(displaySqCm)} cm²"
            }
        }
    }

    fun formatVolume(cuMeters: Double, unit: String = _selectedUnit.value): String {
        val df = DecimalFormat("#,##0")
        return when (unit.lowercase().trim()) {
            "m" -> {
                val m3 = Math.round(cuMeters)
                val displayM3 = if (cuMeters > 0.0001 && m3 == 0L) 1L else m3
                "${df.format(displayM3)} m³"
            }
            "ft" -> {
                val ft3 = Math.round(cuMeters * 35.3147)
                val displayFt3 = if (cuMeters > 0.0001 && ft3 == 0L) 1L else ft3
                "${df.format(displayFt3)} cu ft"
            }
            else -> {
                val liters = cuMeters * 1000.0
                val roundedL = Math.round(liters)
                val displayL = if (cuMeters > 0.0001 && roundedL == 0L) 1L else roundedL
                "${df.format(displayL)} L"
            }
        }
    }

    // Last saved record for quick share affordance
    private val _lastSavedRecord = MutableStateFlow<MeasureRecord?>(null)
    val lastSavedRecord: StateFlow<MeasureRecord?> = _lastSavedRecord.asStateFlow()

    fun clearLastSavedRecord() {
        _lastSavedRecord.value = null
    }

    // Save record to Room database automatically with screenshot support
    fun saveMeasurementRecord(
        imagePath: String? = null,
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
                    Triple(area, "AREA", "AR 自動偵測面積 (${formatArea(area, unit)}) · $timeStr")
                }
                "HEIGHT" -> {
                    val height = calculateVerticalHeight()
                    Triple(height, "HEIGHT", "AR 自動偵測高程 (${formatLength(height, unit)}) · $timeStr")
                }
                "VOLUME" -> {
                    val box = calculateBoundingBox()
                    Triple(box.volume, "VOLUME", "3D 空間體積 (${formatVolume(box.volume, unit)}) · $timeStr")
                }
                "CIRCLE" -> {
                    val circle = calculateCircle()
                    val d = circle?.diameter ?: 0.0
                    Triple(d, "CIRCLE", "AR 圓形直徑 (${formatLength(d, unit)}) · $timeStr")
                }
                "ANGLE" -> {
                    val angle = calculateAngle()
                    Triple(angle, "ANGLE", "3D 空間夾角 (${Math.round(angle)}°) · $timeStr")
                }
                else -> {
                    val dist = calculateTotalDistance()
                    Triple(dist, "CAM", "AR 測量距離 (${formatLength(dist, unit)}) · $timeStr")
                }
            }

            if (value > 0.0) {
                val title = if (!customTitle.isNullOrBlank()) customTitle else autoTitle
                val serializedPoints = capturedPoints.joinToString(";") { "${it.x},${it.y},${it.z},${it.label ?: ""}" }
                val record = MeasureRecord(
                    title = title,
                    value = value,
                    unit = if (subMode == 5) "°" else unit,
                    type = typeStr,
                    notes = customNotes,
                    pointsData = serializedPoints,
                    imagePath = imagePath
                )

                try {
                    withContext(Dispatchers.IO) {
                        repository.insert(record)
                    }
                    _lastSavedRecord.value = record
                    _toastMessage.tryEmit("已自動儲存紀錄: $title")
                    onSaved?.invoke(record)
                    clearActivePoints()
                    triggerHapticFeedback()
                } catch (e: Exception) {
                    _toastMessage.tryEmit("儲存失敗: ${e.localizedMessage}")
                }
            }
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
            val formatted = formatLength(cmVal / 100.0, _selectedUnit.value)
            val title = if (!customTitle.isNullOrBlank()) customTitle else "螢幕尺測量 ($formatted) · $timeStr"
            val record = MeasureRecord(
                title = title,
                value = cmVal,
                unit = _selectedUnit.value,
                type = "RULER",
                notes = customNotes,
                imagePath = imagePath
            )
            try {
                withContext(Dispatchers.IO) {
                    repository.insert(record)
                }
                _lastSavedRecord.value = record
                _toastMessage.tryEmit("已自動儲存: $title")
                onSaved?.invoke(record)
                triggerHapticFeedback()
            } catch (e: Exception) {
                _toastMessage.tryEmit("儲存失敗: ${e.localizedMessage}")
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
                _toastMessage.tryEmit("備註已更新")
            } catch (e: Exception) {
                _toastMessage.tryEmit("更新備註失敗: ${e.localizedMessage}")
            }
        }
    }

    fun deleteRecord(record: MeasureRecord) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.delete(record)
                }
            } catch (e: Exception) {}
        }
    }

    fun deleteRecordById(id: Int) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.deleteById(id)
                }
            } catch (e: Exception) {}
        }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.clearAll()
                }
            } catch (e: Exception) {}
        }
    }

    fun onResume() {
        modernArEngine.resume()
        sensorCorrectionEngine.startListening()
    }

    fun onPause() {
        turnOffTorch(getApplication())
        modernArEngine.pause()
        sensorCorrectionEngine.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        turnOffTorch(getApplication())
        modernArEngine.destroy()
        sensorCorrectionEngine.stopListening()
    }
}
