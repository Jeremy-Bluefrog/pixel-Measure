package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MeasureViewModel
import java.util.function.Consumer

class MainActivity : ComponentActivity() {
    private var crossWindowBlurListener: Consumer<Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Step 3: Unlock Window display refresh rate (60Hz / 120Hz) on Android R (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val params = window.attributes
            // Request high refresh rate to prevent Window/TextureView 30Hz display clamp
            val displayModes = display?.supportedModes
            val has120Hz = displayModes?.any { it.refreshRate >= 119f } == true
            params.preferredRefreshRate = if (has120Hz) 120f else 60f
            window.attributes = params
        }

        // Hardware-Accelerated Window Blur (Android 12 / API 31+ up to Android 17+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply {
                    blurBehindRadius = 40
                }
                val listener = Consumer<Boolean> { isBlurEnabled ->
                    if (isBlurEnabled) {
                        window.attributes = window.attributes.apply {
                            blurBehindRadius = 40
                        }
                    }
                }
                crossWindowBlurListener = listener
                windowManager.addCrossWindowBlurEnabledListener(listener)
            } catch (e: Throwable) {
                android.util.Log.w("MainActivity", "Window blur initialization error: ${e.message}")
            }
        }
        
        // Initialize the measuring tool viewmodel
        viewModel = ViewModelProvider(this)[MeasureViewModel::class.java]
        
        setContent {
            MyApplicationTheme(dynamicColor = true) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    private lateinit var viewModel: MeasureViewModel

    override fun onResume() {
        super.onResume()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && crossWindowBlurListener != null) {
            try {
                windowManager.removeCrossWindowBlurEnabledListener(crossWindowBlurListener!!)
            } catch (e: Throwable) {
                // Ignore if already unregistered
            }
        }
    }
}
