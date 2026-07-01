package com.example.netfilter

import java.nio.ByteBuffer

/**
 * Analyse des requêtes DNS et fabrication des réponses (bloquée ou transmise),
 * pour IPv4 ET IPv6. La charge DNS est identique quelle que soit la version IP ;
 * seul l'emballage IP/UDP + les sommes de contrôle diffèrent.
 *
 * Limites : ne gère pas les en-têtes d'extension IPv6 ni le DNS sur TCP (voir README).
 */
object DnsPacket {

    /** Nom demandé (ex. "ads.exemple.com"). null si la requête est malformée. */
    fun readQueryName(packet: ByteBuffer, dnsStart: Int): String? {
        var pos = dnsStart + 12
        val sb = StringBuilder()
        while (pos < packet.limit()) {
            val len = packet.get(pos).toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) != 0) return null // pas de compression dans une question
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..len) {
                if (pos + i >= packet.limit()) return null
                sb.append((packet.get(pos + i).toInt() and 0xFF).toChar())
            }
            pos += len + 1
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    /** Type de la requête : 1 = A, 28 = AAAA, etc. */
    fun readQueryType(packet: ByteBuffer, dnsStart: Int): Int {
        var pos = dnsStart + 12
        while (pos < packet.limit()) {
            val len = packet.get(pos).toInt() and 0xFF
            if (len == 0) break
            pos += len + 1
        }
        pos += 1
        if (pos + 1 >= packet.limit()) return 0
        return ((packet.get(pos).toInt() and 0xFF) shl 8) or (packet.get(pos + 1).toInt() and 0xFF)
    }

    /** Longueur (octets) de la section question : nom + octet nul + QTYPE + QCLASS. */
    fun questionByteLength(packet: ByteBuffer, dnsStart: Int): Int {
        var pos = dnsStart + 12
        while (pos < packet.limit()) {
            val len = packet.get(pos).toInt() and 0xFF
            if (len == 0) break
            pos += len + 1
        }
        return (pos + 1) - (dnsStart + 12) + 4
    }

    /**
     * Réponse "bloqué", adaptée au type demandé :
     *   A    -> 0.0.0.0     AAAA -> ::     autre -> réponse vide (NODATA)
     */
    fun buildBlockedResponse(
        request: ByteBuffer,
        ipVersion: Int,
        ipHeaderLen: Int,
        udpStart: Int,
        dnsStart: Int,
        qtype: Int
    ): ByteBuffer? {
        val qLen = questionByteLength(request, dnsStart)
        val headerAndQuestion = 12 + qLen
        if (dnsStart + headerAndQuestion > request.limit()) return null

        val answer: ByteArray = when (qtype) {
            1 -> byteArrayOf(0xC0.toByte(), 0x0C, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 0, 0, 0, 0)
            28 -> byteArrayOf(
                0xC0.toByte(), 0x0C, 0, 28, 0, 1, 0, 0, 0, 60, 0, 16,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
            )
            else -> ByteArray(0)
        }
        val ancount = if (answer.isEmpty()) 0 else 1

        val dns = ByteArray(headerAndQuestion + answer.size)
        for (i in 0 until headerAndQuestion) dns[i] = request.get(dnsStart + i)
        dns[2] = 0x81.toByte(); dns[3] = 0x80.toByte() // drapeaux de réponse
        dns[6] = 0; dns[7] = ancount.toByte()          // ANCOUNT
        dns[8] = 0; dns[9] = 0; dns[10] = 0; dns[11] = 0 // NSCOUNT/ARCOUNT (on jette l'EDNS)
        System.arraycopy(answer, 0, dns, headerAndQuestion, answer.size)

        return wrap(request, ipVersion, ipHeaderLen, udpStart, dns)
    }

    /** Emballe une charge DNS (réponse du résolveur) dans un paquet IP/UDP vers l'app. */
    fun buildForwardedResponse(
        request: ByteBuffer,
        ipVersion: Int,
        ipHeaderLen: Int,
        udpStart: Int,
        dnsStart: Int,
        dnsPayload: ByteArray
    ): ByteBuffer? = wrap(request, ipVersion, ipHeaderLen, udpStart, dnsPayload)

    /** Durée de mise en cache déduite de la réponse (TTL borné). */
    fun responseTtlSeconds(payload: ByteArray): Long {
        return try {
            val ancount = ((payload[6].toInt() and 0xFF) shl 8) or (payload[7].toInt() and 0xFF)
            if (ancount == 0) return 30L // cache négatif court
            var pos = 12
            while (pos < payload.size) {
                val len = payload[pos].toInt() and 0xFF
                if (len == 0) break
                pos += len + 1
            }
            pos += 1 + 4 // octet nul + QTYPE + QCLASS -> début de la 1re réponse
            if (pos < payload.size && (payload[pos].toInt() and 0xC0) == 0xC0) {
                pos += 2 // nom = pointeur de compression
            } else {
                while (pos < payload.size) {
                    val len = payload[pos].toInt() and 0xFF
                    if (len == 0) { pos += 1; break }
                    pos += len + 1
                }
            }
            pos += 4 // TYPE + CLASS
            if (pos + 3 >= payload.size) return 300L
            val ttl = ((payload[pos].toInt() and 0xFF).toLong() shl 24) or
                    ((payload[pos + 1].toInt() and 0xFF).toLong() shl 16) or
                    ((payload[pos + 2].toInt() and 0xFF).toLong() shl 8) or
                    (payload[pos + 3].toInt() and 0xFF).toLong()
            ttl.coerceIn(30L, 3600L)
        } catch (e: Exception) {
            300L
        }
    }

    // --- Emballage IP/UDP ---

    private fun wrap(
        request: ByteBuffer, ipVersion: Int, ipHeaderLen: Int, udpStart: Int, dns: ByteArray
    ): ByteBuffer =
        if (ipVersion == 4) wrapIpv4(request, ipHeaderLen, udpStart, dns)
        else wrapIpv6(request, udpStart, dns)

    private fun wrapIpv4(request: ByteBuffer, ihl: Int, udpStart: Int, dns: ByteArray): ByteBuffer {
        val udpLen = 8 + dns.size
        val total = ihl + udpLen
        val out = ByteArray(total)
        for (i in 0 until ihl) out[i] = request.get(i)
        for (i in 0 until 4) { // inverse IP src <-> dst
            out[12 + i] = request.get(16 + i)
            out[16 + i] = request.get(12 + i)
        }
        out[2] = ((total shr 8) and 0xFF).toByte(); out[3] = (total and 0xFF).toByte()
        out[8] = 64 // TTL
        out[udpStart] = request.get(udpStart + 2); out[udpStart + 1] = request.get(udpStart + 3)
        out[udpStart + 2] = request.get(udpStart); out[udpStart + 3] = request.get(udpStart + 1)
        out[udpStart + 4] = ((udpLen shr 8) and 0xFF).toByte(); out[udpStart + 5] = (udpLen and 0xFF).toByte()
        System.arraycopy(dns, 0, out, udpStart + 8, dns.size)

        out[10] = 0; out[11] = 0
        val ip = checksum(out, 0, ihl)
        out[10] = ((ip shr 8) and 0xFF).toByte(); out[11] = (ip and 0xFF).toByte()

        out[udpStart + 6] = 0; out[udpStart + 7] = 0
        var sum = 0L
        for (i in 12 until 20 step 2) sum += word(out, i) // pseudo : src + dst
        sum += 17; sum += udpLen
        var i = udpStart; var rem = udpLen
        while (rem > 1) { sum += word(out, i); i += 2; rem -= 2 }
        if (rem == 1) sum += (out[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv().toInt() and 0xFFFF
        out[udpStart + 6] = ((ck shr 8) and 0xFF).toByte(); out[udpStart + 7] = (ck and 0xFF).toByte()
        return ByteBuffer.wrap(out)
    }

    private fun wrapIpv6(request: ByteBuffer, udpStart: Int, dns: ByteArray): ByteBuffer {
        val udpLen = 8 + dns.size
        val total = 40 + udpLen
        val out = ByteArray(total)
        out[0] = 0x60.toByte()                        // version 6
        out[4] = ((udpLen shr 8) and 0xFF).toByte()   // longueur de charge utile
        out[5] = (udpLen and 0xFF).toByte()
        out[6] = 17                                   // en-tête suivant = UDP
        out[7] = 64                                   // limite de sauts
        for (i in 0 until 16) { // inverse src (24..39) <-> dst (8..23)
            out[8 + i] = request.get(24 + i)
            out[24 + i] = request.get(8 + i)
        }
        out[udpStart] = request.get(udpStart + 2); out[udpStart + 1] = request.get(udpStart + 3)
        out[udpStart + 2] = request.get(udpStart); out[udpStart + 3] = request.get(udpStart + 1)
        out[udpStart + 4] = ((udpLen shr 8) and 0xFF).toByte(); out[udpStart + 5] = (udpLen and 0xFF).toByte()
        System.arraycopy(dns, 0, out, udpStart + 8, dns.size)

        out[udpStart + 6] = 0; out[udpStart + 7] = 0
        var sum = 0L
        for (i in 8 until 40 step 2) sum += word(out, i) // pseudo : src + dst
        sum += (udpLen.toLong() and 0xFFFF)              // longueur (haut = 0 à ces tailles)
        sum += 17                                        // en-tête suivant
        var i = udpStart; var rem = udpLen
        while (rem > 1) { sum += word(out, i); i += 2; rem -= 2 }
        if (rem == 1) sum += (out[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        var ck = sum.inv().toInt() and 0xFFFF
        if (ck == 0) ck = 0xFFFF // en IPv6, un checksum calculé à 0 doit valoir 0xFFFF
        out[udpStart + 6] = ((ck shr 8) and 0xFF).toByte(); out[udpStart + 7] = (ck and 0xFF).toByte()
        return ByteBuffer.wrap(out)
    }

    private fun word(b: ByteArray, i: Int) =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    private fun checksum(b: ByteArray, start: Int, len: Int): Int {
        var sum = 0L; var i = start; var rem = len
        while (rem > 1) { sum += word(b, i); i += 2; rem -= 2 }
        if (rem == 1) sum += (b[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }
}
