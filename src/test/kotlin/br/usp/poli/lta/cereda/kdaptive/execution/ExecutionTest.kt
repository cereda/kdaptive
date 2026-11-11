// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils
import br.usp.poli.lta.cereda.kdaptive.utils.PathOutcome
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExecutionTest {
    @BeforeEach
    fun resetIds() {
        IdentifierUtils.reset()
    }

    private val aa = EvenAsAutomaton()

    @Test
    fun `recognises empty string (zero a's is even)`() {
        assertTrue(aa.recognize(emptyList()).accepted)
    }

    @Test
    fun `recognises aa`() {
        assertTrue(aa.recognize(aa.symbols('a', 'a')).accepted)
    }

    @Test
    fun `rejects single a`() {
        assertFalse(aa.recognize(aa.symbols('a')).accepted)
    }

    @Test
    fun `rejects ab (one a)`() {
        assertFalse(aa.recognize(aa.symbols('a', 'b')).accepted)
    }

    @Test
    fun `recognises aab (two a's)`() {
        assertTrue(aa.recognize(aa.symbols('a', 'a', 'b')).accepted)
    }

    @Test
    fun `recognises bbbb (zero a's)`() {
        assertTrue(aa.recognize(aa.symbols('b', 'b', 'b', 'b')).accepted)
    }

    @Test
    fun `rejects aaa (three a's)`() {
        assertFalse(aa.recognize(aa.symbols('a', 'a', 'a')).accepted)
    }

    @Test
    fun `recognition paths are recorded`() {
        val result = aa.recognize(aa.symbols('a', 'a'))
        val paths = result.paths
        assertTrue(paths.isNotEmpty())
        assertTrue(paths.any { it.outcome == PathOutcome.ACCEPTED })
    }
}
