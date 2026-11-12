// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * An immutable snapshot of the topology of an adaptive automaton.
 *
 * A topology is the *shape* of an automaton at one moment: the edges it owns
 * and the submachines it is made of. The snapshot never changes: once taken,
 * it keeps describing the topology it was taken from, even when the automaton
 * moves on.
 *
 * The snapshot is what the engine hands to its observers. A visitor receives a
 * topology, not the live mapping: it can inspect the automaton, but it cannot
 * reach into the running recognition and change it behind the engine's back.
 *
 * The queries of the snapshot are the vocabulary of the library for asking
 * about a topology. What a *filter* means is expressed by
 * [TransitionPattern.matches] instead; the two never overlap.
 *
 * @property edges the edges of the topology
 * @property submachines the submachines of the topology
 */
public data class Topology(
    /** The edges of the topology. */
    val edges: Set<TransitionEdge>,
    /** The submachines of the topology. */
    val submachines: Set<Submachine>,
) {
    /** The number of edges of the topology. */
    val size: Int get() = edges.size

    /** The edges leaving [state]. */
    public fun edgesFrom(state: State): List<TransitionEdge> = edges.filter { it.source == state }

    /** The edges entering [target]. */
    public fun edgesTo(target: State): List<TransitionEdge> = edges.filter { it.target == target }

    /** The edges consuming [symbol]. */
    public fun edgesConsuming(symbol: Symbol): List<TransitionEdge> =
        edges.filter { it.transition is Transition.SymbolTransition && it.transition.symbol == symbol }

    /** The edges calling [submachine]. */
    public fun callsTo(submachine: String): List<TransitionEdge> =
        edges.filter { it.transition is Transition.Call && it.transition.submachine == submachine }

    /** The edges calling any of the [submachines]. */
    public fun callsTo(submachines: Set<String>): List<TransitionEdge> =
        edges.filter { it.transition is Transition.Call && it.transition.submachine in submachines }

    /** The edges consuming no input. */
    val epsilonEdges: List<TransitionEdge> get() = edges.filter { it.transition is Transition.Epsilon }

    /** The edges leaving [state] and consuming no input. */
    public fun epsilonEdgesFrom(state: State): List<TransitionEdge> =
        edgesFrom(state).filter { it.transition is Transition.Epsilon }

    /** The edges leaving [state] and consuming no input, calls included. */
    public fun epsilonOrCallEdgesFrom(state: State): List<TransitionEdge> =
        edgesFrom(state).filter { it.transition is Transition.Epsilon || it.transition is Transition.Call }

    /**
     * The edges leaving [state] that a path may take at the given input.
     *
     * A path may take an edge consuming [symbol], or an edge consuming
     * nothing at all — an epsilon transition or a submachine call.
     *
     * @param state the state the path is in
     * @param symbol the symbol at the cursor of the path
     * @return the edges the path may take
     */
    public fun applicableEdgesFrom(state: State, symbol: Symbol): List<TransitionEdge> =
        edgesFrom(state).filter { edge ->
            when (edge.transition) {
                is Transition.SymbolTransition -> edge.transition.symbol == symbol
                is Transition.Epsilon, is Transition.Call -> true
                is Transition.Return -> false
            }
        }

    /**
     * The edges satisfying [pattern].
     *
     * The pattern owns the meaning of the filters it carries, so the
     * snapshot never has an opinion of its own about what a filter means.
     *
     * @param pattern the pattern the edges are matched against
     * @return the edges matching [pattern]
     */
    public fun matching(pattern: TransitionPattern): List<TransitionEdge> = edges.filter { pattern.matches(it) }

    /** The edges whose prior action is the one named [name]. */
    public fun edgesWithPrior(name: String): List<TransitionEdge> = edges.filter { it.hasPriorAction(name) }

    /** The edges whose post action is the one named [name]. */
    public fun edgesWithPost(name: String): List<TransitionEdge> = edges.filter { it.hasPostAction(name) }

    /**
     * The edge carrying the given identifier.
     *
     * @param id the identifier of the edge
     * @return the edge
     * @throws IllegalStateException if the topology has no edge with that identifier
     */
    public fun fromId(id: Int): TransitionEdge =
        edges.firstOrNull { it.id == id } ?: error("No edge with identifier $id.")

    /**
     * The submachine owning [state].
     *
     * @param state the state
     * @return the submachine owning the state
     * @throws IllegalStateException if no submachine owns the state
     */
    public fun fromState(state: State): Submachine = submachines.firstOrNull { state in it.states }
        ?: error("No submachine contains state '$state'.")

    /** Returns a human-readable representation of this topology. */
    override fun toString(): String = buildString {
        append("Topology(edges=$size, submachines=${submachines.size})")
    }

    /**
     * The factory of the snapshots.
     *
     * A snapshot is always taken from a live topology: the two constructors
     * above build one from parts, and [of] builds one from the automaton
     * itself.
     */
    public companion object {
        /**
         * Takes a snapshot of [transitions] and [submachines].
         *
         * The snapshot copies the collections, so it keeps describing the
         * topology it was taken from even when the automaton moves on.
         *
         * @param transitions the mapping to snapshot
         * @param submachines the submachines to snapshot
         * @return the snapshot of the given topology
         */
        public fun of(transitions: TransitionMapping, submachines: Submachines): Topology = Topology(
            edges = transitions.transitions.toSet(),
            submachines = submachines.toSet(),
        )
    }
}
