// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * A transition of the adaptive automaton's mapping relation.
 *
 * A transition is one of four kinds:
 * - [SymbolTransition]: consumes a symbol from the input
 * - [Epsilon]: consumes no input (spontaneous transition)
 * - [Call]: calls a submachine, pushing the return state
 * - [Return]: returns from a submachine, popping the stack
 */
sealed class Transition {
    /** The source state of this transition, if any. */
    abstract val sourceState: State?

    /** The target state of this transition, if any. */
    abstract val targetState: State?

    /**
     * A transition that consumes a symbol from the input.
     *
     * @param symbol the symbol consumed by this transition
     */
    data class SymbolTransition(
        val symbol: Symbol,
        override val sourceState: State? = null,
        override val targetState: State? = null,
    ) : Transition() {
        init {
            require(symbol !is SymbolDomain) {
                "A symbol transition consumes a concrete symbol, not a domain."
            }
        }
    }

    /** Consumes no input (spontaneous transition). */
    object Epsilon : Transition() {
        override val sourceState: State? = null
        override val targetState: State? = null
    }

    /** Calls a submachine, pushing the return state. */
    data class Call(
        /** The name of the submachine being called. */
        val submachine: String,
    ) : Transition() {
        override val sourceState: State? = null
        override val targetState: State? = null
    }

    /** Returns from a submachine, popping the stack. */
    object Return : Transition() {
        override val sourceState: State? = null
        override val targetState: State? = null
    }
}
