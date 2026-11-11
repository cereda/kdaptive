// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.dsl

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AutomatonDslTest {
    @BeforeEach
    fun resetIds() {
        IdentifierUtils.reset()
    }

    // -----------------------------------------------------------------------
    // Raw-value consume
    // -----------------------------------------------------------------------
    @Test
    fun `consume with string state names and char symbols`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    state("q1")
                    accepting("q2")
                }
                consume(from = "q0", symbol = 'a', to = "q1")
                consume(from = "q1", symbol = 'b', to = "q2")
                main("M")
            }
        assertEquals(2, spec.transitions.size)
        val t1 = spec.transitions[0]
        assertEquals("q0", t1.source.toString())
        assertEquals("q1", t1.target.toString())
        assertTrue(t1.transition is Transition.SymbolTransition)
        assertEquals(CharSymbol('a'), (t1.transition as Transition.SymbolTransition).symbol)
    }

    @Test
    fun `consume with State and Symbol objects`() {
        val q0 = object : State() {}
        val q1 = object : State() {}
        val symA = object : Symbol() {}
        val spec =
            automaton {
                submachine("M") {
                    initial(q0)
                    accepting(q1)
                }
                consume(from = q0, symbol = symA, to = q1)
                main("M")
            }
        assertEquals(1, spec.transitions.size)
        val t = spec.transitions[0]
        assertSame(q0, t.source)
        assertSame(q1, t.target)
        assertSame(symA, (t.transition as Transition.SymbolTransition).symbol)
    }

    @Test
    fun `consume with prior and post action names as strings`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                consume(from = "q0", symbol = 'a', to = "q1", prior = "X", post = "Y")
                main("M")
            }
        assertEquals(1, spec.transitions.size)
        val t = spec.transitions[0]
        assertEquals("X", (t.prior as ActionRef.Bound).name)
        assertEquals("Y", (t.post as ActionRef.Bound).name)
    }

    // -----------------------------------------------------------------------
    // sym() helper
    // -----------------------------------------------------------------------
    @Test
    fun `sym helper creates CharSymbol`() {
        assertEquals(CharSymbol('x'), sym('x'))
    }

    // -----------------------------------------------------------------------
    // Epsilon transitions
    // -----------------------------------------------------------------------
    @Test
    fun `epsilon with string state names`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                epsilon(from = "q0", to = "q1")
                main("M")
            }
        assertEquals(1, spec.transitions.size)
        val t = spec.transitions[0]
        assertTrue(t.transition is Transition.Epsilon)
    }

    // -----------------------------------------------------------------------
    // Submachine calls
    // -----------------------------------------------------------------------
    @Test
    fun `call with string state names`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q2")
                }
                submachine("S") {
                    initial("s0")
                    accepting("s1")
                }
                call(from = "q0", submachineName = "S", to = "q2")
                main("M")
            }
        assertEquals(1, spec.transitions.size)
        val t = spec.transitions[0]
        assertTrue(t.transition is Transition.Call)
        assertEquals("S", (t.transition as Transition.Call).submachine)
    }

    // -----------------------------------------------------------------------
    // from() block builder
    // -----------------------------------------------------------------------
    @Test
    fun `from block groups transitions from same source`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                    accepting("q2")
                }
                from("q0") {
                    symbol('a', "q1")
                    symbol('b', "q2")
                    epsilon("q1")
                }
                main("M")
            }
        assertEquals(3, spec.transitions.size)
        // All transitions should have source "q0"
        assertTrue(spec.transitions.all { it.source.toString() == "q0" })
    }

    @Test
    fun `from block with prior and post actions`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                from("q0") {
                    symbol('a', "q1", prior = "X", post = "Y")
                }
                main("M")
            }
        val t = spec.transitions.single()
        assertEquals("X", (t.prior as ActionRef.Bound).name)
        assertEquals("Y", (t.post as ActionRef.Bound).name)
    }

    // -----------------------------------------------------------------------
    // Infix operators: builder on "q0" on 'a' to "q1"
    // -----------------------------------------------------------------------
    @Test
    fun `infix on-to builds transition`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                val stepSource = on("q0")
                val step = stepSource.on('a')
                step.to("q1")
                main("M")
            }
        assertEquals(1, spec.transitions.size)
        val t = spec.transitions[0]
        assertEquals("q0", t.source.toString())
        assertEquals("q1", t.target.toString())
        assertEquals(CharSymbol('a'), (t.transition as Transition.SymbolTransition).symbol)
    }

    @Test
    fun `infix on-to with prior and post`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                (on("q0").on('a')).to("q1", prior = "X", post = "Y")
                main("M")
            }
        val t = spec.transitions.single()
        assertEquals("X", (t.prior as ActionRef.Bound).name)
        assertEquals("Y", (t.post as ActionRef.Bound).name)
    }

    /**
     * Guards the bare infix chain (`this on "q0" on 'a' to "q1"`): without the one-arg
     * infix `to` overload, the trailing `to` would silently resolve to the stdlib
     * `Pair` factory and build NO edge. This test must keep expecting one edge.
     */
    @Test
    fun `bare infix on-to chain builds the edge`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                this on "q0" on 'a' to "q1"
                this on "q1" on 'b' to "q0"
                main("M")
            }
        assertEquals(2, spec.transitions.size)
        val first = spec.transitions.single { (it.transition as Transition.SymbolTransition).symbol == CharSymbol('a') }
        assertEquals("q1", first.target.toString())
        assertEquals("q0", first.source.toString())
        val second =
            spec.transitions.single { (it.transition as Transition.SymbolTransition).symbol == CharSymbol('b') }
        assertEquals("q0", second.target.toString())
    }

    // -----------------------------------------------------------------------
    // specToAutomaton integration
    // -----------------------------------------------------------------------
    @Test
    fun `spec recognizes accepted input`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                consume(from = "q0", symbol = 'a', to = "q1")
                main("M")
            }
        val aa = specToAutomaton(spec)
        assertTrue(aa.recognize(listOf(CharSymbol('a'))).accepted)
    }

    @Test
    fun `spec rejects rejected input`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                consume(from = "q0", symbol = 'a', to = "q1")
                main("M")
            }
        val aa = specToAutomaton(spec)
        assertFalse(aa.recognize(listOf(CharSymbol('b'))).accepted)
    }

    // -----------------------------------------------------------------------
    // Error cases
    // -----------------------------------------------------------------------
    @Test
    fun `consume rejects invalid state type`() {
        assertThrows(IllegalArgumentException::class.java) {
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                consume(from = 42, symbol = 'a', to = "q1")
                main("M")
            }
        }
    }

    @Test
    fun `consume rejects invalid symbol type`() {
        assertThrows(IllegalArgumentException::class.java) {
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                consume(from = "q0", symbol = 42, to = "q1")
                main("M")
            }
        }
    }

    @Test
    fun `consume rejects invalid action ref type`() {
        assertThrows(IllegalArgumentException::class.java) {
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                consume(from = "q0", symbol = 'a', to = "q1", post = 42)
                main("M")
            }
        }
    }

    // -----------------------------------------------------------------------
    // State object identity
    // -----------------------------------------------------------------------
    @Test
    fun `state name lookup returns same instance`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                val s0 = state("q0")
                val s0again = state("q0")
                assertSame(s0, s0again)
                consume(from = s0, symbol = 'a', to = "q1")
                main("M")
            }
        assertEquals(1, spec.transitions.size)
    }
}

// Minimal State/Symbol implementations for testing
// (using model package State and Symbol, which are abstract)
