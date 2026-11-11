// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.actions.actionRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises the elementary operations in the relevant filter combinations (all
 * wildcards, all concrete, and mixed) so that each per-parameter
 * wildcard/filter branch is taken by each operation.
 *
 * The two families never overlap: the symbol family selects the
 * symbol-consuming edges, the call family the submachine-call edges, and
 * epsilon is named explicitly by [Symbol.Epsilon].
 */
class ElementaryActionsMatrixTest {
    private object S0 : State() {
        override fun toString(): String = "s0"
    }

    private object S1 : State() {
        override fun toString(): String = "s1"
    }

    private object S2 : State() {
        override fun toString(): String = "s2"
    }

    private object X : Symbol() {
        override fun toString(): String = "x"
    }

    private object Y : Symbol() {
        override fun toString(): String = "y"
    }

    /**
     * Builds: s0 -x-> s1 (prior PA, post PB{s1}); s0 eps-> s2;
     * s1 call M -> s2 (prior PA3, post PB); s2 -y-> s0 (prior PA2{s1,"str"}).
     */
    private fun mapping(): TransitionMapping {
        val m = TransitionMapping()
        m.add(
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.SymbolTransition(symbol = X),
                prior = actionRef("PA"),
                post = actionRef("PB", S1),
            ),
        )
        m.add(
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.Epsilon,
            ),
        )
        m.add(
            TransitionEdge(
                source = S1,
                target = S2,
                transition = Transition.Call(submachine = "M"),
                prior = actionRef("PA3"),
                post = actionRef("PB"),
            ),
        )
        m.add(
            TransitionEdge(
                source = S2,
                target = S0,
                transition = Transition.SymbolTransition(symbol = Y),
                prior = actionRef("PA2", S1, "str"),
            ),
        )
        return m
    }

    @Test
    fun `symbol query overload matrix`() {
        val ea = ElementaryActions(mapping(), Submachines())

        // All wildcards: the two symbol-consuming edges, and nothing else.
        assertEquals(2, ea.query(source = State.Any, symbol = Symbol.Any, target = State.Any).size)
        assertEquals(0, ea.query(source = State.Any, symbol = Symbol.Any, target = S2).size)
        assertEquals(1, ea.query(source = State.Any, symbol = Symbol.Any, target = S1).size)
        assertEquals(1, ea.query(source = S0, symbol = Symbol.Any, target = State.Any).size)
        assertEquals(0, ea.query(source = S1, symbol = Symbol.Any, target = State.Any).size)
        assertEquals(1, ea.query(source = S2, symbol = Symbol.Any, target = State.Any).size)
    }

    @Test
    fun `the symbol family never selects an epsilon or a call edge`() {
        val ea = ElementaryActions(mapping(), Submachines())

        assertEquals(1, ea.query(source = State.Any, symbol = Y, target = State.Any).size)
        assertEquals(1, ea.query(source = S2, symbol = Y, target = S0).size)
        assertEquals(1, ea.query(source = State.Any, symbol = X, target = S1).size)

        // A concrete symbol that no edge consumes matches nothing: the epsilon
        // edge is not a wildcard match any more.
        val missing = CharSymbol('!')
        assertEquals(0, ea.query(source = State.Any, symbol = missing, target = State.Any).size)
        assertEquals(0, ea.query(source = S1, symbol = missing, target = State.Any).size)
        assertEquals(0, ea.query(source = State.Any, symbol = missing, target = S1).size)

        // Epsilon is named explicitly.
        assertEquals(1, ea.query(source = State.Any, symbol = Symbol.Epsilon, target = State.Any).size)
        assertEquals(1, ea.query(source = S0, symbol = Symbol.Epsilon, target = S2).size)
        assertEquals(0, ea.query(source = S1, symbol = Symbol.Epsilon, target = State.Any).size)
    }

    @Test
    fun `call query overload matrix`() {
        val ea = ElementaryActions(mapping(), Submachines())

        // With a null submachine filter the family is still call-kind-limited.
        assertEquals(1, ea.queryCall(source = State.Any, submachine = SubmachineRef.Any, target = State.Any).size)
        assertEquals(1, ea.queryCall(source = State.Any, submachine = SubmachineRef.Any, target = S2).size)
        assertEquals(1, ea.queryCall(S1, SubmachineRef.Named("M"), State.Any).size)
        assertEquals(0, ea.queryCall(S1, SubmachineRef.Named("N"), State.Any).size)
        assertEquals(0, ea.queryCall(S0, SubmachineRef.Named("M"), State.Any).size)
    }

    @Test
    fun `symbol query with prior and post filters`() {
        val ea = ElementaryActions(mapping(), Submachines())

        // A named filter matches the action whatever its arguments.
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Named("PA")).size)
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Named("PA2")).size)
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Free, ActionRef.Named("PB")).size)

        // A bound filter fixes the arguments, and therefore the arity.
        assertEquals(1, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Free, actionRef("PB", S1)).size)
        assertEquals(0, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Free, actionRef("PB")).size)
        assertEquals(0, ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Free, actionRef("PB", S2)).size)

        // Prior AND post must both match.
        assertEquals(
            1,
            ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Named("PA"), ActionRef.Named("PB")).size,
        )
        assertEquals(
            0,
            ea.query(State.Any, Symbol.Any, State.Any, ActionRef.Named("PA2"), ActionRef.Named("PB")).size,
        )

        // The transition filters keep working alongside the action filters.
        assertEquals(1, ea.query(S0, X, S1, ActionRef.Named("PA")).size)
        assertEquals(0, ea.query(S1, X, S1, ActionRef.Named("PA")).size)
    }

    @Test
    fun `prior and post together overload matrix`() {
        val ea = ElementaryActions(mapping(), Submachines())

        // The call family only sees the call edge: priors attached to symbol
        // edges (PA, PA2) are never returned by these queries.
        assertTrue(ea.queryCall(State.Any, SubmachineRef.Any, State.Any, ActionRef.Named("PA")).isEmpty())
        assertTrue(ea.queryCall(State.Any, SubmachineRef.Any, State.Any, ActionRef.Named("PA2")).isEmpty())
        assertTrue(
            ea.queryCall(
                State.Any,
                SubmachineRef.Named("M"),
                State.Any,
                ActionRef.Free,
                ActionRef.Named("PB"),
            ).isNotEmpty(),
        )

        // Zero-argument prior reference: name-only match on the call edge.
        assertTrue(ea.queryCall(State.Any, SubmachineRef.Any, State.Any, ActionRef.Named("PA3")).isNotEmpty())
        assertEquals(1, ea.queryCall(State.Any, SubmachineRef.Any, State.Any, actionRef("PA3")).size)

        // Argument list shape must match the reference's arity.
        assertTrue(
            ea.queryCall(
                prior = actionRef("PA3", S1),
                source = State.Any,
                submachine = SubmachineRef.Any,
                target = State.Any,
            ).isEmpty(),
        )
        assertTrue(ea.queryCall(State.Any, SubmachineRef.Any, State.Any, actionRef("PA3", S1, S1)).isEmpty())
    }

    @Test
    fun `remove overload matrix`() {
        val m1 = mapping()
        ElementaryActions(m1, Submachines()).remove(source = S0, symbol = X, target = State.Any)
        // Only the x edge matches: the epsilon edge is not a wildcard match.
        assertEquals(3, m1.size)

        val m2 = mapping()
        ElementaryActions(m2, Submachines()).remove(source = S2, symbol = Y, target = S0)
        assertEquals(0, Topology.of(m2, Submachines()).edgesConsuming(Y).size)

        val m3 = mapping()
        ElementaryActions(m3, Submachines()).remove(source = State.Any, symbol = Symbol.Any, target = State.Any)
        // The epsilon and the call edge survive a symbol-family removal.
        assertEquals(2, m3.size)

        val m4 = mapping()
        ElementaryActions(m4, Submachines()).remove(State.Any, Y, State.Any, ActionRef.Named("PA"))
        // No edge carries both prior PA and symbol Y (the Y edge has prior PA2).
        assertEquals(4, m4.size)
        assertEquals(1, Topology.of(m4, Submachines()).edgesConsuming(Y).size)

        val m5 = mapping()
        ElementaryActions(m5, Submachines()).remove(State.Any, Symbol.Any, State.Any, actionRef("PA2", S1, "str"))
        assertEquals(0, Topology.of(m5, Submachines()).edgesConsuming(Y).size)
    }

    @Test
    fun `remove with prior and post filters`() {
        val m1 = mapping()
        // A post filter alone removes the symbol edge carrying a post named "PB".
        ElementaryActions(m1, Submachines())
            .remove(State.Any, Symbol.Any, State.Any, ActionRef.Free, ActionRef.Named("PB"))
        assertEquals(3, m1.size)

        val m2 = mapping()
        // Prior AND post must both match: only the s0 -x-> s1 edge carries both.
        ElementaryActions(m2, Submachines()).remove(
            State.Any,
            Symbol.Any,
            State.Any,
            ActionRef.Named("PA"),
            ActionRef.Named("PB"),
        )
        assertEquals(3, m2.size)

        val m3 = mapping()
        // The transition filters keep working alongside the action filters.
        ElementaryActions(m3, Submachines()).remove(S2, Y, S0, actionRef("PA2", S1, "str"), ActionRef.Free)
        assertEquals(3, m3.size)
        assertEquals(0, Topology.of(m3, Submachines()).edgesConsuming(Y).size)

        val m4 = mapping()
        // The builder routes a post-only filter to the five-part removal.
        ElementaryActions(m4, Submachines()).remove { post = ActionRef.Named("PB") }
        assertEquals(3, m4.size)

        val m5 = mapping()
        // The builder routes a prior-and-post filter to the five-part removal.
        ElementaryActions(m5, Submachines()).remove {
            prior = ActionRef.Named("PA")
            post = ActionRef.Named("PB")
        }
        assertEquals(3, m5.size)
    }

    @Test
    fun `removeCall overload matrix`() {
        val m1 = mapping()
        ElementaryActions(m1, Submachines())
            .removeCall(source = State.Any, submachine = SubmachineRef.Any, target = State.Any)
        // Only the call edge leaves the mapping.
        assertEquals(3, m1.size)

        val m2 = mapping()
        ElementaryActions(m2, Submachines())
            .removeCall(source = S1, submachine = SubmachineRef.Named("M"), target = State.Any)
        assertEquals(3, m2.size)

        val m3 = mapping()
        ElementaryActions(m3, Submachines()).removeCall { submachine = SubmachineRef.Named("N") }
        // No edge calls N: nothing is removed.
        assertEquals(4, m3.size)
    }

    @Test
    fun `builder blocks cover both families and both action filters`() {
        val ea = ElementaryActions(mapping(), Submachines())

        // Symbol family, no action filter.
        assertEquals(1, ea.query { symbol = Y }.size)
        // Symbol family, prior filter only.
        assertEquals(1, ea.query { prior = ActionRef.Named("PA") }.size)
        // Symbol family, post filter only.
        assertEquals(1, ea.query { post = ActionRef.Named("PB") }.size)
        // Symbol family, both action filters.
        assertEquals(
            1,
            ea.query {
                prior = ActionRef.Named("PA")
                post = ActionRef.Named("PB")
            }.size,
        )

        // Call family, no action filter.
        assertEquals(1, ea.queryCall { submachine = SubmachineRef.Named("M") }.size)
        // Call family, prior filter only.
        assertEquals(1, ea.queryCall { prior = ActionRef.Named("PA3") }.size)
        // Call family, post filter only.
        assertEquals(1, ea.queryCall { post = ActionRef.Named("PB") }.size)
        // Call family, both action filters.
        assertEquals(
            1,
            ea.queryCall {
                prior = ActionRef.Named("PA3")
                post = ActionRef.Named("PB")
            }.size,
        )
    }
}
