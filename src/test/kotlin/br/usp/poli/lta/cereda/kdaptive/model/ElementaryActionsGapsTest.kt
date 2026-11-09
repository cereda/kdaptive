// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.actions.asState
import br.usp.poli.lta.cereda.kdaptive.model.actions.asSymbol
import br.usp.poli.lta.cereda.kdaptive.model.actions.castOrThrow
import br.usp.poli.lta.cereda.kdaptive.model.actions.isType
import br.usp.poli.lta.cereda.kdaptive.model.actions.requireNonNull
import br.usp.poli.lta.cereda.kdaptive.model.actions.withType
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Covers the type helpers of the actions package ([castOrThrow], [asState],
 * [asSymbol], [requireNonNull], `isType<>()` and `withType<>()`) plus the
 * parameterless [ElementaryActions.add] overload.
 */
class ElementaryActionsGapsTest {
    private object P0 : State() {
        override fun toString(): String = "p0"
    }

    private object P1 : State() {
        override fun toString(): String = "p1"
    }

    private object S0 : Symbol() {
        override fun toString(): String = "s0"
    }

    @Test
    fun `castOrThrow succeeds and fails loudly`() {
        val value: Any = CharSymbol('a')
        assertEquals(CharSymbol::class, value.castOrThrow<CharSymbol>()::class)
        assertThrows<ClassCastException> { value.castOrThrow<String>() }
    }

    @Test
    fun `state and symbol casts succeed and fail loudly`() {
        assertSame(P0, P0.asState<State>())
        assertSame(S0, S0.asSymbol<Symbol>())
        assertThrows<ClassCastException> { "nope".asSymbol<Symbol>() }
        assertThrows<ClassCastException> { "nope".asState<State>() }
    }

    @Test
    fun `requireNonNull returns value or throws`() {
        val value: State = P0
        assertSame(value, requireNonNull(value))
        assertThrows<IllegalArgumentException> { requireNonNull(null) }
    }

    @Test
    fun `isType filters by transition kind`() {
        val symbolEdge =
            TransitionEdge(
                source = P0,
                target = P1,
                transition = Transition.SymbolTransition(symbol = S0),
            )
        val callEdge = TransitionEdge(source = P0, target = P1, transition = Transition.Call(submachine = "M"))

        assertTrue(symbolEdge.isType<Transition.SymbolTransition>())
        assertTrue(callEdge.isType<Transition.Call>())
        assertFalse(symbolEdge.isType<Transition.Call>())
        assertFalse(callEdge.isType<Transition.Return>())
    }

    @Test
    fun `withType collects by transition kind`() {
        val m = TransitionMapping()
        m.add(
            TransitionEdge(source = P0, target = P1, transition = Transition.SymbolTransition(symbol = S0)),
        )
        m.add(TransitionEdge(source = P1, target = P0, transition = Transition.Epsilon))
        m.add(TransitionEdge(source = P0, target = P0, transition = Transition.Call(submachine = "M")))

        assertEquals(1, m.withType<Transition.Epsilon>().size)
        assertEquals(0, m.withType<Transition.Return>().size)
        assertEquals(2, m.withType<Transition.Epsilon>().size + m.withType<Transition.SymbolTransition>().size)
    }

    @Test
    fun `elementary add without prior action`() {
        val m = TransitionMapping()
        m.add(
            TransitionEdge(source = P1, target = P1, transition = Transition.Epsilon),
        )
        val ea = ElementaryActions(m)

        ea.add(P0, S0, P1)

        assertEquals(2, m.size())
        assertTrue(m.withSymbol(S0).isNotEmpty())
    }
}
