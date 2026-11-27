package fr.refusbot.data

import android.content.Context
import android.util.Log
import fr.refusbot.core.Confidence
import fr.refusbot.core.OutcomeStatus
import fr.refusbot.core.SessionTiming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Une entrée de l'historique. Jamais de texte d'écran : seulement le résultat, des compteurs,
 * la plateforme reconnue et un motif générique.
 */
data class LogEntry(
    val time: Long,
    val packageName: String,
    val appName: String,
    val status: OutcomeStatus,
    val togglesOff: Int,
    val reason: String?,
    val platform: String = "inconnue",
    val confidence: Confidence = Confidence.PROBABLE,
    val actions: Int = 0,
    /** Empreinte structurelle de la fenêtre (sans texte). */
    val signature: Int = 0,
    /** Fenêtre différente de toutes celles déjà refusées dans cette appli. */
    val interfaceChanged: Boolean = false,
    val consentDetected: Boolean = false,
    val controlsRecognized: Int = 0,
    val actionAttempts: Int = 0,
    val actionExecutions: Int = 0,
    val blockedActions: Int = 0,
    val stateVerified: Boolean? = null,
    val captureTruncated: Boolean = false,
    /** Durées des étapes (lecture de l'écran, décochage, défilement, attentes…) ; null pour les anciennes entrées. */
    val timing: SessionTiming.Summary? = null,
    /** Identifiant persistant utilisé comme clé stable par les listes Compose. */
    val id: String = UUID.randomUUID().toString(),
) {
    val isFailure: Boolean get() = status == OutcomeStatus.IMPOSSIBLE || status == OutcomeStatus.INTERRUPTED || status == OutcomeStatus.INCOMPLETE_CAPTURE
}

/** Historique local (fichier JSON privé), limité aux [MAX_ENTRIES] dernières entrées. */
class LogRepository(context: Context, scope: CoroutineScope) {
    private val file = File(context.filesDir, "journal.json")
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Entrées, la plus récente en premier. */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    /**
     * Lecture initiale du fichier. Toute écriture l'attend : sinon une entrée ajoutée juste après
     * le démarrage du processus (service relancé au démarrage du téléphone, sans que l'interface
     * ait été ouverte) écraserait le fichier avec une liste encore vide, et l'historique serait perdu.
     */
    private val loading: Job = scope.launch {
        val initial = read()
        mutex.withLock { _entries.value = initial }
    }

    /** Attend que l'historique enregistré soit chargé en mémoire. */
    suspend fun awaitLoaded() = loading.join()

    suspend fun add(entry: LogEntry) {
        loading.join()
        mutex.withLock {
            val updated = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
            _entries.value = updated
            write(updated)
        }
    }

    suspend fun clear() {
        loading.join()
        mutex.withLock {
            _entries.value = emptyList()
            write(emptyList())
        }
    }

    /**
     * Empreintes de toutes les fenêtres déjà refusées dans cette appli. Une appli peut afficher
     * plusieurs fenêtres légitimes (bandeau, fenêtre complète) : une interface n'est « nouvelle »
     * que si elle ne ressemble à aucune d'elles.
     */
    fun refusedSignatures(packageName: String): Set<Int> =
        _entries.value.asSequence()
            .filter { it.packageName == packageName && it.status.isRefusal && it.signature != 0 }
            .map { it.signature }
            .toSet()

    private suspend fun read(): List<LogEntry> = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext emptyList()
        try {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { fromJson(it, i) } }
        } catch (e: JSONException) {
            Log.w("RefusBot", "journal illisible, réinitialisé", e)
            emptyList()
        } catch (e: IOException) {
            Log.w("RefusBot", "journal illisible", e)
            emptyList()
        }
    }

    private suspend fun write(entries: List<LogEntry>) = withContext(Dispatchers.IO) {
        try {
            val array = JSONArray()
            entries.forEach { array.put(toJson(it)) }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(array.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(array.toString())
                tmp.delete()
            }
        } catch (e: IOException) {
            // Stockage plein ou indisponible : le journal reste en mémoire, rien ne doit planter.
            Log.w("RefusBot", "journal non enregistré", e)
        }
    }

    companion object {
        const val MAX_ENTRIES = 500

        private fun toJson(e: LogEntry): JSONObject = JSONObject()
            .put("t", e.time)
            .put("p", e.packageName)
            .put("a", e.appName)
            .put("s", e.status.name)
            .put("n", e.togglesOff)
            .put("pf", e.platform)
            .put("c", e.confidence.name)
            .put("ac", e.actions)
            .put("sg", e.signature)
            .put("ic", e.interfaceChanged)
            .put("cd", e.consentDetected)
            .put("cr", e.controlsRecognized)
            .put("aa", e.actionAttempts)
            .put("ae", e.actionExecutions)
            .put("ab", e.blockedActions)
            .put("sv", e.stateVerified ?: JSONObject.NULL)
            .put("ct", e.captureTruncated)
            .put("id", e.id)
            .apply { e.reason?.let { put("m", it) } }
            .apply { e.timing?.let { put("tm", timingJson(it)) } }

        private fun timingJson(t: SessionTiming.Summary): JSONObject =
            JSONObject().apply { t.toMap().forEach { (k, v) -> put(k, v) } }

        private fun timingFrom(o: JSONObject?): SessionTiming.Summary? {
            if (o == null) return null
            val m = HashMap<String, Long>()
            o.keys().forEach { k -> m[k] = o.optLong(k) }
            return SessionTiming.Summary.fromMap(m)
        }

        private fun fromJson(o: JSONObject, legacyIndex: Int): LogEntry {
            // Compatibilité avec le journal de la version 1.0.0 (clé « r »).
            val parsedStatus = OutcomeStatus.entries.firstOrNull { it.name == o.optString("s") }
                ?: when (o.optString("r")) {
                    "DIRECT" -> OutcomeStatus.DIRECT_SUCCESS
                    "OPTIONS" -> OutcomeStatus.OPTIONS_SUCCESS
                    else -> OutcomeStatus.IMPOSSIBLE
                }
            // Les anciennes entrées ne stockaient pas la preuve explicite d'état final (clé « sv »).
            // Ne pas transformer rétroactivement un ancien libellé OPTIONS_SUCCESS en succès vérifié.
            val legacyWithoutVerification = !o.has("sv") && parsedStatus == OutcomeStatus.OPTIONS_SUCCESS
            val status = if (legacyWithoutVerification) OutcomeStatus.UNVERIFIED else parsedStatus
            return LogEntry(
                time = o.optLong("t"),
                packageName = o.optString("p"),
                appName = o.optString("a"),
                status = status,
                togglesOff = if (legacyWithoutVerification) 0 else o.optInt("n"),
                reason = if (legacyWithoutVerification) {
                    "Ancienne entrée : l'état final des préférences n'était pas enregistré."
                } else if (o.has("m")) o.optString("m") else null,
                platform = o.optString("pf", "inconnue"),
                confidence = Confidence.entries.firstOrNull { it.name == o.optString("c") } ?: Confidence.PROBABLE,
                actions = o.optInt("ac"),
                signature = o.optInt("sg"),
                interfaceChanged = o.optBoolean("ic"),
                consentDetected = if (o.has("cd")) o.optBoolean("cd") else status == OutcomeStatus.OBSERVED ||
                    status.isRefusal || status == OutcomeStatus.IMPOSSIBLE || status == OutcomeStatus.INCOMPLETE_CAPTURE,
                controlsRecognized = o.optInt("cr"),
                actionAttempts = o.optInt("aa"),
                actionExecutions = o.optInt("ae"),
                blockedActions = o.optInt("ab"),
                stateVerified = if (o.has("sv") && !o.isNull("sv")) o.optBoolean("sv") else null,
                captureTruncated = o.optBoolean("ct"),
                timing = timingFrom(o.optJSONObject("tm")),
                // Migration déterministe : les anciennes lignes reçoivent un ID stable
                // selon leur position historique, puis cet ID est persisté à la prochaine écriture.
                id = o.optString("id").takeIf { it.isNotBlank() } ?: "legacy-$legacyIndex",
            )
        }

        private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        private fun iso(time: Long) = ISO.format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()))

        /** Export volontaire au format JSON. */
        fun exportJson(entries: List<LogEntry>): String {
            val array = JSONArray()
            entries.forEach { e ->
                array.put(
                    JSONObject()
                        .put("date", iso(e.time))
                        .put("application", e.appName)
                        .put("package", e.packageName)
                        .put("plateforme", e.platform)
                        .put("resultat", e.status.label)
                        .put("confiance", e.confidence.label)
                        .put("actions", e.actions)
                        .put("banniereDetectee", e.consentDetected)
                        .put("controlesReconnus", e.controlsRecognized)
                        .put("actionsTentees", e.actionAttempts)
                        .put("actionsExecutees", e.actionExecutions)
                        .put("actionsBloquees", e.blockedActions)
                        .put("etatVerifie", when (e.stateVerified) { true -> "oui"; false -> "non"; null -> "non_mesure" })
                        .put("captureTronquee", e.captureTruncated)
                        .put("interrupteursDesactives", e.togglesOff)
                        .put("interfaceModifiee", e.interfaceChanged)
                        .put("motif", e.reason ?: "")
                        // Durées en millisecondes (22/11) : où passe le temps sur le téléphone.
                        .put("durees", e.timing?.let(::timingJson) ?: JSONObject.NULL),
                )
            }
            return array.toString(2)
        }

        /** Export volontaire au format CSV (séparateur « ; », compatible tableurs français). */
        fun exportCsv(entries: List<LogEntry>): String {
            fun cell(value: String): String {
                // Les champs texte peuvent provenir du nom d'une appli ou d'un libellé externe.
                // Neutraliser les préfixes interprétés comme formules par les tableurs (CSV injection).
                val safe = if (Regex("^[\\u0000-\\u0020]*[=+@-]").containsMatchIn(value)) "'$value" else value
                return "\"" + safe.replace("\"", "\"\"") + "\""
            }
            val header = "date;application;package;plateforme;resultat;confiance;actions;banniere_detectee;controles_reconnus;actions_tentees;actions_executees;actions_bloquees;etat_verifie;capture_tronquee;interrupteurs_desactives;interface_modifiee;motif;duree_totale_ms;durees"
            return (listOf(header) + entries.map { e ->
                listOf(
                    iso(e.time), e.appName, e.packageName, e.platform, e.status.label, e.confidence.label,
                    e.actions.toString(),
                    if (e.consentDetected) "oui" else "non",
                    e.controlsRecognized.toString(),
                    e.actionAttempts.toString(),
                    e.actionExecutions.toString(),
                    e.blockedActions.toString(),
                    when (e.stateVerified) { true -> "oui"; false -> "non"; null -> "non_mesure" },
                    if (e.captureTruncated) "oui" else "non",
                    e.togglesOff.toString(),
                    if (e.interfaceChanged) "oui" else "non",
                    e.reason ?: "",
                    e.timing?.totalMs?.toString() ?: "",
                    e.timing?.toMap()?.entries?.joinToString(" ") { "${it.key}=${it.value}" } ?: "",
                ).joinToString(";") { cell(it) }
            }).joinToString("\n")
        }
    }
}
