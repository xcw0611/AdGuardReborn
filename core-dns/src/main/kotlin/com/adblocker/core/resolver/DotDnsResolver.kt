package com.adblocker.core.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * DNS-over-TLS (DoT) Resolver using standard SSLSocket (RFC 7858).
 */
class DotDnsResolver(
    val serverHost: String = "dns.alidns.com",
    val serverPort: Int = 853,
    override val name: String = "DNS-over-TLS ($serverHost)",
    private val socketFactory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
) : DnsResolver {

    override val protocolName: String = "DoT"

    override suspend fun resolve(queryBytes: ByteArray, domain: String, qType: Int): ByteArray = withContext(Dispatchers.IO) {
        val socket = socketFactory.createSocket(serverHost, serverPort) as SSLSocket
        socket.soTimeout = 4000
        socket.startHandshake()

        socket.use { s ->
            val out = DataOutputStream(s.outputStream)
            val input = DataInputStream(s.inputStream)

            // RFC 7858: 2-byte length prefix
            out.writeShort(queryBytes.size)
            out.write(queryBytes)
            out.flush()

            // Read response 2-byte length
            val responseLength = input.readUnsignedShort()
            if (responseLength <= 0 || responseLength > 65535) {
                throw IOException("Invalid DoT response length: $responseLength")
            }

            val responseData = ByteArray(responseLength)
            input.readFully(responseData)
            responseData
        }
    }
}
