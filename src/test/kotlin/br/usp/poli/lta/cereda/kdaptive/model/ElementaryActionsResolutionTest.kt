// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.actions.actionRef
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression test for the overload ambiguities of [ElementaryActions].
 *
 * The API used to offer two `query` overloads of the same arity (one per
 * transition family) and two `remove` overloads of the same arity with the
 * same parameter names in a different order. Calls such as
 * `query(null, null, null)` and `remove(priorAction = ..., source = ...,
 * symbol = ..., target = ...)` did not compile at all.
 *
 * The fix gives each family at most one overload per arity, with the
 * prior-action filter first and the post-action filter last. **This file
 * compiling is the actual regression test**: every call shape below used to be
 * ambiguous, and none of them is any more.
 */
class ElementaryActionsResolutionTest {

    private object R0 : State() {
        override fun toString(): String = "r0"
    }

    private object R1 : State() {
        override fun toString(): String = "r1"
    }

    /** A mapping with one symbol edge and one call edge. */
    private fun mapping(): TransitionMapping {
        val m = TransitionMapping()
        m.add(
            TransitionEdge(
                source = R0,
                target = R1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                prior = actionRef("A"),
                post = actionRef("B"),
            ),
        )
        m.add(
            TransitionEdge(
                source = R0,
                target = R1,
                transition = Transition.Call(submachine = "M"),
                prior = actionRef("A"),
                post = actionRef("B"),
            ),
        )
        return m
    }

    @Test
    fun `every query arity resolves positionally`() {
        val ea = ElementaryActions(mapping())

        // Three nulls used to be ambiguous between the symbol and call families.
        assertEquals(2, ea.query(null, null, null).size)
        // Four nulls used to be ambiguous between two call-family shapes.
        assertEquals(2, ea.query(null, null, null, null).size)
        // Five nulls used to be ambiguous between two call-family shapes.
        assertEquals(2, ea.query(null, null, null, null, null).size)

        // The call family has the same shape, under its own name.
        assertEquals(1, ea.queryCall(null, null, null).size)
        assertEquals(1, ea.queryCall(null, null, null, null).size)
        assertEquals(1, ea.queryCall(null, null, null, null, null).size)
    }

    @Test
    fun `every query arity resolves by named argument`() {
        val ea = ElementaryActions(mapping())

        assertEquals(2, ea.query(source = null, symbol = null, target = null).size)
        assertEquals(
            2,
            ea.query(priorAction = null, source = null, symbol = null, target = null).size,
        )
        assertEquals(
            2,
            ea.query(priorAction = null, source = null, symbol = null, target = null, postAction = null).size,
        )

        assertEquals(
            1,
            ea.queryCall(source = null, submachine = null, target = null).size,
        )
        assertEquals(
            1,
            ea.queryCall(priorAction = null, source = null, submachine = null, target = null).size,
        )
        assertEquals(
            1,
            ea.queryCall(
                priorAction = null,
                source = null,
                submachine = null,
                target = null,
                postAction = null,
            ).size,
        )
    }

    @Test
    fun `every remove arity resolves positionally and by named argument`() {
        // The two four-argument removals used to declare the same parameter
        // names in a different order, so even a fully named call was ambiguous.
        val positional = mapping()
        ElementaryActions(positional).remove(null, null, null)
        assertEquals(0, positional.size())

        val prior = mapping()
        ElementaryActions(prior).remove(null, null, null, null)
        assertEquals(0, prior.size())

        val both = mapping()
        ElementaryActions(both).remove(null, null, null, null, null)
        assertEquals(0, both.size())

        val named = mapping()
        ElementaryActions(named).remove(priorAction = null, source = null, symbol = null, target = null)
        assertEquals(0, named.size())

        val namedBoth = mapping()
        ElementaryActions(namedBoth).remove(
            priorAction = null,
            source = null,
            symbol = null,
            target = null,
            postAction = null,
        )
        assertEquals(0, namedBoth.size())
    }

    @Test
    fun `the builder blocks resolve too`() {
        val m = mapping()
        val ea = ElementaryActions(m)

        assertEquals(2, ea.query { }.size)
        assertEquals(1, ea.queryCall { }.size)
        ea.remove { }
        assertEquals(0, m.size())
    }

    @Test
    fun `the action filters still discriminate`() {
        val ea = ElementaryActions(mapping())

        // Prior "A" and post "B" are carried by both edges.
        assertEquals(2, ea.query(actionRef("A"), null, null, null).size)
        assertEquals(2, ea.query(null, null, null, null, actionRef("B")).size)
        assertEquals(2, ea.query(actionRef("A"), null, null, null, actionRef("B")).size)

        // A reference that matches nothing narrows the result down.
        assertTrue(ea.query(actionRef("nope"), null, null, null).isEmpty())
        assertTrue(ea.queryCall(actionRef("nope"), null, null, null).isEmpty())
    }
}
