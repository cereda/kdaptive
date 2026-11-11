// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The family of a pattern is derived from its filters, so a pattern can never
 * contradict itself.
 */
class TransitionPatternKindTest {

    @Test
    fun `a pattern naming no submachine belongs to the symbol family`() {
        assertEquals(TransitionPatternKind.SYMBOL, TransitionPattern().kind)
        assertEquals(TransitionPatternKind.SYMBOL, TransitionPattern(symbol = CharSymbol('a')).kind)
    }

    @Test
    fun `the epsilon family is named by the epsilon symbol`() {
        assertEquals(TransitionPatternKind.EPSILON, TransitionPattern(symbol = Symbol.Epsilon).kind)
    }

    @Test
    fun `a domain naming epsilon and other symbols is the mixed family`() {
        val mixed = Symbol.anyOf(CharSymbol('a'), Symbol.Epsilon)

        assertEquals(TransitionPatternKind.MIXED, TransitionPattern(symbol = mixed).kind)
    }

    @Test
    fun `a domain naming only epsilon is the epsilon family`() {
        val onlyEpsilon = Symbol.anyOf(Symbol.Epsilon)

        assertEquals(TransitionPatternKind.EPSILON, TransitionPattern(symbol = onlyEpsilon).kind)
    }

    @Test
    fun `a pattern naming a submachine belongs to the call family`() {
        assertEquals(
            TransitionPatternKind.SUBMACHINE,
            TransitionPattern(submachine = SubmachineRef.Any).kind,
        )
        assertEquals(
            TransitionPatternKind.SUBMACHINE,
            TransitionPattern(submachine = SubmachineRef.Named("M")).kind,
        )
        assertEquals(
            TransitionPatternKind.SUBMACHINE,
            TransitionPattern(submachine = SubmachineRef.anyOf("M", "N")).kind,
        )
    }

    @Test
    fun `the submachine filter drives the domain size`() {
        assertEquals(null, TransitionPattern(submachine = SubmachineRef.Any).submachineDomainSize)
        assertEquals(1, TransitionPattern(submachine = SubmachineRef.Named("M")).submachineDomainSize)
        assertEquals(2, TransitionPattern(submachine = SubmachineRef.anyOf("M", "N")).submachineDomainSize)
        assertEquals(1, TransitionPattern().submachineDomainSize)
    }

    @Test
    fun `the symbol filter does not apply to the call family`() {
        val call = TransitionPattern(submachine = SubmachineRef.Any)

        assertEquals(1, call.symbolDomainSize)
    }

    @Test
    fun `the symbol filter drives the domain size in the symbol family`() {
        assertEquals(null, TransitionPattern().symbolDomainSize)
        assertEquals(1, TransitionPattern(symbol = CharSymbol('a')).symbolDomainSize)
        assertEquals(2, TransitionPattern(symbol = Symbol.anyOf(CharSymbol('a'), CharSymbol('b'))).symbolDomainSize)
    }
}
