// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Records the actions fired by the action-carrying automata. */
private object ActionLog {
    val fired = mutableListOf<String>()
}

/** Registers the two recording actions on this automaton. */
private fun AdaptiveAutomaton.registerRecordingActions() {
    actions.add(Action(name = "P") { _, transition, _ -> ActionLog.fired.add("P:${transition::class.simpleName}") })
    actions.add(Action(name = "Q") { _, transition, _ -> ActionLog.fired.add("Q:${transition::class.simpleName}") })
}

/**
 * Exercises [AdaptiveAutomaton]'s ergonomic DSL: [AdaptiveAutomaton.consume],
 * [AdaptiveAutomaton.epsilon], [AdaptiveAutomaton.call], [AdaptiveAutomaton.from],
 * the resolution error paths, and the structural [AdaptiveAutomaton.toString].
 */
class AdaptiveAutomatonDslTest {
    /** Recognises `a c* b` via submachine call/return (built with the DSL). */
    private class DslCallAutomaton : AdaptiveAutomaton() {
        private val q0 = object : State() {}
        private val q1 = object : State() {}
        private val q2 = object : State() {}
        private val q3 = object : State() {}
        private val m0 = object : State() {}
        private val m1 = object : State() {}

        override fun newInstance(): AdaptiveAutomaton = DslCallAutomaton()

        override fun setup() {
            mainSubmachine = "Main"
            submachines.add(
                Submachine(
                    name = "Main",
                    states = setOf(q0, q1, q2, q3),
                    initialState = q0,
                    acceptingStates = setOf(q3),
                ),
            )
            submachines.add(
                Submachine(name = "StarC", states = setOf(m0, m1), initialState = m0, acceptingStates = setOf(m0, m1)),
            )

            from(q0) {
                symbol(CharSymbol('a'), q1)
            }
            from(q1) {
                call("StarC", q2)
            }
            from(q2) {
                symbol(CharSymbol('b'), q3)
            }
            from(m0) {
                symbol(CharSymbol('c'), m1)
            }
            from(m1) {
                symbol(CharSymbol('c'), m1)
            }
        }

        fun chars(vararg input: Char): List<CharSymbol> = input.map { CharSymbol(it) }
    }

    private val state = object : State() {}

    /** Epsilon and call edges carrying prior and post actions, group form. */
    private class GroupActionAutomaton : AdaptiveAutomaton() {
        private val q0 = object : State() {}
        private val q1 = object : State() {}
        private val q2 = object : State() {}
        private val n0 = object : State() {}

        override fun newInstance(): AdaptiveAutomaton = GroupActionAutomaton()

        override fun setup() {
            mainSubmachine = "Main"
            submachines.add(
                Submachine(name = "Main", states = setOf(q0, q1, q2), initialState = q0, acceptingStates = setOf(q2)),
            )
            submachines.add(
                Submachine(name = "N", states = setOf(n0), initialState = n0, acceptingStates = setOf(n0)),
            )
            registerRecordingActions()
            from(q0) {
                epsilon(q1, prior = "P", post = "Q")
            }
            from(q1) {
                call("N", q2, prior = "P")
            }
        }
    }

    /** Epsilon and call edges carrying prior and post actions, plain form. */
    private class PlainActionAutomaton : AdaptiveAutomaton() {
        private val q0 = object : State() {}
        private val q1 = object : State() {}
        private val q2 = object : State() {}
        private val n0 = object : State() {}

        override fun newInstance(): AdaptiveAutomaton = PlainActionAutomaton()

        override fun setup() {
            mainSubmachine = "Main"
            submachines.add(
                Submachine(name = "Main", states = setOf(q0, q1, q2), initialState = q0, acceptingStates = setOf(q2)),
            )
            submachines.add(
                Submachine(name = "N", states = setOf(n0), initialState = n0, acceptingStates = setOf(n0)),
            )
            registerRecordingActions()
            epsilon(q0, q1, prior = "P", post = "Q")
            call(q1, "N", q2, prior = "P")
        }
    }

    @Test
    fun `epsilon and call edges fire their prior and post actions`() {
        ActionLog.fired.clear()
        val aa = GroupActionAutomaton()
        assertTrue(aa.recognize(emptyList()).accepted)
        assertEquals(listOf("P:Epsilon", "Q:Epsilon", "P:Call"), ActionLog.fired)
    }

    @Test
    fun `plain epsilon and call edges fire their prior and post actions`() {
        ActionLog.fired.clear()
        val aa = PlainActionAutomaton()
        assertTrue(aa.recognize(emptyList()).accepted)
        assertEquals(listOf("P:Epsilon", "Q:Epsilon", "P:Call"), ActionLog.fired)
    }

    @Test
    fun `dsl automaton recognizes a c-star b`() {
        val aa = DslCallAutomaton()
        assertTrue(aa.recognize(aa.chars('a', 'b')).accepted)
        assertTrue(aa.recognize(aa.chars('a', 'c', 'b')).accepted)
        assertTrue(aa.recognize(aa.chars('a', 'c', 'c', 'c', 'b')).accepted)
    }

    @Test
    fun `dsl automaton rejects invalid input`() {
        val aa = DslCallAutomaton()
        assertFalse(aa.recognize(aa.chars('a')).accepted)
        assertFalse(aa.recognize(aa.chars('a', 'b', 'b')).accepted)
        assertFalse(aa.recognize(aa.chars('b')).accepted)
    }

    @Test
    fun `toString lists submachines and main submachine`() {
        val aa = DslCallAutomaton()
        aa.setup()
        val text = aa.toString()
        assertTrue(text.contains("Main"))
        assertTrue(text.contains("StarC"))
        assertTrue(text.isNotEmpty())
    }

    @Test
    fun `consume rejects invalid state type`() {
        val aa = DslCallAutomaton()
        assertThrows<IllegalArgumentException> { aa.consume(123, CharSymbol('a'), state) }
    }

    @Test
    fun `consume rejects invalid symbol type`() {
        val aa = DslCallAutomaton()
        assertThrows<IllegalArgumentException> { aa.consume(state, 123, state) }
    }

    @Test
    fun `consume rejects invalid action ref type`() {
        val aa = DslCallAutomaton()
        assertThrows<IllegalArgumentException> { aa.consume(state, CharSymbol('a'), state, prior = 999) }
    }
}
