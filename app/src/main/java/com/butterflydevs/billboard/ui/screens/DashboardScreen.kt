package com.butterflydevs.billboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.butterflydevs.billboard.ui.components.AboutDrawerContent
import com.butterflydevs.billboard.ui.components.AdBanner
import com.butterflydevs.billboard.ui.components.AddMeterDialog
import com.butterflydevs.billboard.ui.components.MarqueeNotice
import com.butterflydevs.billboard.ui.components.MeterCard
import com.butterflydevs.billboard.ui.components.OfflineNotice
import com.butterflydevs.billboard.ui.components.openExternalLink
import com.butterflydevs.billboard.viewmodel.DashboardViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel(),
    isOffline: Boolean = false,
    onOpenDetails: (String) -> Unit
) {
    val uiState = viewModel.uiState
    var showAddDialog by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val remoteConfig = viewModel.remoteConfig

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AboutDrawerContent()
            }
        }
    ) {
        Scaffold(
            topBar = {
                // The notice sits inside topBar rather than in the list so it stays pinned under
                // the app bar and never scrolls away with the meter cards.
                Column {
                    TopAppBar(
                        title = { Text("BillBoard", style = MaterialTheme.typography.titleMedium) },
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "About the app")
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.refresh(forceRemoteConfig = true) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    MarqueeNotice(
                        config = remoteConfig?.marquee,
                        onClickUrl = { url -> context.openExternalLink(url) }
                    )
                    if (isOffline) {
                        OfflineNotice()
                    }
                }
            },
            bottomBar = {
                AdBanner(
                    config = remoteConfig?.advertisement,
                    imageLoader = viewModel.adImageLoader,
                    onClickUrl = { url -> context.openExternalLink(url) }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    addError = null
                    showAddDialog = true
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add meter")
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    uiState.isInitialLoading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.meters.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "No meters yet. Tap + to add your first meter.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(uiState.meters, key = { it.meter }) { meter ->
                                MeterCard(
                                    meter = meter,
                                    onRemove = { viewModel.removeMeter(meter.meter) },
                                    onOpenDetails = { onOpenDetails(meter.meter) }
                                )
                            }
                            if (uiState.statusText.isNotBlank()) {
                                item {
                                    Text(
                                        uiState.statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMeterDialog(
            isSubmitting = isAdding,
            serverError = addError,
            onDismiss = { if (!isAdding) showAddDialog = false },
            onConfirm = { meterNo, nickname, provider, meterType ->
                isAdding = true
                addError = null
                viewModel.addMeter(
                    meterNo,
                    nickname,
                    provider,
                    meterType,
                    onSuccess = {
                        isAdding = false
                        showAddDialog = false
                    },
                    onError = { message ->
                        isAdding = false
                        addError = message
                    }
                )
            }
        )
    }
}
