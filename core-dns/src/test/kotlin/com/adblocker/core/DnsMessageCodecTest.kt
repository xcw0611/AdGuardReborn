package com.adblocker.core

import com.adblocker.core.packet.DnsMessageCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class DnsMessageCodecTest {

    private fun createSampleDnsQuery(id: Int, domain: String, qType: Int = DnsMessageCodec.TYPE_A): ByteArray {
        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(12)
        header.putShort(id.toShort())
        header.putShort(0x0100.toShort()) // RD = 1
        header.putShort(1) // QDCOUNT = 1
        header.putShort(0)
        header.putShort(0)
        header.putShort(0)
        out.write(header.array())

        // Domain labels
        val parts = domain.split('.')
        for (part in parts) {
            out.write(part.length)
            out.write(part.toByteArray(Charsets.US_ASCII))
        }
        out.write(0) // Null terminator

        val footer = ByteBuffer.allocate(4)
        footer.putShort(qType.toShort())
        footer.putShort(1) // IN class
        out.write(footer.array())

        return out.toByteArray()
    }

    @Test
    fun testParseQuery() {
        val raw = createSampleDnsQuery(0x1234, "ad.doubleclick.net", DnsMessageCodec.TYPE_A)
        val msg = DnsMessageCodec.parse(raw)
        assertNotNull(msg)
        assertEquals(0x1234, msg!!.header.id)
        assertEquals(1, msg.questions.size)
        assertEquals("ad.doubleclick.net", msg.questions[0].name)
        assertEquals(DnsMessageCodec.TYPE_A, msg.questions[0].qType)
    }

    @Test
    fun testBuildNxDomainResponse() {
        val raw = createSampleDnsQuery(0x5678, "malware.tracking.com", DnsMessageCodec.TYPE_A)
        val msg = DnsMessageCodec.parse(raw)!!
        val respBytes = DnsMessageCodec.buildNxDomainResponse(msg)

        val resp = DnsMessageCodec.parse(respBytes)
        assertNotNull(resp)
        assertEquals(0x5678, resp!!.header.id)
        assertTrue(resp.header.isResponse)
        assertEquals(DnsMessageCodec.RCODE_NXDOMAIN, resp.header.rcode)
        assertEquals(1, resp.questions.size)
        assertEquals("malware.tracking.com", resp.questions[0].name)
    }

    @Test
    fun testBuildNullAddressResponse() {
        val raw = createSampleDnsQuery(0x9ABC, "ads.google.com", DnsMessageCodec.TYPE_A)
        val msg = DnsMessageCodec.parse(raw)!!
        val respBytes = DnsMessageCodec.buildNullAddressResponse(msg)

        val resp = DnsMessageCodec.parse(respBytes)
        assertNotNull(resp)
        assertEquals(0x9ABC, resp!!.header.id)
        assertTrue(resp.header.isResponse)
        assertEquals(DnsMessageCodec.RCODE_NOERROR, resp.header.rcode)
        assertEquals(1, resp.header.anCount)
    }
}
