// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.dot

import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.sets.SubmachinesSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping

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
class DotExporter {

    /**
     * Renders [transitions] and [submachines] as a DOT document.
     *
     * The rendering is deterministic: the same topology always yields the same
     * document, which makes consecutive snapshots diffable.
     *
     * @param transitions the transition mapping to render
     * @param submachines the submachines defining clusters and accepting states
     * @return a DOT-formatted string
     */
    fun toDot(transitions: TransitionMapping, submachines: SubmachinesSet): String {
        val allTransitions = transitions.getTransitions().toList()
        val allSubmachines = submachines.getSubmachines().sortedBy { it.name }
        val stateIndex = indexStates(allTransitions, allSubmachines)
        val ownerOf = ownersOf(allSubmachines)
        val (internalEdges, externalEdges) = allTransitions.partition { edge -> isInternal(edge, ownerOf) }

        val sb = StringBuilder()
        sb.append("digraph Automaton {\n")
        sb.append("  rankdir=LR;\n")
        sb.append("  node [shape=circle, fontname=\"Courier\"];\n")
        sb.append("  edge [fontname=\"Courier\"];\n")
        sb.append('\n')

        for (sm in allSubmachines) {
            appendCluster(sb, sm, stateIndex, ownerOf, internalEdges)
        }

        for (edge in externalEdges.sortedBy { it.id }) {
            sb.append("  ${edgeStatement(edge, stateIndex)}\n")
        }

        sb.append("}\n")
        return sb.toString()
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

    /** Tells whether [edge] connects two states of the same submachine. */
    private fun isInternal(edge: TransitionEdge, ownerOf: Map<State, String>): Boolean {
        val owner = ownerOf[edge.source]
        return owner != null && owner == ownerOf[edge.target]
    }

    /** Appends the cluster of [sm], its states and the edges internal to it. */
    private fun appendCluster(
        sb: StringBuilder,
        sm: Submachine,
        stateIndex: Map<State, Int>,
        ownerOf: Map<State, String>,
        internalEdges: List<TransitionEdge>,
    ) {
        sb.append("  subgraph cluster_${escapeId(sm.name)} {\n")
        sb.append("    label = \"${escapeLabel(sm.name)}\";\n")
        for (state in sm.states.sortedBy { stateIndex.getValue(it) }) {
            val shape = if (sm.acceptingStates.contains(state)) "doublecircle" else "circle"
            sb.append("    ${nodeId(state, stateIndex)} [label=\"${escapeLabel(state.toString())}\", shape=$shape];\n")
        }
        val ownEdges = internalEdges.filter { ownerOf[it.source] == sm.name }.sortedBy { it.id }
        for (edge in ownEdges) {
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
            edge.prior?.let { append(" | prior=${actionLabel(it)}") }
            edge.post?.let { append(" | post=${actionLabel(it)}") }
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
     * underscore-prefixed four-digit code point. Since an underscore in the
     * output always introduces such an escape, the original identifier can be
     * recovered, so `"a-b"` and `"a_b"` remain two different clusters.
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
        const val UNDERSCORE_ESCAPE = "_005f"

        /** The radix the code points are written in. */
        const val HEX_RADIX = 16

        /** The number of digits a code point is padded to. */
        const val CODE_POINT_WIDTH = 4
    }

    /** Escapes the characters that would otherwise break a quoted DOT label. */
    private fun escapeLabel(label: String): String = label
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
