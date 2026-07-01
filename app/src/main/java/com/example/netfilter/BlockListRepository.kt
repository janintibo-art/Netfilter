package com.example.netfilter

import android.content.Context
import java.net.URL

/**
 * Gère règles de filtrage ET réglages (résolveur amont, démarrage au boot).
 */
object BlockListRepository {

    private const val PREFS = "netfilter_prefs"
    private const val KEY_CUSTOM = "custom_domains"
    private const val KEY_WHITELIST = "whitelist_domains"
    private const val KEY_SOURCES = "list_sources"
    private const val KEY_BLOCK_DOH = "block_doh"
    private const val KEY_UPSTREAM = "upstream_dns"
    private const val KEY_START_ON_BOOT = "start_on_boot"
    private const val KEY_EXCLUDED_APPS = "excluded_apps"
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val CACHE_FILE = "downloaded_blocklist.txt"

    const val DEFAULT_UPSTREAM = "8.8.8.8"

    fun load(context: Context): Set<String> {
        val result = HashSet<String>()
        runCatching {
            context.assets.open("blocklist.txt").bufferedReader().forEachLine {
                parseLine(it)?.let(result::add)
            }
        }
        runCatching {
            context.openFileInput(CACHE_FILE).bufferedReader().forEachLine {
                parseLine(it)?.let(result::add)
            }
        }
        if (isDohBlockingEnabled(context)) {
            runCatching {
                context.assets.open("doh-blocklist.txt").bufferedReader().forEachLine {
                    parseLine(it)?.let(result::add)
                }
            }
        }
        result.addAll(getCustomDomains(context))
        return result
    }

    private fun parseLine(raw: String): String? {
        var line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) return null
        if (line.startsWith("0.0.0.0") || line.startsWith("127.0.0.1")) {
            line = line.substringAfter(' ').trim().substringBefore(' ')
        }
        line = line.substringBefore('#').trim()
        return if (line.isNotEmpty() && line.contains('.')) line.lowercase() else null
    }

    // --- Domaines bloqués perso ---
    fun getCustomDomains(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_CUSTOM, emptySet()) ?: emptySet()

    fun addCustomDomain(context: Context, domain: String) {
        val clean = parseLine(domain) ?: return
        val set = getCustomDomains(context).toMutableSet().apply { add(clean) }
        prefs(context).edit().putStringSet(KEY_CUSTOM, set).apply()
    }

    fun removeCustomDomain(context: Context, domain: String) {
        val set = getCustomDomains(context).toMutableSet().apply { remove(domain.lowercase()) }
        prefs(context).edit().putStringSet(KEY_CUSTOM, set).apply()
    }

    // --- Liste blanche ---
    fun getWhitelist(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_WHITELIST, emptySet()) ?: emptySet()

    fun addWhitelistDomain(context: Context, domain: String) {
        val clean = parseLine(domain) ?: return
        val set = getWhitelist(context).toMutableSet().apply { add(clean) }
        prefs(context).edit().putStringSet(KEY_WHITELIST, set).apply()
    }

    fun removeWhitelistDomain(context: Context, domain: String) {
        val set = getWhitelist(context).toMutableSet().apply { remove(domain.lowercase()) }
        prefs(context).edit().putStringSet(KEY_WHITELIST, set).apply()
    }

    // --- Blocage DoH ---
    fun isDohBlockingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLOCK_DOH, true)

    fun setDohBlocking(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BLOCK_DOH, enabled).apply()
    }

    // --- Résolveur DNS amont ---
    fun getUpstreamDns(context: Context): String =
        prefs(context).getString(KEY_UPSTREAM, DEFAULT_UPSTREAM) ?: DEFAULT_UPSTREAM

    fun setUpstreamDns(context: Context, ip: String) {
        prefs(context).edit().putString(KEY_UPSTREAM, ip).apply()
    }

    // --- Démarrage au boot ---
    fun isStartOnBoot(context: Context): Boolean =
        prefs(context).getBoolean(KEY_START_ON_BOOT, false)

    fun setStartOnBoot(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_START_ON_BOOT, enabled).apply()
    }

    // --- Apps exclues du filtrage (packages qui contournent le VPN) ---
    fun getExcludedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_EXCLUDED_APPS, emptySet()) ?: emptySet()

    fun setExcludedApps(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_EXCLUDED_APPS, HashSet(packages)).apply()
    }

    // --- Mise à jour automatique des listes ---
    fun isAutoUpdateEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_UPDATE, false)

    fun setAutoUpdate(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    // --- Listes distantes ---
    fun getSources(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SOURCES, defaultSources()) ?: defaultSources()

    fun addSource(context: Context, url: String) {
        val set = getSources(context).toMutableSet().apply { add(url) }
        prefs(context).edit().putStringSet(KEY_SOURCES, set).apply()
    }

    fun refreshDownloadedLists(context: Context): Int {
        val domains = LinkedHashSet<String>()
        for (url in getSources(context)) {
            runCatching {
                URL(url).openStream().bufferedReader().forEachLine {
                    parseLine(it)?.let(domains::add)
                }
            }
        }
        context.openFileOutput(CACHE_FILE, Context.MODE_PRIVATE).bufferedWriter().use { w ->
            domains.forEach { w.appendLine(it) }
        }
        return domains.size
    }

    private fun defaultSources() = setOf(
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
