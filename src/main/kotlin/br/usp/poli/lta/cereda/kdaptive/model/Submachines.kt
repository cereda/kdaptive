// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * The submachines of the adaptive automaton.
 *
 * The collection is itself iterable: an automaton's submachines are reached by
 * iterating over them, and their number is asked of them directly. There is
 * no second, more official way of reaching them.
 *
 * Manages a collection of [Submachine] instances that can be looked up by
 * name or by state.
 */
class Submachines : Iterable<Submachine> {
    private val entries = mutableSetOf<Submachine>()

    /**
     * Adds a submachine to the set.
     *
     * @param submachine the submachine to add
     */
    fun add(submachine: Submachine) {
        entries.add(submachine)
    }

    /** Returns the submachines of the automaton. */
    override fun iterator(): Iterator<Submachine> = entries.iterator()

    /**
     * Tells whether the collection holds [submachine].
     *
     * @param submachine the submachine to look for
     * @return true when an equal submachine is in the collection
     */
    operator fun contains(submachine: Submachine): Boolean = submachine in entries

    /**
     * Replaces all submachines in the collection with the given submachines.
     *
     * @param newSubmachines the new submachines
     */
    fun replaceAll(newSubmachines: Set<Submachine>) {
        entries.clear()
        entries.addAll(newSubmachines)
    }

    /**
     * The number of submachines in the collection.
     */
    val size: Int get() = entries.size

    /**
     * Retrieves a submachine containing the given state.
     *
     * @param state the state to search for
     * @return the submachine containing the state
     * @throws IllegalStateException if no submachine contains the state
     */
    fun fromState(state: State): Submachine {
        val result = entries.firstOrNull { it.states.contains(state) }
        return result ?: error("No submachine contains state '$state'.")
    }

    /**
     * Retrieves a submachine by name.
     *
     * @param name the submachine name
     * @return the matching submachine
     * @throws IllegalStateException if no submachine with the given name exists
     */
    fun fromName(name: String): Submachine {
        val result = entries.firstOrNull { it.name == name }
        return result ?: error("No submachine with name '$name'.")
    }

    /**
     * Removes a submachine by name.
     *
     * @param name the submachine name
     * @throws IllegalStateException if no submachine with the given name exists
     */
    fun removeByName(name: String) {
        val result = entries.firstOrNull { it.name == name }
        require(result != null) { "No submachine with name '$name'." }
        entries.remove(result)
    }

    /**
     * Creates a shallow copy of this collection.
     *
     * The submachines themselves are immutable, and their identifier plays no
     * part in their equality, so the copy shares the instances it holds: a
     * copy of a collection of submachines holds the same submachines.
     *
     * @return a new [Submachines] with the same submachines
     */
    fun copy(): Submachines {
        val s = Submachines()
        s.entries.addAll(entries)
        return s
    }

    /** Returns a human-readable representation of this collection of submachines. */
    override fun toString(): String {
        val sb = StringBuilder("Submachines: {")
        if (entries.isNotEmpty()) {
            sb.append("\n")
            for (s in entries) {
                sb.append("  $s\n")
            }
        }
        sb.append("}")
        return sb.toString()
    }
}
