package com.example.netfilter

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import kotlin.concurrent.thread

/**
 * Liste les apps qui accèdent à Internet et permet de cocher celles à EXCLURE du
 * filtrage (elles contourneront le VPN). Le tunnel est reconstruit à la sortie de
 * l'écran si le filtrage tourne, car la liste des apps exclues n'est prise en compte
 * qu'au moment où le tunnel est établi.
 */
class AppsActivity : Activity() {

    private data class AppItem(val pkg: String, val label: String, val info: ApplicationInfo)

    private val excluded = HashSet<String>()
    private var changed = false
    private lateinit var listView: ListView
    private lateinit var loadingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        listView = findViewById(R.id.apps_list)
        loadingText = findViewById(R.id.apps_loading)

        excluded.addAll(BlockListRepository.getExcludedApps(this))

        // Chargement de la liste sur un thread séparé (peut être long).
        thread {
            val pm = packageManager
            val items = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .asSequence()
                .filter { it.packageName != packageName }
                .filter {
                    pm.checkPermission(android.Manifest.permission.INTERNET, it.packageName) ==
                        PackageManager.PERMISSION_GRANTED
                }
                .map { AppItem(it.packageName, pm.getApplicationLabel(it).toString(), it) }
                .sortedBy { it.label.lowercase() }
                .toList()

            runOnUiThread {
                loadingText.visibility = View.GONE
                listView.adapter = AppsAdapter(items)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Applique les changements : reconstruit le tunnel si le filtrage tourne.
        if (changed && FilterVpnService.isRunning) {
            startService(
                Intent(this, FilterVpnService::class.java).setAction(FilterVpnService.ACTION_RESTART)
            )
            changed = false
        }
    }

    private inner class AppsAdapter(val items: List<AppItem>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
            val item = items[position]

            view.findViewById<ImageView>(R.id.app_icon)
                .setImageDrawable(item.info.loadIcon(packageManager))
            view.findViewById<TextView>(R.id.app_name).text = item.label

            val check = view.findViewById<CheckBox>(R.id.app_check)
            check.setOnCheckedChangeListener(null) // évite un déclenchement lors du recyclage
            check.isChecked = excluded.contains(item.pkg)
            check.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) excluded.add(item.pkg) else excluded.remove(item.pkg)
                BlockListRepository.setExcludedApps(this@AppsActivity, excluded)
                changed = true
            }

            view.setOnClickListener { check.toggle() }
            return view
        }
    }
}
