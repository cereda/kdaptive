// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.predicates.hasPostAction
import br.usp.poli.lta.cereda.kdaptive.model.predicates.hasPriorAction
import br.usp.poli.lta.cereda.kdaptive.model.predicates.isEpsilon
import br.usp.poli.lta.cereda.kdaptive.model.predicates.isOrdinary
import br.usp.poli.lta.cereda.kdaptive.model.predicates.isSubmachineReturn
import br.usp.poli.lta.cereda.kdaptive.model.predicates.isSymbolConsumption
import br.usp.poli.lta.cereda.kdaptive.model.sets.ActionsSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.SubmachinesSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the remaining [TransitionMapping] single-axis and combined queries,
 * the set accessors of [ActionsSet] and [SubmachinesSet], the [Stack] clear and
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

    @Test
    fun `single-axis queries`() {
        val m = mapping()

        assertTrue(m.withTargetState(Q0).isNotEmpty())
        assertTrue(m.withTargetState(Q1).isNotEmpty())
        assertTrue(m.withTargetState(Q2).isNotEmpty())
        assertTrue(m.withSubmachine("M").isNotEmpty())
        assertTrue(m.withSubmachine("nope").isEmpty())
        assertTrue(m.withEpsilonTransitions().isNotEmpty())
        assertTrue(m.withEpsilonFromSourceState(Q1).isNotEmpty())
        assertTrue(m.withEpsilonFromSourceState(Q0).isEmpty())
    }

    @Test
    fun `combined-criteria queries`() {
        val m = mapping()
        val a = CharSymbol('a')

        assertTrue(m.withSourceStateAndSymbol(Q0, a).isNotEmpty())
        assertTrue(m.withSourceStateAndSymbol(Q2, a).isEmpty())

        assertTrue(m.withSourceAndTargetStates(Q1, Q0).isNotEmpty())
        assertTrue(m.withSourceAndTargetStates(Q0, Q0).isEmpty())

        assertTrue(m.withSourceStateAndSubmachine(Q1, "M").isNotEmpty())
        assertTrue(m.withSourceStateAndSubmachine(Q0, "M").isEmpty())

        assertTrue(m.withSymbolAndTargetState(a, Q1).isNotEmpty())
        assertTrue(m.withSymbolAndTargetState(a, Q0).isEmpty())
    }

    @Test
    fun `prior and post action name queries`() {
        val m = mapping()

        assertEquals(2, m.withPriorAction("PA").size)
        assertTrue(m.withPriorAction("nope").isEmpty())

        assertEquals(1, m.withPostAction("PB").size)
        assertTrue(m.withPostAction("nope").isEmpty())
    }

    @Test
    fun `actions set accessors`() {
        val set = ActionsSet()
        set.add(Action(name = "A") { _, _, _ -> })
        set.add(Action(name = "B") { _, _, _ -> })

        assertEquals(2, set.getActions().size)
        assertEquals(2, set.copy().size())
        set.setActions(emptySet())
        assertEquals(0, set.size())
    }

    @Test
    fun `submachines set accessors`() {
        val set = SubmachinesSet()
        val sm =
            Submachine(
                name = "M",
                states = setOf(Q0, Q1),
                initialState = Q0,
                acceptingStates = setOf(Q1),
            )
        set.add(sm)

        assertEquals(1, set.getSubmachines().size)
        assertEquals(1, set.copy().size())
        set.setSubmachines(emptySet())
        assertEquals(0, set.size())
    }

    @Test
    fun `stack clear and rendering`() {
        val stack = Stack()
        stack.clear()
        assertTrue(stack.isEmpty())
        assertEquals("Stack: {}", stack.toString())

        stack.push(Q0)
        stack.push(Q1)
        assertEquals(listOf(Q1, Q0), stack.elementsList())
        assertEquals("Stack: { q1, q0, top=q1 }", stack.toString())
        assertEquals(Q1, stack.top())

        stack.clear()
        assertTrue(stack.isEmpty())
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
