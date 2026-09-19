package com.example.ui.viewmodel

import com.google.ar.core.Anchor
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Modern 3D Point model representing spatial coordinates in meters with optional AR anchor.
 */
@Serializable
data class Point3D(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val z: Double = 0.0,
    val pitch: Float = 0.0f,
    val yaw: Float = 0.0f,
    val isArPrecision: Boolean = true,
    val label: String? = null,
    @Transient val anchor: Anchor? = null
)

fun Point3D.serialize(): String {
    val lEncoded = (label ?: "").replace(",", "\\,").replace(";", "\\;")
    return "$x,$y,$z,$pitch,$yaw,$isArPrecision,$lEncoded"
}

fun String.deserializePoint3D(): Point3D? {
    return try {
        val parts = this.split(",")
        if (parts.size < 3) return null
        val x = parts[0].toDoubleOrNull() ?: 0.0
        val y = parts[1].toDoubleOrNull() ?: 0.0
        val z = parts[2].toDoubleOrNull() ?: 0.0
        val pitch = if (parts.size > 3) parts[3].toFloatOrNull() ?: 0f else 0f
        val yaw = if (parts.size > 4) parts[4].toFloatOrNull() ?: 0f else 0f
        val isAr = if (parts.size > 5) parts[5].toBoolean() else true
        val label = if (parts.size >= 7) parts[6].replace("\\,", ",").replace("\\;", ";") else null
        Point3D(x, y, z, pitch, yaw, isAr, label, null)
    } catch (e: Exception) {
        null
    }
}

fun List<Point3D>.serializePoints(): String {
    return this.joinToString(";") { it.serialize() }
}

fun String.deserializePoints(): List<Point3D> {
    if (this.isBlank()) return emptyList()
    return this.split(";").mapNotNull { it.deserializePoint3D() }
}
