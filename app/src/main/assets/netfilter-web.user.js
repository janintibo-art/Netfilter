// ==UserScript==
// @name         NetFilter Web
// @namespace    netfilter.web
// @version      1.5
// @description  Bloque des catégories de sites, les masque des recherches, cache bannières cookies et clickbait, nettoie les URL, change l'identité, mode lecture — le tout depuis un bouton flottant dans la page. Compagnon navigateur de l'app NetFilter.
// @author       toi
// @match        *://*/*
// @run-at       document-start
// @grant        GM_setValue
// @grant        GM_getValue
// @grant        GM_registerMenuCommand
// @noframes
// ==/UserScript==

(function () {
    'use strict';

    /* ------------------------------------------------------------------ *
     *  CATÉGORIES + LISTES DE DOMAINES
     *  Toutes ces listes sont éditables : ajoute/retire des domaines ici,
     *  ou utilise le menu Tampermonkey (« Ajouter un domaine à bloquer »).
     *
     *  NOTE sur « Médias d'extrême droite » : c'est la catégorie la plus
     *  SUBJECTIVE. Le classement reflète des caractérisations courantes de
     *  la presse/recherche, pas un fait objectif. Modifie à ta convenance.
     * ------------------------------------------------------------------ */
    const CATEGORIES = [
        {
            id: 'bollore',
            name: 'Médias du groupe Bolloré',
            domains: [
                'cnews.fr', 'europe1.fr', 'lejdd.fr', 'canalplus.com', 'canalplus.fr',
                'mycanal.fr', 'cstar.fr', 'c8.fr', 'dailymotion.com',
                'capital.fr', 'gala.fr', 'femmeactuelle.fr', 'geo.fr', 'voici.fr',
                'programme-tv.net', 'teleloisirs.fr', 'cuisineactuelle.fr',
                'caminteresse.fr', 'neonmag.fr', 'prima.fr', 'harpersbazaar.fr',
                'parismatch.com'
            ]
        },
        {
            id: 'farright',
            name: "Médias d'extrême droite (subjectif)",
            domains: [
                'valeursactuelles.com', 'frontieresmedia.fr', 'causeur.fr', 'bvoltaire.fr',
                'fdesouche.com', 'tvlibertes.com', 'ripostelaique.com',
                'egaliteetreconciliation.fr', 'rivarol.com', 'omerta.media', 'frontpopulaire.fr',
                'breitbart.com', 'infowars.com', 'thegatewaypundit.com', 'rebelnews.com',
                'compact-online.de'
            ]
        },
        {
            id: 'multinationals',
            name: 'Multinationales',
            domains: [
                'mcdonalds.com', 'kfc.com', 'burgerking.com', 'subway.com', 'nestle.com',
                'coca-cola.com', 'coca-colacompany.com', 'pepsi.com', 'pepsico.com',
                'danone.com', 'mondelezinternational.com', 'unilever.com', 'kelloggs.com',
                'mars.com', 'redbull.com', 'starbucks.com',
                'nike.com', 'adidas.com', 'zara.com', 'hm.com', 'primark.com', 'uniqlo.com',
                'shein.com', 'gap.com',
                'totalenergies.com', 'shell.com', 'bp.com', 'exxonmobil.com', 'chevron.com',
                'loreal.com', 'pg.com', 'philipmorrisinternational.com', 'nestleusa.com'
            ]
        },
        {
            id: 'football',
            name: 'Football',
            domains: [
                'footmercato.net', 'onzemondial.com', 'maxifoot.fr', 'sofoot.com',
                'football365.fr', 'goal.com', 'transfermarkt.fr', 'transfermarkt.com',
                'fifa.com', 'uefa.com', 'lfp.fr', 'psg.fr', 'om.fr', 'butfootballclub.fr',
                'football-direct.com'
            ]
        },
        {
            id: 'sport',
            name: 'Sport (général)',
            domains: [
                'lequipe.fr', 'eurosport.fr', 'espn.com', 'skysports.com', 'beinsports.com',
                'bein.com', 'dazn.com', 'flashscore.fr', 'flashscore.com', 'livescore.com',
                'sofascore.com', 'sport.fr', 'rmcsport.bfmtv.com', 'sportmail.fr', 'sports.fr'
            ]
        },
        {
            id: 'socialmedia',
            name: 'Réseaux sociaux',
            domains: [
                'facebook.com', 'fb.com', 'fbcdn.net', 'messenger.com', 'instagram.com',
                'cdninstagram.com', 'tiktok.com', 'tiktokcdn.com', 'x.com', 'twitter.com',
                't.co', 'twimg.com', 'snapchat.com', 'reddit.com', 'redd.it', 'pinterest.com',
                'pinterest.fr', 'linkedin.com', 'tumblr.com', 'threads.net', 'twitch.tv'
            ]
        }
    ];

    /* ------------------------------------------------------------------ *
     *  RÉGLAGES (persistés par Tampermonkey)
     * ------------------------------------------------------------------ */
    const K = {
        cat: id => 'nfw-cat-' + id,
        custom: 'nfw-custom',
        whitelist: 'nfw-whitelist',
        blockNav: 'nfw-block-nav',
        hideSearch: 'nfw-hide-search',
        hideAll: 'nfw-hide-all',
        ua: 'nfw-ua',
        cookies: 'nfw-cookies',
        clickbait: 'nfw-clickbait',
        cleanurls: 'nfw-cleanurls'
    };
    const getVal = (k, d) => GM_getValue(k, d);
    const setVal = (k, v) => GM_setValue(k, v);
    const getArr = k => { try { return JSON.parse(getVal(k, '[]')); } catch (e) { return []; } };
    const setArr = (k, a) => setVal(k, JSON.stringify(a));

    /* ------------------------------------------------------------------ *
     *  IDENTITÉ NAVIGATEUR (User-Agent) — « téléphone » ou « ordinateur »
     *  Best-effort : override côté JavaScript. Pour un résultat GARANTI,
     *  utilise aussi le mode « Site pour ordinateur » de Firefox.
     *  (Peut être bloqué par la sécurité de certains sites — CSP.)
     * ------------------------------------------------------------------ */
    function applyIdentity() {
        const mode = getVal(K.ua, 'auto');
        if (mode !== 'desktop' && mode !== 'mobile') return;
        const DESKTOP = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0';
        const MOBILE = 'Mozilla/5.0 (Android 14; Mobile; rv:128.0) Gecko/20100101 Firefox/128.0';
        const ua = mode === 'desktop' ? DESKTOP : MOBILE;
        const platform = mode === 'desktop' ? 'Win32' : 'Linux aarch64';
        const touch = mode === 'desktop' ? 0 : 5;
        const code = '(function(){try{' +
            'Object.defineProperty(Navigator.prototype,"userAgent",{get:function(){return ' + JSON.stringify(ua) + ';},configurable:true});' +
            'Object.defineProperty(Navigator.prototype,"platform",{get:function(){return ' + JSON.stringify(platform) + ';},configurable:true});' +
            'Object.defineProperty(Navigator.prototype,"maxTouchPoints",{get:function(){return ' + touch + ';},configurable:true});' +
            '}catch(e){}})();';
        try {
            const s = document.createElement('script');
            s.textContent = code;
            (document.head || document.documentElement || document).appendChild(s);
            s.remove();
        } catch (e) {}
    }
    applyIdentity();

    const isCatEnabled = id => getVal(K.cat(id), false) === true;
    const blockNavOn = () => getVal(K.blockNav, true) === true;
    const hideSearchOn = () => getVal(K.hideSearch, true) === true;
    const hideAllOn = () => getVal(K.hideAll, false) === true;
    const cookiesOn = () => getVal(K.cookies, true) === true;
    const clickbaitOn = () => getVal(K.clickbait, true) === true;
    const cleanUrlsOn = () => getVal(K.cleanurls, true) === true;

    /* ------------------------------------------------------------------ *
     *  ENSEMBLES DE DOMAINES (bloqués / liste blanche)
     * ------------------------------------------------------------------ */
    function buildBlockedSet() {
        const set = new Set();
        for (const cat of CATEGORIES) {
            if (isCatEnabled(cat.id)) cat.domains.forEach(d => set.add(d.toLowerCase()));
        }
        getArr(K.custom).forEach(d => set.add(String(d).toLowerCase()));
        return set;
    }
    let blockedSet = buildBlockedSet();
    let whitelistSet = new Set(getArr(K.whitelist).map(d => String(d).toLowerCase()));

    function rebuildSets() {
        blockedSet = buildBlockedSet();
        whitelistSet = new Set(getArr(K.whitelist).map(d => String(d).toLowerCase()));
    }

    function matchesSet(host, set) {
        if (set.size === 0) return false;
        host = host.toLowerCase();
        if (set.has(host)) return true;
        let idx = host.indexOf('.');
        while (idx !== -1) {
            if (set.has(host.slice(idx + 1))) return true;
            idx = host.indexOf('.', idx + 1);
        }
        return false;
    }

    function hasSessionBypass(host) {
        try { return sessionStorage.getItem('nfw-bypass-' + host) === '1'; }
        catch (e) { return false; }
    }

    function isBlocked(host) {
        if (!host) return false;
        host = host.toLowerCase();
        if (hasSessionBypass(host)) return false;
        if (matchesSet(host, whitelistSet)) return false;
        return matchesSet(host, blockedSet);
    }

    /* ------------------------------------------------------------------ *
     *  1) BLOCAGE DE LA NAVIGATION (dès le chargement de la page)
     * ------------------------------------------------------------------ */
    function showBlockPage(host) {
        try { window.stop(); } catch (e) {}
        const render = () => {
            document.documentElement.innerHTML =
                '<head><meta charset="utf-8"><title>Bloqué — NetFilter</title></head>' +
                '<body style="margin:0;height:100vh;display:flex;align-items:center;justify-content:center;' +
                'font-family:system-ui,Segoe UI,Roboto,sans-serif;background:#1b1b1f;color:#eee;text-align:center">' +
                '<div style="max-width:420px;padding:32px">' +
                '<div style="font-size:52px;margin-bottom:8px">🛡️</div>' +
                '<h1 style="font-size:22px;margin:0 0 6px">Site bloqué</h1>' +
                '<p style="opacity:.8;margin:0 0 4px">NetFilter Web a bloqué :</p>' +
                '<p style="font-weight:bold;font-size:18px;margin:0 0 24px;word-break:break-all">' + host + '</p>' +
                '<button id="nfw-back" style="margin:4px;padding:10px 18px;border:0;border-radius:8px;' +
                'background:#2e7d32;color:#fff;font-size:15px;cursor:pointer">Retour</button>' +
                '<button id="nfw-bypass" style="margin:4px;padding:10px 18px;border:1px solid #555;border-radius:8px;' +
                'background:transparent;color:#bbb;font-size:15px;cursor:pointer">Voir quand même</button>' +
                '<p style="opacity:.5;font-size:12px;margin-top:20px">« Voir quand même » débloque ce site jusqu\'à la fermeture de l\'onglet.</p>' +
                '</div></body>';
            const back = document.getElementById('nfw-back');
            if (back) back.addEventListener('click', () => {
                if (history.length > 1) history.back(); else window.location.href = 'about:blank';
            });
            const bypass = document.getElementById('nfw-bypass');
            if (bypass) bypass.addEventListener('click', () => {
                try { sessionStorage.setItem('nfw-bypass-' + host, '1'); } catch (e) {}
                location.reload();
            });
        };
        if (document.documentElement) render();
        else document.addEventListener('readystatechange', function once() {
            if (document.documentElement) { document.removeEventListener('readystatechange', once); render(); }
        });
    }

    if (blockNavOn() && isBlocked(location.hostname)) {
        showBlockPage(location.hostname);
        return; // inutile d'installer le reste sur une page bloquée
    }

    /* ------------------------------------------------------------------ *
     *  2) MASQUAGE DANS LES RÉSULTATS DE RECHERCHE
     * ------------------------------------------------------------------ */
    // Conteneur de résultat à masquer, par moteur (sélecteurs indicatifs).
    const ENGINE_SELECTORS = {
        'google.': 'div.g, div.MjjYud, div.tF2Cxc, div[data-hveid]',
        'bing.': 'li.b_algo, li.b_ans',
        'duckduckgo.': 'article[data-testid="result"], li[data-layout], .result',
        'qwant.': '[data-testid="webResult"], .result, article',
        'ecosia.': '[data-test-id="mainline-result-web"], div.result, article.result',
        'yahoo.': 'div.algo, li.dd',
        'startpage.': '.w-gl__result, .result',
        'search.brave.': '.snippet, [data-type="web"]',
        'lite.duckduckgo.': 'tr'
    };

    function currentEngineSelector() {
        const h = location.hostname.toLowerCase();
        for (const key in ENGINE_SELECTORS) if (h.indexOf(key) !== -1) return ENGINE_SELECTORS[key];
        return null;
    }

    function climb(el, levels) {
        let cur = el;
        for (let i = 0; i < levels && cur && cur.parentElement; i++) cur = cur.parentElement;
        return cur;
    }

    function hostOf(href) {
        try { return new URL(href, location.href).hostname; } catch (e) { return ''; }
    }

    function processResults(engineSelector, hideEverywhere) {
        const links = document.querySelectorAll('a[href]');
        for (const a of links) {
            const host = hostOf(a.href);
            if (!host || !isBlocked(host)) continue;
            let container = null;
            if (engineSelector) container = a.closest(engineSelector);
            if (!container && engineSelector) container = climb(a, 4);
            if (!container && hideEverywhere) container = a; // hors moteur : on masque juste le lien
            if (container && container.style.display !== 'none') {
                container.dataset.nfwHidden = '1';
                container.style.setProperty('display', 'none', 'important');
            }
        }
    }

    function runHiding() {
        const engineSelector = currentEngineSelector();
        const onEngine = !!engineSelector;
        if (!onEngine && !hideAllOn()) return;         // hors moteur : seulement si "masquer partout"
        if (onEngine && !hideSearchOn()) return;        // moteur : seulement si masquage recherche actif
        processResults(engineSelector, !onEngine && hideAllOn());
    }

    /* ------------------------------------------------------------------ *
     *  ANTI-BANNIÈRES COOKIES (masque les fenêtres de consentement)
     * ------------------------------------------------------------------ */
    const COOKIE_SELECTORS = [
        '#onetrust-consent-sdk', '#onetrust-banner-sdk',
        '#CybotCookiebotDialog', '#CybotCookiebotDialogBodyUnderlay',
        '#didomi-host',
        '.qc-cmp2-container', '#qc-cmp2-container',
        '#axeptio_overlay',
        '#tarteaucitronRoot', '#tarteaucitronAlertBig',
        'div[id^="sp_message_container"]',
        '#usercentrics-root', '#uc-banner-modal',
        '#truste-consent-track', '.truste_overlay',
        '#cmplz-cookiebanner-container', '.cmplz-cookiebanner',
        '.cky-consent-container', '.cky-overlay',
        '.osano-cm-window',
        '#cookie-banner', '#cookie-consent', '#cookie-notice', '#cookieConsent',
        '.cookie-banner', '.cookie-consent', '.cookie-notice', '.cookie-law-info-bar',
        '#gdpr-cookie-message', '.gdpr-cookie-notice'
    ];

    function hideCookieBanners() {
        for (const sel of COOKIE_SELECTORS) {
            document.querySelectorAll(sel).forEach(el => {
                el.dataset.nfwHidden = '1';
                el.style.setProperty('display', 'none', 'important');
            });
        }
        // rétablit le défilement souvent bloqué par ces bannières
        document.documentElement.style.setProperty('overflow', 'auto', 'important');
        if (document.body) document.body.style.setProperty('overflow', 'auto', 'important');
    }

    /* ------------------------------------------------------------------ *
     *  MODE LECTURE (à la demande) — épure la page pour ne garder que le texte.
     *  Basique : le mode lecture natif de Firefox est meilleur quand il est proposé.
     * ------------------------------------------------------------------ */
    function enableReadingMode() {
        const pool = document.querySelectorAll(
            'article, main, [role="main"], .article, .post, .entry-content, #content, .content'
        );
        const candidates = pool.length ? pool : document.querySelectorAll('div, section');
        let best = null, bestScore = 0;
        candidates.forEach(el => {
            const text = (el.innerText || '').length;
            const paragraphs = el.querySelectorAll('p').length;
            const score = text + paragraphs * 200;
            if (score > bestScore) { bestScore = score; best = el; }
        });
        if (!best) { alert('Contenu principal introuvable sur cette page.'); return; }
        const html = best.innerHTML;
        const title = document.title || '';
        document.documentElement.innerHTML = '<head><meta charset="utf-8"></head><body></body>';
        document.title = title;
        const style = document.createElement('style');
        style.textContent =
            'body{max-width:720px;margin:24px auto;padding:0 20px;' +
            'font:18px/1.7 Georgia,"Times New Roman",serif;color:#222;background:#fafafa}' +
            'img{max-width:100%;height:auto}a{color:#1a6}h1{font-size:26px;line-height:1.3}';
        document.head.appendChild(style);
        const h1 = document.createElement('h1');
        h1.textContent = title;
        document.body.appendChild(h1);
        const box = document.createElement('div');
        box.innerHTML = html;
        document.body.appendChild(box);
    }

    function processPage() {
        try { runHiding(); } catch (e) {}
        try { if (cookiesOn()) hideCookieBanners(); } catch (e) {}
        try { if (clickbaitOn()) hideClickbait(); } catch (e) {}
        try { if (cleanUrlsOn()) { cleanLinks(); cleanCurrentUrl(); } } catch (e) {}
    }

    /* Masque les encarts de « contenu recommandé » sponsorisé (Taboola, Outbrain…). */
    const CLICKBAIT_SELECTORS = [
        '[id^="taboola"]', '.taboola', '.trc_related_container', '.trc_rbox_container',
        'div[data-placement*="taboola" i]',
        '#taboola-below-article-thumbnails', '#taboola-right-rail-thumbnails',
        '.OUTBRAIN', '.ob-widget', '[id^="outbrain_widget"]', 'div[data-ob-template]',
        '.ob-smartfeed-wrapper'
    ];
    function hideClickbait() {
        for (const sel of CLICKBAIT_SELECTORS) {
            document.querySelectorAll(sel).forEach(el => {
                el.dataset.nfwHidden = '1';
                el.style.setProperty('display', 'none', 'important');
            });
        }
    }

    /* Nettoyage d'URL : retire les paramètres de suivi et déballe les redirections. */
    const TRACK_PARAMS = new Set([
        'utm_source', 'utm_medium', 'utm_campaign', 'utm_term', 'utm_content', 'utm_id',
        'fbclid', 'gclid', 'gclsrc', 'dclid', 'msclkid', 'mc_eid', 'mc_cid', 'igshid',
        'twclid', 'yclid', '_openstat', 'vero_id', 'wickedid', 'oly_enc_id', 'oly_anon_id',
        'ref_src', 'ref_url', 'spm', 'cmpid', 'icid'
    ]);
    function cleanOneUrl(raw) {
        try {
            const url = new URL(raw, location.href);
            let changed = false;
            for (const p of Array.from(url.searchParams.keys())) {
                if (TRACK_PARAMS.has(p.toLowerCase())) { url.searchParams.delete(p); changed = true; }
            }
            return changed ? url.href : null;
        } catch (e) { return null; }
    }
    function unwrapRedirect(raw) {
        try {
            const url = new URL(raw, location.href);
            if (/(^|\.)google\./.test(url.hostname) && url.pathname === '/url') {
                const t = url.searchParams.get('q') || url.searchParams.get('url');
                if (t && /^https?:/i.test(t)) return t;
            }
            return null;
        } catch (e) { return null; }
    }
    function cleanLinks() {
        document.querySelectorAll('a[href]').forEach(a => {
            const unwrapped = unwrapRedirect(a.href);
            if (unwrapped) a.href = unwrapped;
            const cleaned = cleanOneUrl(a.href);
            if (cleaned && cleaned !== a.href) a.href = cleaned;
        });
    }
    function cleanCurrentUrl() {
        const cleaned = cleanOneUrl(location.href);
        if (cleaned && cleaned !== location.href) {
            try { history.replaceState(history.state, '', cleaned); } catch (e) {}
        }
    }

    // Débounce pour le contenu chargé dynamiquement (scroll infini, SPA, bannières tardives).
    let scheduled = false;
    function scheduleProcess() {
        if (scheduled) return;
        scheduled = true;
        setTimeout(() => { scheduled = false; try { processPage(); } catch (e) {} }, 250);
    }

    function startObserver() {
        processPage();
        const obs = new MutationObserver(scheduleProcess);
        obs.observe(document.documentElement, { childList: true, subtree: true });
    }

    /* ------------------------------------------------------------------ *
     *  APPLICATION À CHAUD (sans recharger)
     * ------------------------------------------------------------------ */
    function unhideAll() {
        document.querySelectorAll('[data-nfw-hidden]').forEach(el => {
            el.style.removeProperty('display');
            el.removeAttribute('data-nfw-hidden');
        });
    }
    function applyLive() {
        rebuildSets();
        unhideAll();
        try { processPage(); } catch (e) {}
    }
    function setIdentity(mode) {
        setVal(K.ua, mode);
        try { sessionStorage.setItem('nfw-panel-open', '1'); } catch (e) {}
        location.reload(); // l'identité ne s'applique qu'au chargement
    }

    /* ------------------------------------------------------------------ *
     *  BOUTON FLOTTANT + PANNEAU DE RÉGLAGES (dans la page)
     * ------------------------------------------------------------------ */
    function css(el, o) { for (const k in o) el.style[k] = o[k]; }

    function section(text) {
        const d = document.createElement('div');
        d.textContent = text;
        css(d, { fontWeight: 'bold', fontSize: '11px', color: '#5E6B7B',
            textTransform: 'uppercase', letterSpacing: '.05em', margin: '12px 0 6px' });
        return d;
    }
    function checkboxRow(label, get, set) {
        const row = document.createElement('label');
        css(row, { display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            padding: '6px 0', cursor: 'pointer', gap: '8px' });
        const span = document.createElement('span');
        span.textContent = label; css(span, { flex: '1', lineHeight: '1.3' });
        const cb = document.createElement('input');
        cb.type = 'checkbox'; cb.checked = !!get();
        css(cb, { width: '20px', height: '20px', flex: '0 0 auto' });
        cb.addEventListener('change', () => set(cb.checked));
        row.appendChild(span); row.appendChild(cb);
        return row;
    }
    function radioRow(name, label, checked, onSel) {
        const row = document.createElement('label');
        css(row, { display: 'flex', alignItems: 'center', padding: '6px 0', cursor: 'pointer', gap: '8px' });
        const rb = document.createElement('input');
        rb.type = 'radio'; rb.name = name; rb.checked = checked;
        css(rb, { width: '18px', height: '18px' });
        rb.addEventListener('change', () => { if (rb.checked) onSel(); });
        const span = document.createElement('span');
        span.textContent = label; css(span, { flex: '1' });
        row.appendChild(rb); row.appendChild(span);
        return row;
    }
    function actionButton(label, onClick) {
        const b = document.createElement('button');
        b.textContent = label;
        css(b, { display: 'block', width: '100%', textAlign: 'left', padding: '9px 10px',
            margin: '4px 0', border: '0', borderRadius: '8px', background: '#EAF1FA',
            color: '#0C2A47', fontSize: '14px', cursor: 'pointer' });
        b.addEventListener('click', onClick);
        return b;
    }

    function buildPanel() {
        if (!document.body || document.getElementById('nfw-fab')) return;

        const fab = document.createElement('div');
        fab.id = 'nfw-fab';
        fab.textContent = '\uD83D\uDEE1';
        css(fab, { position: 'fixed', bottom: '16px', right: '16px', zIndex: '2147483647',
            width: '44px', height: '44px', borderRadius: '50%',
            background: 'linear-gradient(135deg,#1877D2,#17BEC4)', color: '#fff',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '22px', boxShadow: '0 2px 8px rgba(0,0,0,.35)', cursor: 'pointer',
            opacity: '0.75', userSelect: 'none' });

        const panel = document.createElement('div');
        panel.id = 'nfw-panel';
        css(panel, { position: 'fixed', bottom: '70px', right: '16px', zIndex: '2147483647',
            width: '270px', maxHeight: '72vh', overflowY: 'auto', boxSizing: 'border-box',
            background: '#fff', color: '#0C2A47', borderRadius: '14px',
            boxShadow: '0 6px 24px rgba(0,0,0,.35)', padding: '12px 14px',
            font: '14px system-ui,Segoe UI,Roboto,sans-serif', display: 'none' });

        const title = document.createElement('div');
        title.textContent = 'NetFilter Web';
        css(title, { fontWeight: 'bold', fontSize: '16px', marginBottom: '2px' });
        panel.appendChild(title);

        panel.appendChild(section('Bloquer ces sites'));
        for (const cat of CATEGORIES) {
            panel.appendChild(checkboxRow(cat.name,
                () => isCatEnabled(cat.id),
                v => { setVal(K.cat(cat.id), v); applyLive(); }));
        }

        panel.appendChild(section('Affichage'));
        panel.appendChild(checkboxRow("Bloquer l'accès aux sites", blockNavOn, v => setVal(K.blockNav, v)));
        panel.appendChild(checkboxRow('Masquer dans les recherches', hideSearchOn, v => { setVal(K.hideSearch, v); applyLive(); }));
        panel.appendChild(checkboxRow('Masquer les liens partout', hideAllOn, v => { setVal(K.hideAll, v); applyLive(); }));
        panel.appendChild(checkboxRow('Bannières cookies', cookiesOn, v => { setVal(K.cookies, v); applyLive(); }));
        panel.appendChild(checkboxRow('Blocs de clickbait', clickbaitOn, v => { setVal(K.clickbait, v); applyLive(); }));
        panel.appendChild(checkboxRow('Nettoyer les URL', cleanUrlsOn, v => { setVal(K.cleanurls, v); applyLive(); }));

        panel.appendChild(section('Identité'));
        const uaMode = getVal(K.ua, 'auto');
        panel.appendChild(radioRow('nfw-ua', 'Automatique', uaMode === 'auto', () => setIdentity('auto')));
        panel.appendChild(radioRow('nfw-ua', 'Ordinateur', uaMode === 'desktop', () => setIdentity('desktop')));
        panel.appendChild(radioRow('nfw-ua', 'Téléphone', uaMode === 'mobile', () => setIdentity('mobile')));

        panel.appendChild(section('Actions'));
        panel.appendChild(actionButton('\uD83D\uDCD6 Mode lecture', () => { try { enableReadingMode(); } catch (e) {} }));
        panel.appendChild(actionButton('\u2795 Bloquer un domaine\u2026', () => {
            const d = prompt('Domaine à bloquer (ex. exemple.com) :');
            if (d && d.trim()) { const a = getArr(K.custom); a.push(d.trim().toLowerCase()); setArr(K.custom, a); applyLive(); }
        }));
        panel.appendChild(actionButton('\u2714 Autoriser un domaine\u2026', () => {
            const d = prompt('Domaine à ne jamais bloquer :');
            if (d && d.trim()) { const a = getArr(K.whitelist); a.push(d.trim().toLowerCase()); setArr(K.whitelist, a); applyLive(); }
        }));

        fab.addEventListener('click', () => {
            const open = panel.style.display !== 'none';
            panel.style.display = open ? 'none' : 'block';
            fab.style.opacity = open ? '0.75' : '1';
        });

        document.body.appendChild(fab);
        document.body.appendChild(panel);

        try {
            if (sessionStorage.getItem('nfw-panel-open') === '1') {
                sessionStorage.removeItem('nfw-panel-open');
                panel.style.display = 'block'; fab.style.opacity = '1';
            }
        } catch (e) {}
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => { startObserver(); buildPanel(); });
    } else {
        startObserver(); buildPanel();
    }

    /* ------------------------------------------------------------------ *
     *  3) MENU DE RÉGLAGES (icône Tampermonkey)
     *  Chaque changement recharge la page pour s'appliquer proprement.
     * ------------------------------------------------------------------ */
    function toggleAndReload(key, defVal) {
        setVal(key, !(getVal(key, defVal) === true));
        location.reload();
    }

    function buildMenu() {
        // Catégories
        for (const cat of CATEGORIES) {
            const on = isCatEnabled(cat.id);
            GM_registerMenuCommand((on ? '✅ ' : '⬜ ') + cat.name, () => {
                setVal(K.cat(cat.id), !on);
                location.reload();
            });
        }

        // Modes
        GM_registerMenuCommand(
            (blockNavOn() ? '✅' : '⬜') + ' Bloquer l\'accès aux sites',
            () => toggleAndReload(K.blockNav, true)
        );
        GM_registerMenuCommand(
            (hideSearchOn() ? '✅' : '⬜') + ' Masquer des résultats de recherche',
            () => toggleAndReload(K.hideSearch, true)
        );
        GM_registerMenuCommand(
            (hideAllOn() ? '✅' : '⬜') + ' Masquer les liens sur TOUS les sites',
            () => toggleAndReload(K.hideAll, false)
        );
        GM_registerMenuCommand(
            (cookiesOn() ? '✅' : '⬜') + ' Masquer les bannières cookies',
            () => toggleAndReload(K.cookies, true)
        );
        GM_registerMenuCommand(
            (clickbaitOn() ? '✅' : '⬜') + ' Masquer les blocs de clickbait',
            () => toggleAndReload(K.clickbait, true)
        );
        GM_registerMenuCommand(
            (cleanUrlsOn() ? '✅' : '⬜') + ' Nettoyer les URL (mouchards, redirections)',
            () => toggleAndReload(K.cleanurls, true)
        );

        // Identité navigateur (téléphone / ordinateur)
        const uaMode = getVal(K.ua, 'auto');
        GM_registerMenuCommand((uaMode === 'auto' ? '✅' : '⬜') + ' Identité : automatique', () => {
            setVal(K.ua, 'auto'); location.reload();
        });
        GM_registerMenuCommand((uaMode === 'desktop' ? '✅' : '⬜') + ' Identité : Ordinateur 🖥️', () => {
            setVal(K.ua, 'desktop'); location.reload();
        });
        GM_registerMenuCommand((uaMode === 'mobile' ? '✅' : '⬜') + ' Identité : Téléphone 📱', () => {
            setVal(K.ua, 'mobile'); location.reload();
        });

        // Mode lecture (action ponctuelle)
        GM_registerMenuCommand('📖 Mode lecture (nettoyer la page)', () => {
            try { enableReadingMode(); } catch (e) { alert('Mode lecture indisponible ici.'); }
        });

        // Règles perso
        GM_registerMenuCommand('➕ Ajouter un domaine à bloquer', () => {
            const d = prompt('Domaine à bloquer (ex. exemple.com) :');
            if (d && d.trim()) {
                const arr = getArr(K.custom);
                arr.push(d.trim().toLowerCase());
                setArr(K.custom, arr);
                location.reload();
            }
        });
        GM_registerMenuCommand('✔️ Autoriser un domaine (liste blanche)', () => {
            const d = prompt('Domaine à ne jamais bloquer (ex. exemple.com) :');
            if (d && d.trim()) {
                const arr = getArr(K.whitelist);
                arr.push(d.trim().toLowerCase());
                setArr(K.whitelist, arr);
                location.reload();
            }
        });
        GM_registerMenuCommand('🗒️ Voir / effacer mes règles perso', () => {
            const custom = getArr(K.custom);
            const white = getArr(K.whitelist);
            const msg = 'BLOQUÉS PERSO :\n' + (custom.join('\n') || '(aucun)') +
                '\n\nLISTE BLANCHE :\n' + (white.join('\n') || '(aucun)') +
                '\n\nEffacer toutes les règles perso ? (OK = oui)';
            if (confirm(msg)) {
                setArr(K.custom, []);
                setArr(K.whitelist, []);
                location.reload();
            }
        });
    }

    buildMenu();
})();
