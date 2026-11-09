// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils

/**
 * An edge in the transition mapping: a [Transition] plus its source/target
 * states and optional prior/post actions.
 *
 * Actions are decoupled from the transition itself: they are metadata that
 * the execution engine applies before/after traversing the edge.
 */
data class TransitionEdge(
    /** The source state of this edge. */
    val source: State,
    /** The target state of this edge. */
    val target: State,
    /** The transition represented by this edge. */
    val transition: Transition,
    /** The prior action reference executed before traversing this edge, if any. */
    val prior: ActionRef? = null,
    /** The post action reference executed after traversing this edge, if any. */
    val post: ActionRef? = null,
    /** The unique identifier of this edge. */
    val id: Int = IdentifierUtils.nextTransitionId(),
) {
    /** Returns a human-readable representation of this edge, rendered as a graph arrow. */
    override fun toString(): String {
        val arrow = when (val t = transition) {
            is Transition.SymbolTransition -> " -[${t.symbol}]-> "
            is Transition.Epsilon -> " --> "
            is Transition.Call -> " -[call ${t.submachine}]-> "
            is Transition.Return -> " -[return]-> "
        }
        return buildString {
            append("TransitionEdge(id=$id, $source$arrow$target")
            prior?.let { append(", prior=${it.render()}") }
            post?.let { append(", post=${it.render()}") }
            append(")")
        }
    }

    private fun ActionRef.render(): String {
        val name = name ?: "unnamed"
        return if (args.isEmpty()) name else "$name(${args.joinToString(", ")})"
    }

    /** True if this edge has a prior action with the given name. */
    fun hasPriorAction(name: String): Boolean = prior?.name == name

    /** True if this edge has a post action with the given name. */
    fun hasPostAction(name: String): Boolean = post?.name == name
}
