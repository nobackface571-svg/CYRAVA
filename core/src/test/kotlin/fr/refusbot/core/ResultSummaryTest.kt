package fr.refusbot.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ResultSummaryTest {
    @Test
    fun `chaque appli cochee est comptee une seule fois, les decochees jamais`() {
        val latest = mapOf(
            "a" to OutcomeStatus.OPTIONS_SUCCESS,
            "b" to OutcomeStatus.UNVERIFIED,
            "c" to OutcomeStatus.PARTIAL,
            "d" to OutcomeStatus.INCOMPLETE_CAPTURE,
            "e" to OutcomeStatus.OBSERVED,
            "f" to OutcomeStatus.INTERRUPTED,
            "plus.cochee" to OutcomeStatus.IMPOSSIBLE,
        )
        val s = ResultSummary.of(setOf("a", "b", "c", "d", "e", "f", "jamais.vue"), latest)
        assertEquals(7, s.selected)
        assertEquals(1, s.count(ResultCategory.VERIFIED))
        assertEquals(1, s.count(ResultCategory.UNVERIFIED))
        assertEquals(1, s.count(ResultCategory.INCOMPLETE))
        assertEquals("l'appli décochée n'est pas comptée", 1, s.count(ResultCategory.FAILED))
        assertEquals(2, s.count(ResultCategory.NO_ACTION))
        assertEquals(1, s.noResult)
        assertEquals(s.selected, s.byCategory.values.sum() + s.noResult)
    }

    @Test
    fun `seul le refus avec interrupteurs relus est verifie`() {
        for (st in OutcomeStatus.entries) {
            assertEquals(st.name, st == OutcomeStatus.OPTIONS_SUCCESS, ResultCategory.of(st) == ResultCategory.VERIFIED)
        }
    }
}
