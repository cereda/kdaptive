// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The result of a recognition, and the flag that stops it.
 *
 * The result carries the verdict *and* the paths it was made of. The flag
 * stops the recognition on an acceptance — never on a rejection, because a
 * rejecting path says nothing about the branches still in flight.
 */
class RecognitionResultTest {

    private object R0 : State() {
        override fun toString(): String = "r0"
    }

    private object R1 : State() {
        override fun toString(): String = "r1"
    }

    private object R2 : State() {
        override fun toString(): String = "r2"
    }

    private object R3 : State() {
        override fun toString(): String = "r3"
    }

    private object R4 : State() {
        override fun toString(): String = "r4"
    }

    private object R5 : State() {
        override fun toString(): String = "r5"
    }

    private object R6 : State() {
        override fun toString(): String = "r6"
    }

    /** An automaton that forks: one branch accepts, the other rejects. */
    private class ForkingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = ForkingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(R0, R1, R2),
                    initialState = R0,
                    acceptingStates = setOf(R1),
                ),
            )
            consume(R0, CharSymbol('a'), R1)
            consume(R0, CharSymbol('a'), R2)
        }
    }

    @Test
    fun `the result carries the verdict and the paths`() {
        val result = ForkingAa().recognize(listOf(CharSymbol('a')))

        assertTrue(result.accepted)
        assertEquals(2, result.paths.size)
        assertEquals(1, result.acceptedPaths.size)
        assertEquals(1, result.rejectedPaths.size)
        assertTrue(result.cutPaths.isEmpty())
    }

    @Test
    fun `every reported path reached a verdict`() {
        val result = ForkingAa().recognize(listOf(CharSymbol('a')))

        assertTrue(result.paths.all { it.done })
        assertTrue(result.paths.all { it.outcome != PathOutcome.PENDING })
    }

    @Test
    fun `the paths of the result are the paths that reached a verdict`() {
        val result = ForkingAa().recognize(listOf(CharSymbol('a')))

        assertEquals(result.paths.size, result.acceptedPaths.size + result.rejectedPaths.size + result.cutPaths.size)
    }

    /**
     * An automaton whose accepting branch only accepts after two forks, so
     * the acceptance happens in a later round than the rejection.
     */
    private class LateAcceptingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = LateAcceptingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(R0, R1, R2, R3, R4, R5, R6),
                    initialState = R0,
                    acceptingStates = setOf(R3, R5, R6),
                ),
            )
            epsilon(R0, R1)
            epsilon(R0, R2)
            epsilon(R2, R3)
            epsilon(R2, R4)
            epsilon(R4, R5)
            epsilon(R4, R6)
        }
    }

    @Test
    fun `a rejection does not stop the recognition`() {
        // The first branch rejects at once; the second one accepts later.
        // Under the old flag — which stopped on any finished path — the
        // rejection would have silenced the acceptance.
        val aa = LateAcceptingAa()
        aa.stopOnFirstAcceptance = true

        val result = aa.recognize(emptyList())

        assertTrue(result.accepted)
        assertTrue(result.acceptedPaths.isNotEmpty())
    }

    @Test
    fun `the recognition is exhaustive by default`() {
        val result = LateAcceptingAa().recognize(emptyList())

        assertTrue(result.accepted)
        assertEquals(3, result.acceptedPaths.size)
        assertEquals(1, result.rejectedPaths.size)
    }

    @Test
    fun `stopOnFirstAcceptance stops the recognition at the first acceptance`() {
        val exhaustive = LateAcceptingAa().recognize(emptyList())

        val aa = LateAcceptingAa()
        aa.stopOnFirstAcceptance = true
        val stopped = aa.recognize(emptyList())

        assertTrue(stopped.accepted)
        // The recognition stopped as soon as a branch accepted: the branches
        // that were still in flight are not among the reported paths.
        assertTrue(stopped.acceptedPaths.size < exhaustive.acceptedPaths.size)
    }

    @Test
    fun `the flag is inherited by the clone`() {
        val aa = ForkingAa()
        aa.stopOnFirstAcceptance = true

        val result = aa.recognize(listOf(CharSymbol('a')))

        assertTrue(result.accepted)
    }

    @Test
    fun `the configured automaton is never mutated by a recognition`() {
        val aa = ForkingAa()
        val before = aa.transitions.size

        aa.recognize(listOf(CharSymbol('a')))

        assertEquals(before, aa.transitions.size)
    }

    @Test
    fun `two recognitions of the same automaton do not share their paths`() {
        val aa = ForkingAa()

        val first = aa.recognize(listOf(CharSymbol('a')))
        val second = aa.recognize(listOf(CharSymbol('a')))

        assertEquals(first.paths.size, second.paths.size)
        assertTrue(first.paths.map { it.id }.intersect(second.paths.map { it.id }.toSet()).isEmpty())
    }
}
