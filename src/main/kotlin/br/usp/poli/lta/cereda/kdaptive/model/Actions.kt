// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * The actions of the adaptive automaton.
 *
 * The set is itself a collection: an automaton's actions are reached by
 * iterating over them, and their number is asked of them directly. There is
 * no second, more official way of reaching them.
 *
 * Manages a collection of [Action] instances that can be looked up by name
 * and executed during recognition.
 */
class Actions : Iterable<Action> {
    private val entries = mutableSetOf<Action>()

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
        entries.removeIf { it.name == action.name }
        entries.add(action)
    }

    /** Returns the actions of the automaton. */
    override fun iterator(): Iterator<Action> = entries.iterator()

    /**
     * Tells whether the set holds [action].
     *
     * @param action the action to look for
     * @return true when an equal action is in the set
     */
    operator fun contains(action: Action): Boolean = action in entries

    /**
     * Replaces all actions in the set with the given actions.
     *
     * @param newActions the new set of actions
     */
    fun replaceAll(newActions: Set<Action>) {
        entries.clear()
        entries.addAll(newActions)
    }

    /**
     * The number of actions in the set.
     */
    val size: Int get() = entries.size

    /**
     * Retrieves an action by name.
     *
     * @param name the action name
     * @return the matching action
     * @throws IllegalStateException if no action with the given name exists
     */
    fun fromName(name: String): Action {
        val result = entries.firstOrNull { it.name == name }
        return result ?: error("No action with name '$name'.")
    }

    /**
     * Removes an action by name.
     *
     * @param name the action name
     * @throws IllegalStateException if no action with the given name exists
     */
    fun removeByName(name: String) {
        val result = entries.firstOrNull { it.name == name }
        require(result != null) { "No action with name '$name'." }
        entries.remove(result)
    }

    /**
     * Creates a shallow copy of this action set.
     *
     * @return a new [Actions] with the same actions
     */
    fun copy(): Actions {
        val c = Actions()
        c.entries.addAll(entries)
        return c
    }

    /** Returns a human-readable representation of this action set. */
    override fun toString(): String {
        val sb = StringBuilder("Set of actions: {")
        if (entries.isNotEmpty()) {
            sb.append("\n")
            for (a in entries) {
                sb.append("  $a\n")
            }
        }
        sb.append("}")
        return sb.toString()
    }
}
