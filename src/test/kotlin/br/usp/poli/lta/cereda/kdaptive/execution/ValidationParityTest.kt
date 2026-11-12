// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.dsl.AutomatonBuilder
import br.usp.poli.lta.cereda.kdaptive.dsl.automaton
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private object V0 : State() {
    override fun toString(): String = "v0"
}

private object V1 : State() {
    override fun toString(): String = "v1"
}

private object V2 : State() {
    override fun toString(): String = "v2"
}

/**
 * The imperative API and the builder DSL accept the same automata.
 *
 * Both describe a topology — a set of submachines, a set of edges and a main
 * submachine — and both apply the same rules to it. A topology one of them
 * rejects is rejected by the other one too, with the same message.
 */
class ValidationParityTest {

    /** An automaton whose topology is built by [build], on every clone. */
    private class ImperativeAa(private val build: (AdaptiveAutomaton) -> Unit) : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = ImperativeAa(build)

        override fun setup() {
            build(this)
        }
    }

    /** Builds the DSL counterpart of a topology. */
    private fun dsl(build: AutomatonBuilder.() -> Unit) = automaton(build)

    /** Runs a recognition, so the imperative API applies its validation. */
    private fun recognize(aa: AdaptiveAutomaton) {
        aa.recognize(listOf(CharSymbol('a')))
    }

    @Test
    fun `a missing main submachine is rejected by both APIs`() {
        val dslError =
            assertThrows<IllegalArgumentException> {
                dsl {
                    submachine("M") {
                        initial(V0)
                        accepting(V1)
                    }
                }
            }
        val imperativeError =
            assertThrows<IllegalArgumentException> {
                recognize(
                    ImperativeAa { aa ->
                        aa.submachines.add(
                            Submachine(
                                name = "M",
                                states = setOf(V0, V1),
                                initialState = V0,
                                acceptingStates = setOf(V1),
                            ),
                        )
                    },
                )
            }

        assertTrue(dslError.message!!.contains("main submachine"))
        assertEquals(dslError.message, imperativeError.message)
    }

    @Test
    fun `an undeclared main submachine is rejected by both APIs`() {
        val dslError =
            assertThrows<IllegalArgumentException> {
                dsl {
                    submachine("M") {
                        initial(V0)
                        accepting(V1)
                    }
                    main("Typo")
                }
            }
        val imperativeError =
            assertThrows<IllegalArgumentException> {
                recognize(
                    ImperativeAa { aa ->
                        aa.submachines.add(
                            Submachine(
                                name = "M",
                                states = setOf(V0, V1),
                                initialState = V0,
                                acceptingStates = setOf(V1),
                            ),
                        )
                        aa.mainSubmachine = "Typo"
                    },
                )
            }

        assertTrue(dslError.message!!.contains("'Typo'"))
        assertEquals(dslError.message, imperativeError.message)
    }

    @Test
    fun `an edge endpoint owned by no submachine is rejected by both APIs`() {
        val dslError =
            assertThrows<IllegalArgumentException> {
                dsl {
                    submachine("M") {
                        initial(V0)
                        accepting(V1)
                    }
                    symbol(V0, 'a', V2)
                    main("M")
                }
            }
        val imperativeError =
            assertThrows<IllegalArgumentException> {
                recognize(
                    ImperativeAa { aa ->
                        aa.submachines.add(
                            Submachine(
                                name = "M",
                                states = setOf(V0, V1),
                                initialState = V0,
                                acceptingStates = setOf(V1),
                            ),
                        )
                        aa.symbol(V0, 'a', V2)
                        aa.mainSubmachine = "M"
                    },
                )
            }

        assertTrue(dslError.message!!.contains("'v2'"))
        assertTrue(dslError.message!!.contains("not part of any submachine"))
        assertEquals(dslError.message, imperativeError.message)
    }

    @Test
    fun `a call to an undeclared submachine is rejected by both APIs`() {
        val dslError =
            assertThrows<IllegalArgumentException> {
                dsl {
                    submachine("M") {
                        initial(V0)
                        accepting(V1)
                    }
                    call(V0, "Typo", V1)
                    main("M")
                }
            }
        val imperativeError =
            assertThrows<IllegalArgumentException> {
                recognize(
                    ImperativeAa { aa ->
                        aa.submachines.add(
                            Submachine(
                                name = "M",
                                states = setOf(V0, V1),
                                initialState = V0,
                                acceptingStates = setOf(V1),
                            ),
                        )
                        aa.call(V0, "Typo", V1)
                        aa.mainSubmachine = "M"
                    },
                )
            }

        assertTrue(dslError.message!!.contains("'Typo'"))
        assertTrue(dslError.message!!.contains("undeclared submachine"))
        assertEquals(dslError.message, imperativeError.message)
    }

    @Test
    fun `a call returning to a state of another submachine is rejected by both APIs`() {
        val dslError =
            assertThrows<IllegalArgumentException> {
                dsl {
                    submachine("M") {
                        initial(V0)
                        accepting(V1)
                    }
                    submachine("N") {
                        initial(V2)
                        accepting(V2)
                    }
                    call(V0, "N", V2)
                    main("M")
                }
            }
        val imperativeError =
            assertThrows<IllegalArgumentException> {
                recognize(
                    ImperativeAa { aa ->
                        aa.submachines.add(
                            Submachine(
                                name = "M",
                                states = setOf(V0, V1),
                                initialState = V0,
                                acceptingStates = setOf(V1),
                            ),
                        )
                        aa.submachines.add(
                            Submachine(
                                name = "N",
                                states = setOf(V2),
                                initialState = V2,
                                acceptingStates = setOf(V2),
                            ),
                        )
                        aa.call(V0, "N", V2)
                        aa.mainSubmachine = "M"
                    },
                )
            }

        assertTrue(dslError.message!!.contains("'v2'"))
        assertTrue(dslError.message!!.contains("calling submachine"))
        assertEquals(dslError.message, imperativeError.message)
    }

    @Test
    fun `a domain used as an edge endpoint is rejected by both APIs`() {
        val dslError =
            assertThrows<IllegalArgumentException> {
                dsl {
                    submachine("M") {
                        initial(V0)
                        accepting(V1)
                    }
                    symbol(V0, 'a', State.anyOf(V1, V2))
                    main("M")
                }
            }
        val imperativeError =
            assertThrows<IllegalArgumentException> {
                recognize(
                    ImperativeAa { aa ->
                        aa.submachines.add(
                            Submachine(
                                name = "M",
                                states = setOf(V0, V1, V2),
                                initialState = V0,
                                acceptingStates = setOf(V1),
                            ),
                        )
                        aa.symbol(V0, 'a', State.anyOf(V1, V2))
                        aa.mainSubmachine = "M"
                    },
                )
            }

        assertTrue(dslError.message!!.contains("domain"))
        assertEquals(dslError.message, imperativeError.message)
    }

    @Test
    fun `a valid topology is accepted by both APIs`() {
        val spec =
            dsl {
                submachine("M") {
                    initial(V0)
                    accepting(V1)
                }
                symbol(V0, 'a', V1)
                main("M")
            }
        assertEquals(1, spec.transitions.size)

        val aa =
            ImperativeAa { automaton ->
                automaton.submachines.add(
                    Submachine(
                        name = "M",
                        states = setOf(V0, V1),
                        initialState = V0,
                        acceptingStates = setOf(V1),
                    ),
                )
                automaton.symbol(V0, 'a', V1)
                automaton.mainSubmachine = "M"
            }
        assertTrue(aa.recognize(listOf(CharSymbol('a'))).accepted)
    }
}
