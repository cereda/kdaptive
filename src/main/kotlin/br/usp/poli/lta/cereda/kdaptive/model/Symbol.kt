// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * Abstract representation of a symbol belonging to the input alphabet of an
 * adaptive automaton.
 *
 * Users typically create concrete symbols as data classes, e.g.:
 *     data class CharSymbol(val value: Char) : Symbol()
 *
 * Two distinguished symbols are provided for use as *filters*, never as
 * alphabet elements:
 *
 * - [Any] matches any symbol-consuming transition;
 * - [Epsilon] matches epsilon transitions, which consume no input.
 *
 * Epsilon is not part of the input alphabet: it denotes the empty word, and
 * the automaton models it as a distinct transition kind
 * ([Transition.Epsilon]). [Epsilon] exists so that a pattern can name those
 * transitions explicitly, instead of overloading a null symbol.
 */
abstract class Symbol protected constructor() {
    /** The markers a filter may use to name a symbol. */
    companion object {
        /** The wildcard symbol: matches any symbol-consuming transition. */
        val Any: Symbol = AnySymbol

        /** The empty symbol: matches epsilon transitions only. */
        val Epsilon: Symbol = EpsilonSymbol
    }
}

/**
 * The wildcard symbol, used as a filter to match any symbol-consuming
 * transition.
 *
 * This symbol never belongs to the input alphabet of an automaton: it is
 * rejected wherever a concrete symbol is required.
 */
object AnySymbol : Symbol() {
    /** Returns a human-readable representation of this wildcard. */
    override fun toString(): String = "*"
}

/**
 * The empty symbol, used as a filter to match epsilon transitions.
 *
 * Epsilon is not part of the input alphabet of an automaton: it is rejected
 * wherever a concrete symbol is required.
 */
object EpsilonSymbol : Symbol() {
    /** Returns a human-readable representation of this symbol. */
    override fun toString(): String = "ε"
}
