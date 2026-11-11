// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private object C0 : State() {
    override fun toString(): String = "c0"
}

private object C1 : State() {
    override fun toString(): String = "c1"
}

/**
 * The call family against the new submachine filter: the wildcard, the named
 * filter and the domain, and the additions that fan out over a domain.
 */
class SubmachineFilterTest {

    /** A mapping with two call edges, one to M and one to N. */
    private fun mapping(): TransitionMapping {
        val m = TransitionMapping()
        m.add(
            TransitionEdge(
                source = C0,
                target = C1,
                transition = Transition.Call(submachine = "M"),
            ),
        )
        m.add(
            TransitionEdge(
                source = C0,
                target = C1,
                transition = Transition.Call(submachine = "N"),
            ),
        )
        return m
    }

    @Test
    fun `the wildcard matches every call edge`() {
        val ea = ElementaryActions(mapping())

        assertEquals(2, ea.queryCall().size)
        assertEquals(2, ea.queryCall(submachine = SubmachineRef.Any).size)
    }

    @Test
    fun `a named filter matches the calls to that submachine`() {
        val ea = ElementaryActions(mapping())

        assertEquals(1, ea.queryCall(submachine = SubmachineRef.Named("M")).size)
        assertEquals(1, ea.queryCall(submachine = SubmachineRef.Named("N")).size)
        assertEquals(0, ea.queryCall(submachine = SubmachineRef.Named("P")).size)
    }

    @Test
    fun `a domain matches the calls to any of its submachines`() {
        val ea = ElementaryActions(mapping())

        assertEquals(2, ea.queryCall(submachine = SubmachineRef.anyOf("M", "N")).size)
        assertEquals(1, ea.queryCall(submachine = SubmachineRef.anyOf("M")).size)
        assertEquals(1, ea.queryCall(submachine = SubmachineRef.anyOf("N", "P")).size)
    }

    @Test
    fun `a removal over a domain removes the calls it names`() {
        val m = mapping()
        val ea = ElementaryActions(m)

        ea.removeCall(submachine = SubmachineRef.anyOf("M", "N"))

        assertEquals(0, m.size())
    }

    @Test
    fun `a removal over a domain leaves the calls it does not name`() {
        val m = mapping()
        val ea = ElementaryActions(m)

        ea.removeCall(submachine = SubmachineRef.Named("M"))

        assertEquals(1, m.size())
        assertEquals("N", (m.getTransitions().single().transition as Transition.Call).submachine)
    }

    @Test
    fun `the call family never selects a symbol transition`() {
        val m = mapping()
        m.add(
            TransitionEdge(
                source = C0,
                target = C1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            ),
        )
        val ea = ElementaryActions(m)

        assertEquals(2, ea.queryCall().size)
        assertEquals(1, ea.query().size)
    }

    @Test
    fun `an addition over a domain adds one edge per submachine`() {
        val m = TransitionMapping()
        val ea = ElementaryActions(m)

        ea.addCall(C0, SubmachineRef.anyOf("M", "N"), C1)

        assertEquals(2, m.size())
        assertEquals(
            setOf("M", "N"),
            m.getTransitions().map { (it.transition as Transition.Call).submachine }.toSet(),
        )
    }

    @Test
    fun `an addition over a domain reports one operation per edge`() {
        val m = TransitionMapping()
        val operations = mutableListOf<MappingOperation>()
        m.onOperation = { operations.add(it) }
        val ea = ElementaryActions(m)

        ea.addCall(C0, SubmachineRef.anyOf("M", "N"), C1)

        assertEquals(2, operations.size)
        assertTrue(operations.all { it is MappingOperation.Add })
        assertEquals(
            setOf("M", "N"),
            operations.map { (it.pattern.submachine as SubmachineRef.Named).name }.toSet(),
        )
    }

    @Test
    fun `an addition over a domain crosses the state domains`() {
        val m = TransitionMapping()
        val ea = ElementaryActions(m)

        ea.addCall(State.anyOf(C0, C1), SubmachineRef.anyOf("M", "N"), C0)

        assertEquals(4, m.size())
    }

    @Test
    fun `an addition rejects the wildcard and the empty filter`() {
        val ea = ElementaryActions(TransitionMapping())

        assertThrows<IllegalArgumentException> { ea.addCall(C0, SubmachineRef.Any, C1) }
        assertThrows<IllegalArgumentException> { ea.addCall(C0, SubmachineRef.None, C1) }
    }

    @Test
    fun `a call query rejects the empty filter`() {
        val ea = ElementaryActions(mapping())

        assertThrows<IllegalArgumentException> { ea.queryCall(submachine = SubmachineRef.None) }
        assertThrows<IllegalArgumentException> { ea.removeCall(submachine = SubmachineRef.None) }
    }

    @Test
    fun `the builder blocks carry the same filter`() {
        val ea = ElementaryActions(mapping())

        assertEquals(1, ea.queryCall { submachine = SubmachineRef.Named("M") }.size)
        assertEquals(2, ea.queryCall { submachine = SubmachineRef.anyOf("M", "N") }.size)
        assertEquals(2, ea.queryCall { }.size)
    }
}
