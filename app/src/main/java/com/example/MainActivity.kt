package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MeasureViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
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
