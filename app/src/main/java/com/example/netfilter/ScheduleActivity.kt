package com.example.netfilter

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/**
 * Programmation horaire : chaque règle (dés)active une catégorie à une heure donnée,
 * tous les jours. Ex. « bloquer Réseaux sociaux à 22:00 » + « débloquer à 07:00 ».
 */
class ScheduleActivity : Activity() {

    private lateinit var rulesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)
        rulesContainer = findViewById(R.id.schedule_container)
        findViewById<Button>(R.id.schedule_add).setOnClickListener { addRuleFlow() }
        findViewById<Button>(R.id.schedule_timer).setOnClickListener { timerFlow() }
        refreshRules()
    }

    /** Blocage ponctuel : bloque une catégorie maintenant, puis la débloque après un délai. */
    private fun timerFlow() {
        val themes = BlockListRepository.THEMES
        val names = themes.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Bloquer temporairement…")
            .setItems(names) { _, i ->
                val theme = themes[i]
                val labels = arrayOf("30 minutes", "1 heure", "2 heures", "4 heures")
                val minutes = intArrayOf(30, 60, 120, 240)
                AlertDialog.Builder(this)
                    .setTitle("Pendant combien de temps ?")
                    .setItems(labels) { _, d ->
                        BlockListRepository.setThemeEnabled(this, theme.id, true)
                        if (FilterVpnService.isRunning) {
                            startService(
                                Intent(this, FilterVpnService::class.java)
                                    .setAction(FilterVpnService.ACTION_RELOAD)
                            )
                        }
                        SchedulerManager.startTemporaryBlock(this, theme.id, minutes[d])
                        Toast.makeText(
                            this,
                            "${theme.name} bloqué pour ${labels[d]}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    .show()
            }
            .show()
    }

    private fun addRuleFlow() {
        val themes = BlockListRepository.THEMES
        val names = themes.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Quelle catégorie programmer ?")
            .setItems(names) { _, i ->
                val theme = themes[i]
                AlertDialog.Builder(this)
                    .setTitle(theme.name)
                    .setItems(arrayOf("Bloquer (activer)", "Débloquer (désactiver)")) { _, a ->
                        val enable = a == 0
                        val now = Calendar.getInstance()
                        TimePickerDialog(this, { _, h, m ->
                            val rule = ScheduleRule(ScheduleStore.nextId(this), theme.id, enable, h, m)
                            ScheduleStore.addRule(this, rule)
                            SchedulerManager.scheduleRule(this, rule)
                            refreshRules()
                        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
                    }
                    .show()
            }
            .show()
    }

    private fun themeName(id: String): String =
        BlockListRepository.THEMES.firstOrNull { it.id == id }?.name ?: id

    private fun refreshRules() {
        rulesContainer.removeAllViews()
        val rules = ScheduleStore.getRules(this).sortedWith(compareBy({ it.hour }, { it.minute }))
        if (rules.isEmpty()) {
            TextView(this).apply {
                text = "Aucune règle. Appuie sur « Ajouter une règle »."
                rulesContainer.addView(this)
            }
            return
        }
        for (rule in rules) {
            val icon = if (rule.enable) "\uD83D\uDEAB" else "\u2705" // 🚫 bloquer / ✅ débloquer
            val verb = if (rule.enable) "bloquer" else "débloquer"
            val time = String.format("%02d:%02d", rule.hour, rule.minute)
            addRow("$icon  $time — $verb ${themeName(rule.themeId)}") {
                SchedulerManager.cancelRule(this, rule)
                ScheduleStore.removeRule(this, rule.id)
                refreshRules()
            }
        }
    }

    private fun addRow(label: String, onRemove: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tv = TextView(this).apply {
            text = label
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btn = Button(this).apply {
            text = "\u2715"
            setOnClickListener { onRemove() }
        }
        row.addView(tv); row.addView(btn)
        rulesContainer.addView(row)
    }
}
