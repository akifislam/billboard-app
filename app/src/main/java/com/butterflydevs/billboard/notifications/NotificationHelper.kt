package com.butterflydevs.billboard.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.butterflydevs.billboard.R

const val LOW_BALANCE_THRESHOLD = 100.0
private const val CHANNEL_ID = "low_balance"
private const val PREFS_NAME = "low_balance_state"

object NotificationHelper {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Low balance alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies you when a meter's balance drops below ৳$LOW_BALANCE_THRESHOLD"
        }
        manager.createNotificationChannel(channel)
    }

    /** Fires once per drop below the threshold; resets once the balance recovers, so it won't repeat every refresh cycle. */
    fun checkAndNotify(context: Context, meterNo: String, nickname: String, balance: Double?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "notified_$meterNo"
        if (balance == null) return
        if (balance < LOW_BALANCE_THRESHOLD) {
            if (!prefs.getBoolean(key, false)) {
                notify(context, meterNo, nickname)
                prefs.edit().putBoolean(key, true).apply()
            }
        } else {
            prefs.edit().putBoolean(key, false).apply()
        }
    }

    private fun notify(context: Context, meterNo: String, nickname: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Android renders a small icon as an alpha-only silhouette, so this is the BillBoard "B"
        // mark rather than the full logo, and setColor supplies the brand green the system tints
        // it with. The complete green icon rides along as the large icon, so the notification
        // carries the same mark as the launcher instead of the old unrelated bar-chart glyph.
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_billboard)
            .setColor(ContextCompat.getColor(context, R.color.billboard_brand))
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.billboard_app))
            .setContentTitle("Low balance")
            .setContentText("$nickname [$meterNo] has low balance. Please recharge.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(meterNo.hashCode(), notification)
    }
}
