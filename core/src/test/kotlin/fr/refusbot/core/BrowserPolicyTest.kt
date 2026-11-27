package fr.refusbot.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPolicyTest {
    @Test fun chrome_firefox_and_samsung_internet_are_browsers() {
        assertTrue(BrowserPolicy.isBrowser("com.android.chrome"))
        assertTrue(BrowserPolicy.isBrowser("org.mozilla.firefox"))
        assertTrue(BrowserPolicy.isBrowser("com.sec.android.app.sbrowser"))
        assertFalse(BrowserPolicy.isBrowser("com.openai.chatgpt"))
    }

    /** Décision du 21/11/2026 : dans un navigateur, toute reconnaissance certaine suffit (Didomi, TCF, inconnue). */
    @Test fun certain_recognition_in_browser_is_known() {
        assertTrue(BrowserPolicy.knownInBrowser("com.android.chrome", Confidence.CERTAIN))
        assertTrue(BrowserPolicy.knownInBrowser("org.mozilla.firefox", Confidence.CERTAIN))
        assertTrue(BrowserPolicy.knownInBrowser("com.opera.browser", Confidence.CERTAIN))
    }

    @Test fun uncertain_recognition_in_browser_stays_new() {
        assertFalse(BrowserPolicy.knownInBrowser("com.android.chrome", Confidence.PROBABLE))
        assertFalse(BrowserPolicy.knownInBrowser("org.mozilla.firefox", Confidence.INSUFFISANT))
    }

    @Test fun ordinary_apps_keep_the_per_app_rule() {
        assertFalse(BrowserPolicy.knownInBrowser("com.example.game", Confidence.CERTAIN))
    }

    @Test fun samsung_internet_is_marked_unsupported() {
        assertTrue(BrowserPolicy.isUnsupported("com.sec.android.app.sbrowser"))
        assertTrue(BrowserPolicy.isUnsupported("com.sec.android.app.sbrowser.beta"))
        assertFalse(BrowserPolicy.isUnsupported("com.android.chrome"))
        assertFalse(BrowserPolicy.isUnsupported("org.mozilla.firefox"))
    }
}
