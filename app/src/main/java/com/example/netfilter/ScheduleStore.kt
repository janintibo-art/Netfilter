package com.example.netfilter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Une règle horaire : à HH:MM, (dés)activer une catégorie thématique. */
data class ScheduleRule(
    val id: Int,
    val themeId: String,
    val enable: Boolean,
    val hour: Int,
    val minute: Int
)

/**
 * Règles de programmation horaire, stockées en JSON dans les préférences.
 * (Le filtrage doit rester activé pour que les changements de catégorie s'appliquent.)
 */
object ScheduleStore {

    private const val PREFS = "netfilter_prefs"
    private const val KEY = "schedule_rules"

    fun getRules(context: Context): List<ScheduleRule> {
        val raw = prefs(context).getString(KEY, "[]") ?: "[]"
        val out = ArrayList<ScheduleRule>()
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    ScheduleRule(
                        o.getInt("id"),
                        o.getString("theme"),
                        o.getBoolean("enable"),
                        o.getInt("hour"),
                        o.getInt("minute")
                    )
                )
            }
        }
        return out
    }

    private fun saveRules(context: Context, rules: List<ScheduleRule>) {
        val arr = JSONArray()
        for (r in rules) {
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("theme", r.themeId)
                    .put("enable", r.enable)
                    .put("hour", r.hour)
                    .put("minute", r.minute)
            )
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply()
    }

    fun addRule(context: Context, rule: ScheduleRule) {
        val list = getRules(context).toMutableList()
        list.add(rule)
        saveRules(context, list)
    }

    fun removeRule(context: Context, id: Int) {
        saveRules(context, getRules(context).filter { it.id != id })
    }

    fun nextId(context: Context): Int =
        (getRules(context).maxOfOrNull { it.id } ?: 0) + 1

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
