// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model.sets

import br.usp.poli.lta.cereda.kdaptive.model.Action

/**
 * The set of actions of the adaptive automaton.
 *
 * Manages a collection of [Action] instances that can be looked up by name
 * and executed during recognition.
 */
class ActionsSet {
    private val actions = mutableSetOf<Action>()

    /**
     * Adds an action to the set.
     *
     * An action is identified by its name: adding an action whose name is
     * already taken replaces the previous one, so the set never holds two
     * actions under the same name. This mirrors the builder DSL, which
     * rejects a duplicate name outright.
     *
     * @param action the action to add
     */
    fun add(action: Action) {
        actions.removeIf { it.name == action.name }
        actions.add(action)
    }

    /**
     * Returns all actions in the set.
     *
     * The set is live: it reflects the set as it is, and mutating it mutates
     * the set of actions.
     *
     * @return the live set of all actions
     */
    fun getActions(): Set<Action> = actions

    /**
     * Replaces all actions in the set with the given actions.
     *
     * @param newActions the new set of actions
     */
    fun setActions(newActions: Set<Action>) {
        actions.clear()
        actions.addAll(newActions)
    }

    /**
     * Returns the number of actions in the set.
     *
     * @return the action count
     */
    fun size(): Int = actions.size

    /**
     * Retrieves an action by name.
     *
     * @param name the action name
     * @return the matching action
     * @throws IllegalStateException if no action with the given name exists
     */
    fun fromName(name: String): Action {
        val result = actions.firstOrNull { it.name == name }
        return result ?: error("No action with name '$name'.")
    }

    /**
     * Removes an action by name.
     *
     * @param name the action name
     * @throws IllegalStateException if no action with the given name exists
     */
    fun removeByName(name: String) {
        val result = actions.firstOrNull { it.name == name }
        require(result != null) { "No action with name '$name'." }
        actions.remove(result)
    }

    /**
     * Returns a shallow copy of this action set.
     *
     * @return a new [ActionsSet] with the same actions
     */
    fun copy(): ActionsSet {
        val c = ActionsSet()
        c.actions.addAll(actions)
        return c
    }

    /** Returns a human-readable representation of this action set. */
    override fun toString(): String {
        val sb = StringBuilder("Set of actions: {")
        if (actions.isNotEmpty()) {
            sb.append("\n")
            for (a in actions) {
                sb.append("  $a\n")
            }
        }
        sb.append("}")
        return sb.toString()
    }
}
