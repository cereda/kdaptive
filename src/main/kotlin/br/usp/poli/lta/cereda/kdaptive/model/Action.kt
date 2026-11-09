// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils

/**
 * Handler executed when an adaptive action fires: it receives the transition
 * mapping (which may be mutated by elementary actions), the current transition
 * as reference, and the action parameters.
 */
typealias ActionHandler = (transitions: TransitionMapping, transition: Transition, parameters: List<Any>) -> Unit

/** An action of the adaptive automaton, executed when a transition references it by name. */
data class Action(
    /** The unique identifier for this action. */
    val id: Int = IdentifierUtils.nextActionId(),
    /** The name of this action. */
    val name: String,
    /** The handler function executed when this action fires. */
    val handler: ActionHandler,
) {
    /** Returns a human-readable representation of this action. */
    override fun toString(): String = "Action(id=$id, name=$name)"
}

/** A reference to an action with optional arguments. */
data class ActionRef(
    /** The name of the action. */
    val name: String?,
    /** The arguments passed to the action. */
    val args: List<Any> = emptyList(),
)
