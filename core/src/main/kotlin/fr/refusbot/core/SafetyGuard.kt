package fr.refusbot.core

/**
 * GARDE-FOU CENTRAL, appliqué à TOUT clic (boutons et interrupteurs).
 *
 * Un élément est bloqué si l'un des textes de son sous-arbre contient une forme interdite
 * (accepter, accept, agree, autoriser, allow, consentir…), SAUF si ce texte correspond
 * EXACTEMENT à une formule de refus de `rules.json` (« Continuer sans accepter », « Disagree »…).
 *
 * En cas de doute, le clic n'a pas lieu.
 */
private const val SENSITIVE_ACCESSIBILITY_TOKEN = "accessibility-data-sensitive"
private const val PROTECTED_SUBTREE_TOKEN = "protected-accessibility-subtree"
private const val FOREIGN_SCRIPT_TOKEN = "foreign-script"

class SafetyGuard(private val rules: Rules) {

    sealed interface Verdict {
        val isAllowed: Boolean

        data object Allowed : Verdict {
            override val isAllowed = true
        }

        data class Blocked(val text: String, val token: String) : Verdict {
            override val isAllowed = false

            /** Motif destiné au journal : aucun texte sensible n'est reproduit. */
            val reason: String get() = when (token) {
                SENSITIVE_ACCESSIBILITY_TOKEN -> "garde-fou : cible marquée comme sensible bloquée"
                PROTECTED_SUBTREE_TOKEN -> "garde-fou : cible située dans un sous-arbre protégé bloquée"
                FOREIGN_SCRIPT_TOKEN -> "garde-fou : texte dans un alphabet non pris en charge bloqué"
                else -> "garde-fou : bouton contenant « $token » bloqué"
            }

            /** Détail avec le libellé, réservé au mode diagnostic. */
            val detail: String get() = when (token) {
                SENSITIVE_ACCESSIBILITY_TOKEN -> "cible marquée comme donnée d'accessibilité sensible"
                PROTECTED_SUBTREE_TOKEN -> "cible située dans un sous-arbre protégé"
                FOREIGN_SCRIPT_TOKEN -> "« ${text.take(80)} » contient des lettres d'un alphabet non pris en charge"
                else -> "« ${text.take(80)} » contient « $token »"
            }
        }
    }

    /**
     * Vérifie un texte isolé. [selectionConfirm] : le moteur a vérifié que tout est refusé ; seules
     * alors les formules exactes de [SelectionConfirm] (« Autoriser la sélection ») passent.
     */
    fun checkText(raw: String, selectionConfirm: Boolean = false): Verdict {
        // Audit du 21/11 (n° 4) : la forme canonique ne garde que a-z et 0-9. Un libellé russe, grec,
        // chinois… devenait vide (donc autorisé), et « Аccept » avec un « А » cyrillique devenait « ccept ».
        // Toute lettre hors de l'alphabet latin (accents compris, une fois retirés) bloque le clic.
        if (hasForeignLetters(raw)) return Verdict.Blocked(raw, FOREIGN_SCRIPT_TOKEN)
        val canon = TextNormalizer.canonical(raw)
        if (canon.isEmpty()) return Verdict.Allowed
        val token = forbiddenToken(canon) ?: return Verdict.Allowed
        if (canon in rules.refusalFormulas) return Verdict.Allowed
        if (isNegatedConsent(canon)) return Verdict.Allowed
        if (selectionConfirm && SelectionConfirm.isFormula(canon)) return Verdict.Allowed
        return Verdict.Blocked(raw, token)
    }

    /** Vérifie la cible d'un clic/dépliage, sans lire un sous-arbre marqué sensible. */
    fun checkNode(target: UiNode, selectionConfirm: Boolean = false): Verdict {
        val nodes = target.walk().toList()
        if (nodes.any { it.isAccessibilityDataSensitive }) {
            return Verdict.Blocked("", SENSITIVE_ACCESSIBILITY_TOKEN)
        }
        if (nodes.any { it.isPassword || it.isEditable || it.insideProtectedSubtree }) {
            return Verdict.Blocked("", PROTECTED_SUBTREE_TOKEN)
        }
        for (t in target.subtreeTexts()) {
            val v = checkText(t, selectionConfirm)
            if (!v.isAllowed) return v
        }
        return Verdict.Allowed
    }

    /**
     * Refus formulé avec une négation (Didomi, 20/11 : « Je n'accepte rien: Refuser notre traitement
     * des données et fermer »). Autorisé seulement si CHAQUE forme interdite du texte est un verbe
     * d'acceptation (jamais un mot de paiement ou d'abonnement) précédé immédiatement d'une négation
     * (« n'accepte », « ne pas autoriser », « sans accepter »), et si aucun mot interdit isolé (« ok »,
     * « oui »…) n'apparaît. « Je n'accepte pas X mais j'accepte Y » reste bloqué (2e forme non niée).
     */
    fun isNegatedConsent(canon: String): Boolean {
        if (rules.forbiddenWords.any { TextNormalizer.containsPhrase(canon, it) }) return false
        val words = canon.split(' ').filter { it.isNotEmpty() }
        // « Pourquoi ne pas accepter ? », « Why not accept all? » : une question invite à accepter.
        if (words.any { it in QUESTION_WORDS }) return false
        var found = false
        for ((i, w) in words.withIndex()) {
            val token = rules.forbiddenSubstrings.firstOrNull { w.contains(it) } ?: continue
            if (token !in NEGATABLE) return false
            val before = words.getOrNull(i - 1)
            // « pas » ou « jamais » seuls ne suffisent pas : « Ne ratez pas : accepter » donnerait
            // « pas accepter ». Seules les formes complètes « ne pas / ne jamais accepter » comptent.
            val paired = before in PAIRED_NEGATIONS && words.getOrNull(i - 2) == "ne"
            // Contraction anglaise (« don t accept ») : le « t » ne vaut négation que précédé d'un
            // auxiliaire anglais fermé ; « je t'autorise » reste une acceptation, donc bloquée.
            val contracted = before == "t" && words.getOrNull(i - 2) in ENGLISH_AUX
            val negated = before in NEGATIONS || paired || contracted
            if (!negated) return false
            // Audit du 21/11 : « Je n'accepte que les cookies publicitaires » = « seulement » ;
            // « ne pas ne pas accepter » = double négation. Dans les deux cas : pas un refus.
            if (words.getOrNull(i + 1) in RESTRICTIVE_AFTER) return false
            val negationStart = if (paired) i - 2 else i - 1
            if (words.getOrNull(negationStart - 1) in ANY_NEGATION) return false
            found = true
        }
        return found
    }

    /** Lettres que la normalisation ne ramène pas à a-z (cyrillique, grec, CJK, « ø », « ł »…). */
    private fun hasForeignLetters(raw: String): Boolean =
        TextNormalizer.normalize(raw).any { it.isLetter() && it !in 'a'..'z' }

    /** Mots qui transforment « je n'accepte… » en « je n'accepte QUE… » (seulement) : une acceptation. */
    private val RESTRICTIVE_AFTER = setOf("que", "qu", "only", "solo", "nur", "seulement", "uniquement")

    /** Mots de négation (toutes formes) : deux négations de suite s'annulent (« ne pas ne pas accepter »). */
    private val ANY_NEGATION = setOf("n", "ne", "pas", "jamais", "sans", "not", "no", "non", "never", "t")

    private val NEGATABLE = setOf("accept", "autoris", "agree", "allow", "consentir", "consens")

    /**
     * Négations valables seules juste avant le verbe.
     * « no » est volontairement absent : « No thanks » est ambigu dans les deux sens.
     * « t » n'y figure PAS : en français, « Je t'autorise » serait lu à tort comme un refus
     * (contre-audit du 24/11). La contraction anglaise est reconnue séparément ([ENGLISH_AUX]).
     */
    private val NEGATIONS = setOf("n", "ne", "sans", "not", "never")

    /**
     * Auxiliaires anglais dont la contraction « n't » devient « n t » en forme canonique
     * (« Don't accept » → « don t accept ») : le « t » ne vaut négation que juste après l'un
     * d'eux. Liste fermée : un « t » après tout autre mot (« je t'autorise ») n'est pas une négation.
     */
    private val ENGLISH_AUX = setOf(
        "don", "doesn", "didn", "won", "can", "couldn", "shouldn", "wouldn",
        "isn", "aren", "wasn", "weren", "ain", "mustn", "needn", "shan",
    )

    /** Valables seulement précédées de « ne » (« ne pas accepter », « ne jamais autoriser »). */
    private val PAIRED_NEGATIONS = setOf("pas", "jamais")

    /** Mots de question : la négation devient une suggestion (« pourquoi ne pas accepter »). */
    private val QUESTION_WORDS = setOf("pourquoi", "why", "how", "what", "comment", "quoi")

    /** Renvoie la forme interdite trouvée dans un texte canonique, ou null. */
    fun forbiddenToken(canon: String): String? {
        rules.forbiddenSubstrings.firstOrNull { canon.contains(it) }?.let { return it }
        return rules.forbiddenWords.firstOrNull { TextNormalizer.containsPhrase(canon, it) }
    }
}
