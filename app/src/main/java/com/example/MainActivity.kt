package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MeasureViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Step 3: Unlock Window display refresh rate (60Hz / 120Hz) on Android R (API 30+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val params = window.attributes
            // Request high refresh rate to prevent Window/TextureView 30Hz display clamp
            val displayModes = display?.supportedModes
            val has120Hz = displayModes?.any { it.refreshRate >= 119f } == true
            params.preferredRefreshRate = if (has120Hz) 120f else 60f
            window.attributes = params
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
            viewModel.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::viewModel.isInitialized) {
            viewModel.onPause()
        }
    }
}
