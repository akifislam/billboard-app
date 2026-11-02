package com.butterflydevs.billboard

import android.app.Application
import com.butterflydevs.billboard.notifications.LowBalanceWorker
import com.butterflydevs.billboard.notifications.NotificationHelper

class NescoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        LowBalanceWorker.schedule(this)
    }
}
