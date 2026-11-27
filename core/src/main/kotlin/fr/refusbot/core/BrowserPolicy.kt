package fr.refusbot.core

/**
 * Navigateurs web : chaque site affiche sa propre fenêtre de cookies, donc l'empreinte « par appli »
 * du profil Prudent considère presque toujours la fenêtre comme nouvelle et n'agit jamais
 * (historique du 18/11 : 21 fenêtres « Observé » dans Chrome).
 *
 * Dans un navigateur, une fenêtre reconnue avec CERTITUDE est considérée comme connue, que sa
 * plateforme soit nommée (Didomi, OneTrust…), « TCF générique » ou « inconnue » (décision du
 * propriétaire, 21/11/2026 : 4 sites seulement observés dans Firefox le 20/11). Une fenêtre
 * seulement « probable » reste soumise à la règle normale (aucune action si l'interface est nouvelle).
 * Le garde-fou contre les acceptations s'applique toujours, quel que soit le profil.
 */
object BrowserPolicy {
    private val BROWSERS = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.google.android.apps.chrome",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "org.chromium.chrome",
    )

    fun isBrowser(packageName: String): Boolean = packageName.trim().lowercase() in BROWSERS

    /**
     * Navigateurs déclarés « non pris en charge » (décision du propriétaire, 20/11/2026) :
     * Samsung Internet. L'appli le signale dans l'onglet Applications et ne le coche pas avec
     * « Tout cocher » ; l'utilisateur peut encore le cocher lui-même, à ses risques.
     */
    private val UNSUPPORTED = setOf(
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
    )

    fun isUnsupported(packageName: String): Boolean = packageName.trim().lowercase() in UNSUPPORTED

    /** Vrai si, dans un navigateur, une reconnaissance certaine suffit à connaître la fenêtre. */
    fun knownInBrowser(packageName: String, confidence: Confidence): Boolean =
        isBrowser(packageName) && confidence == Confidence.CERTAIN

    /**
     * Barre d'adresse d'un navigateur (contre-audit M2) : son texte (l'adresse du site visité) ne doit
     * jamais être copié dans un instantané ni un diagnostic. Identifiants relevés sur Chrome et
     * dérivés, Samsung Internet, Firefox (ancienne et nouvelle barre, diagnostics du 20/11) et
     * DuckDuckGo ; en plus, tout identifiant contenant « url » ou « address » dans l'interface du
     * navigateur (hors page web, filtrée par l'appelant).
     */
    fun isAddressBar(packageName: String, viewId: String?): Boolean {
        if (viewId == null || !isBrowser(packageName)) return false
        val id = viewId.substringAfter(":id/").lowercase()
        return id in ADDRESS_BAR_IDS || id.contains("url") || id.contains("address") || id.contains("omnibox")
    }

    private val ADDRESS_BAR_IDS = setOf(
        "url_bar", "location_bar_edit_text", "mozac_browser_toolbar_url_view", "omnibartextinput",
        "addressbar_url_box", "search_box_text",
    )
}
