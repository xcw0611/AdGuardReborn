package com.adblocker.core.resolver

/**
 * Common interface for upstream DNS resolvers.
 */
interface DnsResolver {
    val name: String
    val protocolName: String

    /**
     * Resolves a raw DNS query and returns the raw DNS response packet.
     * @param queryBytes RFC 1035 wire-format DNS query packet
     * @param domain Domain name being queried
     * @param qType DNS Record Type (e.g., A=1, AAAA=28)
     */
    suspend fun resolve(queryBytes: ByteArray, domain: String, qType: Int): ByteArray
}
