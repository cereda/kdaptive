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
 *
 * A transition carries its payload only: the endpoints of an edge belong to
 * the edge, which is the only place they are needed.
 */
sealed class Transition {
    /**
     * A transition that consumes a symbol from the input.
     *
     * @param symbol the symbol consumed by this transition
     */
    data class SymbolTransition(val symbol: Symbol) : Transition() {
        init {
            require(symbol !is SymbolDomain) {
                "A symbol transition consumes a concrete symbol, not a domain."
            }
        }
    }

    /** Consumes no input (spontaneous transition). */
    object Epsilon : Transition()

    /**
     * Calls a submachine, pushing the return state.
     *
     * @property submachine the name of the submachine being called
     */
    data class Call(
        /** The name of the submachine being called. */
        val submachine: String,
    ) : Transition()

    /** Returns from a submachine, popping the stack. */
    object Return : Transition()
}
