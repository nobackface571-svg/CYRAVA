package fr.refusbot.data

import android.content.Context
import android.util.Log
import fr.refusbot.core.Rules
import fr.refusbot.core.RulesValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Règles actives : celles livrées dans l'APK (assets/rules.json) ou un fichier importé par
 * l'utilisateur, toujours validé par [RulesValidator] par rapport aux règles livrées : le garde-fou
 * et la gestion des interrupteurs restent ceux de l'appli, et chaque formule ajoutée est contrôlée.
 * La version importée précédente est conservée pour un retour arrière.
 */
class RulesRepository(private val context: Context) {
    private val dir = File(context.filesDir, "regles")
    private val custom = File(dir, "importees.json")
    private val previous = File(dir, "precedentes.json")

    enum class Source(val label: String) { BUNDLED("règles d'origine"), IMPORTED("règles importées") }

    /** @param hasPrevious un fichier importé précédent peut être rétabli. */
    data class Active(val rules: Rules, val source: Source, val hasPrevious: Boolean = false)

    private val bundled: Rules by lazy {
        Rules.fromJson(context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    private val _active = MutableStateFlow(load())
    val active: StateFlow<Active> = _active.asStateFlow()

    private fun load(): Active {
        if (custom.isFile) {
            try {
                // Toujours contrôlé par rapport aux règles livrées : les sections de sécurité restent celles de l'appli.
                val (rules, report) = RulesValidator.parseAndValidate(custom.readText(), bundled)
                if (rules != null) return Active(rules, Source.IMPORTED, previous.isFile)
                Log.w("RefusBot", "règles importées invalides, retour aux règles d'origine : ${report.errors}")
            } catch (e: IOException) {
                Log.w("RefusBot", "règles importées illisibles", e)
            }
        }
        return Active(bundled, Source.BUNDLED, previous.isFile)
    }

    /** Importe un fichier de règles ; renvoie le rapport de validation (rien n'est changé s'il est invalide). */
    suspend fun import(json: String): RulesValidator.Report = withContext(Dispatchers.IO) {
        val (rules, report) = RulesValidator.parseAndValidate(json, bundled)
        if (rules != null) {
            dir.mkdirs()
            if (custom.isFile) custom.copyTo(previous, overwrite = true)
            custom.writeText(json)
            _active.value = Active(rules, Source.IMPORTED, previous.isFile)
        }
        report
    }

    /**
     * Revient au fichier importé précédent. Le fichier remplacé devient à son tour « précédent » :
     * le retour arrière est lui-même réversible.
     */
    suspend fun restorePrevious(): Boolean = withContext(Dispatchers.IO) {
        if (!previous.isFile) return@withContext false
        try {
            val restored = previous.readText()
            val (rules, _) = RulesValidator.parseAndValidate(restored, bundled)
            if (rules == null) return@withContext false
            val replaced = if (custom.isFile) custom.readText() else null
            dir.mkdirs()
            custom.writeText(restored)
            if (replaced != null) previous.writeText(replaced) else previous.delete()
            _active.value = Active(rules, Source.IMPORTED, previous.isFile)
            true
        } catch (e: IOException) {
            Log.w("RefusBot", "retour aux règles précédentes impossible", e)
            false
        }
    }

    /** Revient aux règles livrées avec l'appli. */
    suspend fun resetToBundled() = withContext(Dispatchers.IO) {
        if (custom.isFile) {
            dir.mkdirs()
            custom.copyTo(previous, overwrite = true)
            custom.delete()
        }
        _active.value = Active(bundled, Source.BUNDLED, previous.isFile)
    }

    /** Supprime les règles importées et leur version précédente (« supprimer toutes les données »). */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        custom.delete()
        previous.delete()
        _active.value = Active(bundled, Source.BUNDLED, hasPrevious = false)
    }

    /** Texte des règles livrées (pour les exporter comme modèle). */
    fun bundledJson(): String = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }

    companion object {
        const val ASSET = "rules.json"
    }
}
