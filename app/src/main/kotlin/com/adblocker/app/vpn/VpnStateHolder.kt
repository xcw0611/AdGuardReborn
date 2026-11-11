package com.adblocker.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

data class VpnStatistics(
    val totalQueries: Long = 0,
    val blockedQueries: Long = 0,
    val activeRulesCount: Int = 0,
    val currentUpstreamName: String = "AliDNS (DoH)"
)

object VpnStateHolder {
    private val _status = MutableStateFlow(VpnStatus.DISCONNECTED)
    val status: StateFlow<VpnStatus> = _status.asStateFlow()

    private val _statistics = MutableStateFlow(VpnStatistics())
    val statistics: StateFlow<VpnStatistics> = _statistics.asStateFlow()

    private val totalCount = AtomicLong(0)
    private val blockedCount = AtomicLong(0)

    fun setStatus(newStatus: VpnStatus) {
        _status.value = newStatus
        if (newStatus == VpnStatus.DISCONNECTED) {
            // Reset counters or preserve session stats
        }
    }

    fun updateRulesCount(count: Int) {
        _statistics.value = _statistics.value.copy(activeRulesCount = count)
    }

    fun setUpstreamName(name: String) {
        _statistics.value = _statistics.value.copy(currentUpstreamName = name)
    }

    fun recordQuery(isBlocked: Boolean) {
        val total = totalCount.incrementAndGet()
        val blocked = if (isBlocked) blockedCount.incrementAndGet() else blockedCount.get()
        _statistics.value = _statistics.value.copy(
            totalQueries = total,
            blockedQueries = blocked
        )
    }

    fun resetStats() {
        totalCount.set(0)
        blockedCount.set(0)
        _statistics.value = _statistics.value.copy(totalQueries = 0, blockedQueries = 0)
    }
}
