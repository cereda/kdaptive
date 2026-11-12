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
 *
 * **Equality**: the library compares states by *value*, so a subclass must
 * define it — a data class does it for free, and an object with no fields
 * compares by identity, which is exactly right for a singleton state. Two
 * states with the same content are the same state: an edge declared twice
 * with the same endpoints is one edge, not two.
 */
public abstract class State protected constructor() {
    /** The markers a filter may use to name a state. */
    public companion object {
        /** The wildcard state: matches any state. */
        public val Any: State = AnyState

        /**
         * Builds a **domain**: the set of states a filter names at once.
         *
         * A domain is a filter marker, like [Any]: it never belongs to the
         * state set of an automaton, and it is rejected wherever a concrete
         * state is required. What it does is name *several* states in a
         * single filter:
         *
         * ```
         * val half = State.anyOf(q0, q1)
         * ea.query(source = half)          // matches the edges leaving q0 or q1
         * ea.add(half, CharSymbol('a'), q2) // adds one edge per state of the domain
         * ```
         *
         * The wildcard [Any] cannot be part of a domain — it would swallow
         * the whole state set — and neither can another domain.
         *
         * @param states the states of the domain
         * @return the domain over [states]
         * @throws IllegalArgumentException if [states] is empty, or names the
         *   wildcard state or another domain
         */
        public fun anyOf(vararg states: State): StateDomain = StateDomain.of(states.toList())
    }
}

/**
 * A domain over states: the set of states a filter names at once.
 *
 * A domain is a filter, not a state: it never belongs to the state set of an
 * automaton. It matches an edge when the edge's endpoint is one of its
 * [members], and an addition over a domain produces one edge per member.
 *
 * Two domains are equal when they name the same states.
 */
public class StateDomain private constructor(
    /** The states of this domain. */
    public val members: Set<State>,
) : State() {
    /** The number of states in this domain. */
    public val size: Int get() = members.size

    /** Returns a human-readable representation of this domain. */
    override fun toString(): String = members.joinToString(prefix = "{", postfix = "}")

    /** Two domains are equal when they name the same states. */
    override fun equals(other: Any?): Boolean = other is StateDomain && other.members == members

    /** The hash code follows the states named by this domain. */
    override fun hashCode(): Int = members.hashCode()

    /** The factory of the domains, used by [State.anyOf]. */
    public companion object {
        /** Builds a domain over [states], rejecting the markers a domain may not contain. */
        internal fun of(states: List<State>): StateDomain {
            require(states.isNotEmpty()) { "A state domain must name at least one state." }
            require(states.none { it === State.Any }) {
                "A state domain cannot contain the wildcard state."
            }
            require(states.none { it is StateDomain }) {
                "A state domain cannot contain another domain."
            }
            return StateDomain(states.toSet())
        }
    }
}

/**
 * The wildcard state, used as a filter to match any state.
 *
 * This state never belongs to the state set of an automaton: it is rejected
 * wherever a concrete state is required.
 */
public object AnyState : State() {
    /** Returns a human-readable representation of this wildcard. */
    override fun toString(): String = "*"
}
