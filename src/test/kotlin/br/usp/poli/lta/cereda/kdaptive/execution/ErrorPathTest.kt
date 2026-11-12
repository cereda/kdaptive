// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.dsl.SubmachineBuilder
import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Exercises the error paths of the execution engine: malformed action
 * references, unknown action names, and the fail-fast accessors of the
 * null-free API.
 */
class ErrorPathTest {
    /** Recognises the single symbol 'a'; its transition carries [fault]. */
    private open class FaultAa(private val fault: (TransitionEdge) -> TransitionEdge?) : AdaptiveAutomaton() {
        private val q0 = object : State() {}
        private val q1 = object : State() {}

        override fun newInstance(): AdaptiveAutomaton = FaultAa(fault)

        override fun setup() {
            mainSubmachine = "M"
            val sm =
                Submachine(
                    name = "M",
                    states = setOf(q0, q1),
                    initialState = q0,
                    acceptingStates = setOf(q1),
                )
            submachines.add(sm)

            val edge =
                TransitionEdge(
                    source = q0,
                    target = q1,
                    transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                )
            transitions.add(fault(edge) ?: edge)
        }

        fun char(vararg chars: Char): List<CharSymbol> = chars.map { CharSymbol(it) }
    }

    @Test
    fun `an automaton without a main submachine fails fast`() {
        val aa = PlainAa()
        val ex = assertThrows<IllegalStateException> { aa.mainSubmachine }
        assertTrue(ex.message!!.contains("Main submachine"))
    }

    /** An automaton exposing its states, so the edge-building guards can be exercised. */
    private class PlainAa : AdaptiveAutomaton() {
        val q0 = object : State() {}
        val q1 = object : State() {}

        override fun newInstance(): AdaptiveAutomaton = PlainAa()

        override fun setup() {
            mainSubmachine = "M"
        }
    }

    @Test
    fun `a filter reference cannot be attached to an edge`() {
        // An edge fires one concrete action: a filter has no place there.
        val aa = PlainAa()
        val ex =
            assertThrows<IllegalArgumentException> {
                aa.symbol(aa.q0, 'a', aa.q1, prior = ActionRef.Named("A"))
            }
        assertTrue(ex.message!!.contains("is a filter"))
    }

    @Test
    fun `a wildcard symbol cannot be consumed`() {
        val aa = PlainAa()
        assertThrows<IllegalArgumentException> { aa.symbol(aa.q0, Symbol.Any, aa.q1) }
        assertThrows<IllegalArgumentException> { aa.symbol(aa.q0, Symbol.Epsilon, aa.q1) }
    }

    @Test
    fun `unknown post action name fails loudly`() {
        val aa = FaultAa { it.copy(post = ActionRef.Bound(name = "no-such-action")) }
        val ex = assertThrows<RecognitionFailedException> { aa.recognize(aa.char('a')) }
        assertTrue(ex.message!!.contains("Recognition path"))
        assertTrue(ex.message!!.contains("No action with name 'no-such-action'."))
    }

    @Test
    fun `an unknown prior action name fails loudly`() {
        val aa = FaultAa { it.copy(prior = ActionRef.Bound(name = "no-such-action")) }
        val ex = assertThrows<RecognitionFailedException> { aa.recognize(aa.char('a')) }
        assertTrue(ex.message!!.contains("Recognition path"))
        assertTrue(ex.message!!.contains("No action with name 'no-such-action'."))
    }

    @Test
    fun `submachine builder without initial state throws`() {
        val builder = SubmachineBuilder(mutableMapOf())
        builder.state("q1")
        val ex = assertThrows<IllegalArgumentException> { builder.build("M") }
        assertTrue(ex.message!!.contains("must define an initial state"))
    }
}
