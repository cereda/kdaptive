// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/*
 * The shared semantics of the filters.
 *
 * Every filter of the library — the ones a [TransitionPattern] carries, the
 * ones a raw predicate is built from — means the same thing, and it means it
 * here, once:
 *
 * - a state filter matches the wildcard [State.Any], the members of a
 *   [StateDomain], or one concrete state;
 * - a symbol filter matches the wildcard [Symbol.Any] (every symbol-consuming
 *   transition), the empty word [Symbol.Epsilon] (the epsilon transitions),
 *   the members of a [SymbolDomain], or one concrete symbol;
 * - a submachine filter matches the wildcard [SubmachineRef.Any], one named
 *   submachine, the submachines of a [SubmachineRef.Domain], or nothing at all
 *   when it is [SubmachineRef.None];
 * - an action-slot filter matches an unconstrained slot ([ActionRef.Free]), a
 *   slot carrying some action ([ActionRef.Any]), an empty slot
 *   ([ActionRef.None]), the named action ([ActionRef.Named]), or the named
 *   action called with exactly the listed arguments ([ActionRef.Bound]).
 *
 * These functions are internal: they are the shared implementation behind the
 * public pattern and the public predicates, not a layer of their own.
 */

/** Tells whether [actual] satisfies the state [filter]. */
internal fun stateMatches(actual: State, filter: State): Boolean = when {
    filter === State.Any -> true
    filter is StateDomain -> actual in filter.members
    else -> actual == filter
}

/**
 * Tells whether [edge] consumes the symbol named by [filter].
 *
 * The empty word is a symbol too: [Symbol.Epsilon] selects the epsilon
 * transitions, and a domain naming it selects them along with its other
 * members.
 */
internal fun symbolMatches(edge: TransitionEdge, filter: Symbol): Boolean = when {
    filter === Symbol.Epsilon -> edge.transition is Transition.Epsilon
    filter === Symbol.Any -> edge.transition is Transition.SymbolTransition
    filter is SymbolDomain -> filter.members.any { member -> symbolMatches(edge, member) }
    else -> edge.transition is Transition.SymbolTransition && edge.transition.symbol == filter
}

/** Tells whether [edge] calls the submachine named by [filter]. */
internal fun submachineMatches(edge: TransitionEdge, filter: SubmachineRef): Boolean = when (filter) {
    SubmachineRef.Any -> edge.transition is Transition.Call
    is SubmachineRef.Named -> edge.transition is Transition.Call && edge.transition.submachine == filter.name
    is SubmachineRef.Domain -> edge.transition is Transition.Call && edge.transition.submachine in filter.names
    SubmachineRef.None -> false
}

/** Tells whether the action slot [actual] satisfies the constraint [filter]. */
internal fun actionSlotMatches(filter: ActionRef, actual: ActionRef): Boolean = when (filter) {
    is ActionRef.Free -> true
    is ActionRef.Any -> actual is ActionRef.Bound
    is ActionRef.None -> actual === ActionRef.None
    is ActionRef.Named -> (actual as? ActionRef.Bound)?.name == filter.name
    is ActionRef.Bound -> bindingMatches(actual, filter)
}

/**
 * Tells whether the action slot [actual] carries exactly the binding
 * [expected].
 *
 * The expected binding fixes the arity; an expected argument equal to
 * [ActionRef.Free] leaves that position unconstrained.
 */
internal fun bindingMatches(actual: ActionRef, expected: ActionRef.Bound): Boolean {
    if (actual !is ActionRef.Bound) return false
    if (actual.name != expected.name) return false
    if (actual.args.size != expected.args.size) return false
    return expected.args.indices.all { index ->
        val wanted = expected.args[index]
        wanted === ActionRef.Free || actual.args[index] == wanted
    }
}
