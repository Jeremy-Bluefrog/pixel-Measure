package com.example.logic.ar

/**
 * AR Plane & Spatial Tracking Stability Level.
 */
enum class StabilityLevel {
    HIGH,      // >= 75%: 極佳 (優質特徵與穩定平面，零漂移)
    MODERATE,  // 50% - 74%: 良好 (可正常測量，輕度微動)
    LOW,       // 25% - 49%: 特徵偏少 / 慢速平移警告 (有漂移風險)
    POOR       // < 25%: 極低 / 未穩定追蹤 (漂移風險高，建議暫緩打點)
}

/**
 * Real-time AR tracking stability and confidence telemetry metrics.
 * Continuously evaluates spatial feature point density, camera panning speed,
 * plane polygon boundaries, and environmental illumination to actively prevent measurement drift.
 */
data class ArTrackingStability(
    val confidenceScore: Float = 0.85f, // 0.0f .. 1.0f (0% .. 100%)
    val level: StabilityLevel = StabilityLevel.HIGH,
    val featurePointsCount: Int = 0,
    val trackingPlanesCount: Int = 0,
    val cameraSpeedMps: Float = 0.0f,
    val lightIntensity: Float = 1.0f,
    val isMotionExcessive: Boolean = false,
    val isFeatureDeficient: Boolean = false,
    val isLightingDeficient: Boolean = false,
    val isDriftRisk: Boolean = false,
    val warningMessage: String? = null
) {
    companion object {
        fun default() = ArTrackingStability(
            confidenceScore = 0.85f,
            level = StabilityLevel.HIGH,
            featurePointsCount = 80,
            trackingPlanesCount = 1,
            cameraSpeedMps = 0.05f,
            lightIntensity = 1.0f,
            isMotionExcessive = false,
            isFeatureDeficient = false,
            isLightingDeficient = false,
            isDriftRisk = false,
            warningMessage = null
        )
    }
}
