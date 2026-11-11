package com.adblocker.core.packet

import java.nio.ByteBuffer

/**
 * Lightweight UDP Packet parser and builder.
 */
data class UdpPacket(
    val sourcePort: Int,
    val destinationPort: Int,
    val length: Int,
    val checksum: Int,
    val payload: ByteArray
) {
    companion object {
        const val HEADER_LENGTH = 8

        fun parse(data: ByteArray, offset: Int = 0, length: Int = data.size): UdpPacket? {
            if (length < HEADER_LENGTH) return null
            val buffer = ByteBuffer.wrap(data, offset, length)
            val srcPort = buffer.short.toInt() and 0xFFFF
            val dstPort = buffer.short.toInt() and 0xFFFF
            val udpLength = buffer.short.toInt() and 0xFFFF
            val checksum = buffer.short.toInt() and 0xFFFF

            val payloadLength = (udpLength - HEADER_LENGTH).coerceAtLeast(0).coerceAtMost(length - HEADER_LENGTH)
            val payload = ByteArray(payloadLength)
            buffer.get(payload)

            return UdpPacket(
                sourcePort = srcPort,
                destinationPort = dstPort,
                length = udpLength,
                checksum = checksum,
                payload = payload
            )
        }

        fun buildResponse(
            request: UdpPacket,
            responseDnsPayload: ByteArray
        ): ByteArray {
            val totalLength = HEADER_LENGTH + responseDnsPayload.size
            val buffer = ByteBuffer.allocate(totalLength)

            // Invert ports: response source port = request dest port (usually 53)
            buffer.putShort(request.destinationPort.toShort())
            buffer.putShort(request.sourcePort.toShort())
            buffer.putShort(totalLength.toShort())
            buffer.putShort(0) // UDP checksum optional in IPv4, 0 means no checksum

            buffer.put(responseDnsPayload)
            return buffer.array()
        }
    }
}
