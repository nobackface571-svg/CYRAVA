package fr.refusbot.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import fr.refusbot.R
import fr.refusbot.core.DiagnosticBundle
import fr.refusbot.core.DiagnosticNames
import fr.refusbot.core.DiagnosticPayloadPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Fichiers JSON du mode diagnostic, stockés dans l'espace privé de l'appli (partage manuel uniquement). */
class DiagnosticsRepository(private val context: Context) {
    private val dir = File(context.filesDir, "diagnostics")
    private val _files = MutableStateFlow(listFiles())

    /** Fichiers, le plus récent en premier. */
    val files: StateFlow<List<File>> = _files.asStateFlow()

    suspend fun save(packageName: String, json: String): File = withContext(Dispatchers.IO) {
        if (!DiagnosticPayloadPolicy.isAllowed(json)) {
            throw java.io.IOException("Diagnostic trop volumineux : limite de ${DiagnosticPayloadPolicy.MAX_BYTES} octets.")
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw java.io.IOException("Impossible de créer le dossier privé des diagnostics.")
        }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT))
        // Réservation atomique : le verrou seul ne suffit pas, car l'écriture a lieu après
        // sa libération. Un fichier .reserve empêche deux sauvegardes simultanées de choisir
        // le même nom, même si elles concernent le même paquet et la même seconde.
        val reservation = synchronized(this@DiagnosticsRepository) {
            var candidate: File
            do {
                val name = DiagnosticNames.next(packageName, stamp) {
                    File(dir, it).exists() || File(dir, "$it.reserve").exists()
                }
                candidate = File(dir, "$name.reserve")
            } while (!candidate.createNewFile())
            candidate
        }
        val file = File(dir, reservation.name.removeSuffix(".reserve"))
        val temporary = File(dir, file.name + ".${java.util.UUID.randomUUID()}.tmp")
        try {
            temporary.writeText(json, Charsets.UTF_8)
            // Le nom final est réservé : aucune autre sauvegarde ne peut le revendiquer.
            if (!temporary.renameTo(file)) {
                throw java.io.IOException("Impossible d'enregistrer le diagnostic de façon atomique.")
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        } finally {
            reservation.delete()
        }
        // On ne garde que les plus récents.
        listFiles().drop(MAX_FILES).forEach { it.delete() }
        refresh()
        file
    }

    fun delete(file: File) {
        if (file.parentFile == dir) file.delete()
        refresh()
    }

    fun deleteAll() {
        listFiles().forEach { it.delete() }
        refresh()
    }

    fun refresh() {
        _files.value = listFiles()
    }

    /** Intent de partage (FileProvider, lecture seule temporaire). */
    fun shareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diag_share_subject, file.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        return Intent.createChooser(send, context.getString(R.string.diag_share_title))
    }

    /**
     * Archive .zip de TOUS les diagnostics enregistrés (et de l'historique, s'il est fourni), pour les
     * partager en une fois (27/11). Créée dans le cache privé (dossier « exports »), remplacée à chaque
     * fois ; rien n'est envoyé sans le choix de l'utilisateur.
     */
    suspend fun shareAllIntent(historyJson: String?): Intent = withContext(Dispatchers.IO) {
        val entries = listFiles().reversed().map { f -> DiagnosticBundle.Entry(f.name, f.lastModified()) { f.inputStream() } } +
            listOfNotNull(historyJson?.let { DiagnosticBundle.Entry("historique.json", it.toByteArray(Charsets.UTF_8)) })
        val exports = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT))
        // Une seule archive à la fois dans le cache : les anciennes sont effacées.
        exports.listFiles { f -> f.name.startsWith("cyrava-diagnostics-") && f.name.endsWith(".zip") }?.forEach { it.delete() }
        val zip = File(exports, "cyrava-diagnostics-$stamp.zip")
        try {
            zip.outputStream().buffered().use { DiagnosticBundle.write(it, entries) }
        } catch (error: Exception) {
            zip.delete()
            throw java.io.IOException("Archive des diagnostics impossible à créer", error)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diag_share_subject, zip.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(zip.name, uri)
        Intent.createChooser(send, context.getString(R.string.diag_share_all_t))
    }

    private fun listFiles(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

    companion object {
        const val MAX_FILES = 30
    }
}
