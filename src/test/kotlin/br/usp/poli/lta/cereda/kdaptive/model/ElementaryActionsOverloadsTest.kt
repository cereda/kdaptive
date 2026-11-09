// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.actions.actionRef
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
 * Coverage tests for [ElementaryActions]' remaining overloads: the five-part
 * submachine query and zero-argument action references (the wildcard-arity
 * branch of the argument matcher).
 */
class ElementaryActionsOverloadsTest {
    private fun sym(c: Char) = CharSymbol(c)

    @Test
    fun `query with prior and post actions together`() {
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(
                source = P0,
                target = P1,
                transition = Transition.Call(submachine = "M"),
                prior = actionRef("A"),
                post = actionRef("B"),
            )
        val t2 =
            TransitionEdge(
                source = P0,
                target = P2,
                transition = Transition.Call(submachine = "M"),
                prior = actionRef("A"),
            )
        val t3 =
            TransitionEdge(
                source = P0,
                target = P2,
                transition = Transition.Call(submachine = "M"),
                post = actionRef("B"),
            )
        val t4 =
            TransitionEdge(
                source = P0,
                target = P2,
                transition = Transition.Call(submachine = "N"),
            )
        mapping.add(t1)
        mapping.add(t2)
        mapping.add(t3)
        mapping.add(t4)

        val ea = ElementaryActions(mapping)

        // Prior AND post must both match (only t1 carries both references).
        val both = ea.query(actionRef("A"), null, "M", null, actionRef("B"))
        assertEquals(setOf(P1), both.map { it.target }.toSet())

        // Prior-only filter, wildcard source.
        val priorOnly = ea.query(actionRef("A"), null, "M", null)
        assertEquals(setOf(P1, P2), priorOnly.map { it.target }.toSet())

        // Post-only filter, wildcard submachine name.
        val postOnly = ea.query(null, null, null, null, actionRef("B"))
        assertEquals(setOf(P1, P2), postOnly.map { it.target }.toSet())

        // Nothing matches a never-registered combination.
        val none = ea.query(actionRef("X"), null, "M", null, actionRef("Z"))
        assertTrue(none.isEmpty())
    }

    @Test
    fun `zero argument action reference matches any arity by name`() {
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
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = actionRef("A", "y"),
            )
        val t4 =
            TransitionEdge(
                source = P1,
                target = P0,
                transition = Transition.SymbolTransition(symbol = sym('b')),
                prior = actionRef("B", "x"),
            )
        mapping.add(t1)
        mapping.add(t2)
        mapping.add(t3)
        mapping.add(t4)

        val ea = ElementaryActions(mapping)

        // A zero-arg reference checks the name only, matching edges whose prior
        // action carries any number of arguments.
        ea.remove(null, null, null, actionRef("A"))
        assertFalse(mapping.hasIdentifier(t1.id))
        assertFalse(mapping.hasIdentifier(t2.id))
        assertFalse(mapping.hasIdentifier(t3.id))
        assertTrue(mapping.hasIdentifier(t4.id))
    }
}
