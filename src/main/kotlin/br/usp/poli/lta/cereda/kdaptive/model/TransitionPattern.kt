// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * The family of elementary operations over the transition mapping.
 *
 * The family tells how the nullable fields of a [TransitionPattern] are to be
 * read: the same null means different things depending on the family.
 */
enum class TransitionPatternKind {
    /** Operations over symbol-consuming transitions. */
    SYMBOL,

    /** Operations over submachine-call transitions. */
    SUBMACHINE,
}

/**
 * The pattern an elementary query or removal matched against the mapping.
 *
 * Every filter is nullable, and null means "not constrained by this filter" —
 * with one exception, documented on [submachine], where the meaning depends on
 * the [kind].
 *
 * The pattern describes what was *asked for*, not what was found: the edges
 * that actually matched are reported separately by
 * [MappingOperation.Query] and [MappingOperation.Remove].
 */
data class TransitionPattern(
    /** The family the pattern belongs to, which fixes the meaning of its null filters. */
    val kind: TransitionPatternKind,
    /** The source-state filter; null means any source state. */
    val source: State? = null,
    /**
     * The symbol filter; null means any symbol.
     *
     * A non-null symbol also matches epsilon transitions, mirroring the
     * engine's own semantics for symbol queries and removals.
     */
    val symbol: Symbol? = null,
    /** The target-state filter; null means any target state. */
    val target: State? = null,
    /**
     * The submachine-call filter.
     *
     * In the [TransitionPatternKind.SUBMACHINE] family, null means "any
     * submachine call"; in the [TransitionPatternKind.SYMBOL] family the field
     * does not apply and is always null.
     */
    val submachine: String? = null,
    /** The prior-action filter; null means any prior action. */
    val prior: ActionRef? = null,
    /** The post-action filter; null means any post action. */
    val post: ActionRef? = null,
)
