// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The structural bounds the engine enforces while it recognises.
 *
 * The bounds are a backstop, not a policy: they exist for the topologies the
 * engine cannot *prove* to be bounded. When a bound fires, the engine throws:
 * the answer there is "unknown", not "rejected".
 *
 * The bounds are exact. The number of live paths never exceeds the bound, and
 * the call stack never grows deeper than the bound: a consumer that sets a
 * bound gets exactly the guarantee the bound names.
 */
class RecognitionBoundsTest {

    /** A state of the forking automaton. */
    private object F0 : State() {
        override fun toString(): String = "f0"
    }

    /**
     * An automaton that forks [branches] paths at once.
     *
     * Every alternative leaves the initial state and consumes the same
     * symbol, so the engine forks one path per alternative.
     */
    private class ForkingAa(private val branches: Int) : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = ForkingAa(branches)

        override fun setup() {
            mainSubmachine = "M"
            val targets = (0 until branches).map { index ->
                object : State() {
                    override fun toString(): String = "f$index"
                }
            }
            submachines.add(
                Submachine(
                    name = "M",
                    states = targets.toSet() + F0,
                    initialState = F0,
                    acceptingStates = emptySet(),
                ),
            )
            targets.forEach { target -> symbol(F0, CharSymbol('a'), target) }
        }
    }

    @Test
    fun `a fan-out larger than the bound is refused`() {
        val aa = ForkingAa(branches = 10)
        aa.limits = RecognitionLimits(maxLivePaths = 4)

        val error = assertThrows<RecognitionFailedException> { aa.recognize(listOf(CharSymbol('a'))) }

        assertTrue(error.message!!.contains("live paths"))
        assertTrue(error.message!!.contains("maxLivePaths"))
    }

    @Test
    fun `the number of live paths never exceeds the bound`() {
        // The root path is alive while it forks, so a fan-out of n branches
        // needs a bound of n + 1: the root and its n children are alive at
        // the same time.
        val aa = ForkingAa(branches = 10)
        aa.limits = RecognitionLimits(maxLivePaths = 11)

        val result = aa.recognize(listOf(CharSymbol('a')))

        assertEquals(10, result.paths.size)
        assertTrue(result.paths.all { it.outcome == PathOutcome.Rejected })
    }

    @Test
    fun `a bound of one leaves no room for a fork`() {
        // The root path is one live path already: it cannot create another
        // one without going past the bound.
        val aa = ForkingAa(branches = 2)
        aa.limits = RecognitionLimits(maxLivePaths = 1)

        val error = assertThrows<RecognitionFailedException> { aa.recognize(listOf(CharSymbol('a'))) }

        assertTrue(error.message!!.contains("live paths"))
    }

    @Test
    fun `a bound that fits lets the recognition finish`() {
        val aa = ForkingAa(branches = 5)
        aa.limits = RecognitionLimits(maxLivePaths = 100)

        val result = aa.recognize(listOf(CharSymbol('a')))

        assertEquals(5, result.paths.size)
    }

    /** A state of the calling automaton. */
    private object M0 : State() {
        override fun toString(): String = "m0"
    }

    /** A state of the calling automaton. */
    private object M1 : State() {
        override fun toString(): String = "m1"
    }

    /** A state of the called automaton. */
    private object S0 : State() {
        override fun toString(): String = "s0"
    }

    /** A state of the called automaton. */
    private object S1 : State() {
        override fun toString(): String = "s1"
    }

    /**
     * An automaton that calls itself, over and over.
     *
     * The two submachines call one another, and neither of them ever
     * consumes anything: the call stack grows until the bound stops it.
     */
    private class RecursiveAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = RecursiveAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(M0, M1),
                    initialState = M0,
                    acceptingStates = setOf(M1),
                ),
            )
            submachines.add(
                Submachine(
                    name = "S",
                    states = setOf(S0, S1),
                    initialState = S0,
                    acceptingStates = setOf(S1),
                ),
            )
            // The action is what lets the automaton past the static check: a
            // cycle that consumes no input may be broken by an action. This
            // one never fires, so the calls go on.
            actions.add(Action(name = "Never") { _, _, _ -> })
            call(M0, "S", M1)
            call(S0, "M", S1)
        }
    }

    @Test
    fun `a recursion deeper than the bound is refused`() {
        val aa = RecursiveAa()
        aa.limits = RecognitionLimits(maxCallDepth = 4)

        val error = assertThrows<RecognitionFailedException> { aa.recognize(emptyList()) }

        assertTrue(error.message!!.contains("call stack of depth"))
        assertTrue(error.message!!.contains("maxCallDepth"))
    }

    @Test
    fun `the call stack never grows deeper than the bound`() {
        // The stack holds one frame per call: the bound is the depth the
        // stack may reach, and the call that would take it past the bound is
        // the one refused.
        val aa = RecursiveAa()
        aa.limits = RecognitionLimits(maxCallDepth = 3)

        val error = assertThrows<RecognitionFailedException> { aa.recognize(emptyList()) }

        assertTrue(error.message!!.contains("reached a call stack of depth 4"))
    }
}
