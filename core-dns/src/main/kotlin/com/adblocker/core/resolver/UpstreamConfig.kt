package com.adblocker.core.resolver

enum class DnsProtocol {
    UDP,
    DOH,
    DOT
}

data class UpstreamConfig(
    val id: String,
    val name: String,
    val description: String,
    val protocol: DnsProtocol,
    val hostOrUrl: String,
    val port: Int = 53
) {
    fun createResolver(): DnsResolver {
        return when (protocol) {
            DnsProtocol.UDP -> UdpDnsResolver(serverHost = hostOrUrl, serverPort = port, name = name)
            DnsProtocol.DOH -> DohDnsResolver(url = hostOrUrl, name = name)
            DnsProtocol.DOT -> DotDnsResolver(serverHost = hostOrUrl, serverPort = port, name = name)
        }
    }

    companion object {
        val PRESETS = listOf(
            UpstreamConfig(
                id = "alidns_doh",
                name = "AliDNS (阿里安全 DoH)",
                description = "国内极速响应，支持防劫持加密解析",
                protocol = DnsProtocol.DOH,
                hostOrUrl = "https://dns.alidns.com/dns-query"
            ),
            UpstreamConfig(
                id = "dnspod_doh",
                name = "DNSPod (腾讯公共 DoH)",
                description = "腾讯云高可用，智能解析节点",
                protocol = DnsProtocol.DOH,
                hostOrUrl = "https://doh.pub/dns-query"
            ),
            UpstreamConfig(
                id = "alidns_dot",
                name = "AliDNS (阿里公共 DoT)",
                description = "基于 TLS 853 端口的传输层加密",
                protocol = DnsProtocol.DOT,
                hostOrUrl = "dns.alidns.com",
                port = 853
            ),
            UpstreamConfig(
                id = "alidns_udp",
                name = "AliDNS (标准 UDP 223.5.5.5)",
                description = "传统标准 DNS，兼容性最高",
                protocol = DnsProtocol.UDP,
                hostOrUrl = "223.5.5.5",
                port = 53
            ),
            UpstreamConfig(
                id = "adguard_doh",
                name = "AdGuard DNS (公共 DoH)",
                description = "官方公共加密解析器",
                protocol = DnsProtocol.DOH,
                hostOrUrl = "https://dns.adguard-dns.com/dns-query"
            ),
            UpstreamConfig(
                id = "cloudflare_doh",
                name = "Cloudflare (1.1.1.1 DoH)",
                description = "全球低延迟极速隐私解析",
                protocol = DnsProtocol.DOH,
                hostOrUrl = "https://cloudflare-dns.com/dns-query"
            ),
            UpstreamConfig(
                id = "google_doh",
                name = "Google Public DNS (DoH)",
                description = "全球高稳定性 Google DNS",
                protocol = DnsProtocol.DOH,
                hostOrUrl = "https://dns.google/dns-query"
            )
        )

        val DEFAULT = PRESETS[0]
    }
}
