// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.utils

import br.usp.poli.lta.cereda.kdaptive.model.State
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The verdict of a path: a tri-state that names itself, instead of a nullable
 * boolean.
 */
class PathOutcomeTest {

    @Test
    fun `a fresh path has not decided yet`() {
        val path = RecognitionPath()

        assertEquals(PathOutcome.PENDING, path.outcome)
        assertFalse(path.done)
    }

    @Test
    fun `an accepting path is done`() {
        val path = RecognitionPath(outcome = PathOutcome.ACCEPTED)

        assertTrue(path.done)
        assertEquals(PathOutcome.ACCEPTED, path.outcome)
    }

    @Test
    fun `a rejecting path is done`() {
        val path = RecognitionPath(outcome = PathOutcome.REJECTED)

        assertTrue(path.done)
        assertEquals(PathOutcome.REJECTED, path.outcome)
    }

    @Test
    fun `the verdict survives a copy`() {
        val path = RecognitionPath().copy(outcome = PathOutcome.ACCEPTED)

        assertTrue(path.done)
        assertEquals(PathOutcome.ACCEPTED, path.outcome)
    }

    @Test
    fun `the state of a path that has not started is still optional`() {
        assertEquals(null, RecognitionPath().state)
    }

    @Test
    fun `the path reports its verdict`() {
        assertTrue(RecognitionPath(outcome = PathOutcome.ACCEPTED).toString().contains("ACCEPTED"))
        assertFalse(RecognitionPath().toString().contains("PENDING"))
    }

    @Test
    fun `a path keeps its state`() {
        val state = object : State() {
            override fun toString(): String = "q0"
        }

        assertEquals(state, RecognitionPath(state = state).state)
    }
}
