package com.menustudio.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.filament.Filament
import com.google.android.filament.utils.Utils
import com.menustudio.app.state.MenuStudioViewModel
import com.menustudio.app.ui.MenuStudioScreen
import com.menustudio.app.ui.theme.MenuStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        // Load Filament native libraries before Compose remembers stage plugins/samplers.
        Filament.init()
        Utils.init()

        setContent {
            val viewModel: MenuStudioViewModel = viewModel()
            MenuStudioTheme {
                MenuStudioScreen(stateHolder = viewModel.holder)
            }
        }
    }
}
