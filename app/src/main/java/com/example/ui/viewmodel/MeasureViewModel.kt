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
import com.example.logic.ar.*
import com.example.logic.sensor.DeviceSensorInfo
import com.example.logic.sensor.SensorSuiteManager
import com.example.logic.sensor.SensorSuiteState
import com.google.ar.core.*
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
    // 0 = Distance/Polyline, 1 = Area, 2 = Height, 3 = 3D Box Volume, 4 = Circle/Diameter, 5 = Angle
    private val _cameraSubMode = MutableStateFlow(0)
    val cameraSubMode: StateFlow<Int> = _cameraSubMode.asStateFlow()

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

    // Hardware Sensors Suite Manager & State
    val sensorSuiteManager = SensorSuiteManager(application)
    val sensorState: StateFlow<SensorSuiteState> = sensorSuiteManager.sensorState
    val installedSensors: StateFlow<List<DeviceSensorInfo>> = sensorSuiteManager.installedSensors

    fun calibrateSensorLevel() {
        sensorSuiteManager.calibrateLevelZero()
        triggerHapticFeedback()
        _toastMessage.tryEmit("已將當前角度設為零度水平基準")
    }

    fun resetSensorLevelCalibration() {
        sensorSuiteManager.resetLevelCalibration()
        _toastMessage.tryEmit("已重設水平儀為出廠絕對基準")
    }

    fun saveSensorRecord(title: String, value: Double, unit: String, type: String = "SENSOR") {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.insert(
                        MeasureRecord(
                            title = title,
                            value = value,
                            unit = unit,
                            type = type
                        )
                    )
                }
                _toastMessage.tryEmit("已儲存感應器數據: $title")
                triggerHapticFeedback()
            } catch (e: Exception) {
                _toastMessage.tryEmit("儲存失敗: ${e.localizedMessage}")
            }
        }
    }

    init {
        sensorSuiteManager.startListening()
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
    fun processGlFrame(frame: Frame, engine: ModernArEngine) {
        // 1. Process any pending tap hit-test
        val tapRequest = pendingHitTestQueue.getAndSet(null)
        val hitX = tapRequest?.first ?: (displayWidth / 2f)
        val hitY = tapRequest?.second ?: (displayHeight / 2f)

        val hitResult = engine.performHitTest(frame, hitX, hitY)

        // 2. Real-time live targeting preview at screen center
        val centerHit = if (tapRequest == null) hitResult else engine.performHitTest(frame, displayWidth / 2f, displayHeight / 2f)

        if (centerHit != null) {
            val pose = centerHit.pose
            val rawPoint = Point3D(
                x = pose.tx().toDouble(),
                y = pose.ty().toDouble(),
                z = pose.tz().toDouble(),
                isArPrecision = true
            )

            // 1. Apply Temporal EMA Jitter Filter
            val smoothedPoint = ArMath.filterJitterEMA(_liveTargetPoint.value, rawPoint)

            // 2. Magnetic Vertex Snapping check
            val snappedVertex = ArMath.findVertexSnap(smoothedPoint, capturedPoints, 0.07)
            val finalTargetPoint: Point3D

            if (snappedVertex != null) {
                finalTargetPoint = snappedVertex
                if (!_isSnapped.value) {
                    _isSnapped.value = true
                    triggerHapticFeedback()
                }
            } else {
                _isSnapped.value = false
                finalTargetPoint = smoothedPoint
            }

            _liveTargetPoint.value = finalTargetPoint

            // 3. Calculate live real-time distance with stability
            if (capturedPoints.isNotEmpty()) {
                val lastPoint = capturedPoints.last()
                val dist = ArMath.distance(lastPoint, finalTargetPoint)
                _liveDistanceMeters.value = dist
            } else {
                _liveDistanceMeters.value = centerHit.distance.toDouble()
            }
        }

        // 3. If a tap was requested, commit the point
        if (tapRequest != null && hitResult != null) {
            val pose = hitResult.pose
            val point = Point3D(
                x = pose.tx().toDouble(),
                y = pose.ty().toDouble(),
                z = pose.tz().toDouble(),
                isArPrecision = true,
                anchor = hitResult.anchor
            )
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                saveUndoState()
                capturedPoints.add(point)
                triggerHapticFeedback()
            }
        }
    }

    fun onArFrameUpdated(data: ModernArFrame) {
        _arTrackingState.value = data.trackingState
        _trackingFailureReason.value = data.trackingFailureReason
        _viewMatrix.value = data.viewMatrix
        _projectionMatrix.value = data.projectionMatrix
        _isDepthAvailable.value = data.isDepthAvailable
        _detectedPlanes.value = data.planes
        _arPlanesCount.value = data.planes.count { it.isTracking }
        _surfaceTypeAtCenter.value = data.surfaceTypeAtCenter
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

    fun requestHitTest(pixelX: Float? = null, pixelY: Float? = null) {
        val x = pixelX ?: (displayWidth / 2f)
        val y = pixelY ?: (displayHeight / 2f)
        pendingHitTestQueue.set(Pair(x, y))
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
        } else if (capturedPoints.isNotEmpty()) {
            capturedPoints.removeAt(capturedPoints.size - 1)
            triggerHapticFeedback()
        }
    }

    fun clearActivePoints() {
        saveUndoState()
        capturedPoints.forEach { it.anchor?.detach() }
        capturedPoints.clear()
        _liveDistanceMeters.value = null
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
        return ArMath.polylineLength(capturedPoints)
    }

    fun calculatePolygonArea(): Double {
        return ArMath.polygonArea(capturedPoints)
    }

    fun calculateBoundingBox(): BoundingBoxResult {
        return ArMath.calculateBoundingBox(capturedPoints)
    }

    fun calculateVerticalHeight(): Double {
        return if (capturedPoints.size >= 2) {
            ArMath.verticalHeight(capturedPoints.first(), capturedPoints.last())
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

    // Formatting utilities
    fun formatLength(meters: Double, unit: String = _selectedUnit.value): String {
        val df = DecimalFormat("#,##0.00")
        return when (unit.lowercase()) {
            "m" -> "${df.format(meters)} m"
            "in" -> "${df.format(meters * 39.3701)} in"
            "ft" -> "${df.format(meters * 3.28084)} ft"
            "yd" -> "${df.format(meters * 1.09361)} yd"
            else -> {
                val cm = meters * 100.0
                "${df.format(cm)} cm"
            }
        }
    }

    fun formatArea(sqMeters: Double, unit: String = _selectedUnit.value): String {
        val df = DecimalFormat("#,##0.00")
        return when (unit.lowercase()) {
            "m" -> "${df.format(sqMeters)} m²"
            "in" -> "${df.format(sqMeters * 1550.0)} in²"
            "ft" -> "${df.format(sqMeters * 10.7639)} sq ft"
            "yd" -> "${df.format(sqMeters * 1.19599)} sq yd"
            else -> {
                val sqCm = sqMeters * 10000.0
                if (sqCm >= 10000.0) "${df.format(sqMeters)} m²" else "${df.format(sqCm)} cm²"
            }
        }
    }

    fun formatVolume(cuMeters: Double, unit: String = _selectedUnit.value): String {
        val df = DecimalFormat("#,##0.00")
        return when (unit.lowercase()) {
            "m" -> "${df.format(cuMeters)} m³"
            "ft" -> "${df.format(cuMeters * 35.3147)} cu ft"
            else -> {
                val liters = cuMeters * 1000.0
                "${df.format(liters)} L (${df.format(cuMeters)} m³)"
            }
        }
    }

    // Save record to Room database automatically without tedious manual typing
    fun saveMeasurementRecord(customTitle: String? = null, customNotes: String? = null) {
        viewModelScope.launch {
            val subMode = _cameraSubMode.value
            val unit = _selectedUnit.value
            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

            val (value, typeStr, autoTitle) = when (subMode) {
                1 -> {
                    val area = calculatePolygonArea()
                    Triple(area, "AREA", "AR 平面面積 (${formatArea(area, unit)}) · $timeStr")
                }
                2 -> {
                    val height = calculateVerticalHeight()
                    Triple(height, "HEIGHT", "AR 垂直高程 (${formatLength(height, unit)}) · $timeStr")
                }
                3 -> {
                    val box = calculateBoundingBox()
                    Triple(box.volume, "VOLUME", "3D 空間體積 (${formatVolume(box.volume, unit)}) · $timeStr")
                }
                4 -> {
                    val circle = calculateCircle()
                    val d = circle?.diameter ?: 0.0
                    Triple(d, "CIRCLE", "AR 圓形直徑 (${formatLength(d, unit)}) · $timeStr")
                }
                5 -> {
                    val angle = calculateAngle()
                    Triple(angle, "ANGLE", "3D 空間夾角 (${DecimalFormat("0.0").format(angle)}°) · $timeStr")
                }
                else -> {
                    val dist = calculateTotalDistance()
                    Triple(dist, "CAM", "AR 空間距離 (${formatLength(dist, unit)}) · $timeStr")
                }
            }

            if (value > 0.0) {
                val title = if (!customTitle.isNullOrBlank()) customTitle else autoTitle
                val serializedPoints = capturedPoints.joinToString(";") { "${it.x},${it.y},${it.z},${it.label ?: ""}" }

                try {
                    withContext(Dispatchers.IO) {
                        repository.insert(
                            MeasureRecord(
                                title = title,
                                value = value,
                                unit = if (subMode == 5) "°" else unit,
                                type = typeStr,
                                notes = customNotes,
                                pointsData = serializedPoints
                            )
                        )
                    }
                    _toastMessage.tryEmit("已自動儲存紀錄: $title")
                    clearActivePoints()
                    triggerHapticFeedback()
                } catch (e: Exception) {
                    _toastMessage.tryEmit("儲存失敗: ${e.localizedMessage}")
                }
            }
        }
    }

    fun saveRulerRecord(customTitle: String? = null, cmVal: Double) {
        viewModelScope.launch {
            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val formatted = formatLength(cmVal / 100.0, _selectedUnit.value)
            val title = if (!customTitle.isNullOrBlank()) customTitle else "螢幕尺測量 ($formatted) · $timeStr"
            try {
                withContext(Dispatchers.IO) {
                    repository.insert(
                        MeasureRecord(
                            title = title,
                            value = cmVal,
                            unit = _selectedUnit.value,
                            type = "RULER"
                        )
                    )
                }
                _toastMessage.tryEmit("已自動儲存: $title")
                triggerHapticFeedback()
            } catch (e: Exception) {
                _toastMessage.tryEmit("儲存失敗: ${e.localizedMessage}")
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
        sensorSuiteManager.startListening()
    }

    fun onPause() {
        modernArEngine.pause()
        sensorSuiteManager.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        modernArEngine.destroy()
        sensorSuiteManager.stopListening()
    }
}
