package com.netguard.proxy

import java.io.BufferedInputStream
import java.io.InputStream

/**
 * Extracts Server Name Indication (SNI) from TLS ClientHello.
 * Peeks at first bytes using mark/reset so data can be replayed for relay.
 */
object SniExtractor {

    fun extract(input: InputStream): String? {
        val buffered = if (input is BufferedInputStream) input
        else BufferedInputStream(input, 8192)

        buffered.mark(4096)
        try {
            val buf = ByteArray(4096)
            val read = buffered.read(buf)
            if (read < 5) return null

            return parseSni(buf, read)
        } catch (_: Exception) {
            return null
        } finally {
            try { buffered.reset() } catch (_: Exception) {}
        }
    }

    private fun parseSni(data: ByteArray, length: Int): String? {
        if (length < 5) return null

        // TLS Record: ContentType(1) + Version(2) + Length(2)
        if (data[0] != 0x16.toByte()) return null  // not Handshake

        val recordLen = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        if (length < 5 + recordLen) return null

        var pos = 5

        // Handshake: Type(1) + Length(3)
        if (pos >= length || data[pos] != 0x01.toByte()) return null  // not ClientHello
        pos += 4  // skip type + 3-byte length

        // ClientHello: Version(2) + Random(32)
        pos += 2 + 32
        if (pos >= length) return null

        // Session ID
        val sessionIdLen = data[pos].toInt() and 0xFF
        pos += 1 + sessionIdLen
        if (pos + 2 > length) return null

        // Cipher Suites
        val cipherSuitesLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 2 + cipherSuitesLen
        if (pos + 1 > length) return null

        // Compression Methods
        val compressionLen = data[pos].toInt() and 0xFF
        pos += 1 + compressionLen
        if (pos + 2 > length) return null

        // Extensions Length
        val extensionsLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 2
        val extensionsEnd = pos + extensionsLen

        // Iterate extensions
        while (pos + 4 <= extensionsEnd && pos + 4 <= length) {
            val extType = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            val extLen = ((data[pos + 2].toInt() and 0xFF) shl 8) or (data[pos + 3].toInt() and 0xFF)
            pos += 4

            if (extType == 0x0000) {  // server_name extension
                if (pos + 5 > length) return null
                // Server Name List Length (2) + Name Type (1) + Name Length (2)
                pos += 2  // skip list length
                val nameType = data[pos].toInt() and 0xFF
                pos++
                if (nameType != 0x00) return null  // not host_name

                val nameLen = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
                pos += 2
                if (pos + nameLen > length) return null

                return String(data, pos, nameLen, Charsets.US_ASCII)
            }

            pos += extLen
        }

        return null
    }
}
