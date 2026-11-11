package com.adblocker.core

import com.adblocker.core.packet.IpPacket
import com.adblocker.core.packet.UdpPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.ByteBuffer

class IpUdpPacketTest {

    private fun createSyntheticIpUdpDnsPacket(): ByteArray {
        val totalLen = 20 + 8 + 4
        val buf = ByteBuffer.allocate(totalLen)

        // IP Header (20 bytes)
        buf.put(0x45.toByte()) // IPv4 + IHL 5
        buf.put(0.toByte()) // TOS
        buf.putShort(totalLen.toShort()) // Total len
        buf.putShort(0x1000.toShort()) // ID
        buf.putShort(0x4000.toShort()) // Flags: DF
        buf.put(64.toByte()) // TTL
        buf.put(IpPacket.PROTOCOL_UDP.toByte()) // Protocol 17
        buf.putShort(0) // Checksum placeholder
        buf.put(byteArrayOf(10, 0, 0, 2)) // Src IP (Client)
        buf.put(byteArrayOf(8, 8, 8, 8)) // Dst IP (DNS Server)

        // UDP Header (8 bytes)
        buf.putShort(54321.toShort()) // Src Port
        buf.putShort(53.toShort()) // Dst Port
        buf.putShort(12.toShort()) // UDP Length: 8 header + 4 payload
        buf.putShort(0) // Checksum

        // Payload (4 bytes)
        buf.put(byteArrayOf(1, 2, 3, 4))

        return buf.array()
    }

    @Test
    fun testParseAndInvertIpPacket() {
        val raw = createSyntheticIpUdpDnsPacket()
        val ip = IpPacket.parse(raw)
        assertNotNull(ip)
        assertEquals(4, ip!!.version)
        assertEquals(IpPacket.PROTOCOL_UDP, ip.protocol)
        assertEquals("10.0.0.2", ip.sourceIpString())
        assertEquals("8.8.8.8", ip.destIpString())

        val udp = UdpPacket.parse(ip.payload)
        assertNotNull(udp)
        assertEquals(54321, udp!!.sourcePort)
        assertEquals(53, udp.destinationPort)

        // Build Response
        val responseUdpPayload = UdpPacket.buildResponse(udp, byteArrayOf(9, 9, 9, 9))
        val responseUdp = UdpPacket.parse(responseUdpPayload)
        assertNotNull(responseUdp)
        assertEquals(53, responseUdp!!.sourcePort)
        assertEquals(54321, responseUdp.destinationPort)

        val responseIpBytes = IpPacket.buildResponsePacket(ip, responseUdpPayload)
        val responseIp = IpPacket.parse(responseIpBytes)
        assertNotNull(responseIp)
        assertEquals("8.8.8.8", responseIp!!.sourceIpString())
        assertEquals("10.0.0.2", responseIp.destIpString())
    }
}
