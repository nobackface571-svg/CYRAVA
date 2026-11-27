package fr.refusbot.core

/** Additive CMP matcher. Recognition metadata never authorizes a click or infers toggle state. */
data class CmpCatalogMarker(val value: String, val source: MarkerSource, val strong: Boolean = false)
enum class MarkerSource { VIEW_ID, ACCESSIBILITY_TEXT, DOM_EXPORT }
data class CmpCatalogProfile(
    val id: String,
    val displayName: String,
    val markers: List<CmpCatalogMarker> = emptyList(),
    val phraseHints: Set<String> = emptySet(),
)
enum class CmpMatchConfidence { HIGH, PROBABLE, AMBIGUOUS, UNKNOWN }
data class CmpProfileMatch(
    val profileId: String?,
    val displayName: String?,
    val confidence: CmpMatchConfidence,
    val matchedMarkers: Set<String>,
    val matchedPhrases: Set<String>,
    val competingProfileIds: Set<String> = emptySet(),
) {
    val mayAuthorizeAction: Boolean get() = false
}
object CmpCatalogMatcher {
    fun match(profiles: List<CmpCatalogProfile>, viewIds: Collection<String>, visibleTexts: Collection<String>): CmpProfileMatch {
        val ids = viewIds.map(::canonical).filter(String::isNotEmpty)
        val texts = visibleTexts.map(::canonical).filter(String::isNotEmpty)
        data class Scored(val profile: CmpCatalogProfile, val markers: Set<String>, val phrases: Set<String>, val strong: Set<String>)
        val scored = profiles.mapNotNull { p ->
            val hits = p.markers.filter { m ->
                val needle = canonical(m.value)
                needle.isNotEmpty() && when (m.source) {
                    MarkerSource.VIEW_ID -> ids.any { it.contains(needle) }
                    MarkerSource.ACCESSIBILITY_TEXT -> texts.any { it.contains(needle) }
                    MarkerSource.DOM_EXPORT -> false
                }
            }
            val phrases = p.phraseHints.map(::canonical).filter { phrase ->
                phrase.isNotEmpty() && texts.any { it.contains(phrase) }
            }.toSet()
            if (hits.isEmpty() && phrases.isEmpty()) null else Scored(
                p, hits.map { canonical(it.value) }.toSet(), phrases,
                hits.filter { it.strong }.map { canonical(it.value) }.toSet(),
            )
        }.sortedWith(compareByDescending<Scored> { it.strong.size }.thenByDescending { it.markers.size + it.phrases.size }.thenBy { it.profile.id })
        if (scored.isEmpty()) return CmpProfileMatch(null, null, CmpMatchConfidence.UNKNOWN, emptySet(), emptySet())
        val top = scored.first()
        val tied = scored.drop(1).filter { it.strong.size == top.strong.size && it.markers.size + it.phrases.size == top.markers.size + top.phrases.size }
        if (tied.isNotEmpty()) return CmpProfileMatch(null, null, CmpMatchConfidence.AMBIGUOUS, top.markers, top.phrases, (tied.map { it.profile.id } + top.profile.id).toSet())
        val independent = (if (top.markers.isNotEmpty()) 1 else 0) + (if (top.phrases.isNotEmpty()) 1 else 0)
        val confidence = when {
            top.strong.isNotEmpty() && independent >= 2 -> CmpMatchConfidence.HIGH
            top.markers.isNotEmpty() || top.phrases.isNotEmpty() -> CmpMatchConfidence.PROBABLE
            else -> CmpMatchConfidence.UNKNOWN
        }
        return CmpProfileMatch(top.profile.id, top.profile.displayName, confidence, top.markers, top.phrases)
    }
    private fun canonical(value: String): String = value.lowercase().trim().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
