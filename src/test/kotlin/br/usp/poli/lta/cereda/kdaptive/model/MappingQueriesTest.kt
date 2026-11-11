// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the remaining [TransitionMapping] single-axis and combined queries,
 * the set accessors of [Actions] and [Submachines], the [Stack] clear and
 * multi-element rendering, and the non-dot predicate values.
 */
class MappingQueriesTest {
    private object Q0 : State() {
        override fun toString(): String = "q0"
    }

    private object Q1 : State() {
        override fun toString(): String = "q1"
    }

    private object Q2 : State() {
        override fun toString(): String = "q2"
    }

    private fun mapping(): TransitionMapping {
        val m = TransitionMapping()
        m.add(
            TransitionEdge(
                source = Q0,
                target = Q1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                prior = ActionRef.Bound(name = "PA"),
                post = ActionRef.Bound(name = "PB", args = listOf(1)),
            ),
        )
        m.add(
            TransitionEdge(source = Q1, target = Q2, transition = Transition.Epsilon),
        )
        m.add(
            TransitionEdge(source = Q1, target = Q0, transition = Transition.Call(submachine = "M")),
        )
        m.add(
            TransitionEdge(
                source = Q2,
                target = Q0,
                transition = Transition.SymbolTransition(symbol = CharSymbol('b')),
                prior = ActionRef.Bound(name = "PA"),
            ),
        )
        return m
    }

    /** The snapshot of the mapping the queries are asked of. */
    private fun topology(): Topology = Topology.of(mapping(), Submachines())

    @Test
    fun `single-axis queries`() {
        val t = topology()

        assertTrue(t.edgesTo(Q0).isNotEmpty())
        assertTrue(t.edgesTo(Q1).isNotEmpty())
        assertTrue(t.edgesTo(Q2).isNotEmpty())
        assertTrue(t.callsTo("M").isNotEmpty())
        assertTrue(t.callsTo("nope").isEmpty())
        assertTrue(t.epsilonEdges.isNotEmpty())
        assertTrue(t.epsilonEdgesFrom(Q1).isNotEmpty())
        assertTrue(t.epsilonEdgesFrom(Q0).isEmpty())
    }

    @Test
    fun `combined-criteria queries`() {
        val t = topology()
        val a = CharSymbol('a')

        assertTrue(t.matching(TransitionPattern(source = Q0, symbol = a)).isNotEmpty())
        assertTrue(t.matching(TransitionPattern(source = Q2, symbol = a)).isEmpty())

        assertTrue(t.matching(TransitionPattern(source = Q1, target = Q0, submachine = SubmachineRef.Any)).isNotEmpty())
        assertTrue(t.matching(TransitionPattern(source = Q0, target = Q0, submachine = SubmachineRef.Any)).isEmpty())

        assertTrue(
            t.matching(TransitionPattern(source = Q1, submachine = SubmachineRef.Named("M"))).isNotEmpty(),
        )
        assertTrue(
            t.matching(TransitionPattern(source = Q0, submachine = SubmachineRef.Named("M"))).isEmpty(),
        )

        assertTrue(t.matching(TransitionPattern(symbol = a, target = Q1)).isNotEmpty())
        assertTrue(t.matching(TransitionPattern(symbol = a, target = Q0)).isEmpty())
    }

    @Test
    fun `prior and post action name queries`() {
        val t = topology()

        assertEquals(2, t.edgesWithPrior("PA").size)
        assertTrue(t.edgesWithPrior("nope").isEmpty())

        assertEquals(1, t.edgesWithPost("PB").size)
        assertTrue(t.edgesWithPost("nope").isEmpty())
    }

    @Test
    fun `actions collection`() {
        val actions = Actions()
        actions.add(Action(name = "A") { _, _, _ -> })
        actions.add(Action(name = "B") { _, _, _ -> })

        assertEquals(2, actions.size)
        assertEquals(2, actions.count())
        assertEquals(2, actions.copy().size)
        actions.replaceAll(emptySet())
        assertEquals(0, actions.size)
    }

    @Test
    fun `submachines collection`() {
        val submachines = Submachines()
        val sm =
            Submachine(
                name = "M",
                states = setOf(Q0, Q1),
                initialState = Q0,
                acceptingStates = setOf(Q1),
            )
        submachines.add(sm)

        assertEquals(1, submachines.size)
        assertEquals(1, submachines.count())
        assertEquals(1, submachines.copy().size)
        submachines.replaceAll(emptySet())
        assertEquals(0, submachines.size)
    }

    @Test
    fun `stack clear and rendering`() {
        val stack = Stack()
        stack.clear()
        assertTrue(stack.isEmpty)
        assertEquals("Stack: {}", stack.toString())

        stack.push(Q0)
        stack.push(Q1)
        assertEquals(listOf(Q1, Q0), stack.elements)
        assertEquals("Stack: { q1, q0, top=q1 }", stack.toString())
        assertEquals(Q1, stack.peek())

        stack.clear()
        assertTrue(stack.isEmpty)
        assertEquals("Stack: {}", stack.toString())
    }

    @Test
    fun `transition subtypes expose their properties`() {
        assertEquals("M", Transition.Call(submachine = "M").submachine)
        assertEquals(CharSymbol('a'), Transition.SymbolTransition(symbol = CharSymbol('a')).symbol)
    }

    @Test
    fun `predicate values behave`() {
        val edge =
            TransitionEdge(
                source = Q0,
                target = Q1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            )
        val callEdge = TransitionEdge(source = Q0, target = Q1, transition = Transition.Call(submachine = "M"))

        assertTrue(isSymbolConsumption(edge))
        assertFalse(isSymbolConsumption(callEdge))
        assertTrue(isOrdinary(edge))
        assertFalse(isOrdinary(callEdge))
        assertTrue(isSubmachineReturn(TransitionEdge(source = Q0, target = Q0, transition = Transition.Return)))
        assertFalse(isSubmachineReturn(edge))
        assertTrue(hasPriorAction(edge.copy(prior = ActionRef.Bound(name = "A"))))
        assertFalse(hasPriorAction(edge))
        assertTrue(hasPostAction(edge.copy(post = ActionRef.Bound(name = "B"))))
        assertTrue(isEpsilon(TransitionEdge(source = Q0, target = Q1, transition = Transition.Epsilon)))
        assertFalse(isEpsilon(callEdge))
    }
}
