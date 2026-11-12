// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * The actions of the adaptive automaton.
 *
 * The collection is itself iterable: an automaton's actions are reached by
 * iterating over them, and their number is asked of them directly. There is
 * no second, more official way of reaching them.
 *
 * Manages a collection of [Action] instances that can be looked up by name
 * and executed during recognition.
 */
public class Actions : Iterable<Action> {
    private val entries = mutableSetOf<Action>()

    /**
     * The actions of the collection, by name.
     *
     * The engine looks an action up by name every time it fires, and a scan
     * of the collection would make the cost of an action grow with the number
     * of actions registered. The index answers the lookup in constant time.
     *
     * A collection built by [replaceAll] may hold several actions under the
     * same name; the index keeps the first of them, which is the one a scan
     * would have found.
     */
    private val byName: MutableMap<String, Action> = LinkedHashMap()

    /**
     * Adds an action to the collection.
     *
     * An action is identified by its name: adding an action whose name is
     * already taken replaces the previous one, so the collection never holds
     * two actions under the same name. This mirrors the builder DSL, which
     * rejects a duplicate name outright.
     *
     * @param action the action to add
     */
    public fun add(action: Action) {
        entries.removeIf { it.name == action.name }
        entries.add(action)
        byName[action.name] = action
    }

    /** Returns the actions of the automaton. */
    override fun iterator(): Iterator<Action> = entries.iterator()

    /**
     * Tells whether the collection holds [action].
     *
     * @param action the action to look for
     * @return true when an equal action is in the collection
     */
    public operator fun contains(action: Action): Boolean = action in entries

    /**
     * Replaces all actions in the collection with the given actions.
     *
     * @param newActions the new actions
     */
    public fun replaceAll(newActions: Set<Action>) {
        entries.clear()
        entries.addAll(newActions)
        rebuildIndex()
    }

    /**
     * The number of actions in the collection.
     */
    public val size: Int get() = entries.size

    /**
     * Retrieves an action by name.
     *
     * @param name the action name
     * @return the matching action
     * @throws IllegalStateException if no action with the given name exists
     */
    public fun fromName(name: String): Action = byName[name] ?: error("No action with name '$name'.")

    /**
     * Removes an action by name.
     *
     * @param name the action name
     * @throws IllegalStateException if no action with the given name exists
     */
    public fun removeByName(name: String) {
        val result = entries.firstOrNull { it.name == name }
        require(result != null) { "No action with name '$name'." }
        entries.remove(result)
        rebuildIndex()
    }

    /**
     * Creates a shallow copy of this collection.
     *
     * The actions themselves are shared: an action is an immutable handler
     * bound to a name, and copying it would only duplicate the binding.
     *
     * @return a new [Actions] with the same actions
     */
    public fun copy(): Actions {
        val c = Actions()
        c.entries.addAll(entries)
        c.rebuildIndex()
        return c
    }

    /**
     * Rebuilds the index of actions by name.
     *
     * The first action registered under a name is the one the index keeps:
     * that is the one a scan of the collection would have found.
     */
    private fun rebuildIndex() {
        byName.clear()
        for (action in entries) {
            if (!byName.containsKey(action.name)) byName[action.name] = action
        }
    }

    /** Returns a human-readable representation of this collection of actions. */
    override fun toString(): String {
        val sb = StringBuilder("Actions: {")
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
