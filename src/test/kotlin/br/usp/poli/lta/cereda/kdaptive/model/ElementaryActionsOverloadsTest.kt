// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.actions.actionRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private object P0 : State() {
    override fun toString(): String = "p0"
}

private object P1 : State() {
    override fun toString(): String = "p1"
}

private object P2 : State() {
    override fun toString(): String = "p2"
}

/**
 * Coverage tests for the writing operations of [ElementaryActions]: the
 * addition of symbol, epsilon and submachine-call transitions, and the
 * difference between a named action filter (arguments unconstrained) and a
 * bound one (exactly these arguments).
 */
class ElementaryActionsOverloadsTest {
    private fun sym(c: Char) = CharSymbol(c)

    @Test
    fun `add installs an edge carrying the prior and the post action`() {
        val mapping = TransitionMapping()
        val ea = ElementaryActions(mapping, Submachines())

        ea.add(P0, sym('a'), P1, actionRef("A", "x"), actionRef("B", 1))

        val edge = mapping.transitions.single()
        assertEquals(ActionRef.Bound("A", listOf("x")), edge.prior)
        assertEquals(ActionRef.Bound("B", listOf(1)), edge.post)
    }

    @Test
    fun `add installs an epsilon transition`() {
        val mapping = TransitionMapping()
        val ea = ElementaryActions(mapping, Submachines())

        ea.add(P0, Symbol.Epsilon, P1)

        val edge = mapping.transitions.single()
        assertTrue(edge.transition is Transition.Epsilon)
        assertEquals(ActionRef.None, edge.prior)
        assertEquals(ActionRef.None, edge.post)
    }

    @Test
    fun `addCall installs a submachine call`() {
        val mapping = TransitionMapping()
        val ea = ElementaryActions(mapping, Submachines())

        ea.addCall(P0, SubmachineRef.Named("M"), P1, actionRef("A"), actionRef("B"))

        val edge = mapping.transitions.single()
        assertEquals(Transition.Call(submachine = "M"), edge.transition)
        assertEquals(ActionRef.Bound("A"), edge.prior)
        assertEquals(ActionRef.Bound("B"), edge.post)
    }

    @Test
    fun `add refuses the wildcards`() {
        val ea = ElementaryActions(TransitionMapping(), Submachines())

        assertThrows<IllegalArgumentException> { ea.add(State.Any, sym('a'), P1) }
        assertThrows<IllegalArgumentException> { ea.add(P0, Symbol.Any, P1) }
        assertThrows<IllegalArgumentException> { ea.add(P0, sym('a'), State.Any) }
        assertThrows<IllegalArgumentException> { ea.addCall(State.Any, SubmachineRef.Named("M"), P1) }
        assertThrows<IllegalArgumentException> { ea.addCall(P0, SubmachineRef.Named("M"), State.Any) }
    }

    @Test
    fun `a named filter matches any arity by name`() {
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(
                source = P0,
                target = P1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = actionRef("A", "x"),
            )
        val t2 =
            TransitionEdge(
                source = P0,
                target = P2,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = actionRef("A"),
            )
        val t3 =
            TransitionEdge(
                source = P1,
                target = P0,
                transition = Transition.SymbolTransition(symbol = sym('b')),
                prior = actionRef("B", "x"),
            )
        mapping.add(t1)
        mapping.add(t2)
        mapping.add(t3)
        val ea = ElementaryActions(mapping, Submachines())

        // A named filter checks the name only.
        assertEquals(2, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Named("A")).size)
        // A bound filter checks the arguments, and therefore the arity.
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Bound("A")).size)
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, actionRef("A", "x")).size)

        ea.remove(State.Any, Symbol.Any, State.Any, ActionRef.Named("A"))
        assertFalse(mapping.contains(t1.id))
        assertFalse(mapping.contains(t2.id))
        assertTrue(mapping.contains(t3.id))
    }

    @Test
    fun `a free argument marker leaves one position open`() {
        val mapping = TransitionMapping()
        mapping.add(
            TransitionEdge(
                source = P0,
                target = P1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = actionRef("A", "x", 1),
            ),
        )
        mapping.add(
            TransitionEdge(
                source = P0,
                target = P2,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = actionRef("A", "y", 2),
            ),
        )
        val ea = ElementaryActions(mapping, Submachines())

        // The marker fixes the arity, but leaves the position unconstrained.
        val loose = ActionRef.Bound("A", listOf(ActionRef.Free, 1))
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, loose).size)

        // A fully concrete binding matches exactly one edge.
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, actionRef("A", "x", 1)).size)
    }
}
