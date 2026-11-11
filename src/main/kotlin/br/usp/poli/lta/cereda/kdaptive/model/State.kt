// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * Abstract representation of a state belonging to the state set of an
 * adaptive automaton.
 *
 * Users typically create concrete states as objects or data classes, e.g.:
 *     object Q0 : State()
 *
 * [Any] is the wildcard state, used as a filter to match any state. It never
 * belongs to the state set of an automaton: it is rejected wherever a concrete
 * state is required.
 */
abstract class State protected constructor() {
    /** The markers a filter may use to name a state. */
    companion object {
        /** The wildcard state: matches any state. */
        val Any: State = AnyState
    }
}

/**
 * The wildcard state, used as a filter to match any state.
 *
 * This state never belongs to the state set of an automaton: it is rejected
 * wherever a concrete state is required.
 */
object AnyState : State() {
    /** Returns a human-readable representation of this wildcard. */
    override fun toString(): String = "*"
}
