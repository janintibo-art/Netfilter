# NetFilter — filtre réseau + widget anti-pub pour Android

Base de projet pour une app qui filtre le trafic du téléphone (bloqueur de pub par DNS)
avec un widget marche/arrêt. **Sans root.**








## Nouveautés (v1.7)

- **Onglet « Script navigateur »** — nouvel écran (bouton sur l'écran principal) avec un
  mode d'emploi et le userscript **NetFilter Web** embarqué. Il permet de bloquer des sites
  entiers dans le navigateur ET de les masquer des résultats de recherche (ce que le filtrage
  DNS ne peut pas faire). Deux façons de le récupérer :
  - **Copier le script** (presse-papier) → à coller dans Tampermonkey (le plus simple) ;
  - **Enregistrer le fichier** `.user.js` où l'on veut (sélecteur système, aucune permission).
  Plus un bouton pour installer Tampermonkey sur Firefox. Le script reprend les 5 catégories
  thématiques + liste blanche + domaines perso.
  *Rappel : ce script ne bloque pas la pub/les traqueurs (utiliser l'app NetFilter et/ou uBlock
  Origin pour ça) ; il gère le blocage de sites entiers et le masquage dans les recherches.*

## Nouveautés (v1.6)

- **Écran « Filtres thématiques »** — regroupe des catégories entières, activables d'un appui
  (appliquées immédiatement). Le blocage Bolloré y a été déplacé, et 4 catégories s'ajoutent :
  - **Médias d'extrême droite** — titres souvent décrits ainsi par la presse et la recherche
    (Valeurs actuelles, Frontières, Causeur, Boulevard Voltaire, Fdesouche… + quelques
    internationaux). **Catégorie subjective et contestée** : c'est un classement éditorial,
    pas un fait objectif. Liste dans `assets/farright-blocklist.txt`, à modifier librement.
  - **Multinationales** — sites de grandes marques (McDo, Nestlé, Coca-Cola, Nike, Shell…).
    Les **géants du web** (Google, Amazon, Apple, Microsoft, Meta…) sont **volontairement exclus**
    car les bloquer au DNS casse une grande partie d'Internet. `assets/multinationals-blocklist.txt`.
  - **Football** — sites de foot et de mercato. `assets/football-blocklist.txt`.
  - **Sport (général)** — sites de sport toutes disciplines. `assets/sport-blocklist.txt`.

  Toutes ces listes sont **éditables** (fichiers dans `app/src/main/assets/`). Un domaine
  bloqué à tort peut aussi être mis en liste blanche depuis l'écran principal.

## Nouveautés (v1.5)

- **Choix des listes de blocage** — nouvel écran « Choisir les listes de blocage » avec un
  catalogue de listes activables (cases à cocher), puis un bouton « Télécharger et appliquer » :
  - StevenBlack (pub + traqueurs + malware) — le défaut
  - HaGeZi Light / Normal / Pro (du plus léger au plus agressif)
  - Phishing Army (anti-hameçonnage / sites malveillants)
  Toutes les URL ont été vérifiées. Tu peux en cumuler plusieurs.
- **Bloqueur médias Bolloré** — interrupteur (désactivé par défaut) qui bloque les sites des
  médias du groupe Bolloré (CNews, Europe 1, JDD, Canal+, C8, Dailymotion, et les magazines
  Prisma : Gala, Capital, Géo, Voici, Femme Actuelle…). La liste est dans
  `assets/bollore-blocklist.txt` et se veut **éditable** : l'actionnariat évolue (scission de
  Vivendi fin 2024) et certains titres ne sont détenus que partiellement — ajoute ou retire
  des domaines à ta convenance, ou mets-en un en liste blanche.
- **Analyseur plus robuste** — les listes au format « Adblock » (`||domaine^`) sont désormais
  comprises, en plus des formats hosts et « un domaine par ligne ».

## Nouveautés (v1.4) — durcissement

- **Support IPv6** — les requêtes AAAA sont désormais filtrées (le tunnel gère IPv4 et
  IPv6). Un domaine bloqué ne se recharge plus « par la porte IPv6 » sur un réseau double-pile.
- **Réponses adaptées au type** — un domaine bloqué renvoie `0.0.0.0` pour une requête A
  et `::` pour une requête AAAA (au lieu d'une réponse A dans tous les cas).
- **Cache DNS** — les réponses sont mises en cache (TTL borné 30 s–1 h) : moins de latence
  et moins de trafic vers le résolveur.
- **Envoi amont fiabilisé** — délai d'attente de 5 s vers le résolveur (plus de blocage
  possible si le serveur ne répond pas).

Restent non gérés (documentés) : le **DNS sur TCP**, les **en-têtes d'extension IPv6**, et
le **DoH** (contré séparément par la liste de blocage des résolveurs chiffrés).

## Nouveautés (v1.3)

- **Filtrage par app** — écran « Apps exclues du filtrage » : cochez les apps qui doivent
  contourner le filtre (utile quand une app bugue avec le blocage DNS, ex. une banque).
  Le tunnel est reconstruit automatiquement à la sortie de l'écran pour appliquer le choix.
  *Nécessite la permission `QUERY_ALL_PACKAGES` pour lister les apps : sans souci pour une
  app installée manuellement, mais interdite de publication sur le Play Store telle quelle.*
- **Mise à jour automatique des listes** — interrupteur « Mise à jour auto (quotidienne) » :
  rafraîchit les listes communautaires une fois par jour en tâche de fond (via WorkManager,
  quand une connexion est disponible), et applique aussitôt si le filtrage tourne.

## Nouveautés (v1.2)

- **Tuile Réglages rapides** — active/coupe le filtrage depuis le volet de notifications
  (à ajouter une fois via l'éditeur de tuiles Android).
- **Notification avec actions** — boutons *Pause 5 min* et *Arrêter* directement sur la
  notification permanente.
- **Démarrage au boot** (optionnel) — relance le filtrage au démarrage du téléphone si
  l'autorisation VPN a déjà été donnée. *Si votre constructeur bloque ce démarrage, activez
  plutôt le « VPN permanent / Always-on VPN » dans les réglages système Android, plus fiable.*
- **Choix du résolveur amont** — Google, Cloudflare, Quad9 (bloque les malwares) ou AdGuard
  (bloque déjà la pub) : un filtrage supplémentaire gratuit, côté serveur.
- **Durées de pause** — 5 / 15 / 60 minutes.
- **Icône dédiée** et démarrage durci (évite les plantages liés aux restrictions Android 12+).

## Nouveautés (v1.1)

- **Liste blanche** — un domaine « autorisé » n'est jamais bloqué, même s'il figure
  dans une liste. Priorité absolue. Idéal quand un blocage casse une app.
- **Pause 5 min** — suspend tout le filtrage temporairement (bouton visible quand actif).
- **Blocage du DNS chiffré (DoH)** — interrupteur qui bloque les résolveurs DoH connus
  (`assets/doh-blocklist.txt`) pour empêcher les navigateurs de contourner le filtre.
  *Heuristique : certains navigateurs gardent des IP de secours et peuvent parfois passer outre.*
- **Statistiques** — total de requêtes bloquées + classement des domaines les plus bloqués
  (bouton « Voir les statistiques »).
- **Rechargement à chaud** — ajouter/retirer une règle prend effet immédiatement, sans couper le VPN.

## Comment ça marche

Android ne laisse pas une app lire le trafic des autres apps directement. La seule porte
officielle est le `VpnService` : on monte un VPN **local** (aucune donnée ne sort vers un
serveur externe), et le système nous fait passer les paquets.

Ici on n'intercepte **que le DNS** (le plus efficace pour bloquer la pub, et économe en
batterie) :

1. On déclare un DNS "virtuel" `10.0.0.1` et on route uniquement lui dans le tunnel.
2. Chaque requête DNS arrive donc chez nous. On lit le domaine demandé.
3. Domaine dans la liste noire → on répond `0.0.0.0` (la pub ne se charge pas).
4. Sinon → on transmet à un vrai résolveur (`8.8.8.8`) et on renvoie sa réponse.

C'est l'approche de **DNS66** et **Blokada**.

## Fichiers

| Fichier | Rôle |
|---|---|
| `FilterVpnService.kt` | Le cœur : monte le VPN, lit les paquets, décide bloquer/transmettre |
| `DnsPacket.kt` | Parsing DNS + fabrication des paquets IP/UDP + checksums |
| `BlockListRepository.kt` | Listes de blocage (fichier local, listes distantes, filtres perso) |
| `FilterWidgetProvider.kt` | Le widget interrupteur |
| `MainActivity.kt` | Autorisation VPN, bouton, ajout de filtres, MàJ des listes |
| `assets/blocklist.txt` | Liste de démarrage (à compléter) |

## Mise en route

1. Ouvre le dossier dans **Android Studio** (nécessite un `settings.gradle.kts` + le plugin
   Gradle Android ; laisse Android Studio générer le wrapper).
2. Branche un téléphone ou lance un émulateur, puis *Run*.
3. Au premier démarrage, Android affiche **une pop-up d'autorisation VPN** : accepte-la.
4. Ajoute le widget sur l'écran d'accueil (appui long → Widgets → NetFilter).

## Ajouter des filtres

- **À la main** : champ « Ajouter un domaine » dans l'app.
- **Listes communautaires** : bouton « Télécharger les listes ». Par défaut la liste
  StevenBlack (pub + tracking, des dizaines de milliers de domaines). Tu peux en ajouter
  d'autres via `BlockListRepository.addSource(...)` — n'importe quel fichier au format
  « hosts » ou « un domaine par ligne » fonctionne.

## Limites de cette base (à améliorer)

Cette version est un socle *lisible*, pas un produit fini. Ce qui manque :

- **DNS sur TCP** et **en-têtes d'extension IPv6** ne sont pas gérés (IPv4/UDP et IPv6/UDP
  simples le sont). La plupart des requêtes passent par UDP, donc l'impact est limité.
- **DoH / DoT** (DNS chiffré, ex. Chrome, certaines apps) contourne le filtrage DNS. Pour le
  contrer il faut bloquer les résolveurs DoH connus, ou passer à l'inspection complète.
- **Filtrage par IP / par URL** : le filtrage DNS bloque un domaine entier, pas une URL
  précise. Pour un vrai « uBlock Origin » avec règles cosmétiques, il faudrait router tout le
  trafic (`.addRoute("0.0.0.0", 0)`) et inspecter/filtrer HTTP — beaucoup plus lourd, et le
  HTTPS n'est pas déchiffrable sans certificat installé.
- Gestion du **cycle de vie** (redémarrage auto, reconnexion) à durcir.

## Aller plus loin : forker un projet existant

Écrire un bloqueur complet et robuste est un vrai chantier. Plutôt que de tout refaire,
regarde ces projets open source (tu peux les étudier ou les forker) :

- **DNS66** — bloqueur DNS minimaliste, exactement ce principe. Idéal pour comprendre.
- **RethinkDNS** — bloqueur + pare-feu moderne et complet (Apache 2.0).
- **NetGuard** — pare-feu sans root, gère le filtrage par app.
- **Blokada** — bloqueur grand public.

Le mien te sert à comprendre le mécanisme ; ces projets te montrent la version aboutie.

---

## Compiler l'APK via GitHub Actions (sans PC, depuis Termux)

Ce projet se compile **tout seul dans le cloud** grâce au fichier
`.github/workflows/build.yml`. Pas besoin d'Android Studio ni du `gradle-wrapper.jar` :
le workflow installe Gradle lui-même. Tu n'as qu'à pousser le code.

### 1. Préparer Termux

```bash
pkg update && pkg install git -y
git config --global user.name "TonNom"
git config --global user.email "ton@email.com"
```

### 2. Créer un dépôt vide sur GitHub

Sur github.com → New repository → nom `netfilter` → **sans** README ni .gitignore
(le projet les contient déjà). Récupère l'URL HTTPS du dépôt.

### 3. Pousser le projet

Depuis le dossier `netfilter` (celui qui contient `settings.gradle.kts`) :

```bash
git init
git add .
git commit -m "Projet NetFilter"
git branch -M main
git remote add origin https://github.com/TON_PSEUDO/netfilter.git
git push -u origin main
```

GitHub te demandera un identifiant : utilise un **token d'accès personnel**
(Settings → Developer settings → Personal access tokens) comme mot de passe.

### 4. Récupérer l'APK

Le push déclenche automatiquement le build. Va dans l'onglet **Actions** du dépôt,
ouvre le workflow en cours, attends le feu vert (quelques minutes), puis télécharge
l'artefact **NetFilter-debug-apk** en bas de la page. Dézippe-le : tu as ton `.apk`.

### 5. Installer

Copie l'APK sur le téléphone, ouvre-le, autorise « installer des applis inconnues ».
C'est une version *debug* (signée avec la clé de debug) : parfait pour un usage perso.

> Astuce : à chaque `git push`, un nouvel APK est refabriqué. Tu peux aussi relancer
> le build à la main depuis Actions → Build APK → « Run workflow ».
