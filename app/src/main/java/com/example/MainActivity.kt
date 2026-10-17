package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MeasureViewModel
import java.util.function.Consumer

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isWide = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                this.display?.isWideColorGamut == true
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay?.isWideColorGamut == true
            }
            if (isWide) {
                window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
            }
        }
        enforceHighDisplayRefreshRate()
        super.onCreate(savedInstanceState)

        // Initialize the measuring tool viewmodel
        viewModel = ViewModelProvider(this)[MeasureViewModel::class.java]
        
        setContent {
            val dynamicColor by viewModel.dynamicColorEnabled.collectAsState()
            MyApplicationTheme(dynamicColor = dynamicColor) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    private lateinit var viewModel: MeasureViewModel

    /**
     * Prevents system SurfaceFlinger / DisplayManager from locking the screen
     * to 30Hz when Camera HAL starts. Explicitly requests 60Hz or higher display mode.
     */
    fun enforceHighDisplayRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val currentDisplay = this.display
                val modes = currentDisplay?.supportedModes ?: emptyArray()
                val targetMode = modes.filter { it.refreshRate >= 59.5f }.maxByOrNull { it.refreshRate }
                    ?: modes.maxByOrNull { it.refreshRate }

                targetMode?.let { mode ->
                    val lp = window.attributes
                    lp.preferredDisplayModeId = mode.modeId
                    lp.preferredRefreshRate = mode.refreshRate.coerceAtLeast(60.0f)
                    window.attributes = lp
                    android.util.Log.i("MainActivity", "Enforced high refresh rate: ${mode.refreshRate}Hz (modeId=${mode.modeId})")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val currentDisplay = windowManager.defaultDisplay
                val modes = currentDisplay?.supportedModes ?: emptyArray()
                val targetMode = modes.filter { it.refreshRate >= 59.5f }.maxByOrNull { it.refreshRate }
                    ?: modes.maxByOrNull { it.refreshRate }

                targetMode?.let { mode ->
                    val lp = window.attributes
                    lp.preferredDisplayModeId = mode.modeId
                    window.attributes = lp
                    android.util.Log.i("MainActivity", "Enforced high display mode: ${mode.modeId} (${mode.refreshRate}Hz)")
                }
            }
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Unable to enforce high display refresh rate: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        enforceHighDisplayRefreshRate()
        if (::viewModel.isInitialized) {
            viewModel.syncWithSystemLocale()
            viewModel.onResume()
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
            viewModel.onPause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
