package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val GoogleSansRounded = FontFamily(
    Font(R.font.google_sans_flex)
)

/**
 * Material 3 Expressive Dual Typography Tokens:
 * - Emphasized display & numeral styles using GoogleSansRounded with tabular figures (`tnum`)
 *   so live measurement readouts, angles, and coordinates never jitter horizontally.
 */
object ExpressiveTypography {
    val displayNumeralHero = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Black,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    )

    val displayNumeralMedium = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp,
        fontFeatureSettings = "tnum"
    )

    val titleNumeral = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )

    val labelNumeral = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
        fontFeatureSettings = "tnum"
    )

    val headlineEmphasized = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    )
}

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.15).sp
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.1).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.3.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.35.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
)
