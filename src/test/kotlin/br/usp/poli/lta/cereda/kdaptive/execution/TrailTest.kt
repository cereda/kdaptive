// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The trail of a path: the edges it traversed, in order.
 *
 * The trail is the history of a path, not a decoration of the forked ones:
 * every path records what it walked through, including the edges that were
 * synthesised by the engine.
 */
class TrailTest {

    private object T0 : State() {
        override fun toString(): String = "t0"
    }

    private object T1 : State() {
        override fun toString(): String = "t1"
    }

    private object T2 : State() {
        override fun toString(): String = "t2"
    }

    private object T3 : State() {
        override fun toString(): String = "t3"
    }

    /** A deterministic automaton: two symbols, two states. */
    private class LinearAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = LinearAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(T0, T1),
                    initialState = T0,
                    acceptingStates = setOf(T1),
                ),
            )
            consume(T0, CharSymbol('a'), T1)
            consume(T1, CharSymbol('b'), T1)
        }
    }

    @Test
    fun `a deterministic run records its traversals`() {
        val result = LinearAa().recognize(listOf(CharSymbol('a'), CharSymbol('b')))

        val path = result.acceptedPaths.single()
        assertEquals(2, path.trail.size)
        assertEquals(T0, path.trail[0].source)
        assertEquals(T1, path.trail[0].target)
        assertEquals(T1, path.trail[1].source)
        assertEquals(T1, path.trail[1].target)
        assertEquals(CharSymbol('a'), (path.trail[0].transition as Transition.SymbolTransition).symbol)
        assertEquals(CharSymbol('b'), (path.trail[1].transition as Transition.SymbolTransition).symbol)
    }

    @Test
    fun `the trail ends at the state the path reached`() {
        val result = LinearAa().recognize(listOf(CharSymbol('a'), CharSymbol('b')))

        val path = result.acceptedPaths.single()
        assertEquals(T1, path.state)
        assertEquals(2, path.cursor)
    }

    @Test
    fun `a rejecting path records the edges it walked through`() {
        val result = LinearAa().recognize(listOf(CharSymbol('a'), CharSymbol('a')))

        val path = result.rejectedPaths.single()
        assertEquals(PathOutcome.REJECTED, path.outcome)
        assertEquals(1, path.trail.size)
        assertEquals(T1, path.state)
    }

    /** An automaton that forks: two alternatives leave the initial state. */
    private class ForkingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = ForkingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(T0, T1, T2),
                    initialState = T0,
                    acceptingStates = setOf(T1, T2),
                ),
            )
            consume(T0, CharSymbol('a'), T1)
            consume(T0, CharSymbol('a'), T2)
        }
    }

    @Test
    fun `each forked path records its own trail`() {
        val result = ForkingAa().recognize(listOf(CharSymbol('a')))

        assertEquals(2, result.acceptedPaths.size)
        val trails = result.acceptedPaths.map { path -> path.trail.single().target }.toSet()
        assertEquals(setOf(T1, T2), trails)
    }

    @Test
    fun `a forked path inherits the trail of its parent`() {
        // The parent forks before traversing anything, so the children's
        // trails hold exactly the edge each of them was created for.
        val result = ForkingAa().recognize(listOf(CharSymbol('a')))

        result.acceptedPaths.forEach { path ->
            assertEquals(1, path.trail.size)
        }
    }

    /** An automaton that calls a submachine and comes back. */
    private class CallingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = CallingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(T0, T1),
                    initialState = T0,
                    acceptingStates = setOf(T1),
                ),
            )
            submachines.add(
                Submachine(
                    name = "S",
                    states = setOf(T2, T3),
                    initialState = T2,
                    acceptingStates = setOf(T3),
                ),
            )
            call(T0, "S", T1)
            consume(T2, CharSymbol('b'), T3)
        }
    }

    @Test
    fun `the synthesised return edge is part of the trail`() {
        val result = CallingAa().recognize(listOf(CharSymbol('b')))

        val path = result.acceptedPaths.single()
        // The call, the symbol edge and the return: three traversals.
        assertEquals(3, path.trail.size)
        assertTrue(path.trail.any { edge -> edge.transition is Transition.Call })
        assertTrue(path.trail.any { edge -> edge.transition is Transition.Return })
        assertEquals(T1, path.state)
    }
}
