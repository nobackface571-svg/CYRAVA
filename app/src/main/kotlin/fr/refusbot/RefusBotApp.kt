package fr.refusbot

import android.app.Application
import android.content.Context
import android.util.Log
import fr.refusbot.core.Rules
import fr.refusbot.data.DiagnosticsRepository
import fr.refusbot.data.LogRepository
import fr.refusbot.data.RulesRepository
import fr.refusbot.data.SettingsRepository
import fr.refusbot.service.Notifier
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RefusBotApp : Application() {

    /** Portée longue durée pour les écritures (journal, réglages) lancées hors d'un écran. */
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            // Un plantage du processus arrêterait aussi le service d'accessibilité.
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "Erreur ignorée dans une tâche de fond", e) },
    )

    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val journal: LogRepository by lazy { LogRepository(this, appScope) }
    val diagnostics: DiagnosticsRepository by lazy { DiagnosticsRepository(this) }

    /** Règles actives : livrées (assets/rules.json) ou importées et validées. */
    val rulesRepository: RulesRepository by lazy { RulesRepository(this) }

    val rules: Rules get() = rulesRepository.active.value.rules

    override fun onCreate() {
        // En premier : un plantage pendant le démarrage doit lui aussi être enregistré.
        CrashRecorder.install(this)
        super.onCreate()
        Notifier.createChannels(this)
        // Profil par défaut : Prudent pour une nouvelle installation, inchangé pour une existante.
        appScope.launch { settings.ensureDefaults() }
    }

    companion object {
        const val TAG = "RefusBot"
    }
}

val Context.refusBot: RefusBotApp get() = applicationContext as RefusBotApp
