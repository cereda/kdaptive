// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * The mapping of transitions of the adaptive automaton.
 *
 * The mapping is the *live* topology: it changes while the automaton runs,
 * because adaptive actions add and remove edges. The questions one may ask
 * about a topology are asked of a [br.usp.poli.lta.cereda.kdaptive.model.Topology]
 * snapshot; what a *filter* means is asked of a
 * [br.usp.poli.lta.cereda.kdaptive.model.TransitionPattern]. The mapping itself
 * owns the edges and reports its own mutations.
 */
class TransitionMapping {
    /** The edges of the mapping. */
    private val edges = mutableSetOf<TransitionEdge>()

    /** Lazy index of edges grouped by source state; null when invalidated by a mutation. */
    private var bySource: Map<State, List<TransitionEdge>>? = null

    /** Cached structural fingerprint of the edges; null when invalidated by a mutation. */
    private var digest: Long? = null

    /** Callback invoked after a transition edge is added to this mapping. */
    var onEdgeAdded: (TransitionEdge) -> Unit = {}

    /** Callback invoked after a transition edge is removed from this mapping. */
    var onEdgeRemoved: (TransitionEdge) -> Unit = {}

    /** Callback invoked after an elementary operation is performed on this mapping. */
    var onOperation: (MappingOperation) -> Unit = {}

    /** Callback invoked when instrumentation-aware code publishes a fact about this mapping. */
    var onObservation: (Observation) -> Unit = {}

    /**
     * Publishes a fact to the [onObservation] callback.
     *
     * This is the channel through which action code reports what it computed
     * and what the engine cannot observe on its own. Publishing a fact never
     * changes the mapping.
     *
     * @param observation the fact to publish
     */
    fun observe(observation: Observation) {
        onObservation(observation)
    }

    /** Returns (building if needed) the index of edges grouped by source state. */
    private fun sourceIndex(): Map<State, List<TransitionEdge>> {
        bySource?.let { return it }
        return edges.groupBy { it.source }.also { bySource = it }
    }

    /**
     * Adds a transition to the mapping.
     *
     * Adding a transition that is already present is a no-op: the
     * [onEdgeAdded] callback only fires on an actual insertion, so observers
     * never see a phantom mutation.
     */
    fun add(t: TransitionEdge) {
        if (!edges.add(t)) return
        invalidate()
        onEdgeAdded(t)
    }

    /**
     * Returns all transitions in the mapping.
     *
     * The set is live: it reflects the mapping as it is, and mutating it
     * mutates the mapping.
     */
    val transitions: Set<TransitionEdge> get() = edges

    /** Checks whether a transition with the given ID exists. */
    fun contains(id: Int): Boolean = edges.any { it.id == id }

    /** The number of transitions in the mapping. */
    val size: Int get() = edges.size

    /**
     * Returns the transitions leaving [state].
     *
     * This is the engine's hot path: the answer comes from a cached index
     * rather than from a scan of the whole mapping.
     */
    internal fun edgesFrom(state: State): List<TransitionEdge> = sourceIndex()[state] ?: emptyList()

    /**
     * A structural fingerprint of the edges of this mapping.
     *
     * The fingerprint describes *what* the mapping holds, not *which* edges it
     * holds: the identifier of an edge is bookkeeping and takes no part in it.
     * Two mappings holding the same edges therefore share a fingerprint, even
     * when they are copies of one another — which is what lets the engine
     * recognise a repeated configuration across a fork.
     *
     * The fingerprint is cached and recomputed only when the mapping changes.
     */
    internal fun structuralDigest(): Long {
        digest?.let { return it }
        val computed = computeDigest()
        digest = computed
        return computed
    }

    /** Computes the structural fingerprint of the edges. */
    private fun computeDigest(): Long {
        var result = 0L
        for (edge in edges) {
            result += mix(edge.hashCode().toLong())
        }
        return result
    }

    /**
     * Mixes [value] into a well-distributed 64-bit fingerprint.
     *
     * The mixing is the finalizer of SplitMix64: it spreads a 32-bit hash
     * over the whole 64-bit range, so that two different edge sets hardly
     * ever share a fingerprint.
     */
    private fun mix(value: Long): Long {
        var mixed = value + GOLDEN_RATIO
        mixed = (mixed xor (mixed ushr SHIFT_A)) * MIX_FACTOR_A
        mixed = (mixed xor (mixed ushr SHIFT_B)) * MIX_FACTOR_B
        return mixed xor (mixed ushr SHIFT_C)
    }

    /** Drops the cached index and fingerprint after a mutation. */
    private fun invalidate() {
        bySource = null
        digest = null
    }

    /**
     * Removes the transition with the given identifier from the mapping.
     *
     * One [onEdgeRemoved] event is fired per edge that leaves the mapping, so
     * the topology channel always balances the number of edges removed.
     */
    fun removeFromIdentifier(id: Int) {
        val removed = edges.filter { it.id == id }
        if (removed.isEmpty()) return
        removed.forEach { edges.remove(it) }
        invalidate()
        removed.forEach { onEdgeRemoved(it) }
    }

    /**
     * Removes every transition from this mapping, keeping the callbacks.
     *
     * This is a reset, not a mutation: no [onEdgeRemoved] event is fired,
     * because the edges were never observed as leaving one by one.
     */
    fun clear() {
        edges.clear()
        invalidate()
    }

    /**
     * Creates a copy of this mapping with copies of all transitions.
     *
     * The callbacks are carried over, so the copy keeps reporting to the same
     * observers. Copying the edges fires no event: they were duplicated, not
     * added.
     */
    fun copy(): TransitionMapping {
        val m = TransitionMapping()
        edges.forEach { m.add(it.copy()) }
        m.onEdgeAdded = onEdgeAdded
        m.onEdgeRemoved = onEdgeRemoved
        m.onOperation = onOperation
        m.onObservation = onObservation
        return m
    }

    /** Returns a human-readable representation of this mapping. */
    override fun toString(): String {
        val sb = StringBuilder("Set of transitions: {")
        if (edges.isNotEmpty()) {
            sb.append("\n")
            for (t in edges) {
                sb.append("  $t\n")
            }
        }
        sb.append("}")
        return sb.toString()
    }

    private companion object {
        /** The odd 64-bit constant of SplitMix64. */
        const val GOLDEN_RATIO = -0x61c8864680b583ebL

        /** The first multiplier of the SplitMix64 finalizer. */
        const val MIX_FACTOR_A = -0x40a7b892e31b1a47L

        /** The second multiplier of the SplitMix64 finalizer. */
        const val MIX_FACTOR_B = -0x6b2fb644ecceee15L

        /** The shift of the first round of the SplitMix64 finalizer. */
        const val SHIFT_A = 30

        /** The shift of the second round of the SplitMix64 finalizer. */
        const val SHIFT_B = 27

        /** The shift of the last round of the SplitMix64 finalizer. */
        const val SHIFT_C = 31
    }
}
