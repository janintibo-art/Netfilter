package com.example.netfilter

import android.content.Context
import java.net.URL

/** Une liste de blocage téléchargeable du catalogue. */
data class ListSource(
    val id: String,
    val name: String,
    val description: String,
    val url: String
)

/** Un filtre thématique intégré (liste embarquée dans l'app), activable par l'utilisateur. */
data class ThemeFilter(
    val id: String,
    val name: String,
    val description: String,
    val asset: String
)

/**
 * Gère toutes les règles de filtrage ET les réglages.
 * Sources : catalogue de listes activables, listes perso (URL), blocage DoH,
 * blocage des médias Bolloré, domaines perso, et liste blanche (traitée par le service).
 */
object BlockListRepository {

    private const val PREFS = "netfilter_prefs"
    private const val KEY_CUSTOM = "custom_domains"
    private const val KEY_WHITELIST = "whitelist_domains"
    private const val KEY_SOURCES = "list_sources"
    private const val KEY_ENABLED_LISTS = "enabled_lists"
    private const val KEY_BLOCK_DOH = "block_doh"
    private const val KEY_BLOCK_BOLLORE = "block_bollore"
    private const val KEY_ENABLED_THEMES = "enabled_themes"
    private const val KEY_UPSTREAM = "upstream_dns"
    private const val KEY_START_ON_BOOT = "start_on_boot"
    private const val KEY_EXCLUDED_APPS = "excluded_apps"
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val CACHE_FILE = "downloaded_blocklist.txt"

    const val DEFAULT_UPSTREAM = "8.8.8.8"

    /** Catalogue de listes proposées (URL vérifiées, format hosts / domaines / Adblock). */
    val CATALOG = listOf(
        ListSource(
            "stevenblack", "StevenBlack (base)",
            "Pub + traqueurs + malware. Bon point de départ.",
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        ),
        ListSource(
            "hagezi_light", "HaGeZi Light",
            "Légère, quasi aucun faux positif.",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/light.txt"
        ),
        ListSource(
            "hagezi_normal", "HaGeZi Normal",
            "Équilibrée. Recommandée pour un usage courant.",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/normal.txt"
        ),
        ListSource(
            "hagezi_pro", "HaGeZi Pro",
            "Agressive, meilleure protection (peut casser quelques sites).",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.txt"
        ),
        ListSource(
            "phishing_army", "Phishing Army",
            "Anti-hameçonnage et sites malveillants.",
            "https://phishing.army/download/phishing_army_blocklist_extended.txt"
        )
    )

    /** Filtres thématiques intégrés (listes embarquées, désactivés par défaut). */
    val THEMES = listOf(
        ThemeFilter(
            "bollore", "Médias du groupe Bolloré",
            "CNews, Europe 1, JDD, Canal+, Dailymotion, magazines Prisma…",
            "bollore-blocklist.txt"
        ),
        ThemeFilter(
            "farright", "Médias d'extrême droite",
            "Titres souvent décrits comme d'extrême droite. Catégorie subjective.",
            "farright-blocklist.txt"
        ),
        ThemeFilter(
            "multinationals", "Multinationales",
            "Sites de grandes marques (hors géants du web pour ne rien casser).",
            "multinationals-blocklist.txt"
        ),
        ThemeFilter(
            "football", "Football",
            "Sites de foot et de mercato.",
            "football-blocklist.txt"
        ),
        ThemeFilter(
            "sport", "Sport (général)",
            "Sites de sport toutes disciplines.",
            "sport-blocklist.txt"
        )
    )

    /** Ensemble des domaines à BLOQUER (la liste blanche est gérée à part par le service). */
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
        for (theme in THEMES) {
            if (isThemeEnabled(context, theme.id)) {
                runCatching {
                    context.assets.open(theme.asset).bufferedReader().forEachLine {
                        parseLine(it)?.let(result::add)
                    }
                }
            }
        }
        result.addAll(getCustomDomains(context))
        return result
    }

    /**
     * Nettoie une ligne. Gère : format hosts ("0.0.0.0 domaine"), domaine simple,
     * et format Adblock ("||domaine^"). Ignore commentaires, règles cosmétiques,
     * règles d'exception (@@), regex et jokers.
     */
    private fun parseLine(raw: String): String? {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) return null
        if (line.startsWith("@@")) return null // règle d'autorisation Adblock
        if (line.contains("##") || line.contains("#@#") || line.contains("#?#") || line.contains("#$#")) {
            return null // règle cosmétique Adblock
        }
        if (line.startsWith("/")) return null // regex Adblock

        var domain: String = if (line.startsWith("||")) {
            // format Adblock réseau : ||domaine^...
            var d = line.substring(2)
            val cut = d.indexOfFirst { it == '^' || it == '/' || it == '$' || it == '|' || it == '*' || it == '?' || it == ' ' }
            if (cut >= 0) d = d.substring(0, cut)
            d
        } else if (line.startsWith("0.0.0.0") || line.startsWith("127.0.0.1")) {
            // format hosts
            line.substringAfter(' ').trim().substringBefore(' ').substringBefore('\t')
        } else {
            // domaine simple par ligne (on retire un éventuel commentaire en fin)
            line.substringBefore('#').trim().substringBefore(' ').substringBefore('\t')
        }

        domain = domain.trim().lowercase()
        val valid = domain.isNotEmpty() &&
            domain.contains('.') &&
            !domain.contains('*') &&
            !domain.contains('/') &&
            !domain.contains(' ')
        return if (valid) domain else null
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

    // --- Catalogue de listes ---
    fun getEnabledListIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ENABLED_LISTS, setOf("stevenblack")) ?: setOf("stevenblack")

    fun isListEnabled(context: Context, id: String): Boolean =
        getEnabledListIds(context).contains(id)

    fun setListEnabled(context: Context, id: String, enabled: Boolean) {
        val set = getEnabledListIds(context).toMutableSet()
        if (enabled) set.add(id) else set.remove(id)
        prefs(context).edit().putStringSet(KEY_ENABLED_LISTS, set).apply()
    }

    // --- Blocage DoH ---
    fun isDohBlockingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLOCK_DOH, true)

    fun setDohBlocking(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BLOCK_DOH, enabled).apply()
    }

    // --- Filtres thématiques (listes intégrées, activables) ---
    fun getEnabledThemeIds(context: Context): Set<String> {
        val p = prefs(context)
        // Migration : reprend l'ancien réglage "Bolloré" (v1.5) si les thèmes n'existent pas encore.
        if (!p.contains(KEY_ENABLED_THEMES) && p.getBoolean(KEY_BLOCK_BOLLORE, false)) {
            return setOf("bollore")
        }
        return p.getStringSet(KEY_ENABLED_THEMES, emptySet()) ?: emptySet()
    }

    fun isThemeEnabled(context: Context, id: String): Boolean =
        getEnabledThemeIds(context).contains(id)

    fun setThemeEnabled(context: Context, id: String, enabled: Boolean) {
        val set = getEnabledThemeIds(context).toMutableSet()
        if (enabled) set.add(id) else set.remove(id)
        prefs(context).edit().putStringSet(KEY_ENABLED_THEMES, set).apply()
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

    // --- Apps exclues du filtrage ---
    fun getExcludedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_EXCLUDED_APPS, emptySet()) ?: emptySet()

    fun setExcludedApps(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_EXCLUDED_APPS, HashSet(packages)).apply()
    }

    // --- Mise à jour automatique ---
    fun isAutoUpdateEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_UPDATE, false)

    fun setAutoUpdate(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    // --- URL perso (avancé) ---
    fun getSources(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SOURCES, emptySet()) ?: emptySet()

    fun addSource(context: Context, url: String) {
        val set = getSources(context).toMutableSet().apply { add(url) }
        prefs(context).edit().putStringSet(KEY_SOURCES, set).apply()
    }

    /**
     * Télécharge les listes activées du catalogue (+ URL perso) et met le cache à jour.
     * À appeler hors du thread principal. Renvoie le nombre de domaines chargés.
     */
    fun refreshDownloadedLists(context: Context): Int {
        val domains = LinkedHashSet<String>()
        val urls = LinkedHashSet<String>()
        CATALOG.filter { isListEnabled(context, it.id) }.forEach { urls.add(it.url) }
        urls.addAll(getSources(context))

        for (url in urls) {
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

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
