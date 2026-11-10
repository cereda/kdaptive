// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model.sets

import br.usp.poli.lta.cereda.kdaptive.model.MappingOperation
import br.usp.poli.lta.cereda.kdaptive.model.Observation
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.predicates.allOf
import br.usp.poli.lta.cereda.kdaptive.model.predicates.anyOf
import br.usp.poli.lta.cereda.kdaptive.model.predicates.sourceState
import br.usp.poli.lta.cereda.kdaptive.model.predicates.submachineCall
import br.usp.poli.lta.cereda.kdaptive.model.predicates.symbol
import br.usp.poli.lta.cereda.kdaptive.model.predicates.targetState

/**
 * The mapping of transitions of the adaptive automaton.
 */
class TransitionMapping {
    private val transitions = mutableSetOf<TransitionEdge>()

    /** Lazy index of transitions grouped by source state; null when invalidated by a mutation. */
    private var bySource: Map<State, List<TransitionEdge>>? = null

    /** Callback invoked after a transition edge is added to this mapping. */
    var onEdgeAdded: ((TransitionEdge) -> Unit)? = null

    /** Callback invoked after a transition edge is removed from this mapping. */
    var onEdgeRemoved: ((TransitionEdge) -> Unit)? = null

    /** Callback invoked after an elementary operation is performed on this mapping. */
    var onOperation: ((MappingOperation) -> Unit)? = null

    /** Callback invoked when instrumentation-aware code publishes a fact about this mapping. */
    var onObservation: ((Observation) -> Unit)? = null

    /**
     * Publishes a fact to the [onObservation] callback, if one is registered.
     *
     * This is the channel through which action code reports what it computed
     * and what the engine cannot observe on its own. Publishing a fact never
     * changes the mapping.
     *
     * @param observation the fact to publish
     */
    fun observe(observation: Observation) {
        onObservation?.invoke(observation)
    }

    /** Returns (building if needed) the index of transitions grouped by source state. */
    private fun sourceIndex(): Map<State, List<TransitionEdge>> {
        bySource?.let { return it }
        return transitions.groupBy { it.source }.also { bySource = it }
    }

    /**
     * Adds a transition to the mapping.
     *
     * Adding a transition that is already present is a no-op: the
     * [onEdgeAdded] callback only fires on an actual insertion, so observers
     * never see a phantom mutation.
     */
    fun add(t: TransitionEdge) {
        if (!transitions.add(t)) return
        bySource = null
        onEdgeAdded?.invoke(t)
    }

    /** Returns all transitions in the mapping. */
    fun getTransitions(): Set<TransitionEdge> = transitions

    /** Checks whether a transition with the given ID exists. */
    fun hasIdentifier(id: Int): Boolean = transitions.any { it.id == id }

    /** Returns the number of transitions in the mapping. */
    fun size(): Int = transitions.size

    /** Returns transitions with the given source state. */
    fun withSourceState(state: State): List<TransitionEdge> = sourceIndex()[state] ?: emptyList()

    /** Returns transitions consuming the given symbol. */
    fun withSymbol(symbol: Symbol): List<TransitionEdge> = transitions
        .asSequence()
        .filter { it.transition is Transition.SymbolTransition && it.transition.symbol == symbol }
        .toList()

    /** Returns transitions with the given target state. */
    fun withTargetState(state: State): List<TransitionEdge> = transitions
        .asSequence()
        .filter { it.target == state }
        .toList()

    /** Returns transitions calling the given submachine. */
    fun withSubmachine(submachine: String): List<TransitionEdge> = transitions
        .asSequence()
        .filter { it.transition is Transition.Call && it.transition.submachine == submachine }
        .toList()

    /** Returns transitions whose source state equals [state] and whose symbol equals [symbol]. */
    fun withSourceStateAndSymbol(state: State, symbol: Symbol): List<TransitionEdge> = transitions
        .asSequence()
        .filter(allOf(listOf(sourceState(state), symbol(symbol))))
        .toList()

    /** Returns transitions whose source state equals [state1] and target state equals [state2]. */
    fun withSourceAndTargetStates(state1: State, state2: State): List<TransitionEdge> = transitions
        .asSequence()
        .filter(allOf(listOf(sourceState(state1), targetState(state2))))
        .toList()

    /**
     * Returns transitions whose source state equals [state1], symbol equals [symbol],
     * and target state equals [state2].
     */
    fun withSourceStateSymbolAndTargetState(state1: State, symbol: Symbol, state2: State): List<TransitionEdge> =
        transitions
            .asSequence()
            .filter(allOf(listOf(sourceState(state1), symbol(symbol), targetState(state2))))
            .toList()

    /** Returns transitions whose source state equals [state] and which call submachine [submachine]. */
    fun withSourceStateAndSubmachine(state: State, submachine: String): List<TransitionEdge> = transitions
        .asSequence()
        .filter(allOf(listOf(sourceState(state), submachineCall(submachine))))
        .toList()

    /** Returns transitions whose symbol equals [symbol] and target state equals [state]. */
    fun withSymbolAndTargetState(symbol: Symbol, state: State): List<TransitionEdge> = transitions
        .asSequence()
        .filter(allOf(listOf(symbol(symbol), targetState(state))))
        .toList()

    /** Returns transitions whose prior action name equals [name]. */
    fun withPriorAction(name: String): List<TransitionEdge> = transitions
        .asSequence()
        .filter { it.prior?.name == name }
        .toList()

    /** Returns transitions whose post action name equals [name]. */
    fun withPostAction(name: String): List<TransitionEdge> = transitions
        .asSequence()
        .filter { it.post?.name == name }
        .toList()

    /** Returns all epsilon transitions. */
    fun withEpsilonTransitions(): List<TransitionEdge> = transitions
        .asSequence()
        .filter { it.transition is Transition.Epsilon }
        .toList()

    /** Returns epsilon transitions whose source state equals [state]. */
    fun withEpsilonFromSourceState(state: State): List<TransitionEdge> =
        withSourceState(state).filter { it.transition is Transition.Epsilon }

    /** Returns epsilon or submachine-call transitions whose source state equals [state]. */
    fun withEpsilonOrSubmachineFromSourceState(state: State): List<TransitionEdge> = transitions
        .asSequence()
        .filter {
            it.source == state && (it.transition is Transition.Epsilon || it.transition is Transition.Call)
        }.toList()

    /** Returns transitions from [state] consuming [symbol], or epsilon/submachine-call transitions. */
    fun withSymbolEpsilonOrSubmachineFromSourceState(state: State, symbol: Symbol): List<TransitionEdge> = transitions
        .asSequence()
        .filter {
            it.source == state && (
                (it.transition is Transition.SymbolTransition && it.transition.symbol == symbol) ||
                    it.transition is Transition.Epsilon ||
                    it.transition is Transition.Call
                )
        }.toList()

    /** Removes the transition with the given identifier from the mapping. */
    fun removeFromIdentifier(id: Int) {
        val removed = transitions.firstOrNull { it.id == id }
        transitions.removeIf { it.id == id }
        bySource = null
        removed?.let { onEdgeRemoved?.invoke(it) }
    }

    /** Creates a copy of this mapping with copies of all transitions. */
    fun copy(): TransitionMapping {
        val m = TransitionMapping()
        transitions.forEach { m.add(it.copy()) }
        return m
    }

    /** Returns a human-readable representation of this mapping. */
    override fun toString(): String {
        val sb = StringBuilder("Set of transitions: {")
        if (transitions.isNotEmpty()) {
            sb.append("\n")
            for (t in transitions) {
                sb.append("  $t\n")
            }
        }
        sb.append("}")
        return sb.toString()
    }
}
