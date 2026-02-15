package com.sovereign.dragonscale

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sovereign.dragonscale.ui.screens.VpnDashboardScreen
import com.sovereign.dragonscale.ui.theme.DragonScaleTheme

class MainActivity : ComponentActivity() {

    /**
     * Callback that will be invoked once the user grants/denies VPN permission.
     * Set by the composable before launching the permission intent.
     */
    private var onVpnPermissionResult: ((Boolean) -> Unit)? = null

    /**
     * ActivityResultLauncher for the VPN consent dialog.
     * Must be registered at creation time (before onStart).
     */
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
            DragonScaleTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VpnDashboardScreen(
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
