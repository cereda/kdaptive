// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private object MS0 : State() {
    override fun toString(): String = "s0"
}

private object MS1 : State() {
    override fun toString(): String = "s1"
}

/**
 * The sinks of the transition mapping: they always hold a callback, and the
 * default one does nothing.
 */
class MappingSinkTest {

    @Test
    fun `the sinks default to no-ops`() {
        val m = TransitionMapping()

        m.onEdgeAdded = { }
        m.onEdgeRemoved = { }
        m.onOperation = { }
        m.onObservation = { }

        // Nothing is registered, so nothing is reported: the callbacks are
        // no-ops, not missing ones.
        m.add(
            TransitionEdge(
                source = MS0,
                target = MS1,
                transition = Transition.Epsilon,
            ),
        )
        m.removeFromIdentifier(m.transitions.first().id)
        m.observe(object : Observation {})
    }

    @Test
    fun `the sinks report what happened`() {
        val m = TransitionMapping()
        val added = mutableListOf<TransitionEdge>()
        val removed = mutableListOf<TransitionEdge>()
        m.onEdgeAdded = { added.add(it) }
        m.onEdgeRemoved = { removed.add(it) }

        val edge =
            TransitionEdge(
                source = MS0,
                target = MS1,
                transition = Transition.Epsilon,
            )
        m.add(edge)
        m.removeFromIdentifier(edge.id)

        assertEquals(listOf(edge), added)
        assertEquals(listOf(edge), removed)
    }

    @Test
    fun `a copy carries the sinks`() {
        val m = TransitionMapping()
        val reported = mutableListOf<MappingOperation>()
        m.onOperation = { reported.add(it) }

        val copy = m.copy()
        ElementaryActions(copy, Submachines()).query()

        assertEquals(1, reported.size)
    }

    @Test
    fun `the observation sink receives the published facts`() {
        val m = TransitionMapping()
        val observed = mutableListOf<Observation>()
        m.onObservation = { observed.add(it) }

        m.observe(object : Observation {})

        assertEquals(1, observed.size)
    }
}
