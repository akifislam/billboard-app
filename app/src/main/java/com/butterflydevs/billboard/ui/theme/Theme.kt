package com.butterflydevs.billboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightBillBoardColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandPrimaryDark,
    onPrimaryContainer = Color.White,
    secondary = BrandPrimaryDark,
    tertiary = BalanceOk,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceMuted,
    error = BalanceLow
)

// BillBoard always uses a fixed light theme, regardless of system light/dark setting.
@Composable
fun ElectricityBillCheckTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightBillBoardColorScheme,
        typography = Typography,
        content = content
    )
}
