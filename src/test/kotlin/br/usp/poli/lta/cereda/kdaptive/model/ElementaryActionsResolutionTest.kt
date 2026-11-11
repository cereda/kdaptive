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
 * The API now offers **one method per operation and family** — `query`,
 * `remove` and `add` for symbols, `queryCall`, `removeCall` and `addCall`
 * for submachine calls — and every filter is an explicit value, never a
 * null. **This file compiling is the actual regression test**: every call
 * shape below used to be ambiguous, and none of them is any more.
 */
class ElementaryActionsResolutionTest {

    private object R0 : State() {
        override fun toString(): String = "r0"
    }

    private object R1 : State() {
        override fun toString(): String = "r1"
    }

    /** A mapping with two symbol edges, one epsilon edge and one call edge. */
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
                transition = Transition.SymbolTransition(symbol = CharSymbol('b')),
            ),
        )
        m.add(
            TransitionEdge(
                source = R0,
                target = R1,
                transition = Transition.Epsilon,
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

        // The symbol family never sees the epsilon or the call edge.
        assertEquals(2, ea.query(State.Any, Symbol.Any, State.Any).size)
        assertEquals(2, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Free).size)
        assertEquals(2, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Free, ActionRef.Free).size)

        // The call family has the same shape, under its own name.
        assertEquals(1, ea.queryCall(State.Any, null, State.Any).size)
        assertEquals(1, ea.queryCall(State.Any, null, State.Any, ActionRef.Free).size)
        assertEquals(1, ea.queryCall(State.Any, null, State.Any, ActionRef.Free, ActionRef.Free).size)
    }

    @Test
    fun `every query arity resolves by named argument`() {
        val ea = ElementaryActions(mapping())

        assertEquals(2, ea.query(source = State.Any, symbol = Symbol.Any, target = State.Any).size)
        assertEquals(
            2,
            ea.query(prior = ActionRef.Free, source = State.Any, symbol = Symbol.Any, target = State.Any).size,
        )
        assertEquals(
            2,
            ea.query(
                post = ActionRef.Free,
                prior = ActionRef.Free,
                source = State.Any,
                symbol = Symbol.Any,
                target = State.Any,
            ).size,
        )

        assertEquals(
            1,
            ea.queryCall(source = State.Any, submachine = null, target = State.Any).size,
        )
        assertEquals(
            1,
            ea.queryCall(prior = ActionRef.Free, source = State.Any, submachine = null, target = State.Any).size,
        )
        assertEquals(
            1,
            ea.queryCall(
                post = ActionRef.Free,
                prior = ActionRef.Free,
                source = State.Any,
                submachine = null,
                target = State.Any,
            ).size,
        )
    }

    @Test
    fun `every remove arity resolves positionally and by named argument`() {
        val positional = mapping()
        ElementaryActions(positional).remove(State.Any, Symbol.Any, State.Any)
        assertEquals(2, positional.size())

        val prior = mapping()
        ElementaryActions(prior).remove(State.Any, Symbol.Any, State.Any, ActionRef.Free)
        assertEquals(2, prior.size())

        val both = mapping()
        ElementaryActions(both).remove(State.Any, Symbol.Any, State.Any, ActionRef.Free, ActionRef.Free)
        assertEquals(2, both.size())

        val named = mapping()
        ElementaryActions(named).remove(
            prior = ActionRef.Free,
            source = State.Any,
            symbol = Symbol.Any,
            target = State.Any,
        )
        assertEquals(2, named.size())

        val namedBoth = mapping()
        ElementaryActions(namedBoth).remove(
            post = ActionRef.Free,
            prior = ActionRef.Free,
            source = State.Any,
            symbol = Symbol.Any,
            target = State.Any,
        )
        assertEquals(2, namedBoth.size())
    }

    @Test
    fun `every removeCall arity resolves positionally and by named argument`() {
        val positional = mapping()
        ElementaryActions(positional).removeCall(State.Any, null, State.Any)
        assertEquals(3, positional.size())

        val named = mapping()
        ElementaryActions(named).removeCall(source = State.Any, submachine = null, target = State.Any)
        assertEquals(3, named.size())
    }

    @Test
    fun `the builder blocks resolve too`() {
        val m = mapping()
        val ea = ElementaryActions(m)

        assertEquals(2, ea.query { }.size)
        assertEquals(1, ea.queryCall { }.size)
        ea.remove { }
        // The symbol family leaves; the epsilon and the call edge stay.
        assertEquals(2, m.size())
    }

    @Test
    fun `the action filters still discriminate`() {
        val ea = ElementaryActions(mapping())

        // Prior "A" and post "B" are carried by the symbol edge and the call edge.
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, actionRef("A")).size)
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Free, actionRef("B")).size)
        assertEquals(
            1,
            ea.query(State.Any, Symbol.Any, State.Any, actionRef("A"), actionRef("B")).size,
        )

        // A reference that matches nothing narrows the result down.
        assertTrue(ea.query(State.Any, Symbol.Any, State.Any, actionRef("nope")).isEmpty())
        assertTrue(ea.queryCall(State.Any, null, State.Any, actionRef("nope")).isEmpty())
    }
}
