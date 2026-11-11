// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model.sets

import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine

/**
 * The set of submachines of the adaptive automaton.
 *
 * Manages a collection of [Submachine] instances that can be looked up by
 * name or by state.
 */
class SubmachinesSet {
    private val submachines = mutableSetOf<Submachine>()

    /**
     * Adds a submachine to the set.
     *
     * @param submachine the submachine to add
     */
    fun add(submachine: Submachine) {
        submachines.add(submachine)
    }

    /**
     * Returns all submachines in the set.
     *
     * The set is live: it reflects the set as it is, and mutating it mutates
     * the set of submachines.
     *
     * @return the live set of all submachines
     */
    fun getSubmachines(): Set<Submachine> = submachines

    /**
     * Replaces all submachines in the set with the given submachines.
     *
     * @param newSubmachines the new set of submachines
     */
    fun setSubmachines(newSubmachines: Set<Submachine>) {
        submachines.clear()
        submachines.addAll(newSubmachines)
    }

    /**
     * Returns the number of submachines in the set.
     *
     * @return the submachine count
     */
    fun size(): Int = submachines.size

    /**
     * Retrieves a submachine containing the given state.
     *
     * @param state the state to search for
     * @return the submachine containing the state
     * @throws IllegalStateException if no submachine contains the state
     */
    fun getFromState(state: State): Submachine {
        val result = submachines.firstOrNull { it.states.contains(state) }
        return result ?: error("No submachine contains state '$state'.")
    }

    /**
     * Retrieves a submachine by name.
     *
     * @param name the submachine name
     * @return the matching submachine
     * @throws IllegalStateException if no submachine with the given name exists
     */
    fun getFromName(name: String): Submachine {
        val result = submachines.firstOrNull { it.name == name }
        return result ?: error("No submachine with name '$name'.")
    }

    /**
     * Removes a submachine by name.
     *
     * @param name the submachine name
     * @throws IllegalStateException if no submachine with the given name exists
     */
    fun removeByName(name: String) {
        val result = submachines.firstOrNull { it.name == name }
        require(result != null) { "No submachine with name '$name'." }
        submachines.remove(result)
    }

    /**
     * Returns a copy of this set with copies of all submachines.
     *
     * @return a new [SubmachinesSet] with copies of all submachines
     */
    fun copy(): SubmachinesSet {
        val s = SubmachinesSet()
        submachines.forEach { s.add(it.copy()) }
        return s
    }

    /** Returns a human-readable representation of this submachine set. */
    override fun toString(): String {
        val sb = StringBuilder("Set of submachines: {")
        if (submachines.isNotEmpty()) {
            sb.append("\n")
            for (s in submachines) {
                sb.append("  $s\n")
            }
        }
        sb.append("}")
        return sb.toString()
    }
}
