// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.sets.ActionsSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.SubmachinesSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private object E0 : State() {
    override fun toString(): String = "e0"
}

private object E1 : State() {
    override fun toString(): String = "e1"
}

/**
 * The equality contract of the model.
 *
 * Every identifier the library hands out — the edge id, the submachine id,
 * the action id — is *bookkeeping*, not identity: it is deliberately outside
 * the equality of the object it belongs to. What identifies an edge is its
 * content: its endpoints, its transition and its actions.
 *
 * The states and the symbols are compared by value, so a subclass must
 * define its equality (a data class does it for free).
 */
class ModelEqualityTest {

    @Test
    fun `two edges with the same content are the same edge`() {
        val first =
            TransitionEdge(
                source = E0,
                target = E1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                prior = ActionRef.Bound("A"),
            )
        val second =
            TransitionEdge(
                source = E0,
                target = E1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                prior = ActionRef.Bound("A"),
            )

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        // The identifiers differ: they are bookkeeping, not identity.
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `edges differing in one element are different edges`() {
        val base =
            TransitionEdge(
                source = E0,
                target = E1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            )

        assertNotEquals(base, base.copy(target = E0))
        assertNotEquals(base, base.copy(source = E1))
        assertNotEquals(
            base,
            base.copy(transition = Transition.SymbolTransition(symbol = CharSymbol('b'))),
        )
        assertNotEquals(base, base.copy(prior = ActionRef.Bound("A")))
        assertNotEquals(base, base.copy(post = ActionRef.Bound("A")))
    }

    @Test
    fun `adding an equal edge is a no-op`() {
        val mapping = TransitionMapping()
        val added = mutableListOf<TransitionEdge>()
        mapping.onEdgeAdded = { added.add(it) }

        val edge =
            TransitionEdge(
                source = E0,
                target = E1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            )
        mapping.add(edge)
        mapping.add(edge.copy())

        assertEquals(1, mapping.size())
        // The observer saw one insertion, not two.
        assertEquals(1, added.size)
    }

    @Test
    fun `a copied mapping holds equal edges with fresh identifiers`() {
        val mapping = TransitionMapping()
        val edge =
            TransitionEdge(
                source = E0,
                target = E1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            )
        mapping.add(edge)

        val copy = mapping.copy()

        assertEquals(mapping.size(), copy.size())
        val copied = copy.getTransitions().single()
        assertEquals(edge, copied)
        assertNotEquals(edge.id, copied.id)
    }

    @Test
    fun `two actions with the same name and handler are the same action`() {
        val handler: ActionHandler = { _, _, _ -> }
        val first = Action(name = "A", handler = handler)
        val second = Action(name = "A", handler = handler)

        assertEquals(first, second)
        assertNotEquals(first.id, second.id)
        assertNotEquals(first, Action(name = "B", handler = handler))
    }

    @Test
    fun `the set of actions holds one action per name`() {
        val set = ActionsSet()
        set.add(Action(name = "A") { _, _, _ -> })
        set.add(Action(name = "A") { _, _, _ -> error("replaced") })

        assertEquals(1, set.size())
        assertEquals("A", set.fromName("A").name)
    }

    @Test
    fun `two submachines with the same content are the same submachine`() {
        val first = Submachine(name = "M", states = setOf(E0, E1), initialState = E0, acceptingStates = setOf(E1))
        val second = Submachine(name = "M", states = setOf(E0, E1), initialState = E0, acceptingStates = setOf(E1))

        assertEquals(first, second)
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `the set of submachines holds one submachine per name`() {
        val set = SubmachinesSet()
        set.add(Submachine(name = "M", states = setOf(E0), initialState = E0, acceptingStates = setOf(E0)))
        set.add(Submachine(name = "M", states = setOf(E0), initialState = E0, acceptingStates = setOf(E0)))

        assertEquals(1, set.size())
        assertTrue(set.getFromName("M").states.contains(E0))
    }

    @Test
    fun `states and symbols are compared by value`() {
        // A data class defines value equality for free; the library relies on it.
        assertEquals(CharSymbol('a'), CharSymbol('a'))
        assertNotEquals(CharSymbol('a'), CharSymbol('b'))
        assertEquals(CharSymbol('a').hashCode(), CharSymbol('a').hashCode())
    }
}
