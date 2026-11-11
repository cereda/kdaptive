// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private object EA0 : State() {
    override fun toString(): String = "e0"
}

private object EA1 : State() {
    override fun toString(): String = "e1"
}

/**
 * The action slots of an edge: they always hold a value, and the only values
 * an edge accepts are a bound action and the empty slot.
 */
class EdgeActionSlotTest {

    private fun edge(prior: ActionRef = ActionRef.None, post: ActionRef = ActionRef.None) = TransitionEdge(
        source = EA0,
        target = EA1,
        transition = Transition.Epsilon,
        prior = prior,
        post = post,
    )

    @Test
    fun `an edge without actions carries the empty slot`() {
        val e = edge()

        assertEquals(ActionRef.None, e.prior)
        assertEquals(ActionRef.None, e.post)
        assertFalse(e.hasPriorAction("A"))
        assertFalse(e.hasPostAction("A"))
    }

    @Test
    fun `an edge rejects the filter-only markers`() {
        assertThrows<IllegalArgumentException> { edge(prior = ActionRef.Free) }
        assertThrows<IllegalArgumentException> { edge(prior = ActionRef.Any) }
        assertThrows<IllegalArgumentException> { edge(prior = ActionRef.Named("A")) }
        assertThrows<IllegalArgumentException> { edge(post = ActionRef.Free) }
        assertThrows<IllegalArgumentException> { edge(post = ActionRef.Any) }
        assertThrows<IllegalArgumentException> { edge(post = ActionRef.Named("A")) }
    }

    @Test
    fun `an edge accepts a bound action`() {
        val e = edge(prior = actionRef("A", 1), post = actionRef("B"))

        assertEquals(ActionRef.Bound("A", listOf(1)), e.prior)
        assertEquals(ActionRef.Bound("B"), e.post)
        assertTrue(e.hasPriorAction("A"))
        assertTrue(e.hasPostAction("B"))
        assertFalse(e.hasPriorAction("B"))
    }

    @Test
    fun `two edges with the same empty slot are the same edge`() {
        assertEquals(edge(), edge())
        assertEquals(edge().hashCode(), edge().hashCode())
    }

    @Test
    fun `two edges with different actions are different edges`() {
        assertNotEquals(edge(), edge(prior = actionRef("A")))
        assertNotEquals(edge(prior = actionRef("A")), edge(post = actionRef("A")))
    }

    @Test
    fun `the action filters read the slots as values`() {
        val m = TransitionMapping()
        m.add(edge(prior = actionRef("A")))
        m.add(edge(post = actionRef("B")))
        val ea = ElementaryActions(m, Submachines())

        // The empty slot is the "no action" value, not a missing one.
        assertEquals(1, ea.query(symbol = Symbol.Epsilon, prior = ActionRef.None).size)
        assertEquals(1, ea.query(symbol = Symbol.Epsilon, post = ActionRef.None).size)
        assertEquals(1, ea.query(symbol = Symbol.Epsilon, prior = ActionRef.Any).size)
        assertEquals(1, ea.query(symbol = Symbol.Epsilon, post = ActionRef.Any).size)
        assertEquals(1, ea.query(symbol = Symbol.Epsilon, prior = ActionRef.Named("A")).size)
        assertEquals(1, ea.query(symbol = Symbol.Epsilon, post = ActionRef.Named("B")).size)
    }
}
