package com.example.netfilter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView

/**
 * Filtres thématiques : des listes de blocage intégrées, activables d'un simple appui.
 * Chaque changement s'applique immédiatement (rechargement à chaud si le filtrage tourne).
 */
class ThemesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_themes)
        findViewById<ListView>(R.id.themes_list).adapter = ThemesAdapter()
    }

    private fun reloadIfRunning() {
        if (FilterVpnService.isRunning) {
            startService(
                Intent(this, FilterVpnService::class.java).setAction(FilterVpnService.ACTION_RELOAD)
            )
        }
    }

    private inner class ThemesAdapter : BaseAdapter() {
        private val items = BlockListRepository.THEMES

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_list, parent, false)
            val item = items[position]

            view.findViewById<TextView>(R.id.list_name).text = item.name
            view.findViewById<TextView>(R.id.list_desc).text = item.description

            val check = view.findViewById<CheckBox>(R.id.list_check)
            check.setOnCheckedChangeListener(null)
            check.isChecked = BlockListRepository.isThemeEnabled(this@ThemesActivity, item.id)
            check.setOnCheckedChangeListener { _, isChecked ->
                BlockListRepository.setThemeEnabled(this@ThemesActivity, item.id, isChecked)
                reloadIfRunning()
            }

            view.setOnClickListener { check.toggle() }
            return view
        }
    }
}
