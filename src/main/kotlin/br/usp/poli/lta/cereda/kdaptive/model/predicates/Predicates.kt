// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model.predicates

import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge

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
val hasPriorAction: TransitionPredicate = { it.prior != null }

/** Matches transitions with a post action. */
val hasPostAction: TransitionPredicate = { it.post != null }

/** Matches transitions whose source state equals [state]. */
fun sourceState(state: State): TransitionPredicate = { it.source == state }

/** Matches transitions whose target state equals [state]. */
fun targetState(state: State): TransitionPredicate = { it.target == state }

/** Matches transitions consuming [symbol]. */
fun symbol(sym: Symbol): TransitionPredicate = {
    it.transition is Transition.SymbolTransition &&
        it.transition.symbol == sym
}

/** Matches transitions calling submachine [name]. */
fun submachineCall(name: String): TransitionPredicate = {
    it.transition is Transition.Call &&
        it.transition.submachine == name
}
