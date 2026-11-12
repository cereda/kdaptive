// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/** A predicate over transition edges: true if the edge matches. */
public typealias TransitionPredicate = (TransitionEdge) -> Boolean

/** Combines predicates with logical OR from a list. */
public fun anyOf(predicates: List<TransitionPredicate>): TransitionPredicate = { t -> predicates.any { it(t) } }

/** Combines predicates with logical AND from a list. */
public fun allOf(predicates: List<TransitionPredicate>): TransitionPredicate = { t -> predicates.all { it(t) } }

/** Negates a predicate. */
public fun not(predicate: TransitionPredicate): TransitionPredicate = { t -> !predicate(t) }

/** Matches epsilon transitions (consumes no input). */
public val isEpsilon: TransitionPredicate = { it.transition is Transition.Epsilon }

/** Matches ordinary (non-submachine-call) transitions. */
public val isOrdinary: TransitionPredicate = { it.transition !is Transition.Call }

/** Matches symbol-consumption transitions. */
public val isSymbolConsumption: TransitionPredicate = { it.transition is Transition.SymbolTransition }

/** Matches submachine-call transitions. */
public val isSubmachineCall: TransitionPredicate = { it.transition is Transition.Call }

/** Matches transitions that return from a submachine. */
public val isSubmachineReturn: TransitionPredicate = { it.transition is Transition.Return }

/** Matches transitions with a prior action. */
public val hasPriorAction: TransitionPredicate = { it.prior is ActionRef.Bound }

/** Matches transitions with a post action. */
public val hasPostAction: TransitionPredicate = { it.post is ActionRef.Bound }

/**
 * Matches transitions whose source state satisfies the state filter [state].
 *
 * The filter means what it means everywhere else: the wildcard [State.Any]
 * matches any source state, a domain matches the states it names, and a
 * concrete state matches only itself.
 */
public fun sourceState(state: State): TransitionPredicate = { stateMatches(it.source, state) }

/**
 * Matches transitions whose target state satisfies the state filter [target].
 *
 * The filter means what it means everywhere else: the wildcard [State.Any]
 * matches any target state, a domain matches the states it names, and a
 * concrete state matches only itself.
 */
public fun targetState(target: State): TransitionPredicate = { stateMatches(it.target, target) }

/**
 * Matches transitions consuming the symbol named by [sym].
 *
 * The filter means what it means everywhere else: the wildcard [Symbol.Any]
 * matches every symbol-consuming transition, the empty word [Symbol.Epsilon]
 * matches the epsilon transitions, a domain matches the symbols it names, and
 * a concrete symbol matches only the transitions consuming it.
 */
public fun symbol(sym: Symbol): TransitionPredicate = { symbolMatches(it, sym) }

/**
 * Matches transitions calling submachine [name].
 */
public fun submachineCall(name: String): TransitionPredicate = {
    submachineMatches(it, SubmachineRef.Named(name))
}

/**
 * Matches transitions calling one of the submachines named [names].
 */
public fun submachineCallIn(names: Set<String>): TransitionPredicate = {
    submachineMatches(it, SubmachineRef.Domain(names))
}
