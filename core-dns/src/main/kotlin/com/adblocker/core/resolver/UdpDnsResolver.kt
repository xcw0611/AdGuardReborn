package com.adblocker.core.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Standard Plain UDP DNS Resolver (Port 53).
 */
class UdpDnsResolver(
    val serverHost: String = "223.5.5.5",
    val serverPort: Int = 53,
    override val name: String = "Standard UDP ($serverHost)"
) : DnsResolver {

    override val protocolName: String = "UDP"

    override suspend fun resolve(queryBytes: ByteArray, domain: String, qType: Int): ByteArray = withContext(Dispatchers.IO) {
        DatagramSocket().use { socket ->
            socket.soTimeout = 4000
            val targetAddress = InetAddress.getByName(serverHost)
            val sendPacket = DatagramPacket(queryBytes, queryBytes.size, targetAddress, serverPort)
            socket.send(sendPacket)

            val receiveBuf = ByteArray(4096)
            val receivePacket = DatagramPacket(receiveBuf, receiveBuf.size)
            socket.receive(receivePacket)

            val response = ByteArray(receivePacket.length)
            System.arraycopy(receivePacket.data, receivePacket.offset, response, 0, receivePacket.length)
            response
        }
    }
}
