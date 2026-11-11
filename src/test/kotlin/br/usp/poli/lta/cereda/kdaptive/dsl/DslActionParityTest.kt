// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.dsl

import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.Arity
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Covers the action references carried by epsilon and call edges in the
 * builder DSL: both edge kinds accept prior and post actions, the group form
 * does too, and every reference obeys the arity contract of its action.
 */
class DslActionParityTest {
    @Test
    fun `an epsilon edge fires its prior and post actions`() {
        val fired = mutableListOf<String>()
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                action("P") { transition, _ -> fired.add("P:${transition::class.simpleName}") }
                action("Q") { transition, _ -> fired.add("Q:${transition::class.simpleName}") }
                epsilon("q0", "q1", prior = "P", post = "Q")
                main("M")
            }
        val aa = specToAutomaton(spec)
        assertTrue(aa.recognize(emptyList()).accepted)
        assertEquals(listOf("P:Epsilon", "Q:Epsilon"), fired)
    }

    @Test
    fun `a call edge fires its prior and post actions`() {
        val fired = mutableListOf<String>()
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                submachine("N") {
                    initial("n0")
                    accepting("n0")
                }
                action("P") { transition, _ -> fired.add("P:${transition::class.simpleName}") }
                action("Q") { transition, _ -> fired.add("Q:${transition::class.simpleName}") }
                call("q0", "N", "q1", prior = "P", post = "Q")
                main("M")
            }
        val aa = specToAutomaton(spec)
        assertTrue(aa.recognize(emptyList()).accepted)
        assertEquals(listOf("P:Call", "Q:Call"), fired)
    }

    @Test
    fun `the group form carries actions on epsilon and call edges`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                submachine("N") {
                    initial("n0")
                    accepting("n1")
                }
                from("q0") {
                    epsilon("q1", prior = "P", post = "Q")
                    call("N", "q1", prior = "P")
                }
                main("M")
            }
        assertEquals(2, spec.transitions.size)
        assertEquals("P", (spec.transitions[0].prior as ActionRef.Bound).name)
        assertEquals("Q", (spec.transitions[0].post as ActionRef.Bound).name)
        assertEquals("P", (spec.transitions[1].prior as ActionRef.Bound).name)
        assertEquals(ActionRef.None, spec.transitions[1].post)
    }

    @Test
    fun `action references on epsilon and call edges obey the arity contract`() {
        val error =
            assertThrows<IllegalArgumentException> {
                automaton {
                    submachine("M") {
                        initial("q0")
                        accepting("q1")
                    }
                    action("A", arity = Arity.Exactly(2)) { _, _ -> }
                    epsilon("q0", "q1", prior = ref("A", "q1"))
                    main("M")
                }
            }
        assertTrue(error.message!!.contains("expects 2"))
        assertTrue(error.message!!.contains("binds 1"))
    }

    @Test
    fun `action references on epsilon and call edges match the declared arity`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                submachine("N") {
                    initial("n0")
                    accepting("n1")
                }
                action("A", arity = Arity.Exactly(1)) { _, _ -> }
                epsilon("q0", "q1", prior = ref("A", "q1"))
                call("q0", "N", "q1", post = ref("A", "q1"))
                main("M")
            }
        assertEquals(2, spec.transitions.size)
    }

    @Test
    fun `an action on an epsilon edge can rewrite the mapping`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    state("q1")
                    accepting("q2")
                }
                action("AddB") { _, _ ->
                    ea.add(state("q1"), CharSymbol('b'), state("q2"))
                }
                epsilon("q0", "q1", post = "AddB")
                main("M")
            }
        val aa = specToAutomaton(spec)
        assertTrue(aa.recognize(listOf(CharSymbol('b'))).accepted)
    }
}
