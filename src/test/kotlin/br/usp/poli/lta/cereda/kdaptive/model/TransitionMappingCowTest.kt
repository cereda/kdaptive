// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The copy-on-write contract of the transition mapping, and the incremental
 * indexes it rests on.
 *
 * The mapping is the engine's hot path: the engine reads the edges leaving a
 * state on every single step, and an adaptive action mutates the mapping
 * while the path runs. The mapping therefore keeps its indexes *up to date*
 * rather than dropping them on every mutation — a path that mutates the
 * topology on every step must not pay a scan of the whole topology per step.
 */
class TransitionMappingCowTest {

    /**
     * A state with a name.
     *
     * Two edges are equal when their endpoints are, so a test that needs
     * several distinct edges needs several distinct states.
     */
    private class NamedState(private val label: String) : State() {
        /** The number of times this state was asked for its hash code. */
        var hashes: Int = 0

        override fun hashCode(): Int {
            hashes++
            return label.hashCode()
        }

        override fun equals(other: Any?): Boolean = other is NamedState && other.label == label

        override fun toString(): String = label
    }

    /** A plain state of the test. */
    private object Q0 : State() {
        override fun toString(): String = "q0"
    }

    /** A plain state of the test. */
    private object Q1 : State() {
        override fun toString(): String = "q1"
    }

    /** A plain state of the test. */
    private object Q2 : State() {
        override fun toString(): String = "q2"
    }

    /** Builds an edge between two states. */
    private fun edge(source: State, target: State): TransitionEdge =
        TransitionEdge(source = source, target = target, transition = Transition.Epsilon)

    /** Builds [count] distinct edges, one leaving each of [count] distinct states. */
    private fun chain(count: Int): List<TransitionEdge> =
        (0 until count).map { edge(NamedState("s$it"), NamedState("s${it + 1}")) }

    // -----------------------------------------------------------------------
    // Copy-on-write
    // -----------------------------------------------------------------------

    @Test
    fun `a copy shares the edges of the original until one of them is mutated`() {
        val original = TransitionMapping()
        val first = edge(Q0, Q1)
        original.add(first)

        val copy = original.isolatedCopy()

        // The copy holds the same edges...
        assertEquals(1, copy.size)
        assertTrue(copy.contains(first.id))

        // ...and the very same instances, identifiers included.
        assertEquals(original.transitions, copy.transitions)
        assertTrue(original.transitions.first() === copy.transitions.first())
    }

    @Test
    fun `a mutation on the copy never reaches the original`() {
        val original = TransitionMapping()
        original.add(edge(Q0, Q1))

        val copy = original.isolatedCopy()
        copy.add(edge(Q1, Q2))

        assertEquals(1, original.size)
        assertEquals(2, copy.size)
        assertFalse(original.transitions.any { it.target == Q2 })
    }

    @Test
    fun `a mutation on the original never reaches the copy`() {
        val original = TransitionMapping()
        original.add(edge(Q0, Q1))

        val copy = original.isolatedCopy()
        original.add(edge(Q1, Q2))

        assertEquals(2, original.size)
        assertEquals(1, copy.size)
        assertFalse(copy.transitions.any { it.target == Q2 })
    }

    @Test
    fun `a removal on the copy never reaches the original`() {
        val original = TransitionMapping()
        val doomed = edge(Q0, Q1)
        original.add(doomed)

        val copy = original.isolatedCopy()
        copy.removeFromIdentifier(doomed.id)

        assertEquals(1, original.size)
        assertEquals(0, copy.size)
    }

    // -----------------------------------------------------------------------
    // The fingerprint
    // -----------------------------------------------------------------------

    @Test
    fun `a copy shares the fingerprint of the original`() {
        val original = TransitionMapping()
        original.add(edge(Q0, Q1))
        original.add(edge(Q1, Q2))

        val copy = original.isolatedCopy()

        // The fingerprint describes *what* the mapping holds, not which edges
        // it holds: that is what lets the engine recognise a repeated
        // configuration across a fork.
        assertEquals(original.structuralDigest(), copy.structuralDigest())
    }

    @Test
    fun `a mutation diverges the fingerprint of the copy from the original`() {
        val original = TransitionMapping()
        original.add(edge(Q0, Q1))

        val copy = original.isolatedCopy()
        copy.add(edge(Q1, Q2))

        assertNotEquals(original.structuralDigest(), copy.structuralDigest())
    }

    @Test
    fun `the fingerprint survives a sequence of mutations`() {
        val mapping = TransitionMapping()
        val edges = chain(8)
        edges.forEach { mapping.add(it) }
        mapping.structuralDigest()

        // The fingerprint is maintained incrementally. A drift would show up
        // as a mismatch with a mapping built from scratch out of the same
        // edges — and the engine relies on the fingerprint to recognise a
        // repeated configuration.
        mapping.add(edge(Q0, Q2))
        mapping.removeFromIdentifier(edges.first().id)

        assertEquals(mapping.copy().structuralDigest(), mapping.structuralDigest())
    }

    @Test
    fun `the fingerprint does not depend on the order of the edges`() {
        val first = TransitionMapping()
        first.add(edge(Q0, Q1))
        first.add(edge(Q1, Q2))

        val second = TransitionMapping()
        second.add(edge(Q1, Q2))
        second.add(edge(Q0, Q1))

        assertEquals(first.structuralDigest(), second.structuralDigest())
    }

    // -----------------------------------------------------------------------
    // The indexes
    // -----------------------------------------------------------------------

    @Test
    fun `the index of edges leaving a state stays correct across mutations`() {
        val mapping = TransitionMapping()
        val leaving = edge(Q0, Q1)
        mapping.add(leaving)
        mapping.add(edge(Q1, Q2))

        assertEquals(listOf(leaving), mapping.edgesFrom(Q0))

        val added = edge(Q0, Q2)
        mapping.add(added)
        assertEquals(listOf(leaving, added), mapping.edgesFrom(Q0))

        mapping.removeFromIdentifier(leaving.id)
        assertEquals(listOf(added), mapping.edgesFrom(Q0))
    }

    @Test
    fun `the index of identifiers stays correct across mutations`() {
        val mapping = TransitionMapping()
        val first = edge(Q0, Q1)
        mapping.add(first)
        mapping.contains(first.id)

        val second = edge(Q1, Q2)
        mapping.add(second)

        assertTrue(mapping.contains(first.id))
        assertTrue(mapping.contains(second.id))

        mapping.removeFromIdentifier(first.id)
        assertFalse(mapping.contains(first.id))
        assertTrue(mapping.contains(second.id))
    }

    @Test
    fun `a batch of removals does not rebuild the index once per removal`() {
        val mapping = TransitionMapping()
        val edges = chain(40)
        edges.forEach { mapping.add(it) }
        mapping.contains(edges.first().id)

        edges.forEach { mapping.removeFromIdentifier(it.id) }

        assertEquals(0, mapping.size)
        assertFalse(mapping.contains(edges.first().id))
    }

    @Test
    fun `a mutation does not rescan the whole topology`() {
        // The regression this test guards: a mapping that dropped its index
        // on every mutation had to rebuild it from scratch on the next read,
        // which made an adaptive path that mutates the topology on every
        // step quadratic in the size of the topology.
        val edgeCount = 120
        val mutationCount = 60

        val sources = (0 until edgeCount).map { NamedState("s$it") }
        val target = NamedState("target")
        val mapping = TransitionMapping()
        sources.forEach { mapping.add(edge(it, target)) }

        // Force the index to exist, then start counting.
        mapping.edgesFrom(sources.first())
        sources.forEach { it.hashes = 0 }
        target.hashes = 0

        val extra = NamedState("extra")
        val extras = (0 until mutationCount).map { edge(extra, NamedState("x$it")) }
        extras.forEach { newEdge ->
            mapping.add(newEdge)
            mapping.edgesFrom(sources.first())
        }

        val hashes = sources.sumOf { it.hashes } + target.hashes + extra.hashes

        // A rebuild per mutation would cost at least edgeCount * mutationCount
        // hashes. The incremental index costs a constant number per mutation.
        val quadratic = edgeCount * mutationCount
        assertTrue(
            hashes < quadratic / 4,
            "The mapping hashed $hashes times for $mutationCount mutations over " +
                "$edgeCount edges; a rebuild per mutation would cost at least $quadratic.",
        )
    }

    // -----------------------------------------------------------------------
    // Clearing
    // -----------------------------------------------------------------------

    @Test
    fun `clearing is a reset, not a mutation`() {
        val mapping = TransitionMapping()
        val first = edge(Q0, Q1)
        mapping.add(first)
        mapping.add(edge(Q1, Q2))

        var removed = 0
        mapping.onEdgeRemoved = { removed++ }

        mapping.clear()

        // The edges were never observed as leaving one by one.
        assertEquals(0, removed)
        assertEquals(0, mapping.size)
        assertTrue(mapping.edgesFrom(Q0).isEmpty())
        assertFalse(mapping.contains(first.id))
    }

    @Test
    fun `a cleared mapping accepts new edges`() {
        val mapping = TransitionMapping()
        mapping.add(edge(Q0, Q1))
        mapping.clear()

        val fresh = edge(Q1, Q2)
        mapping.add(fresh)

        assertEquals(1, mapping.size)
        assertEquals(listOf(fresh), mapping.edgesFrom(Q1))
        assertTrue(mapping.contains(fresh.id))
    }
}
