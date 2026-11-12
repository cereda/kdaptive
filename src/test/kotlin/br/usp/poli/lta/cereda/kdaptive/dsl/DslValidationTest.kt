// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.dsl

import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Covers the build-time checks of the builder DSL: state ownership, call
 * targets, the main submachine, the hardening of [SubmachineBuilder] and the
 * read-only state lookup offered to action handlers.
 */
class DslValidationTest {
    // -----------------------------------------------------------------------
    // State ownership
    // -----------------------------------------------------------------------

    @Test
    fun `an edge endpoint owned by no submachine is rejected`() {
        val error =
            assertThrows<IllegalArgumentException> {
                automaton {
                    submachine("M") {
                        initial("q0")
                        accepting("q1")
                    }
                    symbol("q0", 'a', "q2")
                    main("M")
                }
            }
        assertTrue(error.message!!.contains("'q2'"))
        assertTrue(error.message!!.contains("not part of any submachine"))
        assertTrue(error.message!!.contains("submachine(\"M\")"))
    }

    @Test
    fun `a source owned by no submachine is rejected`() {
        val error =
            assertThrows<IllegalArgumentException> {
                automaton {
                    submachine("M") {
                        initial("q0")
                        accepting("q1")
                    }
                    symbol("q2", 'a', "q1")
                    main("M")
                }
            }
        assertTrue(error.message!!.contains("'q2'"))
    }

    @Test
    fun `a state owned by two submachines is accepted`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    state("q1")
                    accepting("q1")
                }
                submachine("N") {
                    initial("q0")
                    accepting("q1")
                }
                symbol("q0", 'a', "q1")
                main("M")
            }
        assertEquals(1, spec.transitions.size)
    }

    // -----------------------------------------------------------------------
    // Calls
    // -----------------------------------------------------------------------

    @Test
    fun `a call to an undeclared submachine is rejected`() {
        val error =
            assertThrows<IllegalArgumentException> {
                automaton {
                    submachine("M") {
                        initial("q0")
                        accepting("q1")
                    }
                    call("q0", "Typo", "q1")
                    main("M")
                }
            }
        assertTrue(error.message!!.contains("'Typo'"))
        assertTrue(error.message!!.contains("undeclared submachine"))
    }

    @Test
    fun `a call returning to a state of another submachine is rejected`() {
        val error =
            assertThrows<IllegalArgumentException> {
                automaton {
                    submachine("M") {
                        initial("q0")
                        accepting("q1")
                    }
                    submachine("N") {
                        initial("n0")
                        accepting("n1")
                    }
                    call("q0", "N", "n1")
                    main("M")
                }
            }
        assertTrue(error.message!!.contains("'n1'"))
        assertTrue(error.message!!.contains("calling submachine"))
    }

    @Test
    fun `a call returning to a state of the caller is accepted`() {
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
                call("q0", "N", "q1")
                main("M")
            }
        assertEquals(1, spec.transitions.size)
    }

    // -----------------------------------------------------------------------
    // Main submachine
    // -----------------------------------------------------------------------

    @Test
    fun `a missing main submachine is rejected`() {
        val error =
            assertThrows<IllegalArgumentException> {
                automaton {
                    submachine("M") {
                        initial("q0")
                        accepting("q1")
                    }
                }
            }
        assertTrue(error.message!!.contains("main submachine"))
    }

    @Test
    fun `an undeclared main submachine is rejected`() {
        val error =
            assertThrows<IllegalArgumentException> {
                automaton {
                    submachine("M") {
                        initial("q0")
                        accepting("q1")
                    }
                    main("Typo")
                }
            }
        assertTrue(error.message!!.contains("'Typo'"))
        assertTrue(error.message!!.contains("not declared"))
    }

    @Test
    fun `the builder refuses to report a main submachine it was not given`() {
        val builder = AutomatonBuilder()
        val error = assertThrows<IllegalStateException> { builder.main }
        assertTrue(error.message!!.contains("Main submachine"))
    }

    @Test
    fun `a specification naming an unknown main submachine is rejected when materialized`() {
        val spec =
            AutomatonSpec(
                submachines = mapOf("M" to minimalSubmachine("M")),
                main = "Typo",
            )
        val error = assertThrows<IllegalArgumentException> { specToAutomaton(spec) }
        assertTrue(error.message!!.contains("'Typo'"))
    }

    // -----------------------------------------------------------------------
    // SubmachineBuilder
    // -----------------------------------------------------------------------

    @Test
    fun `accepting takes several states at once`() {
        val builder = SubmachineBuilder(mutableMapOf())
        builder.initial("q0")
        builder.accepting("q1", "q2")
        val submachine = builder.build("M")
        assertEquals(setOf("q1", "q2"), submachine.acceptingStates.map { it.toString() }.toSet())
        assertEquals(setOf("q0", "q1", "q2"), submachine.states.map { it.toString() }.toSet())
    }

    @Test
    fun `accepting without arguments is rejected`() {
        val builder = SubmachineBuilder(mutableMapOf())
        val error = assertThrows<IllegalArgumentException> { builder.accepting() }
        assertTrue(error.message!!.contains("at least one accepting state"))
    }

    @Test
    fun `more than one initial state is rejected`() {
        val builder = SubmachineBuilder(mutableMapOf())
        builder.initial("q0")
        builder.initial("q1")
        val error = assertThrows<IllegalArgumentException> { builder.build("M") }
        assertTrue(error.message!!.contains("more than one initial state"))
    }

    // -----------------------------------------------------------------------
    // Action context
    // -----------------------------------------------------------------------

    @Test
    fun `an action handler looks up a state by name`() {
        val looked = mutableListOf<String>()
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                action("A") { _, _ ->
                    looked.add(state("q1").toString())
                }
                on("q0").on('a').to("q1", post = "A")
                main("M")
            }
        val aa = specToAutomaton(spec)
        aa.recognize(listOf(CharSymbol('a')))
        assertEquals(listOf("q1"), looked)
    }

    @Test
    fun `an action handler cannot invent a state`() {
        val error =
            assertThrows<IllegalStateException> {
                val spec =
                    automaton {
                        submachine("M") {
                            initial("q0")
                            accepting("q1")
                        }
                        action("A") { _, _ ->
                            state("qTYPO")
                        }
                        on("q0").on('a').to("q1", post = "A")
                        main("M")
                    }
                specToAutomaton(spec).recognize(listOf(CharSymbol('a')))
            }
        assertTrue(error.message!!.contains("qTYPO"))
        assertTrue(error.message!!.contains("No state named"))
    }

    /** Builds a minimal submachine for the specification-level checks. */
    private fun minimalSubmachine(name: String): Submachine {
        val q0 = object : State() {}
        return Submachine(
            name = name,
            states = setOf(q0),
            initialState = q0,
            acceptingStates = setOf(q0),
        )
    }
}
