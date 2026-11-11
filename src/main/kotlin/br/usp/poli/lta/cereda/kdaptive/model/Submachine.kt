// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * Formal definition of a submachine.
 *
 * Two submachines are equal when they have the same name and the same states.
 * The [id] is bookkeeping, not identity: it is not part of equality, so
 * declaring the same submachine twice does not duplicate it.
 *
 * @param name the name of this submachine
 * @param states the set of all states in this submachine
 * @param initialState the initial state of this submachine
 * @param acceptingStates the set of accepting states
 */
data class Submachine(
    /** The name of this submachine. */
    val name: String,
    /** The set of all states in this submachine. */
    val states: Set<State>,
    /** The initial state of this submachine. */
    val initialState: State,
    /** The set of accepting states. */
    val acceptingStates: Set<State>,
) {
    /**
     * The unique identifier of this submachine.
     *
     * The identifier is deliberately outside the equality of the submachine.
     */
    val id: Int = Identifiers.nextSubmachineId()

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

/**
 * A reference to a submachine, serving two roles.
 *
 * As a **filter**, it constrains the submachine slot of a call edge in a
 * query or a removal:
 *
 * - [Any]: the slot may call any submachine;
 * - [Named]: the slot must call the named submachine;
 * - [Domain]: the slot must call one of the named submachines;
 * - [None]: the filter does not apply — the pattern belongs to another
 *   family.
 *
 * As the **input of an addition**, it names the submachines to call: [Named]
 * adds one call edge, and [Domain] adds one edge per name it names. [Any] and
 * [None] are rejected there: an addition names what it adds.
 *
 * A domain is a filter marker, like [Any]: it never belongs to the topology
 * of an automaton, and it is rejected wherever a single submachine is
 * required.
 */
sealed interface SubmachineRef {
    /** The wildcard: matches any submachine call. */
    data object Any : SubmachineRef

    /**
     * The named submachine: matches the calls to it.
     *
     * @property name the name of the submachine
     */
    data class Named(
        /** The name of the submachine. */
        val name: String,
    ) : SubmachineRef

    /**
     * A domain over submachines: the set of submachines a filter names at once.
     *
     * Two domains are equal when they name the same submachines.
     *
     * @property names the names of the submachines of this domain
     */
    data class Domain(
        /** The names of the submachines of this domain. */
        val names: Set<String>,
    ) : SubmachineRef {
        init {
            require(names.isNotEmpty()) {
                "A submachine domain must name at least one submachine."
            }
        }

        /** The number of submachines in this domain. */
        val size: Int get() = names.size
    }

    /** The marker of a pattern whose submachine filter does not apply. */
    data object None : SubmachineRef

    /** The markers a filter may use to name a submachine. */
    companion object {
        /**
         * Builds a **domain**: the set of submachines a filter names at once.
         *
         * A domain is a filter marker, like [Any]: it never belongs to the
         * topology of an automaton. What it does is name *several* submachines
         * in a single filter:
         *
         * ```
         * val pair = SubmachineRef.anyOf("M", "N")
         * ea.queryCall(submachine = pair)   // matches the calls to M or to N
         * ea.addCall(q0, pair, q1)           // adds one call edge per submachine
         * ```
         *
         * @param names the names of the submachines of the domain
         * @return the domain over [names]
         * @throws IllegalArgumentException if [names] is empty
         */
        fun anyOf(vararg names: String): Domain = Domain(names.toSet())
    }
}
