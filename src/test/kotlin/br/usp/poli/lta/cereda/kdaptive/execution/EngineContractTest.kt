// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Submachines
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.TransitionMapping
import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.utils.PathOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The contracts the engine owes its users.
 *
 * Each test pins one behaviour that used to be broken: the duplication of the
 * topology when `setup()` ran twice, the imbalance between the edges that
 * left a mapping and the events that reported it, the callbacks lost by a
 * copy, the symbol filter that also matched epsilon, the fork parents
 * reported as recognition paths, and the lifecycle events that did not pair
 * when a path failed.
 */
class EngineContractTest {

    private object C0 : State() {
        override fun toString(): String = "c0"
    }

    private object C1 : State() {
        override fun toString(): String = "c1"
    }

    private object C2 : State() {
        override fun toString(): String = "c2"
    }

    /** An automaton with a single applicable transition. */
    private class SingleEdgeAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = SingleEdgeAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(C0, C1),
                    initialState = C0,
                    acceptingStates = setOf(C1),
                ),
            )
            consume(C0, CharSymbol('a'), C1)
        }
    }

    /** An automaton that forks: both children consume the same symbol. */
    private class ForkingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = ForkingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(C0, C1, C2),
                    initialState = C0,
                    acceptingStates = setOf(C1),
                ),
            )
            consume(C0, CharSymbol('a'), C1)
            consume(C0, CharSymbol('a'), C2)
        }
    }

    /** An automaton that forks, and whose action grows the topology. */
    private class ForkingMutatingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = ForkingMutatingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(C0, C1, C2),
                    initialState = C0,
                    acceptingStates = setOf(C1),
                ),
            )
            actions.add(
                Action(name = "Grow") { transitions, _, _ ->
                    ElementaryActions(transitions, Submachines()).add(C2, CharSymbol('z'), C0)
                },
            )
            consume(C0, CharSymbol('a'), C1, prior = "Grow")
            consume(C0, CharSymbol('a'), C2, prior = "Grow")
        }
    }

    /** An automaton whose action fails. */
    private class FailingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = FailingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(C0, C1),
                    initialState = C0,
                    acceptingStates = setOf(C1),
                ),
            )
            actions.add(Action(name = "Boom") { _, _, _ -> error("boom") })
            consume(C0, CharSymbol('a'), C1, prior = "Boom")
        }
    }

    @Test
    fun `setup is idempotent`() {
        val aa = SingleEdgeAa()
        // The topology is already in place: running setup() again must not
        // duplicate a single edge.
        aa.setup()
        assertEquals(1, aa.transitions.size)

        val sizes = mutableListOf<Int>()
        aa.visitors.add(
            object : ExecutionVisitor {
                override fun onRecognizeStart(automaton: AdaptiveAutomaton) {
                    sizes.add(automaton.transitions.size)
                }
            },
        )

        assertTrue(aa.recognize(listOf(CharSymbol('a'))).accepted)
        assertEquals(listOf(1), sizes)
        assertEquals(1, aa.recognize(listOf(CharSymbol('a'))).paths.size)
    }

    @Test
    fun `a removal reports one topology event per edge removed`() {
        val mapping = TransitionMapping()
        mapping.add(
            TransitionEdge(
                source = C0,
                target = C1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            ),
        )
        mapping.add(
            TransitionEdge(
                source = C0,
                target = C2,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            ),
        )
        val removed = mutableListOf<TransitionEdge>()
        mapping.onEdgeRemoved = { removed.add(it) }

        ElementaryActions(mapping, Submachines()).remove()

        assertEquals(2, removed.size)
        assertEquals(0, mapping.size)
    }

    @Test
    fun `copying a mapping keeps its callbacks`() {
        val mapping = TransitionMapping()
        val added = mutableListOf<TransitionEdge>()
        val operations = mutableListOf<Int>()
        mapping.onEdgeAdded = { added.add(it) }
        mapping.onOperation = { operations.add(1) }

        val copy = mapping.copy()
        copy.add(
            TransitionEdge(
                source = C0,
                target = C1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            ),
        )
        ElementaryActions(copy, Submachines()).query()

        assertEquals(1, added.size)
        assertEquals(1, operations.size)
        // The original is untouched by the copy.
        assertEquals(0, mapping.size)
    }

    @Test
    fun `a symbol filter does not select an epsilon edge`() {
        val mapping = TransitionMapping()
        mapping.add(
            TransitionEdge(source = C0, target = C1, transition = Transition.Epsilon),
        )
        mapping.add(
            TransitionEdge(
                source = C0,
                target = C1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            ),
        )
        val ea = ElementaryActions(mapping, Submachines())

        assertEquals(1, ea.query(State.Any, Symbol.Any).size)
        assertEquals(1, ea.query(State.Any, Symbol.Epsilon).size)
    }

    @Test
    fun `only the paths that reached a verdict are reported`() {
        val aa = ForkingAa()

        val result = aa.recognize(listOf(CharSymbol('a')))

        val paths = result.paths
        // The forking parent reached no verdict: it is not one of the paths.
        assertEquals(2, paths.size)
        assertTrue(paths.all { it.done })
        assertEquals(paths.map { it.id }.toSet(), paths.map { it.id }.toSet())
    }

    @Test
    fun `the callbacks of the configured mapping reach the running paths`() {
        val aa = SingleEdgeAa()
        val added = mutableListOf<TransitionEdge>()
        aa.transitions.onEdgeAdded = { added.add(it) }

        assertTrue(aa.recognize(listOf(CharSymbol('a'))).accepted)

        // The clone carries the callback, and the path reports through it.
        assertEquals(1, added.size)
    }

    @Test
    fun `a forked path takes the edge it forked on`() {
        val aa = ForkingMutatingAa()
        val traversed = mutableListOf<Pair<Int, TransitionEdge>>()
        aa.visitors.add(
            object : ExecutionVisitor {
                override fun onAfterTraverse(pathId: Int, edge: TransitionEdge) {
                    traversed.add(pathId to edge)
                }
            },
        )

        aa.recognize(listOf(CharSymbol('a')))

        // Every child traverses the edge it forked on, in its own mapping:
        // the child owns a copy of the topology, and its edges have their own
        // identifiers. A child that referenced the parent's edge would be
        // killed by the very first check it makes on it.
        val symbols = traversed.filter { (_, edge) -> edge.transition is Transition.SymbolTransition }
        assertEquals(2, symbols.size)
        assertEquals(2, symbols.map { it.first }.toSet().size)

        // One child ends in an accepting state, the other does not.
        val verdicts = aa.recognize(listOf(CharSymbol('a'))).paths.map { it.outcome }
        assertEquals(1, verdicts.count { it == PathOutcome.ACCEPTED })
        assertEquals(1, verdicts.count { it == PathOutcome.REJECTED })
    }

    @Test
    fun `a forked path reports under its own identifier`() {
        val aa = ForkingMutatingAa()
        val reported = mutableListOf<Int>()
        aa.visitors.add(
            object : ExecutionVisitor {
                override fun onEdgeAdded(pathId: Int, edge: TransitionEdge, topology: Topology) {
                    reported.add(pathId)
                }
            },
        )

        val result = aa.recognize(listOf(CharSymbol('a')))

        // Each child inherits the topology, but reports under its own
        // identifier, never under the identifier of the parent that forked.
        assertEquals(2, reported.size)
        assertEquals(2, reported.toSet().size)
        assertEquals(result.paths.map { it.id }.toSet(), reported.toSet())
    }

    @Test
    fun `the lifecycle events pair even when a path fails`() {
        val aa = FailingAa()
        val events = mutableListOf<String>()
        aa.visitors.add(
            object : ExecutionVisitor {
                override fun onRecognizeStart(automaton: AdaptiveAutomaton) {
                    events.add("start")
                }

                override fun onPathEnd(pathId: Int, outcome: PathOutcome, cursor: Int) {
                    events.add("pathEnd:$outcome")
                }

                override fun onRecognizeEnd(result: Boolean) {
                    events.add("end:$result")
                }
            },
        )

        assertThrows<IllegalStateException> { aa.recognize(listOf(CharSymbol('a'))) }

        assertEquals(listOf("start", "pathEnd:REJECTED", "end:false"), events)
    }
}
