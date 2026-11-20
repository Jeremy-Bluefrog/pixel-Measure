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

private fun splitUnescaped(input: String, delimiter: Char): List<String> {
    if (input.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    while (i < input.length) {
        val c = input[i]
        if (c == '\\' && i + 1 < input.length && (input[i + 1] == ',' || input[i + 1] == ';')) {
            current.append(c).append(input[i + 1])
            i += 2
        } else if (c == delimiter) {
            result.add(current.toString())
            current.clear()
            i++
        } else {
            current.append(c)
            i++
        }
    }
    result.add(current.toString())
    return result
}

private fun unescapeLabel(raw: String): String? {
    val unescaped = raw.replace("\\,", ",").replace("\\;", ";")
    return unescaped.ifEmpty { null }
}

fun Point3D.serialize(): String {
    val lEncoded = (label ?: "").replace(",", "\\,").replace(";", "\\;")
    return "$x,$y,$z,$pitch,$yaw,$isArPrecision,$lEncoded"
}

fun String.deserializePoint3D(): Point3D? {
    return try {
        val parts = splitUnescaped(this, ',')
        if (parts.size < 3) return null
        val x = parts[0].toDoubleOrNull() ?: return null
        val y = parts[1].toDoubleOrNull() ?: return null
        val z = parts[2].toDoubleOrNull() ?: return null
        // Support legacy 4-field format ("x,y,z,label") where field 3 is a non-numeric label
        if (parts.size == 4 && parts[3].toFloatOrNull() == null) {
            return Point3D(x, y, z, 0f, 0f, true, unescapeLabel(parts[3]), null)
        }
        val pitch = if (parts.size > 3) parts[3].toFloatOrNull() ?: 0f else 0f
        val yaw = if (parts.size > 4) parts[4].toFloatOrNull() ?: 0f else 0f
        val isAr = if (parts.size > 5) parts[5].toBoolean() else true
        val label = if (parts.size >= 7) unescapeLabel(parts[6]) else null
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
    return splitUnescaped(this, ';').mapNotNull { it.deserializePoint3D() }
}
