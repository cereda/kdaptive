// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.dot

import br.usp.poli.lta.cereda.kdaptive.execution.ExecutionVisitor
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge

/**
 * An [ExecutionVisitor] that emits a GraphViz DOT snapshot of the automaton
 * topology on every edge addition or removal.
 *
 * Use this to visualize how an adaptive automaton mutates during recognition.
 * Each snapshot is sent to [sink], which can write to a `.dot` file, pipe to
 * the `dot` binary, or buffer frames for an animation.
 *
 * The sink receives the identifier of the path that performed the mutation
 * together with the snapshot, because forked paths own independent copies of
 * the topology: without the path identifier, the frames of different paths
 * cannot be told apart.
 *
 * Example:
 * ```
 * val frames = mutableListOf<Pair<Int, String>>()
 * val visitor = GraphVizVisitor { pathId, dot -> frames.add(pathId to dot) }
 * automaton.visitors.add(visitor)
 * automaton.recognize(convert("abc"))
 * // frames now contains one DOT document per topology mutation
 * ```
 *
 * @param sink the callback receiving the path identifier and the DOT snapshot
 */
public class GraphVizVisitor(
    /** Sink receiving the path identifier and each DOT snapshot. */
    private val sink: (pathId: Int, dot: String) -> Unit,
) : ExecutionVisitor {

    /** The stateless exporter reused across snapshots. */
    private val exporter = DotExporter()

    override fun onEdgeAdded(pathId: Int, edge: TransitionEdge, topology: Topology) {
        sink(pathId, exporter.toDot(topology))
    }

    override fun onEdgeRemoved(pathId: Int, edge: TransitionEdge, topology: Topology) {
        sink(pathId, exporter.toDot(topology))
    }
}
