package com.example.netfilter

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    companion object {
        const val EXTRA_AUTO_START = "auto_start"
        private const val REQ_VPN = 100

        // Résolveurs proposés (nom affiché -> IP). Quad9 et AdGuard filtrent déjà à la source.
        private val RESOLVERS = linkedMapOf(
            "Google (8.8.8.8)" to "8.8.8.8",
            "Cloudflare (1.1.1.1)" to "1.1.1.1",
            "Quad9 — bloque les malwares (9.9.9.9)" to "9.9.9.9",
            "AdGuard — bloque déjà la pub (94.140.14.14)" to "94.140.14.14"
        )
    }

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var pauseButton: Button
    private lateinit var resolverButton: Button
    private lateinit var rulesContainer: LinearLayout

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refreshUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        toggleButton = findViewById(R.id.toggle_button)
        pauseButton = findViewById(R.id.pause_button)
        resolverButton = findViewById(R.id.resolver_button)
        rulesContainer = findViewById(R.id.rules_container)

        val blockInput = findViewById<EditText>(R.id.domain_input)
        val whiteInput = findViewById<EditText>(R.id.whitelist_input)
        val dohSwitch = findViewById<Switch>(R.id.doh_switch)
        val bootSwitch = findViewById<Switch>(R.id.boot_switch)
        val autoUpdateSwitch = findViewById<Switch>(R.id.auto_update_switch)
        val bolloreSwitch = findViewById<Switch>(R.id.bollore_switch)

        toggleButton.setOnClickListener {
            if (FilterVpnService.isRunning) stopFiltering() else requestVpnThenStart()
        }

        pauseButton.setOnClickListener { showPauseDialog() }

        resolverButton.setOnClickListener { showResolverDialog() }
        updateResolverButton()

        findViewById<Button>(R.id.add_button).setOnClickListener {
            val d = blockInput.text.toString().trim()
            if (d.isNotEmpty()) {
                BlockListRepository.addCustomDomain(this, d)
                blockInput.text.clear(); reloadService(); refreshRules()
            }
        }

        findViewById<Button>(R.id.whitelist_button).setOnClickListener {
            val d = whiteInput.text.toString().trim()
            if (d.isNotEmpty()) {
                BlockListRepository.addWhitelistDomain(this, d)
                whiteInput.text.clear(); reloadService(); refreshRules()
            }
        }

        dohSwitch.isChecked = BlockListRepository.isDohBlockingEnabled(this)
        dohSwitch.setOnCheckedChangeListener { _, checked ->
            BlockListRepository.setDohBlocking(this, checked); reloadService()
        }

        bootSwitch.isChecked = BlockListRepository.isStartOnBoot(this)
        bootSwitch.setOnCheckedChangeListener { _, checked ->
            BlockListRepository.setStartOnBoot(this, checked)
        }

        autoUpdateSwitch.isChecked = BlockListRepository.isAutoUpdateEnabled(this)
        autoUpdateSwitch.setOnCheckedChangeListener { _, checked ->
            BlockListRepository.setAutoUpdate(this, checked)
            if (checked) UpdateWorker.schedule(this) else UpdateWorker.cancel(this)
        }

        bolloreSwitch.isChecked = BlockListRepository.isBlockBollore(this)
        bolloreSwitch.setOnCheckedChangeListener { _, checked ->
            BlockListRepository.setBlockBollore(this, checked)
            reloadService()
        }

        findViewById<Button>(R.id.update_button).setOnClickListener {
            startActivity(Intent(this, ListsActivity::class.java))
        }

        findViewById<Button>(R.id.apps_button).setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }

        findViewById<Button>(R.id.stats_button).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }

        if (intent.getBooleanExtra(EXTRA_AUTO_START, false)) requestVpnThenStart()
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this, stateReceiver,
            IntentFilter(FilterVpnService.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refreshUi(); refreshRules()
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(stateReceiver) }
    }

    // --- Dialogues ---

    private fun showPauseDialog() {
        val labels = arrayOf("5 minutes", "15 minutes", "1 heure")
        val minutes = intArrayOf(5, 15, 60)
        AlertDialog.Builder(this)
            .setTitle("Mettre le filtrage en pause")
            .setItems(labels) { _, which ->
                startService(
                    Intent(this, FilterVpnService::class.java)
                        .setAction(FilterVpnService.ACTION_PAUSE)
                        .putExtra(FilterVpnService.EXTRA_PAUSE_MINUTES, minutes[which])
                )
                Toast.makeText(this, "En pause ${labels[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showResolverDialog() {
        val names = RESOLVERS.keys.toTypedArray()
        val ips = RESOLVERS.values.toList()
        val current = BlockListRepository.getUpstreamDns(this)
        val checked = ips.indexOf(current).let { if (it < 0) 0 else it }
        AlertDialog.Builder(this)
            .setTitle("Résolveur DNS en amont")
            .setSingleChoiceItems(names, checked) { dialog, which ->
                BlockListRepository.setUpstreamDns(this, ips[which])
                updateResolverButton(); reloadService(); dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun updateResolverButton() {
        val ip = BlockListRepository.getUpstreamDns(this)
        val name = RESOLVERS.entries.firstOrNull { it.value == ip }?.key ?: ip
        resolverButton.text = "Résolveur : $name"
    }

    // --- Cycle du service ---

    private fun reloadService() {
        if (FilterVpnService.isRunning) {
            startService(
                Intent(this, FilterVpnService::class.java).setAction(FilterVpnService.ACTION_RELOAD)
            )
        }
    }

    private fun requestVpnThenStart() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) startActivityForResult(prepare, REQ_VPN) else startFiltering()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && resultCode == Activity.RESULT_OK) startFiltering()
    }

    private fun startFiltering() {
        startForegroundService(
            Intent(this, FilterVpnService::class.java).setAction(FilterVpnService.ACTION_START)
        )
        refreshUi()
    }

    private fun stopFiltering() {
        startService(
            Intent(this, FilterVpnService::class.java).setAction(FilterVpnService.ACTION_STOP)
        )
        refreshUi()
    }

    private fun refreshUi() {
        val on = FilterVpnService.isRunning
        val paused = System.currentTimeMillis() < FilterVpnService.pausedUntil
        statusText.text = when {
            on && paused -> "En pause"
            on -> "Filtrage actif"
            else -> "Filtrage arrêté"
        }
        toggleButton.text = if (on) "Arrêter" else "Démarrer le filtrage"
        pauseButton.visibility = if (on) View.VISIBLE else View.GONE
    }

    private fun refreshRules() {
        rulesContainer.removeAllViews()
        BlockListRepository.getCustomDomains(this).sorted().forEach { d ->
            addRuleRow("\uD83D\uDEAB  $d") {
                BlockListRepository.removeCustomDomain(this, d); reloadService(); refreshRules()
            }
        }
        BlockListRepository.getWhitelist(this).sorted().forEach { d ->
            addRuleRow("\u2705  $d") {
                BlockListRepository.removeWhitelistDomain(this, d); reloadService(); refreshRules()
            }
        }
        if (rulesContainer.childCount == 0) {
            TextView(this).apply {
                text = "Aucune règle personnalisée pour l'instant."
                rulesContainer.addView(this)
            }
        }
    }

    private fun addRuleRow(label: String, onRemove: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tv = TextView(this).apply {
            text = label
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
