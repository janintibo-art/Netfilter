package com.example.netfilter

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/**
 * Onglet « Script navigateur » : explique le userscript Tampermonkey (compagnon de l'app
 * pour bloquer des sites entiers et les masquer des recherches), et permet de le récupérer :
 *   - « Copier le script » -> presse-papier (le plus simple : coller dans Tampermonkey)
 *   - « Enregistrer le fichier » -> enregistre netfilter-web.user.js où l'utilisateur veut
 */
class ScriptActivity : Activity() {

    companion object {
        private const val REQ_SAVE = 200
        private const val ASSET = "netfilter-web.user.js"
        private const val TM_URL = "https://addons.mozilla.org/firefox/addon/tampermonkey/"
    }

    private var scriptText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script)

        scriptText = runCatching {
            assets.open(ASSET).bufferedReader().use { it.readText() }
        }.getOrDefault("")

        findViewById<TextView>(R.id.script_tuto).text = tutorial()

        findViewById<Button>(R.id.script_install_tm).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TM_URL)))
        }

        findViewById<Button>(R.id.script_copy).setOnClickListener {
            if (scriptText.isEmpty()) {
                Toast.makeText(this, "Script introuvable", Toast.LENGTH_SHORT).show()
            } else {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("NetFilter Web", scriptText))
                Toast.makeText(this, "Script copié — colle-le dans Tampermonkey", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<Button>(R.id.script_save).setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/javascript"
                putExtra(Intent.EXTRA_TITLE, ASSET)
            }
            startActivityForResult(intent, REQ_SAVE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SAVE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            val ok = runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(scriptText.toByteArray()) }
            }.isSuccess
            Toast.makeText(
                this,
                if (ok) "Fichier enregistré" else "Échec de l'enregistrement",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun tutorial(): String = """
        NetFilter Web est un petit script qui complète l'app dans ton navigateur. Il fait ce que le filtrage DNS ne peut pas :

        • Bloquer l'accès à des sites entiers (Bolloré, extrême droite, multinationales, foot, sport) — même quand le navigateur chiffre son DNS.
        • Les faire disparaître des résultats de recherche (Google, Bing, DuckDuckGo, Qwant…).

        ⚠️ Il ne remplace pas le blocage pub/traqueurs : pour ça, garde l'app NetFilter et/ou ajoute uBlock Origin. Les deux se complètent.

        ── INSTALLATION (environ 5 min) ──

        1. Sur Firefox Android, installe l'extension « Tampermonkey » (bouton ci-dessous).

        2. Récupère le script : appuie sur « Copier le script » (le plus simple), ou « Enregistrer le fichier ».

        3. Dans Firefox : menu ⋮ → Extensions → Tampermonkey → « Créer un nouveau script ». Efface tout ce qui s'affiche, colle le script, puis enregistre (icône disquette ou Fichier → Enregistrer).

        4. Active les catégories : pendant ta navigation, appuie sur l'icône Tampermonkey → coche « Médias du groupe Bolloré » et/ou les autres. La page se recharge, c'est actif.

        ── C'est fait ! ──

        Teste en tapant cnews.fr dans la barre d'adresse : tu devrais voir une page « Site bloqué ». Fais une recherche Google sur « cnews » : les résultats du site doivent avoir disparu.

        ── Téléphone ou ordinateur ? ──

        Le script propose aussi (menu Tampermonkey → « Identité ») de te faire passer pour un ordinateur 🖥️ ou un téléphone 📱. C'est du « meilleur effort » : certains sites peuvent l'ignorer. Pour un résultat GARANTI, utilise en plus le mode intégré de Firefox : menu ⋮ → « Site pour ordinateur ». Les deux se combinent bien.

        Astuce : les listes de domaines sont écrites en haut du script. Tu peux les modifier directement dans Tampermonkey (onglet du script) pour ajouter ou retirer des sites.
    """.trimIndent()
}
