package com.adblocker.app.vpn

import com.adblocker.app.data.repository.BlockingMode
import com.adblocker.app.data.repository.QueryLogRepository
import com.adblocker.app.firewall.AppFirewallManager
import com.adblocker.core.filter.FilterListManager
import com.adblocker.core.filter.FilterResult
import com.adblocker.core.model.QueryLog
import com.adblocker.core.packet.DnsMessageCodec
import com.adblocker.core.packet.IpPacket
import com.adblocker.core.packet.UdpPacket
import com.adblocker.core.resolver.DnsResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * High-throughput asynchronous packet loop reading from and writing to TUN file descriptor.
 */
class TunPacketLoop(
    private val tunFd: FileDescriptor,
    private val filterManager: FilterListManager,
    private val firewallManager: AppFirewallManager,
    private val queryLogRepository: QueryLogRepository,
    private var dnsResolver: DnsResolver,
    private var blockingMode: BlockingMode = BlockingMode.NXDOMAIN,
    private val scope: CoroutineScope
) {
    private val writeLock = Any()

    fun updateResolver(resolver: DnsResolver) {
        this.dnsResolver = resolver
        VpnStateHolder.setUpstreamName(resolver.name)
    }

    fun updateBlockingMode(mode: BlockingMode) {
        this.blockingMode = mode
    }

    fun runLoop() {
        val inputStream = FileInputStream(tunFd)
        val outputStream = FileOutputStream(tunFd)
        val packetBuffer = ByteArray(32768)

        while (scope.isActive) {
            try {
                val bytesRead = inputStream.read(packetBuffer)
                if (bytesRead <= 0) break

                val ipPacket = IpPacket.parse(packetBuffer, 0, bytesRead) ?: continue
                if (ipPacket.protocol != IpPacket.PROTOCOL_UDP) continue

                val udpPacket = UdpPacket.parse(ipPacket.payload) ?: continue
                if (udpPacket.destinationPort != 53) continue

                val dnsMessage = DnsMessageCodec.parse(udpPacket.payload) ?: continue
                val question = dnsMessage.questions.firstOrNull() ?: continue
                val domain = question.name

                // 1. Check Filter Rules
                val filterResult = filterManager.checkDomain(domain)

                if (filterResult.isBlocked) {
                    handleBlockedDns(ipPacket, udpPacket, dnsMessage, domain, filterResult as FilterResult.Blocked, outputStream)
                } else {
                    handleAllowedDns(ipPacket, udpPacket, dnsMessage, domain, filterResult, outputStream)
                }
            } catch (e: Exception) {
                if (!scope.isActive) break
            }
        }
    }

    private fun handleBlockedDns(
        ipPacket: IpPacket,
        udpPacket: UdpPacket,
        dnsMessage: DnsMessageCodec.DnsMessage,
        domain: String,
        blockResult: FilterResult.Blocked,
        outputStream: FileOutputStream
    ) {
        val responseDnsBytes = when (blockingMode) {
            BlockingMode.NXDOMAIN -> DnsMessageCodec.buildNxDomainResponse(dnsMessage)
            BlockingMode.NULL_IP -> DnsMessageCodec.buildNullAddressResponse(dnsMessage)
        }

        val responseUdp = UdpPacket.buildResponse(udpPacket, responseDnsBytes)
        val responseIp = IpPacket.buildResponsePacket(ipPacket, responseUdp)

        synchronized(writeLock) {
            try {
                outputStream.write(responseIp)
                outputStream.flush()
            } catch (e: Exception) {
                // Ignore write socket errors on shutdown
            }
        }

        VpnStateHolder.recordQuery(isBlocked = true)
        queryLogRepository.emitEvent(
            QueryLog(
                domain = domain,
                queryType = typeToString(dnsMessage.questions.firstOrNull()?.qType ?: 1),
                isBlocked = true,
                matchedRule = blockResult.matchedRule,
                upstreamName = "Blocked (${blockResult.matchedRule})",
                latencyMs = 0
            )
        )
    }

    private fun handleAllowedDns(
        ipPacket: IpPacket,
        udpPacket: UdpPacket,
        dnsMessage: DnsMessageCodec.DnsMessage,
        domain: String,
        filterResult: FilterResult,
        outputStream: FileOutputStream
    ) {
        scope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val resolver = dnsResolver
                val responseDnsBytes = resolver.resolve(dnsMessage.rawBytes, domain, dnsMessage.questions.firstOrNull()?.qType ?: 1)
                val latency = System.currentTimeMillis() - startTime

                val responseUdp = UdpPacket.buildResponse(udpPacket, responseDnsBytes)
                val responseIp = IpPacket.buildResponsePacket(ipPacket, responseUdp)

                synchronized(writeLock) {
                    try {
                        outputStream.write(responseIp)
                        outputStream.flush()
                    } catch (e: Exception) {
                        // socket closed
                    }
                }

                VpnStateHolder.recordQuery(isBlocked = false)
                queryLogRepository.emitEvent(
                    QueryLog(
                        domain = domain,
                        queryType = typeToString(dnsMessage.questions.firstOrNull()?.qType ?: 1),
                        isBlocked = false,
                        matchedRule = if (filterResult is FilterResult.Whitelisted) filterResult.matchedRule else null,
                        upstreamName = resolver.name,
                        latencyMs = latency
                    )
                )
            } catch (e: Exception) {
                // Upstream resolution failure: fallback to SERVFAIL response
                val failDns = DnsMessageCodec.buildNxDomainResponse(dnsMessage)
                val failUdp = UdpPacket.buildResponse(udpPacket, failDns)
                val failIp = IpPacket.buildResponsePacket(ipPacket, failUdp)
                synchronized(writeLock) {
                    try {
                        outputStream.write(failIp)
                    } catch (ex: Exception) {}
                }
            }
        }
    }

    private fun typeToString(type: Int): String = when (type) {
        DnsMessageCodec.TYPE_A -> "A"
        DnsMessageCodec.TYPE_AAAA -> "AAAA"
        DnsMessageCodec.TYPE_HTTPS -> "HTTPS"
        DnsMessageCodec.TYPE_CNAME -> "CNAME"
        DnsMessageCodec.TYPE_TXT -> "TXT"
        else -> "TYPE_$type"
    }
}
