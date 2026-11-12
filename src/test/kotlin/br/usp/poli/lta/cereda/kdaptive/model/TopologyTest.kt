// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The topology snapshot: what the engine hands to its observers.
 *
 * A snapshot describes a topology at one moment and never changes. It is
 * the *only* way to ask a topology about itself — the live mapping no
 * longer carries a filter vocabulary of its own.
 */
class TopologyTest {

    private object G0 : State() {
        override fun toString(): String = "g0"
    }

    private object G1 : State() {
        override fun toString(): String = "g1"
    }

    private object G2 : State() {
        override fun toString(): String = "g2"
    }

    /** A state no submachine of this class owns. */
    private object G3 : State() {
        override fun toString(): String = "g3"
    }

    private val symbolA = CharSymbol('a')
    private val symbolB = CharSymbol('b')

    /** The mapping every query of this class is asked of. */
    private fun mapping(): TransitionMapping {
        val m = TransitionMapping()
        m.add(
            TransitionEdge(
                source = G0,
                target = G1,
                transition = Transition.SymbolTransition(symbol = symbolA),
                prior = ActionRef.Bound(name = "PA"),
                post = ActionRef.Bound(name = "PB"),
            ),
        )
        m.add(
            TransitionEdge(
                source = G0,
                target = G2,
                transition = Transition.SymbolTransition(symbol = symbolB),
            ),
        )
        m.add(
            TransitionEdge(source = G1, target = G2, transition = Transition.Epsilon),
        )
        m.add(
            TransitionEdge(source = G1, target = G0, transition = Transition.Call(submachine = "M")),
        )
        return m
    }

    private fun submachines(): Submachines = Submachines().apply {
        add(
            Submachine(
                name = "M",
                states = setOf(G0, G1, G2),
                initialState = G0,
                acceptingStates = setOf(G1),
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Immutability
    // -----------------------------------------------------------------------

    @Test
    fun `a snapshot does not change when the mapping does`() {
        val m = mapping()
        val snapshot = Topology.of(m, submachines())

        m.add(
            TransitionEdge(
                source = G2,
                target = G0,
                transition = Transition.SymbolTransition(symbol = symbolA),
            ),
        )

        assertEquals(4, snapshot.size)
        assertEquals(5, m.size)
    }

    @Test
    fun `a snapshot does not change when the submachines do`() {
        val m = mapping()
        val sm = submachines()
        val snapshot = Topology.of(m, sm)

        sm.add(
            Submachine(
                name = "N",
                states = setOf(G0),
                initialState = G0,
                acceptingStates = setOf(G0),
            ),
        )

        assertEquals(1, snapshot.submachines.size)
        assertEquals(2, sm.size)
    }

    @Test
    fun `the edges of a snapshot are a copy, not a view`() {
        val m = mapping()
        val snapshot = Topology.of(m, submachines())

        m.removeById(snapshot.edges.first().id)

        assertEquals(4, snapshot.size)
        assertTrue(snapshot.edges.isNotEmpty())
    }

    // -----------------------------------------------------------------------
    // The queries
    // -----------------------------------------------------------------------

    @Test
    fun `edgesFrom answers what the source-state query answered`() {
        val t = Topology.of(mapping(), submachines())

        assertEquals(2, t.edgesFrom(G0).size)
        assertEquals(2, t.edgesFrom(G1).size)
        assertTrue(t.edgesFrom(G2).isEmpty())
    }

    @Test
    fun `edgesConsuming answers what the symbol query answered`() {
        val t = Topology.of(mapping(), submachines())

        assertEquals(1, t.edgesConsuming(symbolA).size)
        assertEquals(1, t.edgesConsuming(symbolB).size)
        assertTrue(t.edgesConsuming(CharSymbol('z')).isEmpty())
    }

    @Test
    fun `edgesTo answers what the target-state query answered`() {
        val t = Topology.of(mapping(), submachines())

        assertEquals(1, t.edgesTo(G0).size)
        assertEquals(1, t.edgesTo(G1).size)
        assertEquals(2, t.edgesTo(G2).size)
    }

    @Test
    fun `callsTo answers what the submachine query answered`() {
        val t = Topology.of(mapping(), submachines())

        assertEquals(1, t.callsTo("M").size)
        assertTrue(t.callsTo("N").isEmpty())
        assertEquals(1, t.callsTo(setOf("M", "N")).size)
        assertTrue(t.callsTo(setOf("N")).isEmpty())
    }

    @Test
    fun `epsilonEdges answers what the epsilon query answered`() {
        val t = Topology.of(mapping(), submachines())

        assertEquals(1, t.epsilonEdges.size)
        assertEquals(1, t.epsilonEdgesFrom(G1).size)
        assertTrue(t.epsilonEdgesFrom(G0).isEmpty())
    }

    @Test
    fun `epsilonOrCallEdgesFrom answers what the free-move query answered`() {
        val t = Topology.of(mapping(), submachines())

        assertEquals(2, t.epsilonOrCallEdgesFrom(G1).size)
        assertTrue(t.epsilonOrCallEdgesFrom(G0).isEmpty())
    }

    @Test
    fun `applicableEdgesFrom answers what the applicable-move query answered`() {
        val t = Topology.of(mapping(), submachines())

        // From G0: the 'a' edge, the 'b' edge and nothing else.
        assertEquals(1, t.applicableEdgesFrom(G0, symbolA).size)
        // From G1: the epsilon edge, the call edge and nothing else.
        assertEquals(2, t.applicableEdgesFrom(G1, symbolA).size)
    }

    @Test
    fun `edgesWithPrior and edgesWithPost answer what the action queries answered`() {
        val t = Topology.of(mapping(), submachines())

        assertEquals(1, t.edgesWithPrior("PA").size)
        assertTrue(t.edgesWithPrior("nope").isEmpty())
        assertEquals(1, t.edgesWithPost("PB").size)
        assertTrue(t.edgesWithPost("nope").isEmpty())
    }

    @Test
    fun `matching agrees with the predicates of the library`() {
        val t = Topology.of(mapping(), submachines())

        // The pattern owns the meaning of the filters: within a family, it
        // answers exactly what the predicates answer, edge by edge.
        val bySourceAndSymbol = t.matching(TransitionPattern(source = G1, symbol = symbolA))
        val bySourceAndSymbolPredicate = t.edges.filter { sourceState(G1)(it) && symbol(symbolA)(it) }
        assertEquals(bySourceAndSymbolPredicate.toSet(), bySourceAndSymbol.toSet())

        val bySymbol = t.matching(TransitionPattern(symbol = symbolA))
        val bySymbolPredicate = t.edges.filter { symbol(symbolA)(it) }
        assertEquals(bySymbolPredicate.toSet(), bySymbol.toSet())

        val byCall = t.matching(TransitionPattern(submachine = SubmachineRef.Named("M")))
        val byCallPredicate = t.edges.filter { submachineCall("M")(it) }
        assertEquals(byCallPredicate.toSet(), byCall.toSet())

        val byTarget = t.matching(TransitionPattern(target = G2, symbol = symbolB))
        val byTargetPredicate = t.edges.filter { targetState(G2)(it) && symbol(symbolB)(it) }
        assertEquals(byTargetPredicate.toSet(), byTarget.toSet())
    }

    @Test
    fun `a pattern belongs to one family and never crosses it`() {
        val t = Topology.of(mapping(), submachines())

        // The symbol family never selects a call, and the call family never
        // selects a symbol-consuming edge.
        val symbolFamily = t.matching(TransitionPattern(source = G1))
        assertTrue(symbolFamily.isEmpty())

        val callFamily = t.matching(TransitionPattern(source = G1, submachine = SubmachineRef.Any))
        assertEquals(1, callFamily.size)
    }

    @Test
    fun `the snapshot knows which submachine owns a state`() {
        val t = Topology.of(mapping(), submachines())

        assertEquals("M", t.ownerOf(G0).name)
        assertEquals("M", t.ownerOf(G1).name)
        assertThrows<NoSuchElementException> { t.ownerOf(G3) }
    }

    @Test
    fun `the snapshot knows its edges by identifier`() {
        val t = Topology.of(mapping(), submachines())
        val edge = t.edges.first()

        assertEquals(edge, t.fromId(edge.id))
        assertThrows<NoSuchElementException> { t.fromId(-1) }
    }

    @Test
    fun `a snapshot renders itself`() {
        val t = Topology.of(mapping(), submachines())

        assertTrue(t.toString().contains("edges=4"))
        assertTrue(t.toString().contains("submachines=1"))
    }

    @Test
    fun `two snapshots of the same topology are equal`() {
        val first = Topology.of(mapping(), submachines())
        val second = Topology.of(mapping(), submachines())

        assertEquals(first, second)
        assertFalse(first === second)
    }
}
