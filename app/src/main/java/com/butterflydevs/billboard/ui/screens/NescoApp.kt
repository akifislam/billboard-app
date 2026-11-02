package com.butterflydevs.billboard.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.butterflydevs.billboard.ui.components.NoInternetOverlay
import com.butterflydevs.billboard.ui.components.rememberIsOnline
import com.butterflydevs.billboard.ui.theme.StatusBarIcons
import com.butterflydevs.billboard.viewmodel.DashboardViewModel
import com.butterflydevs.billboard.viewmodel.hasData
import kotlinx.coroutines.delay

private const val SPLASH_DURATION_MS = 500L

private sealed class Screen {
    object Dashboard : Screen()
    data class MeterDetails(val meterNo: String) : Screen()
}

@Composable
fun NescoApp(viewModel: DashboardViewModel = viewModel()) {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        showSplash = false
    }

    // Returning from a long stint in the background can leave stale data (or a stale error) on
    // screen until the next auto-refresh cycle fires, up to AUTO_REFRESH_MS later. Refresh
    // immediately every time the app comes back to the foreground instead of waiting on it.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The splash is white and needs dark status bar icons; every other screen sits under the
    // brand-coloured app bar and needs light ones.
    StatusBarIcons(dark = showSplash)

    if (showSplash) {
        SplashScreen()
        return
    }

    var screen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    BackHandler(enabled = screen != Screen.Dashboard) { screen = Screen.Dashboard }

    val isOnline by rememberIsOnline()
    // Going offline no longer hides everything: the dashboard keeps showing each meter's last
    // saved balance (flagged as stale) and only falls back to the full-screen notice when there
    // is nothing cached to show — a first run with no connection.
    val hasCachedData = viewModel.uiState.meters.any { it.hasData() }
    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = screen) {
            is Screen.Dashboard -> DashboardScreen(
                viewModel = viewModel,
                isOffline = !isOnline,
                onOpenDetails = { meterNo -> screen = Screen.MeterDetails(meterNo) }
            )
            is Screen.MeterDetails -> {
                val meter = viewModel.uiState.meters.find { it.meter == current.meterNo }
                if (meter == null) {
                    screen = Screen.Dashboard
                } else {
                    MeterDetailsScreen(meter = meter, onBack = { screen = Screen.Dashboard })
                }
            }
        }
        if (!isOnline && !hasCachedData) {
            NoInternetOverlay()
        }
    }
}
