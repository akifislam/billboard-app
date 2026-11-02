package com.butterflydevs.billboard.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Keeps the status bar icons readable against whatever the app draws behind them.
 *
 * Edge-to-edge lets the app bar paint the status bar area, but the icon colour was left to
 * `enableEdgeToEdge`'s default, which follows the *system* light/dark setting. On a phone in dark
 * mode that drew the clock, Wi-Fi and battery icons white on top of BillBoard's always-light app
 * bar, where they blended into it. Setting the appearance from the screen that is actually on
 * screen decouples the two: [dark] icons over the white splash, light icons over the brand-
 * coloured app bar.
 */
@Composable
fun StatusBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = dark
    }
}
