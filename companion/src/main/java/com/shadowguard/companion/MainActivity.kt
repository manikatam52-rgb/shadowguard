package com.shadowguard.companion

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
import com.shadowguard.companion.ui.components.ServerConfigDialog
import com.shadowguard.companion.ui.screens.DashboardScreen
import com.shadowguard.companion.ui.screens.PairingScreen
import com.shadowguard.companion.ui.theme.CompanionTheme
import com.shadowguard.companion.viewmodel.CompanionViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CompanionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsState()
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

                    if (!uiState.isPaired) {
                        PairingScreen(
                            viewModel = viewModel,
                            onOpenServerConfig = { showServerConfigDialog = true }
                        )
                    } else {
                        DashboardScreen(
                            viewModel = viewModel,
                            onOpenServerConfig = { showServerConfigDialog = true }
                        )
                    }
                }
            }
        }
    }
}
