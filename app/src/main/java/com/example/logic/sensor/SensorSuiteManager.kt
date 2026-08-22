package com.example.logic.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Detailed information about an installed hardware sensor on the device.
 */
data class DeviceSensorInfo(
    val name: String,
    val vendor: String,
    val type: Int,
    val typeName: String,
    val power: Float,
    val maximumRange: Float,
    val resolution: Float,
    val isPresent: Boolean
)

/**
 * Comprehensive real-time sensor measurements container.
 */
data class SensorSuiteState(
    // Compass & Orientation
    val azimuthDegrees: Float = 0f,
    val cardinalDirection: String = "N",
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val isLevel: Boolean = false,

    // Magnetometer (Microtesla μT)
    val magX: Float = 0f,
    val magY: Float = 0f,
    val magZ: Float = 0f,
    val magTotal: Float = 0f,
    val isMagnetometerAvailable: Boolean = false,

    // Accelerometer & G-Force (m/s² and g)
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val totalGForce: Float = 1.0f,
    val isAccelerometerAvailable: Boolean = false,

    // Gyroscope (rad/s)
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f,
    val isGyroscopeAvailable: Boolean = false,

    // Barometer & Altitude (hPa & meters)
    val pressureHpa: Float = 1013.25f,
    val altitudeMeters: Float = 0f,
    val isBarometerAvailable: Boolean = false,

    // Ambient Light (Lux)
    val lightLux: Float = 0f,
    val lightCondition: String = "正常",
    val isLightSensorAvailable: Boolean = false,

    // Proximity (cm)
    val proximityCm: Float = 5f,
    val isNear: Boolean = false,
    val isProximityAvailable: Boolean = false,

    // Calibration offsets
    val pitchOffset: Float = 0f,
    val rollOffset: Float = 0f
)

/**
 * Manages device hardware sensors (Rotation Vector, Magnetometer, Accelerometer,
 * Gyroscope, Barometer, Light Sensor, Proximity) with low-latency sampling.
 */
class SensorSuiteManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val gyroSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val pressureSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val lightSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val _sensorState = MutableStateFlow(
        SensorSuiteState(
            isMagnetometerAvailable = magSensor != null,
            isAccelerometerAvailable = accelSensor != null,
            isGyroscopeAvailable = gyroSensor != null,
            isBarometerAvailable = pressureSensor != null,
            isLightSensorAvailable = lightSensor != null,
            isProximityAvailable = proximitySensor != null
        )
    )
    val sensorState: StateFlow<SensorSuiteState> = _sensorState.asStateFlow()

    private val _installedSensors = MutableStateFlow<List<DeviceSensorInfo>>(emptyList())
    val installedSensors: StateFlow<List<DeviceSensorInfo>> = _installedSensors.asStateFlow()

    // Temporary arrays for matrix calculations
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val lastAccel = FloatArray(3)
    private val lastMag = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false

    private var pitchOffset = 0f
    private var rollOffset = 0f

    init {
        queryInstalledSensors()
    }

    private fun queryInstalledSensors() {
        val sm = sensorManager ?: return
        val allSensors = sm.getSensorList(Sensor.TYPE_ALL)
        val list = allSensors.map { s ->
            DeviceSensorInfo(
                name = s.name,
                vendor = s.vendor,
                type = s.type,
                typeName = s.stringType.substringAfterLast("."),
                power = s.power,
                maximumRange = s.maximumRange,
                resolution = s.resolution,
                isPresent = true
            )
        }.sortedBy { it.name }
        _installedSensors.value = list
    }

    fun startListening() {
        val sm = sensorManager ?: return
        rotationSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        pressureSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        lightSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        proximitySensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    fun calibrateLevelZero() {
        val current = _sensorState.value
        pitchOffset = current.pitchDegrees + pitchOffset
        rollOffset = current.rollDegrees + rollOffset
        _sensorState.value = current.copy(
            pitchOffset = pitchOffset,
            rollOffset = rollOffset
        )
    }

    fun resetLevelCalibration() {
        pitchOffset = 0f
        rollOffset = 0f
        _sensorState.value = _sensorState.value.copy(
            pitchOffset = 0f,
            rollOffset = 0f
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val current = _sensorState.value

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)

                // Azimuth: orientationAngles[0] in radians (-pi to pi) -> 0 to 360
                var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                if (azimuth < 0) azimuth += 360f

                // Pitch: orientationAngles[1] in radians (-pi/2 to pi/2) -> degrees
                val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat() - pitchOffset

                // Roll: orientationAngles[2] in radians (-pi to pi) -> degrees
                val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat() - rollOffset

                val isLevel = abs(pitch) < 0.75f && abs(roll) < 0.75f

                _sensorState.value = current.copy(
                    azimuthDegrees = azimuth,
                    cardinalDirection = calculateCardinal(azimuth),
                    pitchDegrees = pitch,
                    rollDegrees = roll,
                    isLevel = isLevel
                )
            }

            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, lastAccel, 0, 3)
                hasAccel = true

                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val totalAccel = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
                val gForce = totalAccel / 9.80665f

                // If rotation vector is missing, fallback to orientation calculation
                if (rotationSensor == null && hasMag) {
                    computeFallbackOrientation()
                }

                _sensorState.value = _sensorState.value.copy(
                    accelX = ax,
                    accelY = ay,
                    accelZ = az,
                    totalGForce = gForce,
                    isAccelerometerAvailable = true
                )
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, lastMag, 0, 3)
                hasMag = true

                val mx = event.values[0]
                val my = event.values[1]
                val mz = event.values[2]
                val totalMag = sqrt((mx * mx + my * my + mz * mz).toDouble()).toFloat()

                if (rotationSensor == null && hasAccel) {
                    computeFallbackOrientation()
                }

                _sensorState.value = _sensorState.value.copy(
                    magX = mx,
                    magY = my,
                    magZ = mz,
                    magTotal = totalMag,
                    isMagnetometerAvailable = true
                )
            }

            Sensor.TYPE_GYROSCOPE -> {
                _sensorState.value = current.copy(
                    gyroX = event.values[0],
                    gyroY = event.values[1],
                    gyroZ = event.values[2],
                    isGyroscopeAvailable = true
                )
            }

            Sensor.TYPE_PRESSURE -> {
                val pressure = event.values[0]
                // Hypsometric formula for standard atmosphere altitude:
                // h = 44330 * (1 - (p / 1013.25)^(1/5.255))
                val altitude = 44330.0f * (1.0f - (pressure / 1013.25f).pow(1.0f / 5.255f))

                _sensorState.value = current.copy(
                    pressureHpa = pressure,
                    altitudeMeters = altitude,
                    isBarometerAvailable = true
                )
            }

            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                val condition = when {
                    lux < 5f -> "極暗 / 夜間"
                    lux < 50f -> "微光 / 暗室"
                    lux < 300f -> "一般室內 / 客廳"
                    lux < 1000f -> "辦公室 / 明亮照明"
                    lux < 10000f -> "陰天戶外"
                    lux < 30000f -> "晴天戶外"
                    else -> "強烈陽光直射"
                }

                _sensorState.value = current.copy(
                    lightLux = lux,
                    lightCondition = condition,
                    isLightSensorAvailable = true
                )
            }

            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = event.sensor.maximumRange
                val isNear = distance < maxRange.coerceAtMost(5.0f)

                _sensorState.value = current.copy(
                    proximityCm = distance,
                    isNear = isNear,
                    isProximityAvailable = true
                )
            }
        }
    }

    private fun computeFallbackOrientation() {
        val success = SensorManager.getRotationMatrix(rotationMatrix, null, lastAccel, lastMag)
        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            if (azimuth < 0) azimuth += 360f
            val pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat() - pitchOffset
            val roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat() - rollOffset
            val isLevel = abs(pitch) < 0.75f && abs(roll) < 0.75f

            _sensorState.value = _sensorState.value.copy(
                azimuthDegrees = azimuth,
                cardinalDirection = calculateCardinal(azimuth),
                pitchDegrees = pitch,
                rollDegrees = roll,
                isLevel = isLevel
            )
        }
    }

    private fun calculateCardinal(degrees: Float): String {
        val deg = (degrees % 360 + 360) % 360
        return when (((deg + 22.5f) / 45f).toInt() % 8) {
            0 -> "北 (N)"
            1 -> "東北 (NE)"
            2 -> "東 (E)"
            3 -> "東南 (SE)"
            4 -> "南 (S)"
            5 -> "西南 (SW)"
            6 -> "西 (W)"
            7 -> "西北 (NW)"
            else -> "北 (N)"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
