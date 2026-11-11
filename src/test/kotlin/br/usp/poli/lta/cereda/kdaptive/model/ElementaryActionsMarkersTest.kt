// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.SubmachineRef
import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.actions.actionRef
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private object T0 : State() {
    override fun toString(): String = "t0"
}

private object T1 : State() {
    override fun toString(): String = "t1"
}

/**
 * The marker doctrine of the elementary actions: a wildcard is a value in its
 * own right, never a null.
 *
 * The two state/symbol markers say *what kind of transition* is selected, and
 * the five action markers say *what the action slot must look like*.
 */
class ElementaryActionsMarkersTest {

    /** A mapping with one symbol edge, one epsilon edge and one call edge. */
    private fun mapping(): TransitionMapping {
        val m = TransitionMapping()
        m.add(
            TransitionEdge(
                source = T0,
                target = T1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                prior = actionRef("A", 1),
            ),
        )
        m.add(
            TransitionEdge(
                source = T0,
                target = T1,
                transition = Transition.Epsilon,
            ),
        )
        m.add(
            TransitionEdge(
                source = T0,
                target = T1,
                transition = Transition.Call(submachine = "M"),
            ),
        )
        return m
    }

    @Test
    fun `the state wildcard matches any state`() {
        val ea = ElementaryActions(mapping())

        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any).size)
        assertEquals(1, ea.query(T0, Symbol.Any, State.Any).size)
        assertEquals(0, ea.query(T1, Symbol.Any, State.Any).size)
    }

    @Test
    fun `the symbol wildcard selects the symbol-consuming edges only`() {
        val ea = ElementaryActions(mapping())

        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any).size)
        assertEquals(1, ea.query(State.Any, Symbol.Epsilon, State.Any).size)
        assertEquals(1, ea.query(State.Any, CharSymbol('a'), State.Any).size)
    }

    @Test
    fun `the free action marker leaves the slot unconstrained`() {
        val ea = ElementaryActions(mapping())

        // The symbol edge carries a prior, the other edges do not: the free
        // marker selects them all.
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Free).size)
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Free, ActionRef.Free).size)
    }

    @Test
    fun `the any action marker requires an action`() {
        val ea = ElementaryActions(mapping())

        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Any).size)
        assertEquals(0, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.None).size)
        assertEquals(0, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Free, ActionRef.Any).size)
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Free, ActionRef.None).size)
    }

    @Test
    fun `the none action marker requires the absence of an action`() {
        val ea = ElementaryActions(mapping())

        // The epsilon edge of the call family carries no action at all.
        assertEquals(1, ea.queryCall(State.Any, SubmachineRef.Any, State.Any, ActionRef.None).size)
        assertEquals(0, ea.queryCall(State.Any, SubmachineRef.Any, State.Any, ActionRef.Any).size)
    }

    @Test
    fun `the named action marker ignores the arguments`() {
        val ea = ElementaryActions(mapping())

        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Named("A")).size)
        assertEquals(0, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Named("B")).size)
    }

    @Test
    fun `the bound action marker fixes the arguments`() {
        val ea = ElementaryActions(mapping())

        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, actionRef("A", 1)).size)
        assertEquals(0, ea.query(State.Any, Symbol.Any, State.Any, actionRef("A", 2)).size)
        assertEquals(0, ea.query(State.Any, Symbol.Any, State.Any, actionRef("A")).size)
        assertTrue(ea.query(State.Any, Symbol.Any, State.Any, actionRef("A", 1, 2)).isEmpty())
    }

    @Test
    fun `the markers are values, not nulls`() {
        // The markers are singletons: two references to a wildcard are the
        // same value, and they are never null.
        assertTrue(State.Any === State.Any)
        assertTrue(Symbol.Any === Symbol.Any)
        assertTrue(Symbol.Epsilon === Symbol.Epsilon)
        assertTrue(ActionRef.Free === ActionRef.Free)
        assertTrue(ActionRef.Any === ActionRef.Any)
        assertTrue(ActionRef.None === ActionRef.None)
    }
}
