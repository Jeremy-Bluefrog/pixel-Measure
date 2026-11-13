package com.example.logic

import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Shared measurement formatting utilities with adaptive decimal precision across metric and imperial units.
 */
object MeasureFormatter {

    fun formatLength(meters: Double, unit: String): String {
        return when (unit.lowercase(Locale.US).trim()) {
            "m" -> String.format(Locale.US, "%.2f m", meters)
            "in" -> {
                val inches = meters * 39.3701
                if (inches >= 100.0) {
                    String.format(Locale.US, "%.1f in", inches)
                } else {
                    String.format(Locale.US, "%.2f in", inches)
                }
            }
            "ft" -> String.format(Locale.US, "%.2f ft", meters * 3.28084)
            "yd" -> String.format(Locale.US, "%.2f yd", meters * 1.09361)
            "mm" -> String.format(Locale.US, "%.0f mm", meters * 1000.0)
            else -> {
                val cm = meters * 100.0
                if (abs(cm - round(cm)) < 0.05) {
                    String.format(Locale.US, "%.0f cm", cm)
                } else {
                    String.format(Locale.US, "%.1f cm", cm)
                }
            }
        }
    }

    fun formatArea(sqMeters: Double, unit: String): String {
        return when (unit.lowercase(Locale.US).trim()) {
            "m" -> String.format(Locale.US, "%.2f m²", sqMeters)
            "in" -> String.format(Locale.US, "%.1f in²", sqMeters * 1550.0)
            "ft" -> String.format(Locale.US, "%.2f sq ft", sqMeters * 10.7639)
            "yd" -> String.format(Locale.US, "%.2f sq yd", sqMeters * 1.19599)
            else -> {
                val sqCm = sqMeters * 10000.0
                if (sqCm >= 100.0) {
                    String.format(Locale.US, "%.0f cm²", sqCm)
                } else {
                    String.format(Locale.US, "%.1f cm²", sqCm)
                }
            }
        }
    }

    fun formatVolume(cuMeters: Double, unit: String): String {
        return when (unit.lowercase(Locale.US).trim()) {
            "m" -> String.format(Locale.US, "%.3f m³", cuMeters)
            "ft" -> String.format(Locale.US, "%.2f cu ft", cuMeters * 35.3147)
            else -> {
                val liters = cuMeters * 1000.0
                if (liters >= 10.0) {
                    String.format(Locale.US, "%.1f L", liters)
                } else {
                    String.format(Locale.US, "%.2f L", liters)
                }
            }
        }
    }
}
