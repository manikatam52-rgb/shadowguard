package com.shadowguard.controller

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.shadowguard.controller.ui.components.ServerConfigDialog
import com.shadowguard.controller.ui.screens.*
import com.shadowguard.controller.ui.theme.ShadowGuardTheme
import com.shadowguard.controller.viewmodel.ControllerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ControllerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ShadowGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
                    val navController = rememberNavController()
                    var showServerConfigDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(uiState.errorMessage) {
                        uiState.errorMessage?.let { msg ->
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                            viewModel.clearMessages()
                        }
                    }

                    LaunchedEffect(uiState.successMessage) {
                        uiState.successMessage?.let { msg ->
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                            viewModel.clearMessages()
                        }
                    }

                    if (showServerConfigDialog) {
                        ServerConfigDialog(
                            currentUrl = uiState.serverUrl,
                            onSaveUrl = { url -> viewModel.updateServerUrl(url) },
                            onDismiss = { showServerConfigDialog = false }
                        )
                    }

                    if (!uiState.isLoggedIn) {
                        AuthScreen(
                            viewModel = viewModel,
                            onOpenServerConfig = { showServerConfigDialog = true }
                        )
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = "dashboard"
                        ) {
                            composable("dashboard") {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onDeviceSelected = { deviceId ->
                                        navController.navigate("device_detail/$deviceId")
                                    },
                                    onNavigateToPairing = {
                                        navController.navigate("pair_device")
                                    },
                                    onNavigateToAuditLogs = {
                                        navController.navigate("audit_logs")
                                    },
                                    onOpenServerConfig = { showServerConfigDialog = true }
                                )
                            }

                            composable(
                                route = "device_detail/{deviceId}",
                                arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
                                DeviceDetailScreen(
                                    deviceId = deviceId,
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("pair_device") {
                                PairDeviceScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable("audit_logs") {
                                AuditLogsScreen(
                                    viewModel = viewModel,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
