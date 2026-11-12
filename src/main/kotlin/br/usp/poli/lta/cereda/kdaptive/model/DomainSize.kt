// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * How many values a filter may select.
 *
 * A filter either names a finite number of things — a concrete state, a
 * domain, a named action — or it names an unbounded number of them: the
 * wildcards [State.Any], [Symbol.Any], [SubmachineRef.Any] and
 * [ActionRef.Free] match whatever the automaton happens to contain.
 *
 * The distinction matters to consumers that price an operation: an unbounded
 * filter is not an empty one, and collapsing the two makes an operation over
 * the whole automaton look free.
 *
 * The library reports the size; it never interprets it. What an unbounded
 * filter means for a given consumer — a cost, a bound, a refusal to price —
 * is the consumer's decision.
 */
public sealed interface DomainSize {
    /**
     * A finite number of values.
     *
     * @property count how many values the filter may select
     */
    public data class Bounded(
        /** How many values the filter may select. */
        val count: Int,
    ) : DomainSize {
        init {
            require(count >= 0) { "A bounded domain size cannot be negative, but it was $count." }
        }
    }

    /** An unbounded number of values: the filter matches whatever the automaton holds. */
    public data object Unbounded : DomainSize
}
