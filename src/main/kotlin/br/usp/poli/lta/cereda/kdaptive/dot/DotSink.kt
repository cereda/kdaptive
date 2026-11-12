// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.dot

/**
 * The sink that receives the GraphViz snapshots of an automaton.
 *
 * The interface is a *functional* one: a consumer writes a lambda and the
 * compiler turns it into a sink, without the ceremony of an anonymous
 * object. It is also a named one: a consumer that wants to declare the sink
 * as a field, or to implement it in another language, has a type to write
 * down.
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
 * ```
 */
public fun interface DotSink {
    /**
     * Receives one snapshot of the topology.
     *
     * @param pathId the identifier of the path that changed the topology
     * @param dot the snapshot, as GraphViz DOT text
     */
    public fun accept(pathId: Int, dot: String)
}
