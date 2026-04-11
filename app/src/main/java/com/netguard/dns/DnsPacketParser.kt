package com.netguard.dns

import java.nio.ByteBuffer

/**
 * Parses DNS wire format per RFC 1035.
 */
object DnsPacketParser {

    data class DnsQuery(
        val transactionId: Int,
        val domain: String,
        val queryType: Int,   // 1=A, 28=AAAA
        val queryClass: Int,
        val rawData: ByteArray,
        val rawLength: Int
    )

    /**
     * Parse a DNS query packet, extracting the domain name.
     */
    fun parseQuery(data: ByteArray, length: Int): DnsQuery? {
        if (length < 12) return null
        val buf = ByteBuffer.wrap(data, 0, length)

        val txId = buf.short.toInt() and 0xFFFF
        val flags = buf.short.toInt() and 0xFFFF
        val qr = (flags shr 15) and 1
        if (qr != 0) return null  // not a query

        val qdCount = buf.short.toInt() and 0xFFFF
        buf.short  // anCount
        buf.short  // nsCount
        buf.short  // arCount

        if (qdCount < 1) return null

        // Parse QNAME
        val domain = parseQName(data, buf.position()) ?: return null
        buf.position(buf.position() + encodedQNameLength(data, buf.position()))

        val qType = if (buf.remaining() >= 2) buf.short.toInt() and 0xFFFF else 1
        val qClass = if (buf.remaining() >= 2) buf.short.toInt() and 0xFFFF else 1

        return DnsQuery(txId, domain, qType, qClass, data.copyOf(length), length)
    }

    /**
     * Parse QNAME labels: length-prefixed, terminated by 0x00.
     */
    private fun parseQName(data: ByteArray, offset: Int): String? {
        val labels = mutableListOf<String>()
        var pos = offset
        while (pos < data.size) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) break
            if (len > 63) return null  // compression pointer or invalid
            pos++
            if (pos + len > data.size) return null
            labels.add(String(data, pos, len, Charsets.US_ASCII))
            pos += len
        }
        return if (labels.isNotEmpty()) labels.joinToString(".") else null
    }

    private fun encodedQNameLength(data: ByteArray, offset: Int): Int {
        var pos = offset
        while (pos < data.size) {
            val len = data[pos].toInt() and 0xFF
            if (len == 0) return pos - offset + 1
            pos += len + 1
        }
        return pos - offset
    }

    /**
     * Build a DNS response that returns 0.0.0.0 for a blocked domain.
     */
    fun buildBlockedResponse(query: DnsQuery): ByteArray {
        val response = query.rawData.copyOf(query.rawLength)

        // Set QR=1 (response bit)
        response[2] = (response[2].toInt() or 0x80).toByte()
        // Set RA=1 (recursion available)
        response[3] = (response[3].toInt() or 0x80).toByte()
        // RCODE=0 (no error)
        response[3] = (response[3].toInt() and 0xF0.toInt()).toByte()

        // Set ANCOUNT=1
        response[6] = 0x00
        response[7] = 0x01

        // Append answer: pointer to QNAME at offset 12, TYPE=A, CLASS=IN, TTL=60, RDLEN=4, 0.0.0.0
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C,         // name pointer to offset 12
            0x00, 0x01,                   // TYPE = A
            0x00, 0x01,                   // CLASS = IN
            0x00, 0x00, 0x00, 0x3C,       // TTL = 60 seconds
            0x00, 0x04,                   // RDLENGTH = 4
            0x00, 0x00, 0x00, 0x00        // RDATA = 0.0.0.0
        )

        return response + answer
    }

    /**
     * Build an NXDOMAIN response.
     */
    fun buildNxdomainResponse(query: DnsQuery): ByteArray {
        val response = query.rawData.copyOf(query.rawLength)
        response[2] = (response[2].toInt() or 0x80).toByte()  // QR=1
        response[3] = ((response[3].toInt() and 0xF0) or 0x03).toByte()  // RCODE=3 NXDOMAIN
        response[3] = (response[3].toInt() or 0x80).toByte()  // RA=1
        return response
    }
}
