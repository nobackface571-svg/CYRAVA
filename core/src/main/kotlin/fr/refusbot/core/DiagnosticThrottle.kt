package fr.refusbot.core

/**
 * Limite les diagnostics automatiques par application ET signature structurelle de fenêtre.
 * Deux CMP différentes dans la même application gardent chacune leur fenêtre de collecte.
 * Les captures manuelles ne doivent pas utiliser cette classe.
 */
class DiagnosticThrottle(
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private data class WindowKey(val packageName: String, val signature: Int)
    private val lastSavedAt = LinkedHashMap<WindowKey, Long>(16, 0.75f, true)

    init {
        require(intervalMs >= 0) { "intervalMs doit être positif ou nul" }
        require(maxEntries > 0) { "maxEntries doit être strictement positif" }
    }

    @Synchronized
    fun tryAcquire(packageName: String, signature: Int, nowMs: Long): Boolean {
        if (packageName.isBlank()) return false
        val key = WindowKey(packageName, signature)
        val previous = lastSavedAt[key]
        if (previous != null && (nowMs < previous || nowMs - previous < intervalMs)) return false
        lastSavedAt[key] = nowMs
        while (lastSavedAt.size > maxEntries) {
            val oldest = lastSavedAt.entries.iterator()
            oldest.next()
            oldest.remove()
        }
        return true
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 10 * 60 * 1000L
        const val DEFAULT_MAX_ENTRIES = 128
    }
}
