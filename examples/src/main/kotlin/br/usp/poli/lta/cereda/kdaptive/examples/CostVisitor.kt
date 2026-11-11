// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.ExecutionVisitor
import br.usp.poli.lta.cereda.kdaptive.model.MappingOperation
import br.usp.poli.lta.cereda.kdaptive.model.Observation
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.TransitionPattern
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
 * transition mapping. It was re-derived for version 2.1.0, when the library
 * learned to report the *size of every filter domain*:
 *
 * - **ω(x) = x / 2** is the cost of scanning a collection of x elements;
 * - the **product** of a pattern is the number of edge shapes its filters may
 *   select: the product of the domain sizes of its six filters. A filter that
 *   does not narrow the search — a wildcard — has an unbounded domain, and an
 *   unbounded domain contributes zero to the product: the number of
 *   combinations it admits is not a finite number, so the model does not
 *   price it;
 * - a **query** scans the mapping once: ω(size);
 * - a **removal** scans the mapping once per shape it may touch, and then
 *   takes those shapes out of the mapping: product·ω(size) − product;
 * - an **addition** scans the mapping once per shape it may touch, and then
 *   inserts those shapes into the mapping: product·ω(size) + product.
 *
 * The size is the size of the mapping when the operation ran. A pattern whose
 * filters are all finite — concrete states, concrete symbols, concrete
 * actions — is therefore priced by the number of shapes it may touch, while a
 * pattern carrying a wildcard is priced by the scan alone.
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

    /**
     * Prices a single operation.
     *
     * The price follows the model described in the KDoc of this class: the
     * scan costs ω(size), and the shapes the pattern may touch multiply it.
     *
     * @param operation the operation to price
     * @return the cost of the operation
     */
    private fun price(operation: MappingOperation): Double {
        val size = operation.mappingSizeBefore.toDouble()
        val shapes = product(operation.pattern)
        return when (operation) {
            is MappingOperation.Query -> omega(size)
            is MappingOperation.Remove -> shapes * omega(size) - shapes
            is MappingOperation.Add -> shapes * omega(size) + shapes
        }
    }

    /** The cost of scanning a collection of [size] elements. */
    private fun omega(size: Double): Double = size / 2.0

    /**
     * The number of edge shapes the filters of [pattern] may select.
     *
     * The number is the product of the domain sizes of the six filters of the
     * pattern. An unbounded filter contributes zero: the number of
     * combinations it admits is not a finite number.
     *
     * @param pattern the pattern to measure
     * @return the number of shapes the pattern may select
     */
    private fun product(pattern: TransitionPattern): Int {
        val sizes =
            listOf(
                pattern.sourceDomainSize,
                pattern.symbolDomainSize,
                pattern.targetDomainSize,
                pattern.submachineDomainSize,
                pattern.priorDomainSize,
                pattern.postDomainSize,
            )
        return sizes.fold(1) { shapes, size -> shapes * (size ?: 0) }
    }
}
