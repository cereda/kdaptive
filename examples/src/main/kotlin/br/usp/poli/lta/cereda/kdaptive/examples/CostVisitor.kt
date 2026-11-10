// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.ExecutionVisitor
import br.usp.poli.lta.cereda.kdaptive.model.MappingOperation
import br.usp.poli.lta.cereda.kdaptive.model.Observation
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.sets.SubmachinesSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping

/**
 * A cost model built entirely on top of the instrumentation facility.
 *
 * This is what a consumer looks like: the library exposes the facts, the
 * consumer decides what they are worth. Nothing here is known to the engine,
 * and changing this cost model requires no change to the library at all.
 *
 * The model prices the work the elementary actions performed over the
 * transition mapping:
 *
 * - a **query** scans the whole mapping, so it costs the size of the mapping;
 * - a **removal** scans the whole mapping and then deletes the edges it
 *   matched, so it costs the size of the mapping plus the edges removed;
 * - an **addition** inserts a single edge, so it costs one.
 *
 * The facts that action code publishes ([Observation]) are counted but not
 * priced: the engine cannot know what they are worth, and neither can this
 * consumer unless the action says so.
 */
class CostVisitor : ExecutionVisitor {

    /** The operations performed, with the path that performed them. */
    private val performed = mutableListOf<Pair<Int, MappingOperation>>()

    /** The facts published by action code, with the path that published them. */
    private val published = mutableListOf<Pair<Int, Observation>>()

    /** The edges that entered the mapping, counted on the topology channel. */
    private val entered = mutableListOf<TransitionEdge>()

    /** The edges that left the mapping, counted on the topology channel. */
    private val left = mutableListOf<TransitionEdge>()

    override fun onMappingOperation(pathId: Int, operation: MappingOperation) {
        performed.add(pathId to operation)
    }

    override fun onObservation(pathId: Int, observation: Observation) {
        published.add(pathId to observation)
    }

    override fun onEdgeAdded(
        pathId: Int,
        edge: TransitionEdge,
        transitions: TransitionMapping,
        submachines: SubmachinesSet,
    ) {
        entered.add(edge)
    }

    override fun onEdgeRemoved(
        pathId: Int,
        edge: TransitionEdge,
        transitions: TransitionMapping,
        submachines: SubmachinesSet,
    ) {
        left.add(edge)
    }

    /** The number of queries performed. */
    val queries: Int get() = performed.count { it.second is MappingOperation.Query }

    /** The number of removals performed. */
    val removals: Int get() = performed.count { it.second is MappingOperation.Remove }

    /** The number of additions performed. */
    val additions: Int get() = performed.count { it.second is MappingOperation.Add }

    /** The number of facts published by action code. */
    val facts: Int get() = published.size

    /** The number of edges that entered the mapping. */
    val edgesAdded: Int get() = entered.size

    /** The number of edges that left the mapping. */
    val edgesRemoved: Int get() = left.size

    /** The paths that performed work. */
    val paths: Int get() = (performed.map { it.first } + published.map { it.first }).distinct().size

    /** The total cost of the operations performed. */
    val cost: Double get() = performed.sumOf { price(it.second) }

    /** The cost of the operations performed, broken down by path. */
    fun costPerPath(): Map<Int, Double> {
        val costs = mutableMapOf<Int, Double>()
        for ((pathId, operation) in performed) {
            costs.merge(pathId, price(operation), Double::plus)
        }
        return costs.toSortedMap()
    }

    /** Returns a human-readable report of the work performed. */
    fun report(): String = buildString {
        appendLine("Operations performed:")
        appendLine("  queries:    $queries")
        appendLine("  removals:   $removals")
        appendLine("  additions:  $additions")
        appendLine("Topology changes:")
        appendLine("  edges added:   $edgesAdded")
        appendLine("  edges removed: $edgesRemoved")
        appendLine("Facts published:  $facts")
        appendLine("Paths involved:   $paths")
        appendLine("Total cost:       $cost")
        val perPath = costPerPath()
        if (perPath.isNotEmpty()) {
            appendLine("Cost per path:")
            perPath.forEach { (pathId, value) -> appendLine("  path $pathId: $value") }
        }
    }

    /** Prices a single operation. */
    private fun price(operation: MappingOperation): Double = when (operation) {
        is MappingOperation.Query -> operation.mappingSizeBefore.toDouble()
        is MappingOperation.Remove -> operation.mappingSizeBefore.toDouble() + operation.edges.size
        is MappingOperation.Add -> 1.0
    }
}
