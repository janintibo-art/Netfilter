package com.example.netfilter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import kotlin.concurrent.thread

/**
 * Choix des listes de blocage à télécharger. L'utilisateur coche les listes voulues,
 * puis appuie sur « Télécharger et appliquer » : les listes activées sont récupérées,
 * fusionnées, et appliquées immédiatement si le filtrage tourne.
 */
class ListsActivity : Activity() {

    private lateinit var progress: TextView
    private lateinit var applyButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lists)

        progress = findViewById(R.id.lists_progress)
        applyButton = findViewById(R.id.lists_apply)
        findViewById<ListView>(R.id.lists_list).adapter = CatalogAdapter()

        applyButton.setOnClickListener { applyLists() }
    }

    private fun applyLists() {
        applyButton.isEnabled = false
        progress.visibility = View.VISIBLE
        progress.text = "Téléchargement des listes en cours…"
        thread {
            val count = BlockListRepository.refreshDownloadedLists(this)
            runOnUiThread {
                progress.text = "$count domaines chargés."
                applyButton.isEnabled = true
                if (FilterVpnService.isRunning) {
                    startService(
                        Intent(this, FilterVpnService::class.java)
                            .setAction(FilterVpnService.ACTION_RELOAD)
                    )
                }
                Toast.makeText(this, "Listes appliquées ($count domaines)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private inner class CatalogAdapter : BaseAdapter() {
        private val items = BlockListRepository.CATALOG

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
            check.isChecked = BlockListRepository.isListEnabled(this@ListsActivity, item.id)
            check.setOnCheckedChangeListener { _, isChecked ->
                BlockListRepository.setListEnabled(this@ListsActivity, item.id, isChecked)
            }

            view.setOnClickListener { check.toggle() }
            return view
        }
    }
}
