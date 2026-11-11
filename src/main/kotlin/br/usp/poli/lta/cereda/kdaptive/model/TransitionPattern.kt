// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * The family of elementary operations over the transition mapping.
 *
 * The family tells which kind of transition the pattern selects, and fixes the
 * meaning of the filters it carries.
 */
enum class TransitionPatternKind {
    /** Operations over symbol-consuming transitions. */
    SYMBOL,

    /** Operations over epsilon transitions, which consume no input. */
    EPSILON,

    /** Operations over submachine-call transitions. */
    SUBMACHINE,
}

/**
 * The pattern an elementary query or removal matched against the mapping.
 *
 * Every filter carries an explicit value: a wildcard is a value in its own
 * right ([State.Any], [Symbol.Any], [ActionRef.Free]), never a null. Epsilon
 * is named by [Symbol.Epsilon] and submachine calls by [submachine].
 *
 * The pattern describes what was *asked for*, not what was found: the edges
 * that actually matched are reported separately by
 * [MappingOperation.Query] and [MappingOperation.Remove].
 *
 * @property kind the family the pattern belongs to, which fixes the meaning of
 *   its filters
 * @property source the source-state filter; [State.Any] means any source state
 * @property symbol the symbol filter; [Symbol.Any] means any
 *   symbol-consuming transition, while [Symbol.Epsilon] means an epsilon
 *   transition
 * @property target the target-state filter; [State.Any] means any target state
 * @property submachine the submachine-call filter; in the
 *   [TransitionPatternKind.SUBMACHINE] family, null means any submachine call,
 *   while in the other families the field does not apply and is always null
 * @property prior the prior-action filter; [ActionRef.Free] means no
 *   constraint at all
 * @property post the post-action filter; [ActionRef.Free] means no constraint
 *   at all
 */
data class TransitionPattern(
    /** The family the pattern belongs to, which fixes the meaning of its filters. */
    val kind: TransitionPatternKind,
    /** The source-state filter; [State.Any] means any source state. */
    val source: State = State.Any,
    /** The symbol filter; [Symbol.Any] means any symbol-consuming transition. */
    val symbol: Symbol = Symbol.Any,
    /** The target-state filter; [State.Any] means any target state. */
    val target: State = State.Any,
    /** The submachine-call filter; null means any submachine call. */
    val submachine: String? = null,
    /** The prior-action filter; [ActionRef.Free] means no constraint. */
    val prior: ActionRef = ActionRef.Free,
    /** The post-action filter; [ActionRef.Free] means no constraint. */
    val post: ActionRef = ActionRef.Free,
)
