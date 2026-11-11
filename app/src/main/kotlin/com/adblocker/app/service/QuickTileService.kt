package com.adblocker.app.service

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.adblocker.app.vpn.CoreVpnService
import com.adblocker.app.vpn.VpnStateHolder
import com.adblocker.app.vpn.VpnStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class QuickTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.launch {
            VpnStateHolder.status.collect { status ->
                updateTileState(status)
            }
        }
    }

    override fun onStopListening() {
        serviceScope.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val currentStatus = VpnStateHolder.status.value
        if (currentStatus == VpnStatus.CONNECTED) {
            CoreVpnService.stopService(this)
        } else if (currentStatus == VpnStatus.DISCONNECTED) {
            CoreVpnService.startService(this)
        }
    }

    private fun updateTileState(status: VpnStatus) {
        val tile = qsTile ?: return
        when (status) {
            VpnStatus.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "AdGuard 防护中"
            }
            VpnStatus.CONNECTING -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "连接中..."
            }
            VpnStatus.DISCONNECTING -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "断开中..."
            }
            VpnStatus.DISCONNECTED -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "AdGuard 已关闭"
            }
        }
        tile.updateTile()
    }
}
