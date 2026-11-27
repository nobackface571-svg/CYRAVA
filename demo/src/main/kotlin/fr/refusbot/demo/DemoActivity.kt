package fr.refusbot.demo

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Démo E2E : le scénario "ump" utilise exclusivement le vrai SDK Google UMP.
 * Les scénarios CMP génériques restent dans une WebView distincte et ne prétendent pas
 * reproduire l'interface ni le comportement de Google UMP.
 */
class DemoActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var consentInformation: ConsentInformation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "ump"
        if (scenario == "ump") {
            showRealUmpForm()
        } else {
            showGenericCmpScenario(scenario)
        }
    }

    private fun showRealUmpForm() {
        status = TextView(this).apply {
            textSize = 16f
            setPadding(32, 48, 32, 32)
            text = "GOOGLE_UMP: initialisation du SDK officiel…"
            contentDescription = "GOOGLE_UMP: initialisation du SDK officiel"
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24, 24, 24, 24)
            addView(status)
        })

        consentInformation = UserMessagingPlatform.getConsentInformation(this)

        // Démo uniquement : repartir d'un état vierge à chaque lancement E2E.
        // reset() ne doit jamais être utilisé dans l'application de production.
        consentInformation.reset()

        val debugBuilder = ConsentDebugSettings.Builder(this)
            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
        val testDeviceHash = BuildConfig.UMP_DEMO_TEST_DEVICE_HASHED_ID
        if (testDeviceHash.isNotBlank()) {
            debugBuilder.addTestDeviceHashedId(testDeviceHash)
        }
        val requestParameters = ConsentRequestParameters.Builder()
            .setConsentDebugSettings(debugBuilder.build())
            .build()

        status.text = "GOOGLE_UMP: demande de mise à jour des informations de consentement…"
        Log.i(TAG, "INFO_UPDATE_REQUESTED")
        consentInformation.requestConsentInfoUpdate(
            this,
            requestParameters,
            {
                val before = "status=${consentInformation.consentStatus};" +
                    "formAvailable=${consentInformation.isConsentFormAvailable};" +
                    "privacyOptions=${consentInformation.privacyOptionsRequirementStatus}"
                Log.i(TAG, "INFO_UPDATED $before")
                status.text = "GOOGLE_UMP: informations mises à jour; $before"
                if (consentInformation.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                ) {
                    addPrivacyOptionsButton()
                }
                if (consentInformation.isConsentFormAvailable) {
                    Log.i(TAG, "FORM_AVAILABLE; calling loadAndShowConsentFormIfRequired")
                } else {
                    Log.w(TAG, "FORM_NOT_AVAILABLE")
                }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                    val after = "status=${consentInformation.consentStatus};" +
                        "canRequestAds=${consentInformation.canRequestAds()};" +
                        "privacyOptions=${consentInformation.privacyOptionsRequirementStatus}"
                    val error = formError?.let { ";error=${it.errorCode}:${it.message}" } ?: ""
                    Log.i(TAG, "FORM_FLOW_COMPLETED $after$error")
                    status.text = "GOOGLE_UMP: formulaire terminé; $after$error"
                    status.contentDescription = status.text
                }
            },
            { requestError ->
                Log.e(TAG, "INFO_UPDATE_FAILED ${requestError.errorCode}: ${requestError.message}")
                status.text = "GOOGLE_UMP: échec mise à jour; error=${requestError.errorCode}:${requestError.message}"
                status.contentDescription = status.text
            },
        )
    }

    private fun addPrivacyOptionsButton() {
        val root = status.parent as? LinearLayout ?: return
        if (root.findViewWithTag<Button>(PRIVACY_BUTTON_TAG) != null) return
        root.addView(Button(this).apply {
            tag = PRIVACY_BUTTON_TAG
            text = "Ouvrir les options de confidentialité UMP"
            setOnClickListener {
                Log.i(TAG, "PRIVACY_OPTIONS_REQUESTED")
                UserMessagingPlatform.showPrivacyOptionsForm(this@DemoActivity) { formError ->
                    val error = formError?.let { " error=${it.errorCode}:${it.message}" } ?: ""
                    Log.i(TAG, "PRIVACY_OPTIONS_COMPLETED$error")
                    status.text = "GOOGLE_UMP: options de confidentialité terminées$error"
                }
            }
        })
    }

    @Suppress("DEPRECATION")
    private fun showGenericCmpScenario(scenario: String) {
        val allowed = setOf("bandeau", "accepte", "inspection")
        val safeScenario = scenario.takeIf { it in allowed } ?: "bandeau"
        val web = android.webkit.WebView(this)
        web.settings.javaScriptEnabled = true
        web.webViewClient = android.webkit.WebViewClient()
        web.fitsSystemWindows = true
        setContentView(web)
        web.loadUrl("file:///android_asset/consent.html#$safeScenario")
    }

    companion object {
        private const val TAG = "GoogleUMP"
        private const val PRIVACY_BUTTON_TAG = "google_ump_privacy_options"
    }
}
