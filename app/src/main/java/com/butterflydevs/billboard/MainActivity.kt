package com.butterflydevs.billboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import com.butterflydevs.billboard.ui.screens.NescoApp
import com.butterflydevs.billboard.ui.theme.ElectricityBillCheckTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // BillBoard is a fixed light theme, so both bars are pinned rather than left to the
        // default, which mirrors the system dark-mode setting. The app bar paints the status bar
        // area in the brand colour and carries light icons; the navigation bar sits over light
        // content and keeps dark ones. Screens override the status bar icons via StatusBarIcons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        maybeRequestNotificationPermission()
        setContent {
            ElectricityBillCheckTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NescoApp()
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
