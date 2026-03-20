package com.sovereign.shield

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.sovereign.shield.ui.navigation.SovereignBottomNav
import com.sovereign.shield.ui.navigation.SovereignNavGraph
import com.sovereign.shield.ui.theme.SovereignShieldTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var onVpnPermissionResult: ((Boolean) -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        onVpnPermissionResult?.invoke(result.resultCode == Activity.RESULT_OK)
        onVpnPermissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SovereignShieldTheme {
                val navController = rememberNavController()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = com.sovereign.shield.ui.theme.SpaceBlack,
                    bottomBar = {
                        SovereignBottomNav(navController = navController)
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        SovereignNavGraph(
                            navController = navController,
                            onRequestVpnPermission = { intent, callback ->
                                onVpnPermissionResult = callback
                                vpnPermissionLauncher.launch(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}
