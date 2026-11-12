// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The validation of the structural bounds.
 *
 * A bound of zero or less would make the engine refuse everything, and a
 * bound nobody can satisfy is a mistake in the configuration, not a runtime
 * condition: the limits are rejected when they are built.
 */
class RecognitionLimitsTest {

    @Test
    fun `the defaults are positive`() {
        val limits = RecognitionLimits()

        assertTrue(limits.maxLivePaths > 0)
        assertTrue(limits.maxStepsPerPath > 0)
        assertTrue(limits.maxCallDepth > 0)
    }

    @Test
    fun `a bound of zero live paths is rejected`() {
        val error = assertThrows<IllegalArgumentException> { RecognitionLimits(maxLivePaths = 0) }

        assertTrue(error.message!!.contains("live paths must be positive"))
    }

    @Test
    fun `a negative bound of live paths is rejected`() {
        val error = assertThrows<IllegalArgumentException> { RecognitionLimits(maxLivePaths = -1) }

        assertTrue(error.message!!.contains("live paths must be positive"))
    }

    @Test
    fun `a bound of zero steps is rejected`() {
        val error = assertThrows<IllegalArgumentException> { RecognitionLimits(maxStepsPerPath = 0) }

        assertTrue(error.message!!.contains("steps per path must be positive"))
    }

    @Test
    fun `a negative bound of steps is rejected`() {
        val error = assertThrows<IllegalArgumentException> { RecognitionLimits(maxStepsPerPath = -5) }

        assertTrue(error.message!!.contains("steps per path must be positive"))
    }

    @Test
    fun `a bound of zero call depth is rejected`() {
        val error = assertThrows<IllegalArgumentException> { RecognitionLimits(maxCallDepth = 0) }

        assertTrue(error.message!!.contains("call depth must be positive"))
    }

    @Test
    fun `a negative bound of call depth is rejected`() {
        val error = assertThrows<IllegalArgumentException> { RecognitionLimits(maxCallDepth = -3) }

        assertTrue(error.message!!.contains("call depth must be positive"))
    }

    @Test
    fun `the limits are a value`() {
        val limits = RecognitionLimits(maxLivePaths = 1, maxStepsPerPath = 2, maxCallDepth = 3)
        val same = RecognitionLimits(maxLivePaths = 1, maxStepsPerPath = 2, maxCallDepth = 3)

        assertEquals(limits, same)
        assertEquals(limits.hashCode(), same.hashCode())
    }
}
