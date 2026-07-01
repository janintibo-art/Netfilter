package com.example.netfilter

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * Statistiques : total bloqué + classement des domaines les plus bloqués.
 * On peut filtrer par recherche, et appuyer sur un domaine pour l'ajouter à la
 * liste blanche (le débloquer) en un geste.
 */
class StatsActivity : Activity() {

    private lateinit var totalText: TextView
    private lateinit var adapter: ArrayAdapter<String>
    private var allEntries: List<Pair<String, Int>> = emptyList()
    private var shown: List<Pair<String, Int>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        totalText = findViewById(R.id.stats_total)
        val list = findViewById<ListView>(R.id.stats_list)
        val search = findViewById<EditText>(R.id.stats_search)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        list.adapter = adapter

        list.setOnItemClickListener { _, _, position, _ ->
            shown.getOrNull(position)?.let { promptWhitelist(it.first) }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                applyFilter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<Button>(R.id.stats_reset).setOnClickListener {
            StatsStore.reset(this)
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        totalText.text = "${StatsStore.totalBlocked()} requêtes bloquées"
        allEntries = StatsStore.top(500)
        applyFilter(findViewById<EditText>(R.id.stats_search).text?.toString() ?: "")
    }

    private fun applyFilter(query: String) {
        val q = query.trim().lowercase()
        shown = if (q.isEmpty()) allEntries else allEntries.filter { it.first.contains(q) }
        adapter.clear()
        adapter.addAll(shown.map { "${it.second}×   ${it.first}" })
        adapter.notifyDataSetChanged()
    }

    private fun promptWhitelist(domain: String) {
        AlertDialog.Builder(this)
            .setTitle("Débloquer ce domaine ?")
            .setMessage("Ajouter « $domain » à la liste blanche ? Il ne sera plus jamais bloqué.")
            .setPositiveButton("Ajouter") { _, _ ->
                BlockListRepository.addWhitelistDomain(this, domain)
                if (FilterVpnService.isRunning) {
                    startService(
                        Intent(this, FilterVpnService::class.java)
                            .setAction(FilterVpnService.ACTION_RELOAD)
                    )
                }
                Toast.makeText(this, "$domain ajouté à la liste blanche", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
