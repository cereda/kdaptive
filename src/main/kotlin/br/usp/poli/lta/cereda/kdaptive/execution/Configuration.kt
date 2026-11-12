// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.State

/**
 * The configuration a recognition path is in.
 *
 * Two configurations are equal when the path would behave identically from
 * both: the same state, in the same submachine, at the same place in the
 * input, with the same call stack, over the same topology.
 *
 * The topology enters the configuration as a *fingerprint* rather than as the
 * edges themselves: the fingerprint describes what the topology holds, not
 * which edges it holds, so two copies of the same topology share it.
 *
 * The configuration is the key of the lineage: the engine indexes the
 * configurations a path has left by the configuration itself, and looks up
 * the lineage in constant time.
 *
 * The class is deliberately *not* a data class. The hash of a configuration is
 * the hot path of the engine — it is computed on every step of every path —
 * and the call stack is the one part of it that costs more than a constant to
 * hash. The stack therefore enters the configuration twice: as the list the
 * equality compares, and as the hash the caller computed once and kept.
 */
internal class Configuration(
    /** The state the path is in. */
    val state: State,
    /** The name of the submachine the path is running. */
    val submachine: String,
    /** The position of the path in the input. */
    val cursor: Int,
    /** The call stack of the path, from the top to the bottom. */
    val stack: List<State>,
    /** The structural fingerprint of the topology the path runs over. */
    val digest: Long,
    /** The hash of [stack], computed and cached by the caller. */
    private val stackHash: Int,
) {

    /**
     * Tells whether [other] describes the same configuration.
     *
     * The call stacks are compared by their contents, but the identity of the
     * list is checked first: a path that did not call or return keeps the
     * same list, and the comparison is then free.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Configuration) return false
        return state == other.state &&
            submachine == other.submachine &&
            cursor == other.cursor &&
            (stack === other.stack || stack == other.stack) &&
            digest == other.digest
    }

    /**
     * The hash of this configuration.
     *
     * The call stack enters the hash through the hash the caller computed:
     * hashing the list again would cost a pass over the stack on every step
     * of every path, for nothing.
     */
    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + submachine.hashCode()
        result = 31 * result + cursor
        result = 31 * result + stackHash
        result = 31 * result + digest.hashCode()
        return result
    }

    /** Returns a human-readable representation of this configuration. */
    override fun toString(): String =
        "Configuration(state=$state, submachine=$submachine, cursor=$cursor, digest=$digest)"
}
