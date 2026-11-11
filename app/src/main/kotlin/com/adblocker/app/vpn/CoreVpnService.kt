package com.adblocker.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.adblocker.app.AdBlockerApp
import com.adblocker.app.R
import com.adblocker.app.ui.MainActivity
import com.adblocker.core.resolver.UpstreamConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

class CoreVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.adblocker.app.action.START"
        const val ACTION_STOP = "com.adblocker.app.action.STOP"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "adblocker_vpn_channel"

        fun startService(context: Context) {
            val intent = Intent(context, CoreVpnService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CoreVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var tunLoop: TunPacketLoop? = null
    private var loopThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startVpn()
                return START_STICKY
            }
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (VpnStateHolder.status.value == VpnStatus.CONNECTED) return
        VpnStateHolder.setStatus(VpnStatus.CONNECTING)

        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch(Dispatchers.IO) {
            try {
                val app = application as AdBlockerApp
                val settings = app.settingsRepository
                val blockingMode = settings.blockingMode.first()
                val upstreamId = settings.selectedUpstreamId.first()
                val customUrl = settings.customUpstreamUrl.first()

                val config = UpstreamConfig.PRESETS.find { it.id == upstreamId } ?: UpstreamConfig.DEFAULT
                val resolver = if (customUrl.isNotBlank()) {
                    UpstreamConfig(
                        id = "custom",
                        name = "Custom DoH",
                        description = customUrl,
                        protocol = com.adblocker.core.resolver.DnsProtocol.DOH,
                        hostOrUrl = customUrl
                    ).createResolver()
                } else {
                    config.createResolver()
                }
                VpnStateHolder.setUpstreamName(resolver.name)

                val builder = Builder()
                    .setSession(getString(R.string.app_name))
                    .setMtu(1500)
                    .addAddress("10.0.0.2", 32)
                    .addDnsServer("10.0.0.2")
                    .addRoute("10.0.0.2", 32) // DNS Split-Tunnel
                    .setBlocking(true)

                // Bypass excluded applications
                val bypassedApps = app.firewallManager.getBypassedApps()
                for (pkg in bypassedApps) {
                    try {
                        builder.addDisallowedApplication(pkg)
                    } catch (e: Exception) {
                        // Package might be uninstalled
                    }
                }

                // Disallow self so our DoH/DoT upstream connections bypass VPN!
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {}

                vpnInterface = builder.establish()
                if (vpnInterface == null) {
                    VpnStateHolder.setStatus(VpnStatus.DISCONNECTED)
                    stopSelf()
                    return@launch
                }

                tunLoop = TunPacketLoop(
                    tunFd = vpnInterface!!.fileDescriptor,
                    filterManager = app.filterListManager,
                    firewallManager = app.firewallManager,
                    queryLogRepository = app.queryLogRepository,
                    dnsResolver = resolver,
                    blockingMode = blockingMode,
                    scope = serviceScope
                )

                VpnStateHolder.setStatus(VpnStatus.CONNECTED)

                // Start observing stats to update foreground notification
                launch {
                    VpnStateHolder.statistics.collect {
                        updateNotification()
                    }
                }

                loopThread = Thread {
                    tunLoop?.runLoop()
                }.apply {
                    name = "TunPacketLoopThread"
                    start()
                }
            } catch (e: Exception) {
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        VpnStateHolder.setStatus(VpnStatus.DISCONNECTING)
        serviceScope.cancel()
        serviceScope = CoroutineScope(Dispatchers.Default + Job())

        try {
            vpnInterface?.close()
        } catch (e: IOException) {}
        vpnInterface = null

        VpnStateHolder.setStatus(VpnStatus.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, CoreVpnService::class.java).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stats = VpnStateHolder.statistics.value
        val content = "已拦截 ${stats.blockedQueries} 个广告/追踪 | 总计 ${stats.totalQueries} 次解析"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_tile)
            .setContentTitle("AdGuard 防护已开启 (${stats.currentUpstreamName})")
            .setContentText(content)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(0, "暂停防护", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
