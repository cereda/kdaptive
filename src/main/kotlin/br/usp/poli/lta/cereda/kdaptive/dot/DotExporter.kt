// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.dot

import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge

/**
 * Exports an automaton topology to GraphViz DOT format.
 *
 * The exporter produces plain DOT text; it does not depend on the `dot`
 * binary or any GraphViz library. Consumers can write the string to a `.dot`
 * file and render it with `dot -Tpng`, or pipe it to any GraphViz-compatible
 * tool.
 *
 * Each submachine is rendered as a GraphViz cluster. States are nodes; edges
 * are labeled with the consumed symbol (or ε / call / return) and optional
 * prior/post action references.
 *
 * Edges whose endpoints belong to the same submachine are emitted inside that
 * submachine's cluster, so GraphViz draws them within the cluster box. Edges
 * crossing a submachine boundary are emitted at the top level.
 */
public class DotExporter {

    /**
     * Renders [topology] as a DOT document.
     *
     * The rendering is deterministic: the same topology always yields the same
     * document, which makes consecutive snapshots diffable.
     *
     * @param topology the topology to render
     * @return a DOT-formatted string
     */
    public fun toDot(topology: Topology): String {
        val allTransitions = topology.edges.toList()
        val allSubmachines = topology.submachines.sortedBy { it.name }
        val stateIndex = indexStates(allTransitions, allSubmachines)
        val ownerOf = ownersOf(allSubmachines)
        val (internalEdges, externalEdges) = partition(allTransitions, ownerOf)

        val sb = StringBuilder()
        sb.append("digraph Automaton {\n")
        sb.append("  rankdir=LR;\n")
        sb.append("  node [shape=circle, fontname=\"Courier\"];\n")
        sb.append("  edge [fontname=\"Courier\"];\n")
        sb.append('\n')

        for (sm in allSubmachines) {
            appendCluster(sb, sm, stateIndex, internalEdges[sm.name].orEmpty())
        }

        for (edge in externalEdges.sortedBy { it.id }) {
            sb.append("  ${edgeStatement(edge, stateIndex)}\n")
        }

        sb.append("}\n")
        return sb.toString()
    }

    /**
     * Splits the edges into the ones internal to a submachine and the others.
     *
     * The edges are partitioned once, and the internal ones are grouped by
     * the submachine that owns them. Grouping them afterwards would mean
     * scanning the whole edge list once per submachine, and the cost of the
     * export would grow with the square of the topology.
     *
     * @param allTransitions the edges of the topology
     * @param ownerOf the owner of each state
     * @return the internal edges by owner, and the edges crossing a boundary
     */
    private fun partition(
        allTransitions: List<TransitionEdge>,
        ownerOf: Map<State, String>,
    ): Pair<Map<String, List<TransitionEdge>>, List<TransitionEdge>> {
        val internal = LinkedHashMap<String, MutableList<TransitionEdge>>()
        val external = mutableListOf<TransitionEdge>()
        for (edge in allTransitions) {
            val owner = ownerOf[edge.source]
            if (owner != null && owner == ownerOf[edge.target]) {
                internal.getOrPut(owner) { mutableListOf() }.add(edge)
            } else {
                external.add(edge)
            }
        }
        return internal to external
    }

    /**
     * Assigns a stable numeric index to every state referenced by a transition
     * or belonging to a submachine.
     *
     * The index drives both the node identifier and the ordering, so states
     * keep their natural order past ten nodes instead of being sorted
     * lexicographically.
     */
    private fun indexStates(allTransitions: List<TransitionEdge>, allSubmachines: List<Submachine>): Map<State, Int> =
        buildMap {
            var counter = 0
            fun indexFor(state: State): Int = getOrPut(state) { counter++ }
            for (edge in allTransitions) {
                indexFor(edge.source)
                indexFor(edge.target)
            }
            for (sm in allSubmachines) {
                for (state in sm.states) {
                    indexFor(state)
                }
            }
        }

    /** Returns the name of the submachine owning each state. */
    private fun ownersOf(allSubmachines: List<Submachine>): Map<State, String> = buildMap {
        for (sm in allSubmachines) {
            for (state in sm.states) {
                putIfAbsent(state, sm.name)
            }
        }
    }

    /** Appends the cluster of [sm], its states and the edges internal to it. */
    private fun appendCluster(
        sb: StringBuilder,
        sm: Submachine,
        stateIndex: Map<State, Int>,
        ownEdges: List<TransitionEdge>,
    ) {
        sb.append("  subgraph cluster_${escapeId(sm.name)} {\n")
        sb.append("    label = \"${escapeLabel(sm.name)}\";\n")
        for (state in sm.states.sortedBy { stateIndex.getValue(it) }) {
            val shape = if (sm.acceptingStates.contains(state)) "doublecircle" else "circle"
            sb.append("    ${nodeId(state, stateIndex)} [label=\"${escapeLabel(state.toString())}\", shape=$shape];\n")
        }
        for (edge in ownEdges.sortedBy { it.id }) {
            sb.append("    ${edgeStatement(edge, stateIndex)}\n")
        }
        sb.append("  }\n")
        sb.append('\n')
    }

    /** Returns the DOT statement drawing [edge], without indentation. */
    private fun edgeStatement(edge: TransitionEdge, stateIndex: Map<State, Int>): String {
        val source = nodeId(edge.source, stateIndex)
        val target = nodeId(edge.target, stateIndex)
        return "$source -> $target [label=\"${escapeLabel(edgeLabel(edge))}\"];"
    }

    /** Returns the DOT node identifier of [state]. */
    private fun nodeId(state: State, stateIndex: Map<State, Int>): String = "n${stateIndex.getValue(state)}"

    private fun edgeLabel(edge: TransitionEdge): String {
        val base = when (val t = edge.transition) {
            is Transition.SymbolTransition -> t.symbol.toString()

            // The epsilon label is the Greek small letter epsilon.
            is Transition.Epsilon -> "\u03B5"

            is Transition.Call -> "call ${t.submachine}"

            is Transition.Return -> "return"
        }
        val actions = buildString {
            (edge.prior as? ActionRef.Bound)?.let { append(" | prior=${actionLabel(it)}") }
            (edge.post as? ActionRef.Bound)?.let { append(" | post=${actionLabel(it)}") }
        }
        return base + actions
    }

    private fun actionLabel(ref: ActionRef.Bound): String {
        val name = ref.name
        return if (ref.args.isEmpty()) name else "$name(${ref.args.joinToString(",")})"
    }

    /**
     * Escapes [id] into a DOT identifier.
     *
     * The escape is injective: two different identifiers never produce the
     * same DOT identifier. Letters and digits are kept, an underscore is
     * written as `_005f`, and any other character is written as its
     * underscore-prefixed four-digit UTF-16 code unit. Since an underscore in
     * the output always introduces such an escape, the original identifier can
     * be recovered, so `"a-b"` and `"a_b"` remain two different clusters.
     */
    private fun escapeId(id: String): String = buildString {
        for (character in id) {
            when {
                character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' -> append(character)
                character == '_' -> append(UNDERSCORE_ESCAPE)
                else -> append("_").append(character.code.toString(HEX_RADIX).padStart(CODE_POINT_WIDTH, '0'))
            }
        }
    }

    private companion object {
        /** The escape written for an underscore. */
        private const val UNDERSCORE_ESCAPE = "_005f"

        /** The radix the code units are written in. */
        private const val HEX_RADIX = 16

        /** The number of digits a code unit is padded to. */
        private const val CODE_POINT_WIDTH = 4
    }

    /** Escapes the characters that would otherwise break a quoted DOT label. */
    private fun escapeLabel(label: String): String = label
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
