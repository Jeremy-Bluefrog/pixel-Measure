package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraManager
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.MeasureDatabase
import com.example.data.model.MeasureRecord
import com.example.data.repository.MeasureRepository
import com.example.logic.TranslationManager
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

    // Torch / Flashlight state
    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

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

    val highFpsModeEnabled = MutableStateFlow(prefs.getBoolean("high_fps_mode_enabled", true))

    fun setHighFpsModeEnabled(enabled: Boolean) {
        highFpsModeEnabled.value = enabled
        prefs.edit().putBoolean("high_fps_mode_enabled", enabled).apply()
        _toastMessage.tryEmit(if (enabled) "已啟用 60Hz 高幀率相機預覽" else "已切換至標準幀率")
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

            // 1. Apply Multi-Sensor Fusion Anti-Tremor & Jitter Filter (Gyroscope + Accelerometer)
            val sensorCorrectedPoint = sensorCorrectionEngine.correctPointWithSensorFusion(
                rawPoint = contactPoint,
                previousPoint = _liveTargetPoint.value
            )
            val smoothedPoint = ArMath.filterJitterEMA(_liveTargetPoint.value, sensorCorrectedPoint)

            // 2. Gravity-aligned orthogonal leveling if measuring line/distance
            val orthogonalAdjustedPoint = if (capturedPoints.isNotEmpty() && _cameraSubMode.value == 0) {
                sensorCorrectionEngine.correctOrthogonalAlignment(capturedPoints.last(), smoothedPoint)
            } else {
                smoothedPoint
            }

            // 3. Magnetic Vertex Snapping check
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

            // 4. Calculate live real-time distance with stability & Dual-Camera Stereo Parallax scale calibration
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

        // 5. If a tap was requested, commit the point with sensor correction
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
            val livePt = _liveTargetPoint.value
            val baseP = if (livePt != null && ArMath.distance(livePt, contactP) < 0.08) {
                livePt.copy(anchor = hitResult.anchor ?: livePt.anchor)
            } else {
                contactP
            }
            val correctedP = if (capturedPoints.isNotEmpty() && _cameraSubMode.value == 0) {
                sensorCorrectionEngine.correctOrthogonalAlignment(capturedPoints.last(), baseP)
            } else {
                baseP
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                saveUndoState()
                capturedPoints.add(correctedP)
                triggerHapticFeedback()
                updateAutoDetectedGeometry()
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
        triggerHapticFeedback()
    }

    fun updatePointLabel(index: Int, newLabel: String) {
        if (index in capturedPoints.indices) {
            capturedPoints[index] = capturedPoints[index].copy(label = newLabel)
        }
    }

    // Toggle Torch
    fun toggleTorch(context: Context) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            val newState = !_isTorchOn.value
            cameraManager.setTorchMode(cameraId, newState)
            _isTorchOn.value = newState
            triggerHapticFeedback()
        } catch (e: Exception) {
            _isTorchOn.value = false
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
        return when (unit.lowercase()) {
            "m" -> "${df.format(Math.round(meters))} m"
            "in" -> "${df.format(Math.round(meters * 39.3701))} in"
            "ft" -> "${df.format(Math.round(meters * 3.28084))} ft"
            "yd" -> "${df.format(Math.round(meters * 1.09361))} yd"
            else -> {
                val cm = meters * 100.0
                "${df.format(Math.round(cm))} cm"
            }
        }
    }

    fun formatArea(sqMeters: Double, unit: String = _selectedUnit.value): String {
        val df = DecimalFormat("#,##0")
        return when (unit.lowercase()) {
            "m" -> "${df.format(Math.round(sqMeters))} m²"
            "in" -> "${df.format(Math.round(sqMeters * 1550.0))} in²"
            "ft" -> "${df.format(Math.round(sqMeters * 10.7639))} sq ft"
            "yd" -> "${df.format(Math.round(sqMeters * 1.19599))} sq yd"
            else -> {
                val sqCm = sqMeters * 10000.0
                if (sqCm >= 10000.0) "${df.format(Math.round(sqMeters))} m²" else "${df.format(Math.round(sqCm))} cm²"
            }
        }
    }

    fun formatVolume(cuMeters: Double, unit: String = _selectedUnit.value): String {
        val df = DecimalFormat("#,##0")
        return when (unit.lowercase()) {
            "m" -> "${df.format(Math.round(cuMeters))} m³"
            "ft" -> "${df.format(Math.round(cuMeters * 35.3147))} cu ft"
            else -> {
                val liters = cuMeters * 1000.0
                "${df.format(Math.round(liters))} L"
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
        modernArEngine.pause()
        sensorCorrectionEngine.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        modernArEngine.destroy()
        sensorCorrectionEngine.stopListening()
    }
}
