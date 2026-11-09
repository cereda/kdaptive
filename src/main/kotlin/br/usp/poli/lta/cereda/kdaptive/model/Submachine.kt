// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils

/**
 * Formal definition of a submachine.
 *
 * @param id the unique identifier for this submachine
 * @param name the name of this submachine
 * @param states the set of all states in this submachine
 * @param initialState the initial state of this submachine
 * @param acceptingStates the set of accepting states
 */
data class Submachine(
    /** The unique identifier for this submachine. */
    val id: Int = IdentifierUtils.nextSubmachineId(),
    /** The name of this submachine. */
    val name: String,
    /** The set of all states in this submachine. */
    val states: Set<State>,
    /** The initial state of this submachine. */
    val initialState: State,
    /** The set of accepting states. */
    val acceptingStates: Set<State>,
) {
    init {
        require(
            states.contains(initialState) && states.containsAll(acceptingStates),
        ) {
            "Undefined states in submachine '$name'."
        }
    }

    /** Returns a human-readable representation of this submachine. */
    override fun toString(): String = "Submachine(id=$id, name=$name, states=[${states.joinToString(", ")}], " +
        "initial=$initialState, accepting=[${acceptingStates.joinToString(", ")}])"
}
