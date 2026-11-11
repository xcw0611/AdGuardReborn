package com.adblocker.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adblocker.app.AdBlockerApp
import com.adblocker.app.vpn.CoreVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val app = context.applicationContext as AdBlockerApp
            CoroutineScope(Dispatchers.IO).launch {
                val autoStart = app.settingsRepository.autoStartOnBoot.first()
                if (autoStart) {
                    CoreVpnService.startService(context)
                }
            }
        }
    }
}
