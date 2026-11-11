package com.adblocker.core.packet

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * RFC 1035 compliant lightweight DNS Parser & Synthesizer.
 */
object DnsMessageCodec {

    const val TYPE_A = 1
    const val TYPE_NS = 2
    const val TYPE_CNAME = 5
    const val TYPE_SOA = 6
    const val TYPE_PTR = 12
    const val TYPE_MX = 15
    const val TYPE_TXT = 16
    const val TYPE_AAAA = 28
    const val TYPE_HTTPS = 65
    const val TYPE_ANY = 255

    const val RCODE_NOERROR = 0
    const val RCODE_FORMERR = 1
    const val RCODE_SERVFAIL = 2
    const val RCODE_NXDOMAIN = 3
    const val RCODE_REFUSED = 5

    data class DnsHeader(
        val id: Int,
        val isResponse: Boolean,
        val opcode: Int,
        val authoritativeAnswer: Boolean,
        val truncation: Boolean,
        val recursionDesired: Boolean,
        val recursionAvailable: Boolean,
        val rcode: Int,
        val qdCount: Int,
        val anCount: Int,
        val nsCount: Int,
        val arCount: Int
    )

    data class DnsQuestion(
        val name: String,
        val qType: Int,
        val qClass: Int,
        val rawOffset: Int,
        val rawLength: Int
    )

    data class DnsMessage(
        val header: DnsHeader,
        val questions: List<DnsQuestion>,
        val rawBytes: ByteArray
    )

    fun parse(data: ByteArray): DnsMessage? {
        if (data.size < 12) return null
        val buffer = ByteBuffer.wrap(data)

        val id = buffer.short.toInt() and 0xFFFF
        val flags = buffer.short.toInt() and 0xFFFF
        val qdCount = buffer.short.toInt() and 0xFFFF
        val anCount = buffer.short.toInt() and 0xFFFF
        val nsCount = buffer.short.toInt() and 0xFFFF
        val arCount = buffer.short.toInt() and 0xFFFF

        val isResponse = (flags and 0x8000) != 0
        val opcode = (flags shr 11) and 0x0F
        val aa = (flags and 0x0400) != 0
        val tc = (flags and 0x0200) != 0
        val rd = (flags and 0x0100) != 0
        val ra = (flags and 0x0080) != 0
        val rcode = flags and 0x000F

        val header = DnsHeader(
            id = id,
            isResponse = isResponse,
            opcode = opcode,
            authoritativeAnswer = aa,
            truncation = tc,
            recursionDesired = rd,
            recursionAvailable = ra,
            rcode = rcode,
            qdCount = qdCount,
            anCount = anCount,
            nsCount = nsCount,
            arCount = arCount
        )

        val questions = mutableListOf<DnsQuestion>()
        for (i in 0 until qdCount) {
            val startPos = buffer.position()
            val domain = readDomainName(buffer, data) ?: return null
            if (buffer.remaining() < 4) return null
            val qType = buffer.short.toInt() and 0xFFFF
            val qClass = buffer.short.toInt() and 0xFFFF
            val length = buffer.position() - startPos

            questions.add(
                DnsQuestion(
                    name = domain,
                    qType = qType,
                    qClass = qClass,
                    rawOffset = startPos,
                    rawLength = length
                )
            )
        }

        return DnsMessage(header, questions, data)
    }

    private fun readDomainName(buffer: ByteBuffer, fullData: ByteArray): String? {
        val labels = mutableListOf<String>()
        var jumped = false
        var jumpsCount = 0
        val initialPos = buffer.position()

        while (true) {
            if (!buffer.hasRemaining()) return null
            val len = buffer.get().toInt() and 0xFF

            if (len == 0) {
                // End of domain
                break
            }

            if ((len and 0xC0) == 0xC0) {
                // Compression pointer
                if (!buffer.hasRemaining()) return null
                val pointerOffset = ((len and 0x3F) shl 8) or (buffer.get().toInt() and 0xFF)
                if (pointerOffset >= fullData.size) return null

                if (!jumped) {
                    // Mark position right after pointer so we know where question ends
                    jumped = true
                }

                jumpsCount++
                if (jumpsCount > 10) return null // Prevent circular pointer loops

                // Seek to pointed offset
                buffer.position(pointerOffset)
            } else {
                if (buffer.remaining() < len) return null
                val bytes = ByteArray(len)
                buffer.get(bytes)
                labels.add(String(bytes, Charsets.US_ASCII))
            }
        }

        if (jumped) {
            // Restore position to just after the first pointer encountered
            // Note: in a DNS Question, a pointer ends the QNAME
            buffer.position(initialPos + 2)
        }

        return labels.joinToString(".").lowercase()
    }

    /**
     * Synthesizes an NXDOMAIN response (Domain does not exist).
     */
    fun buildNxDomainResponse(request: DnsMessage): ByteArray {
        val out = ByteArrayOutputStream()
        val headerBuf = ByteBuffer.allocate(12)

        headerBuf.putShort(request.header.id.toShort())
        // QR=1, RD=request.RD, RA=1, RCODE=3 (NXDOMAIN)
        var flags = 0x8183 // Standard response, recursion desired, recursion available, NXDOMAIN
        if (!request.header.recursionDesired) {
            flags = flags and 0x0100.inv()
        }
        headerBuf.putShort(flags.toShort())
        headerBuf.putShort(request.questions.size.toShort()) // QDCOUNT
        headerBuf.putShort(0) // ANCOUNT = 0
        headerBuf.putShort(0) // NSCOUNT = 0
        headerBuf.putShort(0) // ARCOUNT = 0

        out.write(headerBuf.array())

        // Echo Questions
        for (q in request.questions) {
            out.write(request.rawBytes, q.rawOffset, q.rawLength)
        }

        return out.toByteArray()
    }

    /**
     * Synthesizes an Answer with A record pointing to 0.0.0.0 or AAAA record pointing to ::.
     */
    fun buildNullAddressResponse(request: DnsMessage): ByteArray {
        val out = ByteArrayOutputStream()
        val headerBuf = ByteBuffer.allocate(12)

        val q = request.questions.firstOrNull()
        val isA = q?.qType == TYPE_A
        val isAAAA = q?.qType == TYPE_AAAA

        headerBuf.putShort(request.header.id.toShort())
        // QR=1, AA=1, RD=request.RD, RA=1, RCODE=0 (NOERROR)
        var flags = 0x8580 // Response, Authoritative, Recursion Available, No Error
        if (request.header.recursionDesired) {
            flags = flags or 0x0100
        }
        headerBuf.putShort(flags.toShort())
        headerBuf.putShort(request.questions.size.toShort()) // QDCOUNT
        headerBuf.putShort((if (isA || isAAAA) 1 else 0).toShort()) // ANCOUNT
        headerBuf.putShort(0) // NSCOUNT
        headerBuf.putShort(0) // ARCOUNT

        out.write(headerBuf.array())

        // Echo Questions
        for (question in request.questions) {
            out.write(request.rawBytes, question.rawOffset, question.rawLength)
        }

        // Add 0.0.0.0 (A) or :: (AAAA) Answer Record
        if (isA) {
            val answerBuf = ByteBuffer.allocate(16)
            answerBuf.putShort(0xC00C.toShort()) // Pointer to first domain name at offset 12
            answerBuf.putShort(TYPE_A.toShort())
            answerBuf.putShort(1) // Class IN
            answerBuf.putInt(3600) // TTL = 1 hour
            answerBuf.putShort(4) // RDLENGTH = 4 bytes
            answerBuf.put(byteArrayOf(0, 0, 0, 0)) // 0.0.0.0
            out.write(answerBuf.array())
        } else if (isAAAA) {
            val answerBuf = ByteBuffer.allocate(28)
            answerBuf.putShort(0xC00C.toShort()) // Pointer to first domain name at offset 12
            answerBuf.putShort(TYPE_AAAA.toShort())
            answerBuf.putShort(1) // Class IN
            answerBuf.putInt(3600) // TTL = 1 hour
            answerBuf.putShort(16) // RDLENGTH = 16 bytes
            answerBuf.put(ByteArray(16)) // 0000:0000:...
            out.write(answerBuf.array())
        }

        return out.toByteArray()
    }
}
