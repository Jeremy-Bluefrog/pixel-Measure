package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MeasureViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        val isWide = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.display?.isWideColorGamut == true
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay?.isWideColorGamut == true
        }
        if (isWide) {
            window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
        }
        enforceHighDisplayRefreshRate()
        super.onCreate(savedInstanceState)

        // Initialize the measuring tool viewmodel
        viewModel = ViewModelProvider(this)[MeasureViewModel::class.java]
        viewModel.warmUpArCoreAvailability()
        viewModel.checkAndInitArCore(this)
        
        setContent {
            val dynamicColor by viewModel.dynamicColorEnabled.collectAsStateWithLifecycle()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> systemDark
            }
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                )
            }
            MyApplicationTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    private lateinit var viewModel: MeasureViewModel

    /**
     * Aligns Pixel LTPO Smooth Display refresh rate with the active AR pipeline (60Hz target)
     * instead of forcing 120Hz Choreographer wakeups or dropping to 30Hz Camera HAL lock.
     */
    fun enforceHighDisplayRefreshRate(targetHz: Float = 120.0f) {
        try {
            val desired = targetHz.coerceIn(30.0f, 120.0f)
            val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                this.display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val activeMode = currentDisplay?.mode
            val modes = currentDisplay?.supportedModes ?: emptyArray()
            val sameResModes = if (activeMode != null) {
                modes.filter {
                    it.physicalWidth == activeMode.physicalWidth &&
                        it.physicalHeight == activeMode.physicalHeight
                }.ifEmpty { modes.toList() }
            } else {
                modes.toList()
            }
            val targetMode = sameResModes
                .filter { it.refreshRate >= desired - 0.5f }
                .minByOrNull { kotlin.math.abs(it.refreshRate - desired) }
                ?: sameResModes.maxByOrNull { it.refreshRate }

            targetMode?.let { mode ->
                val resolvedHz = mode.refreshRate.coerceIn(desired, 120.0f)
                val lp = window.attributes
                if (lp.preferredDisplayModeId != mode.modeId ||
                    kotlin.math.abs(lp.preferredRefreshRate - resolvedHz) > 0.5f
                ) {
                    lp.preferredDisplayModeId = mode.modeId
                    lp.preferredRefreshRate = resolvedHz
                    window.attributes = lp
                    android.util.Log.i("MainActivity", "Aligned display refresh rate: ${mode.refreshRate}Hz (modeId=${mode.modeId})")
                }
            }
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Unable to align display refresh rate: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            val targetFps = viewModel.effectiveTargetFps.value.toFloat()
            enforceHighDisplayRefreshRate(targetFps)
            viewModel.pixelPerformanceMonitor.applySustainedPerformanceMode(window, true)
            viewModel.syncWithSystemLocale()
            viewModel.onResume()
            if (viewModel.arSession == null) {
                viewModel.checkAndInitArCore(this, forceRetry = true)
            }
        } else {
            enforceHighDisplayRefreshRate()
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::viewModel.isInitialized) {
            viewModel.syncWithSystemLocale()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::viewModel.isInitialized) {
            viewModel.pixelPerformanceMonitor.applySustainedPerformanceMode(window, false)
            viewModel.onPause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
