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
 *
 * **Equality**: the library compares symbols by *value*, so a subclass must
 * define it — a data class does it for free. Two symbols with the same
 * content are the same symbol: an edge declared twice with the same symbol is
 * one edge, not two.
 */
abstract class Symbol protected constructor() {
    /** The markers a filter may use to name a symbol. */
    companion object {
        /** The wildcard symbol: matches any symbol-consuming transition. */
        val Any: Symbol = AnySymbol

        /** The empty symbol: matches epsilon transitions only. */
        val Epsilon: Symbol = EpsilonSymbol

        /**
         * Builds a **domain**: the set of symbols a filter names at once.
         *
         * A domain is a filter marker, like [Any]: it never belongs to the
         * input alphabet, and it is rejected wherever a concrete symbol is
         * required. What it does is name *several* symbols in a single
         * filter:
         *
         * ```
         * val vowels = Symbol.anyOf(CharSymbol('a'), CharSymbol('e'))
         * ea.query(symbol = vowels)          // matches the edges consuming a or e
         * ea.add(q0, vowels, q1)              // adds one edge per symbol of the domain
         * ```
         *
         * A domain may name [Epsilon] among its members: epsilon denotes the
         * empty word, and a domain naming it selects the epsilon transitions
         * as well as the symbol-consuming ones. The wildcard [Any] cannot be
         * part of a domain — it would swallow the whole alphabet — and
         * neither can another domain.
         *
         * @param symbols the symbols of the domain
         * @return the domain over [symbols]
         * @throws IllegalArgumentException if [symbols] is empty, or names the
         *   wildcard symbol or another domain
         */
        fun anyOf(vararg symbols: Symbol): SymbolDomain = SymbolDomain.of(symbols.toList())
    }
}

/**
 * A domain over symbols: the set of symbols a filter names at once.
 *
 * A domain is a filter, not an alphabet element: it never belongs to the
 * input alphabet. It matches an edge when the symbol the edge consumes — or
 * the empty word, for an epsilon edge — is one of its [members], and an
 * addition over a domain produces one edge per member.
 *
 * Two domains are equal when they name the same symbols.
 */
class SymbolDomain private constructor(
    /** The symbols of this domain. */
    val members: Set<Symbol>,
) : Symbol() {
    /** The number of symbols in this domain. */
    val size: Int get() = members.size

    /** Whether this domain names the empty word among its members. */
    val containsEpsilon: Boolean get() = members.any { it === Symbol.Epsilon }

    /** The members of this domain that are ordinary alphabet symbols. */
    val alphabetMembers: Set<Symbol> get() = members.filterTo(mutableSetOf()) { it !== Symbol.Epsilon }

    /** Returns a human-readable representation of this domain. */
    override fun toString(): String = members.joinToString(prefix = "{", postfix = "}")

    /** Two domains are equal when they name the same symbols. */
    override fun equals(other: Any?): Boolean = other is SymbolDomain && other.members == members

    /** The hash code follows the symbols named by this domain. */
    override fun hashCode(): Int = members.hashCode()

    /** The factory of the domains, used by [Symbol.anyOf]. */
    companion object {
        /** Builds a domain over [symbols], rejecting the markers a domain may not contain. */
        internal fun of(symbols: List<Symbol>): SymbolDomain {
            require(symbols.isNotEmpty()) { "A symbol domain must name at least one symbol." }
            require(symbols.none { it === Symbol.Any }) {
                "A symbol domain cannot contain the wildcard symbol."
            }
            require(symbols.none { it is SymbolDomain }) {
                "A symbol domain cannot contain another domain."
            }
            return SymbolDomain(symbols.toSet())
        }
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
