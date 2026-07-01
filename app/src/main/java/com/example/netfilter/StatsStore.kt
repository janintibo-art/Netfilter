package com.example.netfilter

import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Statistiques de blocage, partagées entre le service (écriture) et les écrans (lecture).
 * Comme tout tourne dans le même processus, l'UI lit directement les valeurs en mémoire.
 * On sauvegarde dans les préférences pour conserver les stats après un redémarrage.
 */
object StatsStore {

    private const val PREFS = "netfilter_stats"
    private const val KEY_TOTAL = "total"
    private const val KEY_COUNTS = "counts"

    private val counts = ConcurrentHashMap<String, Int>()
    private val total = AtomicLong(0)

    fun record(domain: String) {
        total.incrementAndGet()
        counts.merge(domain, 1) { old, _ -> old + 1 }
    }

    fun totalBlocked(): Long = total.get()

    /** Les n domaines les plus bloqués, du plus fréquent au moins fréquent. */
    fun top(n: Int): List<Pair<String, Int>> =
        counts.entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }

    fun reset(context: Context) {
        counts.clear()
        total.set(0)
        save(context)
    }

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        total.set(p.getLong(KEY_TOTAL, 0))
        counts.clear()
        p.getString(KEY_COUNTS, "")?.split("\n")?.forEach { line ->
            val i = line.lastIndexOf('=')
            if (i > 0) {
                val c = line.substring(i + 1).toIntOrNull()
                if (c != null) counts[line.substring(0, i)] = c
            }
        }
    }

    fun save(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val serialized = counts.entries.sortedByDescending { it.value }.take(200)
            .joinToString("\n") { "${it.key}=${it.value}" }
        p.edit().putLong(KEY_TOTAL, total.get()).putString(KEY_COUNTS, serialized).apply()
    }
}
