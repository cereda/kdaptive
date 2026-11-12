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
 *
 * The mapping is also the unit of *isolation*: a forked path owns a mapping of
 * its own, and a mutation on one path never reaches its siblings. Copies are
 * therefore made lazily: a copy shares the edges of the original until one of
 * them is mutated, and only then does it take a private set of its own.
 */
public class TransitionMapping {
    /** The edges of the mapping. */
    private var edges: MutableSet<TransitionEdge> = mutableSetOf()

    /**
     * Whether this mapping may mutate its edges and its indexes in place.
     *
     * A mapping that shares its edges with a copy gives up the ownership of
     * them: the first mutation takes a private copy, and the copy keeps the
     * edges it was made with.
     */
    private var ownsState: Boolean = true

    /**
     * Index of edges grouped by source state; null when it has not been built.
     *
     * The buckets are immutable lists, so a mapping may share them with a
     * copy: a mutation replaces the bucket it touches instead of editing the
     * list in place.
     */
    private var bySource: MutableMap<State, List<TransitionEdge>>? = null

    /** Index of edges by identifier; null when it has not been built. */
    private var byId: MutableMap<Int, TransitionEdge>? = null

    /** Rolling structural fingerprint; null when it has not been computed. */
    private var digest: Long? = null

    /** Callback invoked after a transition edge is added to this mapping. */
    public var onEdgeAdded: (TransitionEdge) -> Unit = {}

    /** Callback invoked after a transition edge is removed from this mapping. */
    public var onEdgeRemoved: (TransitionEdge) -> Unit = {}

    /** Callback invoked after an elementary operation is performed on this mapping. */
    public var onOperation: (MappingOperation) -> Unit = {}

    /** Callback invoked when instrumentation-aware code publishes a fact about this mapping. */
    public var onObservation: (Observation) -> Unit = {}

    /**
     * Publishes a fact to the [onObservation] callback.
     *
     * This is the channel through which action code reports what it computed
     * and what the engine cannot observe on its own. Publishing a fact never
     * changes the mapping.
     *
     * @param observation the fact to publish
     */
    public fun observe(observation: Observation) {
        onObservation(observation)
    }

    /** Returns (building if needed) the index of edges grouped by source state. */
    private fun sourceIndex(): MutableMap<State, List<TransitionEdge>> {
        bySource?.let { return it }
        val index = edges.groupBy { it.source }.toMutableMap()
        bySource = index
        return index
    }

    /** Returns (building if needed) the index of edges by identifier. */
    private fun idIndex(): MutableMap<Int, TransitionEdge> {
        byId?.let { return it }
        val index = LinkedHashMap<Int, TransitionEdge>(edges.size)
        edges.forEach { index[it.id] = it }
        byId = index
        return index
    }

    /**
     * Takes a private copy of the edges and of the indexes, if they are shared.
     *
     * A shared set of edges is never mutated in place: the first mutation
     * takes a private copy, so that the copy keeps the edges it was made with.
     * The indexes follow the edges: they are derived from them, and a mapping
     * that owns its edges owns its indexes too.
     */
    private fun ensureOwnership() {
        if (ownsState) return
        edges = edges.toMutableSet()
        bySource = bySource?.toMutableMap()
        byId = byId?.toMutableMap()
        ownsState = true
    }

    /**
     * Adds a transition to the mapping.
     *
     * Adding a transition that is already present is a no-op: the
     * [onEdgeAdded] callback only fires on an actual insertion, so observers
     * never see a phantom mutation.
     *
     * The indexes are updated in place rather than dropped: a path whose
     * adaptive action adds an edge on every step therefore pays a constant
     * price per step, instead of rebuilding the whole index every time.
     */
    public fun add(t: TransitionEdge) {
        if (edges.contains(t)) return
        ensureOwnership()
        edges.add(t)
        byId?.put(t.id, t)
        bySource?.let { index ->
            index[t.source] = (index[t.source] ?: emptyList()) + t
        }
        digest = digest?.plus(mix(t.hashCode().toLong()))
        onEdgeAdded(t)
    }

    /**
     * Returns all transitions in the mapping.
     *
     * The collection is a live view: it reflects the mapping as it is. Mutate
     * the mapping through [add], [removeFromIdentifier] and [clear] instead:
     * mutating the view directly bypasses the bookkeeping the mapping keeps,
     * and it may disturb a copy that shares the edges.
     */
    public val transitions: Set<TransitionEdge> get() = edges

    /** Checks whether a transition with the given ID exists. */
    public fun contains(id: Int): Boolean = idIndex()[id] != null

    /** The number of transitions in the mapping. */
    public val size: Int get() = edges.size

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

    /**
     * Removes the transition with the given identifier from the mapping.
     *
     * One [onEdgeRemoved] event is fired per edge that leaves the mapping, so
     * the topology channel always balances the number of edges removed.
     *
     * The indexes are updated in place rather than dropped, so that removing
     * a batch of edges does not rebuild the index once per edge.
     */
    public fun removeFromIdentifier(id: Int) {
        val removed = idIndex()[id] ?: return
        ensureOwnership()
        edges.remove(removed)
        byId?.remove(id)
        bySource?.let { index ->
            index[removed.source] = index[removed.source]?.filter { it !== removed } ?: emptyList()
        }
        digest = digest?.minus(mix(removed.hashCode().toLong()))
        onEdgeRemoved(removed)
    }

    /**
     * Removes every transition from this mapping, keeping the callbacks.
     *
     * This is a reset, not a mutation: no [onEdgeRemoved] event is fired,
     * because the edges were never observed as leaving one by one.
     */
    public fun clear() {
        ensureOwnership()
        edges.clear()
        byId?.clear()
        bySource?.clear()
        digest = digest?.let { 0L }
    }

    /**
     * Creates a copy of this mapping with copies of all transitions.
     *
     * The callbacks are carried over, so the copy keeps reporting to the same
     * observers. Copying the edges fires no event: they were duplicated, not
     * added.
     *
     * Every copy of an edge receives a fresh identifier, so the copy holds
     * edges of its own. The engine does not use this method: it uses
     * [isolatedCopy], which is cheaper and keeps the identifiers.
     */
    public fun copy(): TransitionMapping {
        val m = TransitionMapping()
        edges.forEach { m.add(it.copy()) }
        m.onEdgeAdded = onEdgeAdded
        m.onEdgeRemoved = onEdgeRemoved
        m.onOperation = onOperation
        m.onObservation = onObservation
        return m
    }

    /**
     * Creates a copy of this mapping that shares the edges with it.
     *
     * The copy is isolated from the original in the only way that matters: a
     * mutation on either side is invisible to the other. The isolation is
     * lazy — the two mappings hold the same edges until one of them is
     * mutated, and the copy then takes a private set of its own.
     *
     * The edges themselves are shared, not duplicated. An edge is immutable,
     * and its identifier is unique across the whole process, so the copy can
     * refer to an edge by its identifier without ambiguity.
     *
     * The source gives up the ownership of what it holds: from now on, a
     * mutation on either side copies it first. The source therefore keeps the
     * edges it has, and only its *bookkeeping* changes — the edges it holds
     * and the events it fires are the same before and after the copy.
     *
     * @return a copy of this mapping, sharing its edges
     */
    internal fun isolatedCopy(): TransitionMapping {
        // The source gives up the ownership of what it holds: from now on, a
        // mutation on either side copies it first. The copy is therefore
        // isolated from the original by construction, and the two-step
        // "share, then copy" protocol cannot be misused.
        ownsState = false
        val m = TransitionMapping()
        m.edges = edges
        m.ownsState = false
        m.bySource = bySource
        m.byId = byId
        m.digest = digest
        m.onEdgeAdded = onEdgeAdded
        m.onEdgeRemoved = onEdgeRemoved
        m.onOperation = onOperation
        m.onObservation = onObservation
        return m
    }

    /** Returns a human-readable representation of this mapping. */
    override fun toString(): String {
        val sb = StringBuilder("Transitions: {")
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
