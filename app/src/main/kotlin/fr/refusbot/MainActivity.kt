package fr.refusbot

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import fr.refusbot.data.ThemeMode
import fr.refusbot.ui.MainScreen
import fr.refusbot.ui.RefusBotTheme

class MainActivity : ComponentActivity() {

    /** Destination demandée par une notification (consommée par l'interface). */
    private val requestedRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Après un plantage, on affiche d'abord sa cause avec des vues Android simples (sans Compose),
        // pour que l'écran s'affiche même si le plantage vient de l'interface.
        CrashRecorder.pending(this)?.let { report ->
            showCrashReport(report)
            return
        }
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val settings by refusBot.settings.settings.collectAsState(initial = null)
            RefusBotTheme(mode = settings?.theme ?: ThemeMode.SYSTEME) {
                MainScreen(requestedRoute)
            }
        }
    }

    // Écran de secours en français uniquement, sans ressources : il doit marcher même si le reste plante.
    @android.annotation.SuppressLint("SetTextI18n")
    private fun showCrashReport(report: String) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val title = TextView(this).apply {
            text = "CYRAVA s'est arrêté lors du dernier lancement"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }
        val help = TextView(this).apply {
            text = "Voici la cause exacte. Partagez ce rapport (il ne contient que des informations techniques), " +
                "puis touchez « Effacer et relancer »."
            setPadding(0, pad / 2, 0, pad / 2)
        }
        val share = Button(this).apply {
            text = "Partager le rapport"
            setOnClickListener {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Rapport de plantage CYRAVA")
                    .putExtra(Intent.EXTRA_TEXT, report)
                startActivity(Intent.createChooser(send, "Partager le rapport de plantage"))
            }
        }
        val restart = Button(this).apply {
            text = "Effacer et relancer"
            setOnClickListener {
                CrashRecorder.clear(this@MainActivity)
                recreate()
            }
        }
        val details = TextView(this).apply {
            text = report
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(title)
            addView(help)
            addView(share)
            addView(restart)
            addView(details)
        }
        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(content)
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_TAB)?.let { requestedRoute.value = it }
    }

    companion object {
        const val EXTRA_TAB = "fr.refusbot.extra.ONGLET"
        const val TAB_HOME = "accueil"
        const val TAB_HISTORY = "historique"
        const val TAB_DIAGNOSTIC = "diagnostic"
    }
}
