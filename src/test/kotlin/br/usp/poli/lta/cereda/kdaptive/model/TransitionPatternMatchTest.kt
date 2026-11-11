// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The pattern is the single implementation of what a filter means: it
 * answers whether an edge satisfies it, and the elementary operations filter
 * the mapping with exactly that answer.
 */
class TransitionPatternMatchTest {

    private object Q0 : State() {
        override fun toString(): String = "q0"
    }

    private object Q1 : State() {
        override fun toString(): String = "q1"
    }

    private object Q2 : State() {
        override fun toString(): String = "q2"
    }

    /** An edge consuming the symbol 'a'. */
    private fun symbolEdge(
        source: State = Q0,
        target: State = Q1,
        symbol: Symbol = CharSymbol('a'),
        prior: ActionRef = ActionRef.None,
        post: ActionRef = ActionRef.None,
    ): TransitionEdge = TransitionEdge(
        source = source,
        target = target,
        transition = Transition.SymbolTransition(symbol = symbol),
        prior = prior,
        post = post,
    )

    /** An edge consuming nothing. */
    private fun epsilonEdge(source: State = Q0, target: State = Q1): TransitionEdge =
        TransitionEdge(source = source, target = target, transition = Transition.Epsilon)

    /** An edge calling the submachine 'M'. */
    private fun callEdge(submachine: String = "M"): TransitionEdge =
        TransitionEdge(source = Q0, target = Q1, transition = Transition.Call(submachine))

    @Test
    fun `the wildcards match any endpoint and any consuming edge`() {
        val pattern = TransitionPattern()

        assertTrue(pattern.matches(symbolEdge()))
        assertTrue(pattern.matches(symbolEdge(source = Q2, target = Q2, symbol = CharSymbol('z'))))
        // The symbol wildcard names the symbol-consuming transitions only.
        assertFalse(pattern.matches(epsilonEdge()))
        assertFalse(pattern.matches(callEdge()))
    }

    @Test
    fun `a state domain matches its members`() {
        val pattern = TransitionPattern(source = State.anyOf(Q0, Q2))

        assertTrue(pattern.matches(symbolEdge(source = Q0)))
        assertTrue(pattern.matches(symbolEdge(source = Q2)))
        assertFalse(pattern.matches(symbolEdge(source = Q1)))
    }

    @Test
    fun `a concrete state matches only itself`() {
        val pattern = TransitionPattern(target = Q2)

        assertTrue(pattern.matches(symbolEdge(target = Q2)))
        assertFalse(pattern.matches(symbolEdge(target = Q1)))
    }

    @Test
    fun `the epsilon symbol selects the epsilon transitions`() {
        val pattern = TransitionPattern(symbol = Symbol.Epsilon)

        assertTrue(pattern.matches(epsilonEdge()))
        assertFalse(pattern.matches(symbolEdge()))
        assertFalse(pattern.matches(callEdge()))
    }

    @Test
    fun `a symbol domain naming epsilon selects both kinds`() {
        val pattern = TransitionPattern(symbol = Symbol.anyOf(CharSymbol('a'), Symbol.Epsilon))

        assertTrue(pattern.matches(symbolEdge()))
        assertTrue(pattern.matches(epsilonEdge()))
        assertFalse(pattern.matches(symbolEdge(symbol = CharSymbol('b'))))
        assertFalse(pattern.matches(callEdge()))
    }

    @Test
    fun `a concrete symbol selects the transitions consuming it`() {
        val pattern = TransitionPattern(symbol = CharSymbol('b'))

        assertTrue(pattern.matches(symbolEdge(symbol = CharSymbol('b'))))
        assertFalse(pattern.matches(symbolEdge(symbol = CharSymbol('a'))))
        assertFalse(pattern.matches(epsilonEdge()))
    }

    @Test
    fun `the call family selects the calls and ignores the symbol`() {
        val pattern = TransitionPattern(submachine = SubmachineRef.Any)

        assertTrue(pattern.matches(callEdge()))
        assertFalse(pattern.matches(symbolEdge()))
        assertFalse(pattern.matches(epsilonEdge()))
    }

    @Test
    fun `a named submachine selects the calls to it`() {
        val pattern = TransitionPattern(submachine = SubmachineRef.Named("M"))

        assertTrue(pattern.matches(callEdge("M")))
        assertFalse(pattern.matches(callEdge("N")))
    }

    @Test
    fun `a submachine domain selects the calls to any of its members`() {
        val pattern = TransitionPattern(submachine = SubmachineRef.anyOf("M", "N"))

        assertTrue(pattern.matches(callEdge("M")))
        assertTrue(pattern.matches(callEdge("N")))
        assertFalse(pattern.matches(callEdge("O")))
    }

    @Test
    fun `an unconstrained action slot matches any binding`() {
        val bound = ActionRef.Bound("A", listOf(Q0))
        val edge = symbolEdge(prior = bound, post = bound)

        assertTrue(TransitionPattern(prior = ActionRef.Free).matches(edge))
        assertTrue(TransitionPattern(post = ActionRef.Free).matches(edge))
    }

    @Test
    fun `the action slots distinguish empty, any and named`() {
        val bound = ActionRef.Bound("A")
        val edge = symbolEdge(prior = bound)

        assertTrue(TransitionPattern(prior = ActionRef.Any).matches(edge))
        assertFalse(TransitionPattern(prior = ActionRef.None).matches(edge))
        assertTrue(TransitionPattern(prior = ActionRef.None).matches(symbolEdge()))
        assertTrue(TransitionPattern(prior = ActionRef.Named("A")).matches(edge))
        assertFalse(TransitionPattern(prior = ActionRef.Named("B")).matches(edge))
    }

    @Test
    fun `a bound filter fixes the arity and the arguments`() {
        val edge = symbolEdge(prior = ActionRef.Bound("A", listOf(Q0)))

        assertTrue(TransitionPattern(prior = ActionRef.Bound("A", listOf(Q0))).matches(edge))
        assertFalse(TransitionPattern(prior = ActionRef.Bound("A", listOf(Q1))).matches(edge))
        assertFalse(TransitionPattern(prior = ActionRef.Bound("A")).matches(edge))
        // A free argument leaves its position unconstrained.
        assertTrue(TransitionPattern(prior = ActionRef.Bound("A", listOf(ActionRef.Free))).matches(edge))
    }

    @Test
    fun `a bounded domain size cannot be negative`() {
        assertThrows<IllegalArgumentException> { DomainSize.Bounded(-1) }
    }
}
