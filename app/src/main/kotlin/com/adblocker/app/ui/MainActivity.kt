package com.adblocker.app.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.adblocker.app.AdBlockerApp
import com.adblocker.app.ui.screens.DashboardScreen
import com.adblocker.app.ui.screens.FirewallScreen
import com.adblocker.app.ui.screens.LogsScreen
import com.adblocker.app.ui.screens.SettingsScreen
import com.adblocker.app.ui.theme.AdGuardRebornTheme
import com.adblocker.app.vpn.CoreVpnService
import com.adblocker.app.vpn.VpnStateHolder
import com.adblocker.app.vpn.VpnStatus

sealed class Screen(val title: String, val icon: ImageVector) {
    object Dashboard : Screen("仪表盘", Icons.Default.Dashboard)
    object Logs : Screen("实时日志", Icons.Default.ReceiptLong)
    object Firewall : Screen("应用防火墙", Icons.Default.Security)
    object Settings : Screen("设置", Icons.Default.Settings)

    companion object {
        val items = listOf(Dashboard, Logs, Firewall, Settings)
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AdBlockerApp

        setContent {
            AdGuardRebornTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppContent(
                        app = app,
                        onStartVpnRequested = { handleStartVpn() }
                    )
                }
            }
        }
    }

    private var onVpnPermissionGranted: (() -> Unit)? = null

    private fun handleStartVpn() {
        val currentStatus = VpnStateHolder.status.value
        if (currentStatus == VpnStatus.CONNECTED) {
            CoreVpnService.stopService(this)
            return
        }

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            onVpnPermissionGranted = { CoreVpnService.startService(this) }
            vpnLauncher.launch(vpnIntent)
        } else {
            CoreVpnService.startService(this)
        }
    }

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onVpnPermissionGranted?.invoke()
        } else {
            Toast.makeText(this, "需要授予 VPN 权限才能拦截广告域名", Toast.LENGTH_SHORT).show()
        }
        onVpnPermissionGranted = null
    }
}

@Composable
fun MainAppContent(
    app: AdBlockerApp,
    onStartVpnRequested: () -> Unit
) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.items.forEachIndexed { index, screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(modifier = Modifier.padding(innerPadding)) {
            when (selectedIndex) {
                0 -> DashboardScreen(
                    onToggleVpn = onStartVpnRequested,
                    onNavigateToSettings = { selectedIndex = 3 }
                )
                1 -> LogsScreen(
                    queryLogRepository = app.queryLogRepository,
                    filterListManager = app.filterListManager
                )
                2 -> FirewallScreen(
                    firewallManager = app.firewallManager
                )
                3 -> SettingsScreen(
                    settingsRepository = app.settingsRepository
                )
            }
        }
    }
}
