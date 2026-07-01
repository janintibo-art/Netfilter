package com.example.netfilter

import android.content.Context
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Statistiques de blocage, partagées entre le service (écriture) et l'UI (lecture).
 * Suit un total cumulé, un classement par domaine, et un compteur « aujourd'hui »
 * (remis à zéro au changement de jour local).
 */
object StatsStore {

    private const val PREFS = "netfilter_stats"
    private const val KEY_TOTAL = "total"
    private const val KEY_COUNTS = "counts"
    private const val KEY_TODAY = "today"
    private const val KEY_DAY = "day"

    private val counts = ConcurrentHashMap<String, Int>()
    private val total = AtomicLong(0)
    private val today = AtomicLong(0)
    @Volatile private var dayStamp = 0L

    private fun localDay(): Long {
        val now = System.currentTimeMillis()
        return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
    }

    fun record(domain: String) {
        total.incrementAndGet()
        counts.merge(domain, 1) { old, _ -> old + 1 }
        val d = localDay()
        if (d != dayStamp) { dayStamp = d; today.set(0) }
        today.incrementAndGet()
    }

    fun totalBlocked(): Long = total.get()

    fun todayBlocked(): Long = if (localDay() != dayStamp) 0 else today.get()

    fun top(n: Int): List<Pair<String, Int>> =
        counts.entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }

    fun reset(context: Context) {
        counts.clear(); total.set(0); today.set(0); dayStamp = localDay()
        save(context)
    }

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        total.set(p.getLong(KEY_TOTAL, 0))
        today.set(p.getLong(KEY_TODAY, 0))
        dayStamp = p.getLong(KEY_DAY, localDay())
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
        p.edit()
            .putLong(KEY_TOTAL, total.get())
            .putLong(KEY_TODAY, today.get())
            .putLong(KEY_DAY, dayStamp)
            .putString(KEY_COUNTS, serialized)
            .apply()
    }
}
