// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.actions.actionRef
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exercises every overload of [ElementaryActions] in the relevant filter
 * combinations (all wildcards, all concrete, and mixed) so that each
 * per-parameter wildcard/filter branch is taken in each overload.
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
        val ea = ElementaryActions(mapping())

        // All wildcards: everything (including epsilon and call edges) matches.
        assertEquals(4, ea.query(source = null, symbol = null, target = null).size)
        assertEquals(2, ea.query(source = null, symbol = null, target = S2).size)
        assertEquals(2, ea.query(source = S0, symbol = null, target = null).size)
        assertEquals(1, ea.query(source = S1, symbol = null, target = null).size)
        // Documented quirk: a "symbol" filter also matches epsilon edges.
        assertTrue(ea.query(source = null, symbol = Y, target = null).isNotEmpty())
        assertTrue(ea.query(source = S2, symbol = Y, target = S0).isNotEmpty())
        assertTrue(ea.query(source = null, symbol = Y, target = S0).isNotEmpty())
        assertTrue(ea.query(source = null, symbol = X, target = S1).isNotEmpty())
        // A concrete unmatched symbol still matches the epsilon edge.
        val missing = CharSymbol('!')
        assertEquals(1, ea.query(source = null, symbol = missing, target = null).size)
        assertEquals(0, ea.query(source = S1, symbol = missing, target = null).size)
        assertEquals(0, ea.query(source = null, symbol = missing, target = S1).size)
    }

    @Test
    fun `submachine query overload matrix`() {
        val ea = ElementaryActions(mapping())

        // With a null submachine filter the family is still call-kind-limited.
        assertEquals(1, ea.query(source = null, submachine = null, target = null).size)
        assertEquals(1, ea.query(source = null, submachine = null, target = S2).size)
        assertEquals(1, ea.query(S1, "M", null).size)
        assertEquals(0, ea.query(S1, "N", null).size)
        assertEquals(0, ea.query(S0, "M", null).size)

        // Post action filtered by name and by exact argument shape.
        assertEquals(1, ea.query(null, null, null, actionRef("PB")).size)
        assertEquals(1, ea.query(null, null, S2, actionRef("PB")).size)
        assertEquals(1, ea.query(null, "M", null, actionRef("PB")).size)
        // The concrete post filter also requires the exact argument list shape.
        assertEquals(0, ea.query(null, null, null, actionRef("PB", S1)).size)
        assertEquals(0, ea.query(null, null, null, actionRef("PB", S2)).size)
        assertEquals(0, ea.query(null, null, null, actionRef("PB", S1, S2)).size)
        assertEquals(0, ea.query(null, null, null, actionRef("nope")).size)
        // A non-null but never-registered name matches nothing.
        assertEquals(0, ea.query(null, null, null, actionRef("")).size)
    }

    @Test
    fun `prior and post together overload matrix`() {
        val ea = ElementaryActions(mapping())

        // The prior/post query family is call-kind-limited: priors attached to
        // symbol edges (PA, PA2) are never returned by these overloads.
        assertTrue(
            ea.query(priorAction = actionRef("PA"), source = null, submachine = null, target = null, postAction = null)
                .isEmpty(),
        )
        assertTrue(
            ea.query(priorAction = actionRef("PA2", S1, "str"), source = null, submachine = null, target = null)
                .isEmpty(),
        )
        assertTrue(
            ea.query(priorAction = null, source = null, submachine = "M", target = null, postAction = actionRef("PB"))
                .isNotEmpty(),
        )
        // Zero-argument prior reference: name-only match on the call edge.
        assertTrue(
            ea.query(priorAction = actionRef("PA3"), source = null, submachine = null, target = null, postAction = null)
                .isNotEmpty(),
        )
        // Argument list shape must match the reference's arity.
        assertTrue(
            ea.query(priorAction = actionRef("PA3", S1), source = null, submachine = null, target = null).isEmpty(),
        )
        assertTrue(
            ea.query(actionRef("PA3", S1, S1), null, null, null).isEmpty(),
        )
        // The four-argument prior-only overload with a zero-arg reference.
        assertEquals(1, ea.query(actionRef("PA3"), null, null, null).size)
    }

    @Test
    fun `remove overload matrix`() {
        val m1 = mapping()
        ElementaryActions(m1).remove(source = S0, symbol = X, target = null)
        // The epsilon edge from s0 also matches the wildcard-ish symbol filter.
        assertEquals(2, m1.size())

        val m2 = mapping()
        ElementaryActions(m2).remove(source = S2, symbol = Y, target = S0)
        assertEquals(0, m2.withSymbol(Y).size)

        val m3 = mapping()
        ElementaryActions(m3).remove(source = null, symbol = null, target = null)
        assertEquals(0, m3.size())

        val m4 = mapping()
        ElementaryActions(m4).remove(actionRef("PA"), null, Y, null)
        // No edge carries both prior PA and symbol Y (the Y edge has prior PA2).
        assertEquals(4, m4.size())
        assertEquals(1, m4.withSymbol(Y).size)

        val m5 = mapping()
        ElementaryActions(m5).remove(null, null, null, actionRef("PA2", S1, "str"))
        assertEquals(0, m5.withSymbol(Y).size)

        val m6 = mapping()
        // Positional call: 'Y' is not a State, so only the (source..prior) overload applies.
        ElementaryActions(m6).remove(null, Y, null, actionRef("nope"))
        assertEquals(1, m6.withSymbol(Y).size)

        val m7 = mapping()
        ElementaryActions(m7).remove { symbol = Y }
        assertEquals(0, m7.withSymbol(Y).size)

        val m8 = mapping()
        ElementaryActions(m8).remove {
            prior = actionRef("PA2", S1, "str")
            symbol = Y
        }
        assertEquals(0, m8.withSymbol(Y).size)

        val m9 = mapping()
        ElementaryActions(m9).remove { prior = actionRef("PA") }
        // Prior name PA removes only the s0 -x-> s1 edge.
        assertEquals(0, m9.withPriorAction("PA").size)
        assertEquals(3, m9.size())
    }
}
