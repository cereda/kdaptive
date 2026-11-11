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

    /**
     * Operations whose symbol filter names both symbol-consuming and epsilon
     * transitions.
     *
     * A symbol domain may name the empty word among its members, and then it
     * selects the two kinds at once.
     */
    MIXED,
}

/**
 * The pattern an elementary query or removal matched against the mapping.
 *
 * Every filter carries an explicit value: a wildcard is a value in its own
 * right ([State.Any], [Symbol.Any], [ActionRef.Free]), never a null. Epsilon
 * is named by [Symbol.Epsilon] and submachine calls by [submachine].
 *
 * A filter may also name a *domain* — a set of states or symbols built with
 * [State.anyOf] or [Symbol.anyOf] — and then it selects every member of that
 * domain.
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
) {
    /**
     * The number of source states this pattern may select.
     *
     * A concrete state names one state, a domain names as many states as it
     * has members, and the wildcard [State.Any] names an unbounded number of
     * them — which is reported as null.
     */
    val sourceDomainSize: Int?
        get() = when {
            source === State.Any -> null
            source is StateDomain -> source.size
            else -> 1
        }

    /**
     * The number of symbols this pattern may select.
     *
     * A concrete symbol — [Symbol.Epsilon] included — names one symbol, a
     * domain names as many symbols as it has members, and the wildcard
     * [Symbol.Any] names an unbounded number of them — which is reported as
     * null. In the [TransitionPatternKind.SUBMACHINE] family the filter does
     * not apply, and the size is one.
     */
    val symbolDomainSize: Int?
        get() = when (kind) {
            TransitionPatternKind.SUBMACHINE -> 1

            else -> when {
                symbol === Symbol.Any -> null
                symbol is SymbolDomain -> symbol.size
                else -> 1
            }
        }

    /**
     * The number of target states this pattern may select.
     *
     * A concrete state names one state, a domain names as many states as it
     * has members, and the wildcard [State.Any] names an unbounded number of
     * them — which is reported as null.
     */
    val targetDomainSize: Int?
        get() = when {
            target === State.Any -> null
            target is StateDomain -> target.size
            else -> 1
        }

    /**
     * The number of submachines this pattern may select.
     *
     * In the [TransitionPatternKind.SUBMACHINE] family, a null filter names
     * an unbounded number of submachines — which is reported as null — while
     * a named one names exactly one. In the other families the filter does
     * not apply, and the size is one.
     */
    val submachineDomainSize: Int?
        get() = when (kind) {
            TransitionPatternKind.SUBMACHINE -> if (submachine == null) null else 1
            else -> 1
        }

    /**
     * The number of prior actions this pattern may select.
     *
     * A concrete filter — [ActionRef.None], [ActionRef.Named] or
     * [ActionRef.Bound] — names one action, while [ActionRef.Free] and
     * [ActionRef.Any] name an unbounded number of them — which is reported as
     * null.
     */
    val priorDomainSize: Int?
        get() = when (prior) {
            is ActionRef.Free, is ActionRef.Any -> null
            else -> 1
        }

    /**
     * The number of post actions this pattern may select.
     *
     * A concrete filter — [ActionRef.None], [ActionRef.Named] or
     * [ActionRef.Bound] — names one action, while [ActionRef.Free] and
     * [ActionRef.Any] name an unbounded number of them — which is reported as
     * null.
     */
    val postDomainSize: Int?
        get() = when (post) {
            is ActionRef.Free, is ActionRef.Any -> null
            else -> 1
        }
}
