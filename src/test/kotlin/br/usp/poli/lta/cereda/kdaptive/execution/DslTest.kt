// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.dsl.automaton
import br.usp.poli.lta.cereda.kdaptive.dsl.specToAutomaton
import br.usp.poli.lta.cereda.kdaptive.dsl.sym
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.Identifiers
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DslTest {
    @BeforeEach
    fun resetIds() {
        Identifiers.reset()
    }

    /** Even number of 'a's over {a, b}, built with the DSL. */
    private fun evenAsSpec() = automaton {
        val q0 = state("q0")
        val q1 = state("q1")
        submachine("M") {
            initial(q0)
            state(q1)
            accepting(q0)
        }
        consume(q0, sym('a'), q1)
        consume(q0, sym('b'), q0)
        consume(q1, sym('a'), q0)
        consume(q1, sym('b'), q1)
        main("M")
    }

    @Test
    fun `dsl recognises empty string`() {
        val automaton = specToAutomaton(evenAsSpec())
        assertTrue(automaton.recognize(emptyList()).accepted)
    }

    @Test
    fun `dsl recognises aa`() {
        val automaton = specToAutomaton(evenAsSpec())
        assertTrue(automaton.recognize(listOf(CharSymbol('a'), CharSymbol('a'))).accepted)
    }

    @Test
    fun `dsl rejects single a`() {
        val automaton = specToAutomaton(evenAsSpec())
        assertFalse(automaton.recognize(listOf(CharSymbol('a'))).accepted)
    }

    @Test
    fun `dsl recognises aab`() {
        val automaton = specToAutomaton(evenAsSpec())
        assertTrue(automaton.recognize(listOf(CharSymbol('a'), CharSymbol('a'), CharSymbol('b'))).accepted)
    }

    @Test
    fun `dsl automaton can be cloned and reused`() {
        val spec = evenAsSpec()
        val a1 = specToAutomaton(spec)
        val a2 = specToAutomaton(spec)
        assertTrue(a1.recognize(listOf(CharSymbol('a'), CharSymbol('a'))).accepted)
        assertTrue(a2.recognize(listOf(CharSymbol('a'), CharSymbol('a'), CharSymbol('a'), CharSymbol('a'))).accepted)
    }

    /** A submachine "S" that consumes a single 'x'; main calls S and returns. */
    private fun xSpec() = automaton {
        val m0 = state("m0")
        val m1 = state("m1")
        val s0 = state("s0")
        val s1 = state("s1")
        submachine("M") {
            initial(m0)
            accepting(m0)
            accepting(m1)
        }
        submachine("S") {
            initial(s0)
            accepting(s1)
        }
        // direct path: m0 -x-> m1
        consume(m0, sym('x'), m1)
        // submachine path: m0 calls S (which consumes x), returns to m1
        call(m0, "S", m1)
        consume(s0, sym('x'), s1)
        main("M")
    }

    @Test
    fun `dsl submachine call accepts x`() {
        val automaton = specToAutomaton(xSpec())
        val result = automaton.recognize(listOf(CharSymbol('x')))
        assertTrue(result.accepted)
    }

    @Test
    fun `dsl submachine call rejects empty`() {
        val automaton = specToAutomaton(xSpec())
        assertFalse(automaton.recognize(emptyList()).accepted)
    }
}
