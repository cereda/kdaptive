// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * An operation performed on the transition mapping by an elementary action.
 *
 * The hierarchy is sealed and symmetric: a query, a removal and an addition
 * are all operations over the mapping, and each one is reported exactly once,
 * after it has completed. The library reports the operation and its outcome;
 * it never interprets them as a cost.
 *
 * The operation channel is distinct from the topology channel exposed by
 * [br.usp.poli.lta.cereda.kdaptive.execution.ExecutionVisitor.onEdgeAdded] and
 * [br.usp.poli.lta.cereda.kdaptive.execution.ExecutionVisitor.onEdgeRemoved]:
 *
 * - the topology channel reports *what changed*, once per edge that entered or
 *   left the mapping;
 * - the operation channel reports *what was attempted*, once per elementary
 *   operation.
 *
 * The two answer different questions. A removal of three edges produces one
 * [Remove] operation and three topology events; count removals on the
 * operation channel and edges removed on the topology channel, but do not sum
 * them.
 */
public sealed interface MappingOperation {

    /** The pattern the operation matched against the mapping. */
    public val pattern: TransitionPattern

    /** The edges the operation concerned. */
    public val edges: List<TransitionEdge>

    /** The number of edges in the mapping before the operation. */
    public val mappingSizeBefore: Int

    /** The number of edges in the mapping after the operation. */
    public val mappingSizeAfter: Int

    /**
     * A read-only query over the mapping.
     *
     * A query never changes the topology, so it produces no topology event:
     * this is the only way to observe a read.
     */
    public data class Query(
        /** The pattern that was matched against the mapping. */
        override val pattern: TransitionPattern,
        /** The edges the pattern selected. */
        override val edges: List<TransitionEdge>,
        /** The number of edges in the mapping when the query ran. */
        override val mappingSizeBefore: Int,
    ) : MappingOperation {
        /** The number of edges in the mapping when the query ran; a query changes nothing. */
        override val mappingSizeAfter: Int get() = mappingSizeBefore
    }

    /**
     * A removal of the edges matching a pattern.
     *
     * The removal is reported after the edges have left the mapping, so
     * [mappingSizeAfter] is observed rather than predicted. A removal that
     * matched nothing is still reported: it cost a full scan of the mapping
     * even though it changed nothing.
     */
    public data class Remove(
        /** The pattern that was matched against the mapping. */
        override val pattern: TransitionPattern,
        /** The edges the pattern selected, and which were removed. */
        override val edges: List<TransitionEdge>,
        /** The number of edges in the mapping before the removal. */
        override val mappingSizeBefore: Int,
        /** The number of edges in the mapping after the removal. */
        override val mappingSizeAfter: Int,
    ) : MappingOperation

    /**
     * An addition of a single edge to the mapping.
     *
     * The addition is reported after the edge has entered the mapping, so
     * [mappingSizeAfter] is observed rather than predicted.
     */
    public data class Add(
        /** The pattern describing the transition that was added. */
        override val pattern: TransitionPattern,
        /** The edge that was added. */
        val edge: TransitionEdge,
        /** The number of edges in the mapping before the addition. */
        override val mappingSizeBefore: Int,
        /** The number of edges in the mapping after the addition. */
        override val mappingSizeAfter: Int,
    ) : MappingOperation {
        /** The edge that was added. */
        override val edges: List<TransitionEdge> get() = listOf(edge)
    }
}
