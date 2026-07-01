package com.example.netfilter

import java.util.concurrent.ConcurrentHashMap

/**
 * Cache DNS en mémoire : réduit la latence et le trafic vers le résolveur.
 * Clé = "type:domaine". Valeur = la charge DNS renvoyée par le résolveur.
 * L'ID de transaction et la question sont réécrits par le service au moment de servir,
 * pour correspondre exactement à la requête en cours.
 */
object DnsCache {

    private class Entry(val payload: ByteArray, val expiry: Long)

    private val map = ConcurrentHashMap<String, Entry>()
    private const val MAX_ENTRIES = 2000

    fun get(key: String): ByteArray? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() > e.expiry) {
            map.remove(key)
            return null
        }
        return e.payload
    }

    fun put(key: String, payload: ByteArray, ttlSeconds: Long) {
        if (map.size >= MAX_ENTRIES) map.clear() // éviction simple
        map[key] = Entry(payload, System.currentTimeMillis() + ttlSeconds * 1000)
    }

    fun clear() = map.clear()
}
