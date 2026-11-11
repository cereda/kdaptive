// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/** A predicate over transition edges: true if the edge matches. */
typealias TransitionPredicate = (TransitionEdge) -> Boolean

/** Combines predicates with logical OR from a list. */
fun anyOf(predicates: List<TransitionPredicate>): TransitionPredicate = { t -> predicates.any { it(t) } }

/** Combines predicates with logical AND from a list. */
fun allOf(predicates: List<TransitionPredicate>): TransitionPredicate = { t -> predicates.all { it(t) } }

/** Negates a predicate. */
fun not(predicate: TransitionPredicate): TransitionPredicate = { t -> !predicate(t) }

/** Matches epsilon transitions (consumes no input). */
val isEpsilon: TransitionPredicate = { it.transition is Transition.Epsilon }

/** Matches ordinary (non-submachine-call) transitions. */
val isOrdinary: TransitionPredicate = { it.transition !is Transition.Call }

/** Matches symbol-consumption transitions. */
val isSymbolConsumption: TransitionPredicate = { it.transition is Transition.SymbolTransition }

/** Matches submachine-call transitions. */
val isSubmachineCall: TransitionPredicate = { it.transition is Transition.Call }

/** Matches transitions that return from a submachine. */
val isSubmachineReturn: TransitionPredicate = { it.transition is Transition.Return }

/** Matches transitions with a prior action. */
val hasPriorAction: TransitionPredicate = { it.prior is ActionRef.Bound }

/** Matches transitions with a post action. */
val hasPostAction: TransitionPredicate = { it.post is ActionRef.Bound }

/**
 * Matches transitions whose source state satisfies the state filter [state].
 *
 * The filter means what it means everywhere else: the wildcard [State.Any]
 * matches any source state, a domain matches the states it names, and a
 * concrete state matches only itself.
 */
fun sourceState(state: State): TransitionPredicate = { stateMatches(it.source, state) }

/**
 * Matches transitions whose target state satisfies the state filter [target].
 *
 * The filter means what it means everywhere else: the wildcard [State.Any]
 * matches any target state, a domain matches the states it names, and a
 * concrete state matches only itself.
 */
fun targetState(target: State): TransitionPredicate = { stateMatches(it.target, target) }

/**
 * Matches transitions consuming the symbol named by [sym].
 *
 * The filter means what it means everywhere else: the wildcard [Symbol.Any]
 * matches every symbol-consuming transition, the empty word [Symbol.Epsilon]
 * matches the epsilon transitions, a domain matches the symbols it names, and
 * a concrete symbol matches only the transitions consuming it.
 */
fun symbol(sym: Symbol): TransitionPredicate = { symbolMatches(it, sym) }

/**
 * Matches transitions calling submachine [name].
 */
fun submachineCall(name: String): TransitionPredicate = {
    submachineMatches(it, SubmachineRef.Named(name))
}

/**
 * Matches transitions calling one of the submachines named [names].
 */
fun submachineCallIn(names: Set<String>): TransitionPredicate = {
    submachineMatches(it, SubmachineRef.Domain(names))
}
