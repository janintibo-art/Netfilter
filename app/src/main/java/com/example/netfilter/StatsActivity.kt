package com.example.netfilter

import android.app.Activity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView

/**
 * Affiche le nombre total de requêtes bloquées et le classement des domaines
 * les plus bloqués. Lit directement StatsStore (même processus que le service).
 */
class StatsActivity : Activity() {

    private lateinit var totalText: TextView
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        totalText = findViewById(R.id.stats_total)
        val list = findViewById<ListView>(R.id.stats_list)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        list.adapter = adapter

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
        val items = StatsStore.top(100).map { "${it.second}×   ${it.first}" }
        adapter.clear()
        adapter.addAll(items)
        adapter.notifyDataSetChanged()
    }
}
