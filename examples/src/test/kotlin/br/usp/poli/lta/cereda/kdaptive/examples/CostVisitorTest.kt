// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.MappingOperation
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.TransitionPattern
import br.usp.poli.lta.cereda.kdaptive.model.TransitionPatternKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The consumer-side cost model.
 *
 * The model is the one re-derived for version 2.1.0: it prices the scan of
 * the mapping, and multiplies it by the number of edge shapes the pattern of
 * the operation may touch.
 */
class CostVisitorTest {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")

    /** An edge, used as the outcome of an operation. */
    private fun edge(source: State = q0, target: State = q1): TransitionEdge = TransitionEdge(
        source = source,
        target = target,
        transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
    )

    /** A pattern over finite filters: two states, one symbol, one target. */
    private fun finitePattern(): TransitionPattern = TransitionPattern(
        kind = TransitionPatternKind.SYMBOL,
        source = State.anyOf(q0, q1),
        symbol = CharSymbol('a'),
        target = q2,
        prior = ActionRef.None,
        post = ActionRef.None,
    )

    /** A pattern carrying a wildcard: one of its filters is unbounded. */
    private fun unboundedPattern(): TransitionPattern = TransitionPattern(
        kind = TransitionPatternKind.SYMBOL,
        source = State.Any,
        symbol = CharSymbol('a'),
        target = q2,
        prior = ActionRef.None,
        post = ActionRef.None,
    )

    @Test
    fun `a query costs the scan of the mapping`() {
        val visitor = CostVisitor()
        val operation = MappingOperation.Query(finitePattern(), listOf(edge()), 10)

        visitor.onMappingOperation(1, operation)

        // ω(10) = 5: the query scans the mapping once.
        assertEquals(5.0, visitor.cost)
    }

    @Test
    fun `a removal costs the scan of every shape it may touch`() {
        val visitor = CostVisitor()
        val operation = MappingOperation.Remove(finitePattern(), listOf(edge()), 10, 8)

        visitor.onMappingOperation(1, operation)

        // The pattern names 2 states, 1 symbol, 1 target and 1 action on each
        // slot: the product is 2. The removal costs 2·ω(10) − 2 = 8.
        assertEquals(8.0, visitor.cost)
    }

    @Test
    fun `an addition costs the scan of every shape it may touch`() {
        val visitor = CostVisitor()
        val operation = MappingOperation.Add(finitePattern(), edge(), 10, 11)

        visitor.onMappingOperation(1, operation)

        // The addition costs 2·ω(10) + 2 = 12.
        assertEquals(12.0, visitor.cost)
    }

    @Test
    fun `a wildcard filter makes the shapes unbounded`() {
        val visitor = CostVisitor()
        val operation = MappingOperation.Remove(unboundedPattern(), listOf(edge()), 10, 8)

        visitor.onMappingOperation(1, operation)

        // The source filter is unbounded: the number of shapes it admits is
        // not a finite number, so the model does not price them.
        assertEquals(0.0, visitor.cost)
    }

    @Test
    fun `a domain of states multiplies the price of an operation`() {
        val visitor = CostVisitor()
        val narrow =
            MappingOperation.Remove(
                finitePattern().copy(source = q0),
                listOf(edge()),
                10,
                8,
            )
        val wide =
            MappingOperation.Remove(
                finitePattern().copy(source = State.anyOf(q0, q1)),
                listOf(edge()),
                10,
                8,
            )

        visitor.onMappingOperation(1, narrow)
        visitor.onMappingOperation(1, wide)

        // One state: 1·ω(10) − 1 = 4. Two states: 2·ω(10) − 2 = 8.
        assertEquals(12.0, visitor.cost)
    }

    @Test
    fun `the cost of the operations is kept apart by path`() {
        val visitor = CostVisitor()
        val operation = MappingOperation.Query(finitePattern(), listOf(edge()), 10)

        visitor.onMappingOperation(1, operation)
        visitor.onMappingOperation(2, operation)

        assertEquals(10.0, visitor.cost)
        assertEquals(mapOf(1 to 5.0, 2 to 5.0), visitor.costPerPath())
    }

    @Test
    fun `the report counts the operations and their cost`() {
        val visitor = CostVisitor()
        val operation = MappingOperation.Query(finitePattern(), listOf(edge()), 10)

        visitor.onMappingOperation(1, operation)

        val report = visitor.report()
        assertEquals(true, report.contains("queries:    1"))
        assertEquals(true, report.contains("Total cost:       5.0"))
    }

    @Test
    fun `the symbol family ignores the submachine filter`() {
        val visitor = CostVisitor()
        val pattern =
            TransitionPattern(
                kind = TransitionPatternKind.SYMBOL,
                source = q0,
                symbol = Symbol.Any,
                target = q1,
                prior = ActionRef.None,
                post = ActionRef.None,
            )

        visitor.onMappingOperation(1, MappingOperation.Query(pattern, listOf(edge()), 4))

        // ω(4) = 2: the query scans the mapping once.
        assertEquals(2.0, visitor.cost)
    }
}
