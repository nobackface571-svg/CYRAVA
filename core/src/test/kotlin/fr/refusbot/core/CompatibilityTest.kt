package fr.refusbot.core

import fr.refusbot.core.OutcomeStatus.DIRECT_SUCCESS
import fr.refusbot.core.OutcomeStatus.IMPOSSIBLE
import fr.refusbot.core.OutcomeStatus.INTERRUPTED
import fr.refusbot.core.OutcomeStatus.OBSERVED
import fr.refusbot.core.OutcomeStatus.OPTIONS_SUCCESS
import fr.refusbot.core.OutcomeStatus.PARTIAL
import fr.refusbot.core.OutcomeStatus.UNVERIFIED
import org.junit.Assert.assertEquals
import org.junit.Test

class CompatibilityTest {

    private fun rate(vararg s: OutcomeStatus, changed: Boolean = false) = CompatibilityRating.rate(s.toList(), changed)

    @Test
    fun `aucune fenetre ou seulement des interruptions donne inconnue`() {
        assertEquals(Compatibility.INCONNUE, rate())
        assertEquals(Compatibility.INCONNUE, rate(INTERRUPTED, INTERRUPTED))
    }

    @Test
    fun `refus verifies donnent compatible`() {
        assertEquals(Compatibility.COMPATIBLE, rate(OPTIONS_SUCCESS, OPTIONS_SUCCESS, INTERRUPTED, OPTIONS_SUCCESS))
        // Un refus cliqué mais non vérifiable n'est jamais présenté comme « compatible ».
        assertEquals(Compatibility.REFUS_CLIQUE, rate(UNVERIFIED, OPTIONS_SUCCESS, INTERRUPTED, UNVERIFIED))
        assertEquals(Compatibility.REFUS_CLIQUE, rate(DIRECT_SUCCESS, OPTIONS_SUCCESS))
    }

    @Test
    fun `un refus partiel recent donne partielle`() {
        assertEquals(Compatibility.PARTIELLE, rate(PARTIAL, DIRECT_SUCCESS))
        assertEquals(Compatibility.PARTIELLE, rate(IMPOSSIBLE, OPTIONS_SUCCESS))
    }

    @Test
    fun `que des echecs donne non prise en charge`() {
        assertEquals(Compatibility.NON_PRISE_EN_CHARGE, rate(IMPOSSIBLE, IMPOSSIBLE, OBSERVED))
    }

    @Test
    fun `observation seule donne observee`() {
        assertEquals(Compatibility.OBSERVEE, rate(OBSERVED, OBSERVED))
    }

    @Test
    fun `interface modifiee prime sur le reste`() {
        assertEquals(Compatibility.A_VERIFIER, rate(OBSERVED, DIRECT_SUCCESS, changed = true))
        // Nouvelle interface déjà refusée avec succès : rien à vérifier.
        assertEquals(Compatibility.COMPATIBLE, rate(OPTIONS_SUCCESS, OPTIONS_SUCCESS, changed = true))
        assertEquals(Compatibility.REFUS_CLIQUE, rate(UNVERIFIED, OPTIONS_SUCCESS, changed = true))
    }

    @Test
    fun `seules les cinq dernieres fenetres comptent`() {
        assertEquals(
            Compatibility.COMPATIBLE,
            rate(OPTIONS_SUCCESS, OPTIONS_SUCCESS, OPTIONS_SUCCESS, OPTIONS_SUCCESS, OPTIONS_SUCCESS, IMPOSSIBLE),
        )
    }

    @Test
    fun `un mur payant ne rend pas l'appli partielle`() {
        val history = listOf(
            UNVERIFIED to null,
            IMPOSSIBLE to ConsentOrPay.REASON,
            UNVERIFIED to null,
            DIRECT_SUCCESS to null,
        )
        assertEquals(Compatibility.REFUS_CLIQUE, CompatibilityRating.rateHistory(history, false))
        // Un vrai échec, lui, compte toujours.
        assertEquals(
            Compatibility.PARTIELLE,
            CompatibilityRating.rateHistory(listOf(UNVERIFIED to null, IMPOSSIBLE to "aucun bouton de refus"), false),
        )
        // Uniquement des murs payants : rien à juger.
        assertEquals(Compatibility.INCONNUE, CompatibilityRating.rateHistory(listOf(IMPOSSIBLE to ConsentOrPay.REASON), false))
    }
}
