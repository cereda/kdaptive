// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.dsl.automaton
import br.usp.poli.lta.cereda.kdaptive.dsl.specToAutomaton
import br.usp.poli.lta.cereda.kdaptive.dsl.sym
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.MappingOperation
import br.usp.poli.lta.cereda.kdaptive.model.Observation
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.SubmachineRef
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.TransitionPatternKind
import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.sets.SubmachinesSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A fact published by action code, carrying something only the action knows. */
private data class DomainSize(val states: Int) : Observation

/** A fact published by action code, carrying a plain payload. */
private data class Note(val text: String) : Observation

class InstrumentationTest {

    private object Q0 : State() {
        override fun toString(): String = "q0"
    }

    private object Q1 : State() {
        override fun toString(): String = "q1"
    }

    private object G0 : State() {
        override fun toString(): String = "g0"
    }

    @BeforeEach
    fun resetIds() {
        IdentifierUtils.reset()
    }

    // -----------------------------------------------------------------------
    // fixtures
    // -----------------------------------------------------------------------

    /** Builds a symbol edge between two states. */
    private fun edge(source: State, target: State, symbol: Char): TransitionEdge = TransitionEdge(
        source = source,
        target = target,
        transition = Transition.SymbolTransition(symbol = CharSymbol(symbol)),
    )

    /** A mapping holding three symbol transitions: q0 -a-> q1, q1 -b-> q1, q1 -a-> q0. */
    private fun mapping(): TransitionMapping {
        val m = TransitionMapping()
        m.add(edge(Q0, Q1, 'a'))
        m.add(edge(Q1, Q1, 'b'))
        m.add(edge(Q1, Q0, 'a'))
        return m
    }

    /** An automaton whose adaptive action queries, removes and adds transitions. */
    private fun instrumentedSpec() = automaton {
        val q0 = state("q0")
        val q1 = state("q1")
        val g0 = state("g0")
        submachine("M") {
            initial(q0)
            state(q1)
            state(g0)
            accepting(q0)
        }
        action("Work") { _, _ ->
            val matched = ea.query {
                symbol = sym('b')
                target = q1
            }
            observe(DomainSize(matched.size))
            ea.remove {
                symbol = sym('b')
                target = q1
            }
            ea.add(q1, sym('z'), g0)
        }
        consume(q0, sym('a'), q1, prior = "Work")
        consume(q1, sym('b'), q1)
        consume(q1, sym('a'), q0)
        main("M")
    }

    /** An automaton that forks into two children, each running a mutating action. */
    private fun forkingSpec() = automaton {
        val q0 = state("q0")
        val q1 = state("q1")
        val q2 = state("q2")
        submachine("M") {
            initial(q0)
            state(q1)
            state(q2)
            accepting(q1)
        }
        action("Trim") { _, _ ->
            ea.remove { symbol = sym('a') }
            observe(Note("trimmed"))
        }
        consume(q0, sym('a'), q1, prior = "Trim")
        consume(q0, sym('a'), q2, prior = "Trim")
        main("M")
    }

    // -----------------------------------------------------------------------
    // operations reported by the elementary actions
    // -----------------------------------------------------------------------

    @Test
    fun `a query reports the pattern the matched edges and the mapping size`() {
        val m = mapping()
        val operations = mutableListOf<MappingOperation>()
        m.onOperation = { operations.add(it) }

        val ea = ElementaryActions(m)
        val matched = ea.query(State.Any, CharSymbol('a'), State.Any)

        assertEquals(2, matched.size)
        assertEquals(1, operations.size)

        val query = operations.single() as MappingOperation.Query
        assertEquals(TransitionPatternKind.SYMBOL, query.pattern.kind)
        assertEquals(CharSymbol('a'), query.pattern.symbol)
        assertEquals(matched, query.edges)
        assertEquals(3, query.mappingSizeBefore)
        assertEquals(3, query.mappingSizeAfter)
    }

    @Test
    fun `a query over submachine calls reports the submachine family`() {
        val m = mapping()
        val operations = mutableListOf<MappingOperation>()
        m.onOperation = { operations.add(it) }

        ElementaryActions(m).queryCall(State.Any, SubmachineRef.Any, State.Any)

        val query = operations.single() as MappingOperation.Query
        assertEquals(TransitionPatternKind.SUBMACHINE, query.pattern.kind)
        assertEquals(0, query.edges.size)
    }

    @Test
    fun `a removal reports the edges it took out and the sizes around it`() {
        val m = mapping()
        val operations = mutableListOf<MappingOperation>()
        m.onOperation = { operations.add(it) }

        ElementaryActions(m).remove(State.Any, CharSymbol('a'), State.Any)

        assertEquals(1, operations.size)

        val removal = operations.single() as MappingOperation.Remove
        assertEquals(2, removal.edges.size)
        assertEquals(3, removal.mappingSizeBefore)
        assertEquals(1, removal.mappingSizeAfter)
        assertEquals(1, m.size())
    }

    @Test
    fun `a removal that matches nothing is still reported`() {
        val m = mapping()
        val operations = mutableListOf<MappingOperation>()
        m.onOperation = { operations.add(it) }

        ElementaryActions(m).remove(State.Any, CharSymbol('z'), State.Any)

        val removal = operations.single() as MappingOperation.Remove
        assertEquals(0, removal.edges.size)
        assertEquals(3, removal.mappingSizeBefore)
        assertEquals(3, removal.mappingSizeAfter)
        assertEquals(3, m.size())
    }

    @Test
    fun `an addition reports the edge it inserted and the sizes around it`() {
        val m = mapping()
        val operations = mutableListOf<MappingOperation>()
        m.onOperation = { operations.add(it) }

        ElementaryActions(m).add(Q0, CharSymbol('z'), G0)

        assertEquals(1, operations.size)

        val addition = operations.single() as MappingOperation.Add
        assertEquals(3, addition.mappingSizeBefore)
        assertEquals(4, addition.mappingSizeAfter)
        assertEquals(1, addition.edges.size)
        assertEquals(addition.edge, addition.edges.single())
        assertEquals(TransitionPatternKind.SYMBOL, addition.pattern.kind)
        assertEquals(CharSymbol('z'), addition.pattern.symbol)
    }

    @Test
    fun `no operation is reported when no observer is registered`() {
        val m = mapping()
        val ea = ElementaryActions(m)
        ea.query(State.Any, Symbol.Any, State.Any)
        ea.remove(State.Any, CharSymbol('a'), State.Any)
        ea.add(Q0, CharSymbol('z'), G0)
        assertEquals(2, m.size())
    }

    // -----------------------------------------------------------------------
    // operations delivered to visitors
    // -----------------------------------------------------------------------

    @Test
    fun `a visitor receives the operations performed by an action`() {
        val operations = mutableListOf<Pair<Int, MappingOperation>>()
        val visitor = object : ExecutionVisitor {
            override fun onMappingOperation(pathId: Int, operation: MappingOperation) {
                operations.add(pathId to operation)
            }
        }

        val a = specToAutomaton(instrumentedSpec())
        a.visitors.add(visitor)

        assertTrue(a.recognize(listOf(CharSymbol('a'), CharSymbol('a'))))

        assertEquals(3, operations.size)

        val kinds = operations.map { it.second::class.simpleName }
        assertEquals(listOf("Query", "Remove", "Add"), kinds)

        val pathIds = operations.map { it.first }.distinct()
        assertEquals(1, pathIds.size)

        val query = operations[0].second as MappingOperation.Query
        assertEquals(3, query.mappingSizeBefore)
        assertEquals(1, query.edges.size)

        val removal = operations[1].second as MappingOperation.Remove
        assertEquals(3, removal.mappingSizeBefore)
        assertEquals(2, removal.mappingSizeAfter)

        val addition = operations[2].second as MappingOperation.Add
        assertEquals(2, addition.mappingSizeBefore)
        assertEquals(3, addition.mappingSizeAfter)
    }

    @Test
    fun `a visitor receives the facts published by an action`() {
        val observations = mutableListOf<Pair<Int, Observation>>()
        val visitor = object : ExecutionVisitor {
            override fun onObservation(pathId: Int, observation: Observation) {
                observations.add(pathId to observation)
            }
        }

        val a = specToAutomaton(instrumentedSpec())
        a.visitors.add(visitor)

        assertTrue(a.recognize(listOf(CharSymbol('a'), CharSymbol('a'))))

        assertEquals(1, observations.size)
        val (pathId, observation) = observations.single()
        assertEquals(DomainSize(1), observation)
        assertTrue(pathId > 0)
    }

    @Test
    fun `the operation channel and the topology channel are distinct`() {
        val operations = mutableListOf<MappingOperation>()
        val removals = mutableListOf<TransitionEdge>()
        val visitor = object : ExecutionVisitor {
            override fun onMappingOperation(pathId: Int, operation: MappingOperation) {
                operations.add(operation)
            }

            override fun onEdgeRemoved(
                pathId: Int,
                edge: TransitionEdge,
                transitions: TransitionMapping,
                submachines: SubmachinesSet,
            ) {
                removals.add(edge)
            }
        }

        val a = specToAutomaton(instrumentedSpec())
        a.visitors.add(visitor)

        assertTrue(a.recognize(listOf(CharSymbol('a'), CharSymbol('a'))))

        // One removal operation, one edge removed.
        assertEquals(1, operations.count { it is MappingOperation.Remove })
        assertEquals(1, removals.size)
    }

    @Test
    fun `operations and facts of forked paths stay isolated`() {
        val operations = mutableListOf<Pair<Int, MappingOperation>>()
        val observations = mutableListOf<Pair<Int, Observation>>()
        val visitor = object : ExecutionVisitor {
            override fun onMappingOperation(pathId: Int, operation: MappingOperation) {
                operations.add(pathId to operation)
            }

            override fun onObservation(pathId: Int, observation: Observation) {
                observations.add(pathId to observation)
            }
        }

        val a = specToAutomaton(forkingSpec())
        a.visitors.add(visitor)

        a.recognize(listOf(CharSymbol('a')))

        // Each of the two children trimmed its own copy of the mapping.
        assertEquals(2, operations.size)
        assertEquals(2, observations.size)
        assertEquals(2, operations.map { it.first }.distinct().size)
        assertEquals(2, observations.map { it.first }.distinct().size)

        // The two children removed the same two edges from their own copies.
        operations.forEach { (_, operation) ->
            val removal = operation as MappingOperation.Remove
            assertEquals(2, removal.edges.size)
            assertEquals(2, removal.mappingSizeBefore)
            assertEquals(0, removal.mappingSizeAfter)
        }
    }

    @Test
    fun `the topology channel still reports every edge that changes`() {
        val added = mutableListOf<TransitionEdge>()
        val removed = mutableListOf<TransitionEdge>()
        val visitor = object : ExecutionVisitor {
            override fun onEdgeAdded(
                pathId: Int,
                edge: TransitionEdge,
                transitions: TransitionMapping,
                submachines: SubmachinesSet,
            ) {
                added.add(edge)
            }

            override fun onEdgeRemoved(
                pathId: Int,
                edge: TransitionEdge,
                transitions: TransitionMapping,
                submachines: SubmachinesSet,
            ) {
                removed.add(edge)
            }
        }

        val a = specToAutomaton(instrumentedSpec())
        a.visitors.add(visitor)

        assertTrue(a.recognize(listOf(CharSymbol('a'), CharSymbol('a'))))

        assertEquals(1, added.size)
        assertEquals(1, removed.size)
        assertNotEquals(added.single().id, removed.single().id)
    }
}
