package com.butterflydevs.billboard.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.butterflydevs.billboard.R

/**
 * Tracks live internet reachability (not just "some network interface is up") via
 * ConnectivityManager's callback API, so the banner reacts immediately to the user toggling
 * Wi-Fi/data instead of waiting for the next failed request. Requires NET_CAPABILITY_VALIDATED
 * (Android's own probe-confirmed "this network can actually reach the internet"), not just
 * NET_CAPABILITY_INTERNET — a network can declare the latter while still being unable to resolve
 * DNS or reach anything (dead router, captive portal, ISP outage), which would otherwise leave
 * the app reporting "online" while every request fails.
 */
@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val isOnline = remember { mutableStateOf(isCurrentlyOnline(context)) }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService<ConnectivityManager>()
        if (connectivityManager == null) {
            return@DisposableEffect onDispose {}
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // A network can become "available" before Android finishes validating it can
                // actually reach the internet; onCapabilitiesChanged fires again once that
                // validation completes, so re-derive from current state rather than assuming.
                isOnline.value = isCurrentlyOnline(context)
            }

            override fun onLost(network: Network) {
                isOnline.value = isCurrentlyOnline(context)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                isOnline.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        onDispose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    return isOnline
}

private fun isCurrentlyOnline(context: Context): Boolean {
    val connectivityManager = context.getSystemService<ConnectivityManager>() ?: return true
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Fully blocks the app behind a dedicated screen while offline — this app is useless without a
 * network connection (every meter is fetched live), so a thin banner the user could ignore isn't
 * enough. The click-catcher with a null indication swallows all touches so nothing behind it is
 * reachable until connectivity returns, at which point [rememberIsOnline] flips back and this
 * composable is simply not shown.
 */
@Composable
fun NoInternetOverlay(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "No Internet Connection",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Please turn on the internet to continue using BillBoard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Image(
            painter = painterResource(id = R.drawable.billboard_app),
            contentDescription = "BillBoard",
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    }
}
