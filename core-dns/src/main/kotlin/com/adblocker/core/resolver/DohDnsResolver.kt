package com.adblocker.core.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * DNS-over-HTTPS (DoH) Resolver using OkHttp (RFC 8484).
 */
class DohDnsResolver(
    val url: String = "https://dns.alidns.com/dns-query",
    override val name: String = "DNS-over-HTTPS ($url)",
    private val client: OkHttpClient = defaultClient
) : DnsResolver {

    override val protocolName: String = "DoH"

    companion object {
        private val DNS_MESSAGE_MEDIA_TYPE = "application/dns-message".toMediaType()

        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(4, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override suspend fun resolve(queryBytes: ByteArray, domain: String, qType: Int): ByteArray = withContext(Dispatchers.IO) {
        val requestBody = queryBytes.toRequestBody(DNS_MESSAGE_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Accept", "application/dns-message")
            .header("User-Agent", "AdBlockerCore/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("DoH query failed with HTTP code: ${response.code} for $domain")
            }
            val body = response.body ?: throw IOException("Empty DoH response body for $domain")
            body.bytes()
        }
    }
}
