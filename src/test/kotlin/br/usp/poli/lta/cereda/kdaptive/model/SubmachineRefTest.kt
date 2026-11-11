// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The submachine reference: the filter that replaced the null submachine
 * filter, and the domain that made the call family set-valued.
 */
class SubmachineRefTest {

    @Test
    fun `the markers are singletons`() {
        assertSame(SubmachineRef.Any, SubmachineRef.Any)
        assertSame(SubmachineRef.None, SubmachineRef.None)
    }

    @Test
    fun `a named reference carries its name`() {
        assertEquals("M", SubmachineRef.Named("M").name)
        assertEquals(SubmachineRef.Named("M"), SubmachineRef.Named("M"))
        assertNotEquals(SubmachineRef.Named("M"), SubmachineRef.Named("N"))
    }

    @Test
    fun `a domain carries its names`() {
        val domain = SubmachineRef.Domain(setOf("M", "N"))

        assertEquals(2, domain.size)
        assertEquals(setOf("M", "N"), domain.names)
    }

    @Test
    fun `two domains are equal when they name the same submachines`() {
        assertEquals(SubmachineRef.Domain(setOf("M", "N")), SubmachineRef.Domain(setOf("N", "M")))
        assertNotEquals(SubmachineRef.Domain(setOf("M")), SubmachineRef.Domain(setOf("N")))
    }

    @Test
    fun `anyOf builds a domain over the given names`() {
        val domain = SubmachineRef.anyOf("M", "N")

        assertEquals(SubmachineRef.Domain(setOf("M", "N")), domain)
        assertEquals(2, domain.size)
    }

    @Test
    fun `a domain must name at least one submachine`() {
        assertThrows<IllegalArgumentException> { SubmachineRef.Domain(emptySet()) }
        assertThrows<IllegalArgumentException> { SubmachineRef.anyOf() }
    }

    @Test
    fun `a domain of one name is still a domain`() {
        val domain = SubmachineRef.anyOf("M")

        assertEquals(1, domain.size)
        assertNotEquals(SubmachineRef.Named("M"), domain)
    }

    @Test
    fun `the references render as readable text`() {
        assertTrue(SubmachineRef.Named("M").toString().contains("M"))
        assertTrue(SubmachineRef.Domain(setOf("M", "N")).toString().contains("M"))
    }
}
