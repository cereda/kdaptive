// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * How many arguments an adaptive action is called with.
 *
 * The arity is a value in its own right, never a null: [Any] is the wildcard
 * that leaves the number of arguments unconstrained, and [Exactly] fixes it.
 *
 * @see br.usp.poli.lta.cereda.kdaptive.dsl.AutomatonBuilder.action
 */
sealed interface Arity {
    /** The action may be called with any number of arguments. */
    data object Any : Arity

    /**
     * The action must be called with exactly [count] arguments.
     *
     * @property count the number of arguments the action is called with
     */
    data class Exactly(
        /** The number of arguments the action is called with. */
        val count: Int,
    ) : Arity {
        init {
            require(count >= 0) { "An arity cannot be negative, but it was $count." }
        }
    }
}
