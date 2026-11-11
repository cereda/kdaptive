// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.dot

import br.usp.poli.lta.cereda.kdaptive.dsl.automaton
import br.usp.poli.lta.cereda.kdaptive.dsl.specToAutomaton
import br.usp.poli.lta.cereda.kdaptive.dsl.sym
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GraphVizVisitorTest {

    @BeforeEach
    fun resetIds() {
        IdentifierUtils.reset()
    }

    // An automaton whose adaptive action adds a transition.
    private fun addingSpec() = automaton {
        val q0 = state("q0")
        val q1 = state("q1")
        submachine("M") {
            initial(q0)
            state(q1)
            accepting(q0)
        }
        action("Add") { _, _ ->
            ea.add(q1, sym('z'), q0)
        }
        consume(q0, sym('a'), q1, prior = "Add")
        consume(q1, sym('a'), q0)
        main("M")
    }

    // An automaton whose adaptive action removes a transition.
    private fun removingSpec() = automaton {
        val q0 = state("q0")
        val q1 = state("q1")
        submachine("M") {
            initial(q0)
            state(q1)
            accepting(q0)
        }
        action("Remove") { _, _ ->
            ea.remove {
                source = q1
                symbol = sym('z')
            }
        }
        consume(q0, sym('a'), q1, prior = "Remove")
        consume(q1, sym('z'), q0)
        main("M")
    }

    @Test
    fun `visitor emits a snapshot when an edge is added`() {
        val frames = mutableListOf<Pair<Int, String>>()
        val visitor = GraphVizVisitor { pathId, dot -> frames.add(pathId to dot) }

        val a = specToAutomaton(addingSpec())
        a.visitors.add(visitor)

        assertTrue(a.recognize(listOf(CharSymbol('a'), CharSymbol('a'))).accepted)

        assertEquals(1, frames.size)
        val (_, dot) = frames.first()
        assertTrue(dot.startsWith("digraph Automaton {"))
        assertTrue(dot.contains("label=\"z\""))
    }

    @Test
    fun `visitor emits a snapshot when an edge is removed`() {
        val frames = mutableListOf<Pair<Int, String>>()
        val visitor = GraphVizVisitor { pathId, dot -> frames.add(pathId to dot) }

        val a = specToAutomaton(removingSpec())
        a.visitors.add(visitor)

        a.recognize(listOf(CharSymbol('a')))

        assertEquals(1, frames.size)
        val (_, dot) = frames.first()
        assertTrue(dot.startsWith("digraph Automaton {"))
        assertFalse(dot.contains("label=\"z\""))
    }

    @Test
    fun `snapshots carry the identifier of the path that mutated the topology`() {
        val frames = mutableListOf<Pair<Int, String>>()
        val visitor = GraphVizVisitor { pathId, dot -> frames.add(pathId to dot) }

        val a = specToAutomaton(addingSpec())
        a.visitors.add(visitor)

        val result = a.recognize(listOf(CharSymbol('a'), CharSymbol('a')))
        assertTrue(result.accepted)

        val pathIds = result.paths.map { it.id }.toSet()
        assertFalse(frames.isEmpty())
        assertTrue(frames.all { frame -> frame.first in pathIds })
    }

    @Test
    fun `edge events produce snapshots carrying the given path identifier`() {
        val frames = mutableListOf<Pair<Int, String>>()
        val visitor = GraphVizVisitor { pathId, dot -> frames.add(pathId to dot) }

        val spec = automaton {
            val q0 = state("q0")
            val q1 = state("q1")
            submachine("M") {
                initial(q0)
                state(q1)
                accepting(q0)
            }
            consume(q0, sym('a'), q1)
            main("M")
        }
        val a = specToAutomaton(spec)
        a.setup()
        val edge = a.transitions.transitions.first()

        visitor.onEdgeAdded(7, edge, Topology.of(a.transitions, a.submachines))
        visitor.onEdgeRemoved(7, edge, Topology.of(a.transitions, a.submachines))

        assertEquals(2, frames.size)
        assertTrue(frames.all { frame -> frame.first == 7 })
        assertTrue(frames.all { frame -> frame.second.startsWith("digraph Automaton {") })
    }
}
