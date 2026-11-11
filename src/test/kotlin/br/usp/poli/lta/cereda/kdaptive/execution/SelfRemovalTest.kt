// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Submachines
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.utils.PathOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private object R0 : State() {
    override fun toString(): String = "r0"
}

private object R1 : State() {
    override fun toString(): String = "r1"
}

/**
 * A prior action that removes the transition it belongs to.
 *
 * The transition never happens: the path is still at its source state and
 * must choose again among the transitions applicable there. The original
 * implementation re-planned the path in exactly this situation, and the port
 * used to kill the path instead.
 */
class SelfRemovalTest {

    /**
     * An automaton with one edge out of [R0] consuming 'a'. Its prior action
     * removes that edge and installs another one in its place.
     */
    private class SelfRemovingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = SelfRemovingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(R0, R1),
                    initialState = R0,
                    acceptingStates = setOf(R1),
                ),
            )
            actions.add(
                Action(name = "SelfRemove") { transitions, _, _ ->
                    val ea = ElementaryActions(transitions, Submachines())
                    ea.remove { prior = ActionRef.Named("SelfRemove") }
                    ea.add(R0, CharSymbol('a'), R1)
                },
            )
            transitions.add(
                TransitionEdge(
                    source = R0,
                    target = R1,
                    transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                    prior = ActionRef.Bound("SelfRemove"),
                ),
            )
        }
    }

    /** An automaton whose prior action removes every edge it could take. */
    private class RemovingAllAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = RemovingAllAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(R0, R1),
                    initialState = R0,
                    acceptingStates = setOf(R1),
                ),
            )
            actions.add(
                Action(name = "RemoveAll") { transitions, _, _ ->
                    ElementaryActions(transitions, Submachines()).remove(
                        source = State.Any,
                        symbol = Symbol.Any,
                        target = State.Any,
                    )
                },
            )
            transitions.add(
                TransitionEdge(
                    source = R0,
                    target = R1,
                    transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                    prior = ActionRef.Bound("RemoveAll"),
                ),
            )
        }
    }

    @Test
    fun `the path chooses again when its prior action removes the transition`() {
        val aa = SelfRemovingAa()

        // The path takes the edge carrying the prior action, the action
        // removes that edge and installs another one, and the path re-plans:
        // the new edge consumes the same symbol, so the input is accepted.
        val result = aa.recognize(listOf(CharSymbol('a')))
        assertTrue(result.accepted)
        assertEquals(1, result.paths.size)
        assertEquals(PathOutcome.ACCEPTED, result.paths.single().outcome)
    }

    @Test
    fun `the prior action does not fire again on the transition chosen afterwards`() {
        val aa = SelfRemovingAa()
        var fired = 0
        aa.visitors.add(
            object : ExecutionVisitor {
                override fun onBeforeAction(pathId: Int, actionRef: ActionRef.Bound, isPrior: Boolean) {
                    if (isPrior) fired++
                }
            },
        )

        assertTrue(aa.recognize(listOf(CharSymbol('a'))).accepted)

        // The action fired once: the re-planned transition carries no action,
        // and the one that carried it is gone.
        assertEquals(1, fired)
    }

    @Test
    fun `the path ends when the re-planning finds nothing`() {
        val aa = RemovingAllAa()

        // Every edge is gone: the path has nowhere to go and rejects. It does
        // not spin forever.
        val result = aa.recognize(listOf(CharSymbol('a')))
        assertFalse(result.accepted)
        assertEquals(1, result.paths.size)
    }
}
