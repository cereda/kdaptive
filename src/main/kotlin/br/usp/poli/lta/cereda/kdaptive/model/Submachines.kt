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
public class Submachines : Iterable<Submachine> {
    private val entries = mutableSetOf<Submachine>()

    /**
     * The submachines of the collection, by name.
     *
     * The engine looks a submachine up by name every time it enters or
     * leaves one, and a scan of the collection would make the cost of a call
     * grow with the number of submachines registered.
     *
     * Several submachines may share a name: the index keeps the first one
     * registered, which is the one a scan of the collection would have found.
     */
    private val byName: MutableMap<String, Submachine> = LinkedHashMap()

    /**
     * The owner of each state.
     *
     * Several submachines may declare the same state: the index keeps the
     * first one registered, which is the one a scan of the collection would
     * have found.
     */
    private val owners: MutableMap<State, Submachine> = LinkedHashMap()

    /**
     * Adds a submachine to the set.
     *
     * @param submachine the submachine to add
     */
    public fun add(submachine: Submachine) {
        val added = entries.add(submachine)
        if (!added) return
        if (!byName.containsKey(submachine.name)) byName[submachine.name] = submachine
        for (state in submachine.states) {
            if (!owners.containsKey(state)) owners[state] = submachine
        }
    }

    /** Returns the submachines of the automaton. */
    override fun iterator(): Iterator<Submachine> = entries.iterator()

    /**
     * Tells whether the collection holds [submachine].
     *
     * @param submachine the submachine to look for
     * @return true when an equal submachine is in the collection
     */
    public operator fun contains(submachine: Submachine): Boolean = submachine in entries

    /**
     * Replaces all submachines in the collection with the given submachines.
     *
     * @param newSubmachines the new submachines
     */
    public fun replaceAll(newSubmachines: Set<Submachine>) {
        entries.clear()
        entries.addAll(newSubmachines)
        rebuildIndexes()
    }

    /**
     * The number of submachines in the collection.
     */
    public val size: Int get() = entries.size

    /**
     * Retrieves the submachine that owns the given state.
     *
     * A state belongs to the submachine that declares it, and the owner is
     * what the engine needs to know when a path crosses a boundary.
     *
     * @param state the state to search for
     * @return the submachine containing the state
     * @throws NoSuchElementException if no submachine contains the state
     */
    public fun ownerOf(state: State): Submachine =
        owners[state] ?: throw NoSuchElementException("No submachine contains state '$state'.")

    /**
     * Retrieves a submachine by name.
     *
     * The collection is a lookup table first and an iterable second, and the
     * `get` operator says so: `submachines["name"]` is the submachine
     * registered under that name.
     *
     * @param name the submachine name
     * @return the matching submachine
     * @throws NoSuchElementException if no submachine with the given name exists
     */
    public operator fun get(name: String): Submachine =
        byName[name] ?: throw NoSuchElementException("No submachine with name '$name'.")

    /**
     * Removes a submachine by name.
     *
     * @param name the submachine name
     * @throws NoSuchElementException if no submachine with the given name exists
     */
    public fun removeByName(name: String) {
        val result = byName[name]
        if (result == null) throw NoSuchElementException("No submachine with name '$name'.")
        entries.remove(result)
        rebuildIndexes()
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
    public fun copy(): Submachines {
        val s = Submachines()
        s.entries.addAll(entries)
        s.rebuildIndexes()
        return s
    }

    /**
     * Rebuilds the index of submachines by name and the index of their states.
     *
     * The first submachine registered under a name — and the first one
     * declaring a state — is the one the indexes keep: that is the one a scan
     * of the collection would have found.
     */
    private fun rebuildIndexes() {
        byName.clear()
        owners.clear()
        for (submachine in entries) {
            if (!byName.containsKey(submachine.name)) byName[submachine.name] = submachine
            for (state in submachine.states) {
                if (!owners.containsKey(state)) owners[state] = submachine
            }
        }
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
