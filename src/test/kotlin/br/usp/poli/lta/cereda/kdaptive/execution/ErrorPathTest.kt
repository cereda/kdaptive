// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.dsl.SubmachineBuilder
import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Exercises the error paths of the execution engine: querying results before
 * [AdaptiveAutomaton.recognize], malformed action references, and unknown
 * action names.
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
    fun `getRecognitionMap before recognize throws`() {
        val aa = FaultAa { it }
        val ex = assertThrows<IllegalStateException> { aa.getRecognitionMap() }
        assertTrue(ex.message!!.contains("recognize() must be called"))
    }

    @Test
    fun `getRecognitionPaths before recognize throws`() {
        val aa = FaultAa { it }
        val ex = assertThrows<IllegalStateException> { aa.getRecognitionPaths() }
        assertTrue(ex.message!!.contains("recognize() must be called"))
    }

    @Test
    fun `unnamed prior action fails loudly`() {
        val aa = FaultAa { it.copy(prior = ActionRef(name = null)) }
        val ex = assertThrows<IllegalStateException> { aa.recognize(aa.char('a')) }
        assertTrue(ex.message!!.contains("Recognition path"))
        assertTrue(ex.message!!.contains("has no name"))
    }

    @Test
    fun `unknown post action name fails loudly`() {
        val aa = FaultAa { it.copy(post = ActionRef(name = "no-such-action")) }
        val ex = assertThrows<IllegalStateException> { aa.recognize(aa.char('a')) }
        assertTrue(ex.message!!.contains("Recognition path"))
        assertTrue(ex.message!!.contains("No action with name 'no-such-action'."))
    }

    @Test
    fun `unnamed post action registration unresolved fails loudly`() {
        // An action reference without arguments is still valid; ensure a null-name post
        // action is also rejected instead of silently being skipped.
        val aa = FaultAa { it.copy(post = ActionRef(name = null)) }
        val ex = assertThrows<IllegalStateException> { aa.recognize(aa.char('a')) }
        assertTrue(ex.message!!.contains("Recognition path"))
    }

    @Test
    fun `submachine builder without initial state throws`() {
        val builder = SubmachineBuilder(mutableMapOf())
        builder.state("q1")
        val ex = assertThrows<IllegalArgumentException> { builder.build("M") }
        assertTrue(ex.message!!.contains("must define an initial state"))
    }
}
