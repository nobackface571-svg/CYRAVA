package fr.refusbot

import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant

/**
 * Enregistre la cause d'un plantage dans un fichier privé de l'appli, pour l'afficher au lancement
 * suivant. Rien ne sort du téléphone : l'utilisateur choisit lui-même de partager le rapport.
 * Le plantage n'est pas empêché : Android le traite normalement après l'enregistrement.
 */
object CrashRecorder {
    private const val FILE_NAME = "dernier-plantage.txt"
    private const val MAX_CHARS = 20_000

    fun install(context: Context) {
        val dir = context.filesDir
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(dir, FILE_NAME).writeText(report(thread.name, error))
            } catch (_: Throwable) {
                // Ne jamais masquer le plantage d'origine.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Rapport du dernier plantage non encore lu, ou null. */
    fun pending(context: Context): String? =
        try {
            File(context.filesDir, FILE_NAME).takeIf { it.isFile }?.readText()
        } catch (_: Throwable) {
            null
        }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Throwable) {
            // Rien à faire : le rapport sera simplement réaffiché.
        }
    }

    private fun report(threadName: String, error: Throwable): String = buildString {
        appendLine("Rapport de plantage CYRAVA")
        appendLine("Date : ${Instant.now()}")
        appendLine("Appareil : ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Fil d'exécution : $threadName")
        appendLine()
        append(error.stackTraceToString())
    }.take(MAX_CHARS)
}
