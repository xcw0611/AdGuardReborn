package com.adblocker.core.packet

import java.nio.ByteBuffer

/**
 * Lightweight IPv4 Packet representation and manipulation.
 */
data class IpPacket(
    val version: Int,
    val headerLength: Int,
    val totalLength: Int,
    val identification: Int,
    val flags: Int,
    val fragmentOffset: Int,
    val ttl: Int,
    val protocol: Int,
    val checksum: Int,
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val payload: ByteArray
) {
    companion object {
        const val PROTOCOL_UDP = 17
        const val PROTOCOL_TCP = 6

        fun parse(data: ByteArray, offset: Int = 0, length: Int = data.size): IpPacket? {
            if (length < 20) return null
            val buffer = ByteBuffer.wrap(data, offset, length)
            val firstByte = buffer.get().toInt() and 0xFF
            val version = (firstByte shr 4) and 0x0F
            if (version != 4) return null // We focus on IPv4 for VPN split tunneling

            val ihl = (firstByte and 0x0F) * 4
            if (length < ihl) return null

            buffer.get() // TOS
            val totalLength = buffer.short.toInt() and 0xFFFF
            val id = buffer.short.toInt() and 0xFFFF
            val flagsAndFrag = buffer.short.toInt() and 0xFFFF
            val flags = (flagsAndFrag shr 13) and 0x07
            val fragmentOffset = flagsAndFrag and 0x1FFF
            val ttl = buffer.get().toInt() and 0xFF
            val protocol = buffer.get().toInt() and 0xFF
            val checksum = buffer.short.toInt() and 0xFFFF

            val srcIp = ByteArray(4)
            buffer.get(srcIp)
            val dstIp = ByteArray(4)
            buffer.get(dstIp)

            val payloadLength = (totalLength - ihl).coerceAtLeast(0).coerceAtMost(length - ihl)
            val payload = ByteArray(payloadLength)
            buffer.position(offset + ihl)
            buffer.get(payload)

            return IpPacket(
                version = version,
                headerLength = ihl,
                totalLength = totalLength,
                identification = id,
                flags = flags,
                fragmentOffset = fragmentOffset,
                ttl = ttl,
                protocol = protocol,
                checksum = checksum,
                sourceAddress = srcIp,
                destinationAddress = dstIp,
                payload = payload
            )
        }

        fun buildResponsePacket(
            request: IpPacket,
            responseUdpPayload: ByteArray
        ): ByteArray {
            val headerLength = 20
            val totalLength = headerLength + responseUdpPayload.size
            val buffer = ByteBuffer.allocate(totalLength)

            // Version (4) + IHL (5 words = 20 bytes)
            buffer.put(0x45.toByte())
            buffer.put(0x00.toByte()) // DSCP/ECN
            buffer.putShort(totalLength.toShort())
            buffer.putShort((request.identification + 1).toShort())
            buffer.putShort(0x4000.toShort()) // Flags: Don't Fragment
            buffer.put(64.toByte()) // TTL
            buffer.put(PROTOCOL_UDP.toByte())
            buffer.putShort(0) // Checksum placeholder

            // Invert Source and Destination
            buffer.put(request.destinationAddress)
            buffer.put(request.sourceAddress)

            // Calculate IPv4 Header Checksum
            val headerChecksum = calculateChecksum(buffer.array(), 0, headerLength)
            buffer.putShort(10, headerChecksum.toShort())

            // Append UDP payload
            buffer.put(responseUdpPayload)

            return buffer.array()
        }

        private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
            var sum = 0
            var i = offset
            while (i < offset + length - 1) {
                val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                sum += word
                i += 2
            }
            if (i < offset + length) {
                sum += (data[i].toInt() and 0xFF) shl 8
            }
            while ((sum shr 16) > 0) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }
            return (sum.inv()) and 0xFFFF
        }
    }

    fun sourceIpString(): String = sourceAddress.joinToString(".") { (it.toInt() and 0xFF).toString() }
    fun destIpString(): String = destinationAddress.joinToString(".") { (it.toInt() and 0xFF).toString() }
}
