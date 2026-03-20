package com.sovereign.shield

import android.app.Activity
import android.net.VpnService
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.sovereign.shield.ui.navigation.SovereignBottomNav
import com.sovereign.shield.ui.navigation.SovereignNavGraph
import com.sovereign.shield.ui.theme.*
import com.sovereign.shield.vpn.VpnSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

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
                var isAuthenticated by remember { mutableStateOf(false) }
                var biometricRequired by remember { mutableStateOf(false) }
                var biometricFailed by remember { mutableStateOf(false) }

                // Check if biometric lock is enabled
                LaunchedEffect(Unit) {
                    val settings = VpnSettings(this@MainActivity)
                    val lockEnabled = settings.biometricLock.first()
                    if (lockEnabled) {
                        val biometricManager = BiometricManager.from(this@MainActivity)
                        val canAuth = biometricManager.canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                            biometricRequired = true
                            showBiometricPrompt(
                                onSuccess = {
                                    isAuthenticated = true
                                    biometricFailed = false
                                },
                                onFail = { biometricFailed = true }
                            )
                        } else {
                            // Device doesn't support biometric, skip
                            isAuthenticated = true
                        }
                    } else {
                        isAuthenticated = true
                    }
                }

                if (!isAuthenticated) {
                    // Biometric lock screen
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SpaceBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("\uD83D\uDEE1\uFE0F", fontSize = 64.sp)
                            Text(
                                "SOVEREIGN SHIELD",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    letterSpacing = 4.sp, fontWeight = FontWeight.Bold
                                ),
                                color = ShieldBlue
                            )
                            Text(
                                if (biometricFailed) "Authentication failed. Tap to retry."
                                else "Authenticate to continue",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (biometricFailed) StatusDisconnected else TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            if (biometricFailed) {
                                Button(
                                    onClick = {
                                        biometricFailed = false
                                        showBiometricPrompt(
                                            onSuccess = {
                                                isAuthenticated = true
                                                biometricFailed = false
                                            },
                                            onFail = { biometricFailed = true }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ShieldBlue
                                    )
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                } else {
                    // Main app content
                    val navController = rememberNavController()

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = SpaceBlack,
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

    private fun showBiometricPrompt(onSuccess: () -> Unit, onFail: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onFail()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Individual attempt failed, prompt stays open
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Sovereign Shield VPN")
            .setSubtitle("Authenticate to access your VPN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
