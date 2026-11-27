package fr.refusbot.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.refusbot.core.MiniJson
import fr.refusbot.core.Policy
import fr.refusbot.core.SystemPackagePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reglages",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Quelles notifications afficher (libellés traduits : `ui/Labels.kt`). */
enum class NotifyLevel { TOUTES, ECHECS, INTERRUPTIONS, AUCUNE }

enum class ThemeMode { SYSTEME, CLAIR, SOMBRE }

/** Clé d'une interface de consentement : paquet et empreinte structurelle (aucun texte). */
fun interfaceKey(packageName: String, signature: Int): String = "$packageName#$signature"

data class Settings(
    /** Interrupteur principal (pause/reprise). */
    val enabled: Boolean = true,
    /** Paquets des applis surveillées. */
    val monitored: Set<String> = emptySet(),
    /** Applis surveillées en observation seule (jamais d'action). */
    val observedApps: Set<String> = emptySet(),
    /** Profil global. */
    val policy: Policy = Policy.PRUDENT,
    val notifyLevel: NotifyLevel = NotifyLevel.TOUTES,
    val theme: ThemeMode = ThemeMode.SYSTEME,
    /** Export automatique de l'arbre quand une fenêtre n'est pas résolue. */
    val autoDiagnostic: Boolean = false,
    /** Ne pas valider une fenêtre dont des choix n'ont pas pu être lus ou désactivés : la laisser ouverte. */
    val holdIfUnverifiable: Boolean = true,
    /** Refus complet (20/11) : passer par les préférences pour refuser aussi l'intérêt légitime. */
    val completeRefusal: Boolean = true,
    /** Écran d'information sur l'accessibilité lu et accepté (exigence Google Play). */
    val disclosureAccepted: Boolean = false,
    /** Nouvelles interfaces de consentement validées à la main (« paquet#empreinte »). */
    val acknowledgedInterfaces: Set<String> = emptySet(),
) {
    /** L'utilisateur a-t-il validé cette nouvelle interface (profil Prudent) ? */
    fun isAcknowledged(packageName: String, signature: Int): Boolean =
        interfaceKey(packageName, signature) in acknowledgedInterfaces

    /** Profil effectif pour une appli donnée. */
    fun policyFor(packageName: String): Policy = if (packageName in observedApps) Policy.OBSERVATION else policy
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("actif")
        val MONITORED = stringSetPreferencesKey("applis_surveillees")
        val OBSERVED = stringSetPreferencesKey("applis_observation")
        val POLICY = stringPreferencesKey("profil")
        val NOTIFY_LEVEL = stringPreferencesKey("notifications")
        val THEME = stringPreferencesKey("theme")
        val AUTO_DIAGNOSTIC = booleanPreferencesKey("diagnostic_auto")
        val HOLD_IF_UNVERIFIABLE = booleanPreferencesKey("ne_pas_valider_dans_le_doute")
        val COMPLETE_REFUSAL = booleanPreferencesKey("refus_complet")
        val DISCLOSURE_ACCEPTED = booleanPreferencesKey("information_accessibilite_acceptee")
        val ACKNOWLEDGED = stringSetPreferencesKey("interfaces_validees")

        /** Ancienne clé (1.0.0) : « notifier chaque refus ». */
        val LEGACY_NOTIFY = booleanPreferencesKey("notifier_refus")
    }

    val settings: Flow<Settings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            Settings(
                enabled = p[Keys.ENABLED] ?: true,
                monitored = (p[Keys.MONITORED] ?: emptySet())
                    .filter { SystemPackagePolicy.isMonitorable(it, context.packageName) }.toSet(),
                observedApps = (p[Keys.OBSERVED] ?: emptySet())
                    .filter { SystemPackagePolicy.isMonitorable(it, context.packageName) }
                    .toSet() intersect (p[Keys.MONITORED] ?: emptySet()),
                // Nouvelle installation : Prudent. Les installations existantes reçoivent leur valeur
                // une fois pour toutes dans ensureDefaults() (Strict, l'ancien défaut).
                policy = enumOr(p[Keys.POLICY], Policy.PRUDENT),
                notifyLevel = p[Keys.NOTIFY_LEVEL]?.let { enumOr(it, NotifyLevel.TOUTES) }
                    ?: if (p[Keys.LEGACY_NOTIFY] == false) NotifyLevel.ECHECS else NotifyLevel.TOUTES,
                theme = enumOr(p[Keys.THEME], ThemeMode.SYSTEME),
                // Désactivé tant que l'utilisateur ne l'a pas activé lui-même, après avertissement :
                // un diagnostic contient le texte de la fenêtre capturée.
                autoDiagnostic = p[Keys.AUTO_DIAGNOSTIC] ?: false,
                holdIfUnverifiable = p[Keys.HOLD_IF_UNVERIFIABLE] ?: true,
                completeRefusal = p[Keys.COMPLETE_REFUSAL] ?: true,
                disclosureAccepted = p[Keys.DISCLOSURE_ACCEPTED] ?: false,
                acknowledgedInterfaces = p[Keys.ACKNOWLEDGED] ?: emptySet(),
            )
        }
        .distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.ENABLED] = value }
    }

    suspend fun setMonitored(packageName: String, monitored: Boolean) {
        setMonitored(setOf(packageName), monitored)
    }

    suspend fun setMonitored(packages: Collection<String>, monitored: Boolean) {
        context.dataStore.edit { p ->
            val requested = packages.toSet()
            val current = (p[Keys.MONITORED] ?: emptySet())
                .filter { SystemPackagePolicy.isMonitorable(it, context.packageName) }.toSet()
            val eligible = requested.filter { SystemPackagePolicy.isMonitorable(it, context.packageName) }.toSet()
            val updated = if (monitored) current + eligible else current - requested
            p[Keys.MONITORED] = updated
            // Les paquets exclus ne peuvent pas être réintroduits par une sélection ou une ancienne sauvegarde.
            p[Keys.OBSERVED] = (p[Keys.OBSERVED] ?: emptySet()) intersect updated
            p[Keys.ACKNOWLEDGED] = (p[Keys.ACKNOWLEDGED] ?: emptySet())
                .filter { key -> key.substringBefore('#') in updated }.toSet()
        }
    }

    suspend fun setObservation(packageName: String, observeOnly: Boolean) {
        context.dataStore.edit { p ->
            val current = p[Keys.OBSERVED] ?: emptySet()
            val allowed = SystemPackagePolicy.isMonitorable(packageName, context.packageName) &&
                packageName in (p[Keys.MONITORED] ?: emptySet())
            p[Keys.OBSERVED] = if (observeOnly && allowed) current + packageName else current - packageName
        }
    }

    /** Valide la nouvelle interface d'une appli : le profil Prudent peut de nouveau y agir. */
    suspend fun acknowledgeInterface(packageName: String, signature: Int) {
        context.dataStore.edit { p ->
            // Une appli peut avoir plusieurs fenêtres validées (bandeau, fenêtre complète…).
            p[Keys.ACKNOWLEDGED] = (p[Keys.ACKNOWLEDGED] ?: emptySet()) + interfaceKey(packageName, signature)
        }
    }

    suspend fun setPolicy(value: Policy) {
        context.dataStore.edit { it[Keys.POLICY] = value.name }
    }

    suspend fun setNotifyLevel(value: NotifyLevel) {
        context.dataStore.edit { it[Keys.NOTIFY_LEVEL] = value.name }
    }

    suspend fun setTheme(value: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = value.name }
    }

    suspend fun setDisclosureAccepted(value: Boolean) {
        context.dataStore.edit { it[Keys.DISCLOSURE_ACCEPTED] = value }
    }

    suspend fun setCompleteRefusal(value: Boolean) {
        context.dataStore.edit { it[Keys.COMPLETE_REFUSAL] = value }
    }

    suspend fun setHoldIfUnverifiable(value: Boolean) {
        context.dataStore.edit { it[Keys.HOLD_IF_UNVERIFIABLE] = value }
    }

    suspend fun setAutoDiagnostic(value: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DIAGNOSTIC] = value }
    }

    /** Remet tous les réglages à zéro (aucune appli surveillée). */
    /**
     * À appeler au démarrage. Fixe le profil s'il n'a jamais été enregistré :
     * - nouvelle installation (aucun réglage) : Prudent, n'agit que sur les fenêtres reconnues avec certitude ;
     * - installation existante (réglages déjà présents) : Strict, l'ancien défaut, pour ne rien changer
     *   au comportement auquel l'utilisateur est habitué.
     */
    suspend fun ensureDefaults() {
        context.dataStore.edit { p ->
            if (p[Keys.POLICY] == null) {
                p[Keys.POLICY] = if (p.asMap().isEmpty()) Policy.PRUDENT.name else Policy.STRICT.name
            }
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }

    // --- Sauvegarde / restauration (aucun contenu d'écran) -----------------------------------

    suspend fun exportJson(): String {
        val s = current()
        return MiniJson.write(
            linkedMapOf(
                "format" to "refusbot-reglages-1",
                "actif" to s.enabled,
                "profil" to s.policy.name,
                "notifications" to s.notifyLevel.name,
                "theme" to s.theme.name,
                "diagnosticAutomatique" to s.autoDiagnostic,
                "nePasValiderDansLeDoute" to s.holdIfUnverifiable,
                "refusComplet" to s.completeRefusal,
                "applisSurveillees" to s.monitored.sorted(),
                "applisObservation" to s.observedApps.sorted(),
            ),
        )
    }

    /** Restaure des réglages exportés ; renvoie le nombre d'applis restaurées. */
    suspend fun importJson(json: String): Int {
        val root = MiniJson.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("fichier de réglages invalide")
        require(root["format"] == "refusbot-reglages-1") { "ce fichier n'est pas une sauvegarde de réglages RefusBot" }
        fun packages(key: String): Set<String> = (root[key] as? List<*>).orEmpty()
            .mapNotNull { it as? String }
            .filter { PACKAGE_NAME.matches(it) && SystemPackagePolicy.isMonitorable(it, context.packageName) }
            .toSet()
        val monitored = packages("applisSurveillees")
        val observed = packages("applisObservation") intersect monitored
        context.dataStore.edit { p ->
            (root["actif"] as? Boolean)?.let { p[Keys.ENABLED] = it }
            (root["diagnosticAutomatique"] as? Boolean)?.let { p[Keys.AUTO_DIAGNOSTIC] = it }
            (root["nePasValiderDansLeDoute"] as? Boolean)?.let { p[Keys.HOLD_IF_UNVERIFIABLE] = it }
            (root["refusComplet"] as? Boolean)?.let { p[Keys.COMPLETE_REFUSAL] = it }
            // Un « profil » inconnu dans le fichier ne change rien au réglage actuel : jamais de
            // repli sur un profil plus permissif (Strict agit davantage que Prudent).
            (root["profil"] as? String)?.let { name ->
                enumValues<Policy>().firstOrNull { it.name == name }?.let { p[Keys.POLICY] = it.name }
            }
            (root["notifications"] as? String)?.let { p[Keys.NOTIFY_LEVEL] = enumOr(it, NotifyLevel.TOUTES).name }
            (root["theme"] as? String)?.let { p[Keys.THEME] = enumOr(it, ThemeMode.SYSTEME).name }
            p[Keys.MONITORED] = monitored
            p[Keys.OBSERVED] = observed
        }
        return monitored.size
    }

    private companion object {
        val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

        inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}
