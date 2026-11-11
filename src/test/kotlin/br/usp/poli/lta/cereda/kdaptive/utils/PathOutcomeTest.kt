// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.utils

import br.usp.poli.lta.cereda.kdaptive.model.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The verdict of a path: a four-state that names itself, instead of a nullable
 * boolean. A path is always in a state, and always has an outcome.
 */
class PathOutcomeTest {

    private val q0 = object : State() {
        override fun toString(): String = "q0"
    }

    @Test
    fun `a fresh path has not decided yet`() {
        val path = RecognitionPath(state = q0)

        assertEquals(PathOutcome.PENDING, path.outcome)
        assertFalse(path.done)
    }

    @Test
    fun `an accepting path is done`() {
        val path = RecognitionPath(state = q0, outcome = PathOutcome.ACCEPTED)

        assertTrue(path.done)
        assertEquals(PathOutcome.ACCEPTED, path.outcome)
    }

    @Test
    fun `a rejecting path is done`() {
        val path = RecognitionPath(state = q0, outcome = PathOutcome.REJECTED)

        assertTrue(path.done)
        assertEquals(PathOutcome.REJECTED, path.outcome)
    }

    @Test
    fun `a cut path is done, and a cut is not a rejection`() {
        val path = RecognitionPath(state = q0, outcome = PathOutcome.CUT)

        assertTrue(path.done)
        assertEquals(PathOutcome.CUT, path.outcome)
        assertFalse(path.outcome == PathOutcome.REJECTED)
    }

    @Test
    fun `the verdict survives a copy`() {
        val path = RecognitionPath(state = q0).copy(outcome = PathOutcome.ACCEPTED)

        assertTrue(path.done)
        assertEquals(PathOutcome.ACCEPTED, path.outcome)
    }

    @Test
    fun `the path reports its verdict`() {
        assertTrue(RecognitionPath(state = q0, outcome = PathOutcome.ACCEPTED).toString().contains("ACCEPTED"))
        assertFalse(RecognitionPath(state = q0).toString().contains("PENDING"))
    }

    @Test
    fun `a path keeps its state`() {
        assertEquals(q0, RecognitionPath(state = q0).state)
    }
}
