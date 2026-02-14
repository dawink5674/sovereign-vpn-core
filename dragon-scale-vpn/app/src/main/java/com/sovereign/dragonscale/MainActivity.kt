package com.sovereign.dragonscale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sovereign.dragonscale.ui.screens.VpnDashboardScreen
import com.sovereign.dragonscale.ui.theme.DragonScaleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DragonScaleTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VpnDashboardScreen()
                }
            }
        }
    }
}
