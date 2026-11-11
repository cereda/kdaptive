// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.SubmachineRef
import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private object D0 : State() {
    override fun toString(): String = "d0"
}

private object D1 : State() {
    override fun toString(): String = "d1"
}

private object D2 : State() {
    override fun toString(): String = "d2"
}

/**
 * The set-valued domains of the elementary actions.
 *
 * A domain names several states or symbols at once: a filter over a domain
 * selects every member of it, and an addition over domains produces the
 * Cartesian product of its domains.
 */
class DomainActionsTest {

    /** A mapping with two symbol edges, one epsilon edge and one call edge. */
    private fun mapping(): TransitionMapping {
        val m = TransitionMapping()
        m.add(
            TransitionEdge(
                source = D0,
                target = D1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            ),
        )
        m.add(
            TransitionEdge(
                source = D1,
                target = D2,
                transition = Transition.SymbolTransition(symbol = CharSymbol('b')),
            ),
        )
        m.add(
            TransitionEdge(
                source = D2,
                target = D0,
                transition = Transition.Epsilon,
            ),
        )
        m.add(
            TransitionEdge(
                source = D0,
                target = D1,
                transition = Transition.Call(submachine = "M"),
            ),
        )
        return m
    }

    @Test
    fun `a state domain selects the edges whose endpoint it names`() {
        val ea = ElementaryActions(mapping())

        val domain = State.anyOf(D0, D1)
        assertEquals(1, ea.query(source = domain, symbol = CharSymbol('a')).size)
        assertEquals(1, ea.query(source = domain, symbol = CharSymbol('b')).size)
        assertEquals(0, ea.query(source = State.anyOf(D1, D2), symbol = CharSymbol('a')).size)
    }

    @Test
    fun `a symbol domain selects the edges consuming one of its members`() {
        val ea = ElementaryActions(mapping())

        val domain = Symbol.anyOf(CharSymbol('a'), CharSymbol('b'))
        assertEquals(2, ea.query(symbol = domain).size)
        assertEquals(1, ea.query(symbol = Symbol.anyOf(CharSymbol('b'))).size)
    }

    @Test
    fun `a domain naming the empty word selects the epsilon edges as well`() {
        val ea = ElementaryActions(mapping())

        val domain = Symbol.anyOf(CharSymbol('a'), Symbol.Epsilon)
        // The 'a' edge and the epsilon edge.
        assertEquals(2, ea.query(symbol = domain).size)
        // The epsilon edge alone.
        assertEquals(1, ea.query(symbol = Symbol.anyOf(Symbol.Epsilon)).size)
    }

    @Test
    fun `the family of a pattern follows the domain it carries`() {
        val ea = ElementaryActions(mapping())
        val operations = mutableListOf<MappingOperation>()
        val m = mapping()
        m.onOperation = { operations.add(it) }
        val ea2 = ElementaryActions(m)

        ea2.query(symbol = Symbol.anyOf(CharSymbol('a'), CharSymbol('b')))
        ea2.query(symbol = Symbol.anyOf(CharSymbol('a'), Symbol.Epsilon))
        ea2.query(symbol = Symbol.anyOf(Symbol.Epsilon))

        val kinds = operations.map { (it as MappingOperation.Query).pattern.kind }
        assertEquals(
            listOf(
                TransitionPatternKind.SYMBOL,
                TransitionPatternKind.MIXED,
                TransitionPatternKind.EPSILON,
            ),
            kinds,
        )
        // The first query is not affected by the domain of the second one.
        assertEquals(1, ea.query(symbol = CharSymbol('a')).size)
    }

    @Test
    fun `a removal over a domain removes every matching edge`() {
        val m = mapping()
        val ea = ElementaryActions(m)

        ea.remove(symbol = Symbol.anyOf(CharSymbol('a'), CharSymbol('b')))

        // The two symbol edges are gone; the epsilon and the call edges remain.
        assertEquals(2, m.size())
        assertEquals(1, ea.query(symbol = Symbol.Epsilon).size)
        assertEquals(1, ea.queryCall().size)
    }

    @Test
    fun `an addition over domains produces the cartesian product`() {
        val m = TransitionMapping()
        val ea = ElementaryActions(m)

        ea.add(State.anyOf(D0, D1), CharSymbol('a'), State.anyOf(D1, D2))

        // Two sources times one symbol times two targets.
        assertEquals(4, m.size())
        assertEquals(1, ea.query(source = D0, symbol = CharSymbol('a'), target = D1).size)
        assertEquals(1, ea.query(source = D0, symbol = CharSymbol('a'), target = D2).size)
        assertEquals(1, ea.query(source = D1, symbol = CharSymbol('a'), target = D1).size)
        assertEquals(1, ea.query(source = D1, symbol = CharSymbol('a'), target = D2).size)
    }

    @Test
    fun `an addition over a domain naming the empty word produces an epsilon edge`() {
        val m = TransitionMapping()
        val ea = ElementaryActions(m)

        ea.add(D0, Symbol.anyOf(CharSymbol('a'), Symbol.Epsilon), D1)

        assertEquals(2, m.size())
        assertEquals(1, ea.query(source = D0, symbol = CharSymbol('a')).size)
        assertEquals(1, ea.query(source = D0, symbol = Symbol.Epsilon).size)
    }

    @Test
    fun `an addition over domains reports one operation per edge`() {
        val m = TransitionMapping()
        val operations = mutableListOf<MappingOperation>()
        m.onOperation = { operations.add(it) }
        val ea = ElementaryActions(m)

        ea.add(State.anyOf(D0, D1), Symbol.anyOf(CharSymbol('a'), CharSymbol('b')), D1)

        assertEquals(4, operations.size)
        assertTrue(operations.all { it is MappingOperation.Add })
        // Each operation reports the edge it inserted, and the sizes of the
        // mapping before and after the insertion.
        assertEquals(listOf(0, 1, 2, 3), operations.map { it.mappingSizeBefore })
        assertEquals(listOf(1, 2, 3, 4), operations.map { it.mappingSizeAfter })
    }

    @Test
    fun `a call addition over domains produces the cartesian product`() {
        val m = TransitionMapping()
        val operations = mutableListOf<MappingOperation>()
        m.onOperation = { operations.add(it) }
        val ea = ElementaryActions(m)

        ea.addCall(State.anyOf(D0, D1), SubmachineRef.Named("M"), State.anyOf(D1, D2))

        assertEquals(4, m.size())
        assertEquals(4, operations.size)
        assertEquals(4, ea.queryCall().size)
    }

    @Test
    fun `the pattern reports the size of every domain`() {
        val m = mapping()
        val operations = mutableListOf<MappingOperation>()
        m.onOperation = { operations.add(it) }
        val ea = ElementaryActions(m)

        ea.query(
            source = State.anyOf(D0, D1),
            symbol = Symbol.anyOf(CharSymbol('a'), CharSymbol('b'), Symbol.Epsilon),
            target = D2,
            prior = ActionRef.None,
            post = ActionRef.Named("A"),
        )

        val pattern = (operations.single() as MappingOperation.Query).pattern
        assertEquals(2, pattern.sourceDomainSize)
        assertEquals(3, pattern.symbolDomainSize)
        assertEquals(1, pattern.targetDomainSize)
        assertEquals(1, pattern.submachineDomainSize)
        assertEquals(1, pattern.priorDomainSize)
        assertEquals(1, pattern.postDomainSize)
    }

    @Test
    fun `a wildcard filter has an unbounded domain`() {
        val m = mapping()
        val operations = mutableListOf<MappingOperation>()
        m.onOperation = { operations.add(it) }
        val ea = ElementaryActions(m)

        ea.query()

        val pattern = (operations.single() as MappingOperation.Query).pattern
        assertNull(pattern.sourceDomainSize)
        assertNull(pattern.symbolDomainSize)
        assertNull(pattern.targetDomainSize)
        assertNull(pattern.priorDomainSize)
        assertNull(pattern.postDomainSize)
        // The submachine filter does not apply to the symbol family.
        assertEquals(1, pattern.submachineDomainSize)
    }

    @Test
    fun `the call family reports the size of the submachine filter`() {
        val m = mapping()
        val operations = mutableListOf<MappingOperation>()
        m.onOperation = { operations.add(it) }
        val ea = ElementaryActions(m)

        ea.queryCall(submachine = SubmachineRef.Named("M"))
        ea.queryCall()

        val named = (operations[0] as MappingOperation.Query).pattern
        val any = (operations[1] as MappingOperation.Query).pattern
        assertEquals(1, named.submachineDomainSize)
        assertNull(any.submachineDomainSize)
        // The symbol filter does not apply to the call family.
        assertEquals(1, named.symbolDomainSize)
    }

    @Test
    fun `a domain cannot name the wildcard`() {
        val error =
            assertThrows<IllegalArgumentException> {
                State.anyOf(D0, State.Any)
            }
        assertTrue(error.message!!.contains("wildcard"))

        val symbolError =
            assertThrows<IllegalArgumentException> {
                Symbol.anyOf(CharSymbol('a'), Symbol.Any)
            }
        assertTrue(symbolError.message!!.contains("wildcard"))
    }

    @Test
    fun `a domain cannot be empty`() {
        assertThrows<IllegalArgumentException> { State.anyOf() }
        assertThrows<IllegalArgumentException> { Symbol.anyOf() }
    }

    @Test
    fun `a domain cannot contain another domain`() {
        val domain = State.anyOf(D0, D1)
        assertThrows<IllegalArgumentException> { State.anyOf(D0, domain) }
        assertThrows<IllegalArgumentException> { Symbol.anyOf(CharSymbol('a'), Symbol.anyOf(CharSymbol('b'))) }
    }

    @Test
    fun `two domains naming the same members are equal`() {
        assertEquals(State.anyOf(D0, D1), State.anyOf(D1, D0))
        assertEquals(State.anyOf(D0, D1).hashCode(), State.anyOf(D1, D0).hashCode())
        assertEquals(Symbol.anyOf(CharSymbol('a')), Symbol.anyOf(CharSymbol('a')))
    }

    @Test
    fun `a domain is not a concrete state`() {
        val domain = State.anyOf(D0, D1)
        val error =
            assertThrows<IllegalArgumentException> {
                TopologyValidator.validate(
                    edges =
                    listOf(
                        TransitionEdge(
                            source = D0,
                            target = domain,
                            transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                        ),
                    ),
                    submachines =
                    listOf(
                        Submachine(
                            name = "M",
                            states = setOf(D0, D1, D2),
                            initialState = D0,
                            acceptingStates = setOf(D1),
                        ),
                    ),
                    mainSubmachine = "M",
                )
            }
        assertTrue(error.message!!.contains("domain"))
    }

    @Test
    fun `a symbol transition consumes a concrete symbol`() {
        val error =
            assertThrows<IllegalArgumentException> {
                Transition.SymbolTransition(symbol = Symbol.anyOf(CharSymbol('a')))
            }
        assertNotNull(error.message)
    }
}
