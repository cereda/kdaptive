// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.dot

import br.usp.poli.lta.cereda.kdaptive.dsl.automaton
import br.usp.poli.lta.cereda.kdaptive.dsl.specToAutomaton
import br.usp.poli.lta.cereda.kdaptive.dsl.sym
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The DOT export of a topology whose names are not plain ASCII.
 *
 * A DOT identifier may only hold letters, digits and underscores, so the
 * exporter escapes everything else. A DOT *label*, on the other hand, is
 * quoted text, and it holds any character the file can carry: the exporter
 * must keep the names of the automaton readable rather than turning them
 * into a row of escapes.
 */
class DotExporterUnicodeTest {

    private val exporter = DotExporter()

    /** The Greek letter alpha, one UTF-16 code unit. */
    private val alpha = "α"

    /** The Japanese hiragana, one UTF-16 code unit. */
    private val hiragana = "あ"

    /** A mathematical symbol outside the basic multilingual plane. */
    private val mathematical = "𝔸"

    /** The clusters of the DOT document, by their identifier. */
    private fun clusters(dot: String): List<String> =
        dot.lines().filter { line -> line.startsWith("  subgraph cluster_") }

    @Test
    fun `a non-ASCII submachine name is escaped in the cluster identifier`() {
        val spec = automaton {
            val q0 = state("q0")
            val q1 = state("q1")
            submachine("M$alpha") {
                initial(q0)
                state(q1)
                accepting(q1)
            }
            main("M$alpha")
        }
        val automaton = specToAutomaton(spec)
        automaton.setup()
        val dot = exporter.toDot(Topology.of(automaton.transitions, automaton.submachines))

        // The alpha is one code unit: 03B1.
        assertTrue(dot.contains("subgraph cluster_M_03b1 {"))
    }

    @Test
    fun `a non-ASCII submachine name is kept in the cluster label`() {
        val spec = automaton {
            val q0 = state("q0")
            val q1 = state("q1")
            submachine("M$alpha") {
                initial(q0)
                state(q1)
                accepting(q1)
            }
            main("M$alpha")
        }
        val automaton = specToAutomaton(spec)
        automaton.setup()
        val dot = exporter.toDot(Topology.of(automaton.transitions, automaton.submachines))

        assertTrue(dot.contains("label = \"M$alpha\";"))
    }

    @Test
    fun `a non-ASCII state name is kept in the node label`() {
        val spec = automaton {
            val q0 = state("q0")
            val q1 = state("estado$hiragana")
            submachine("M") {
                initial(q0)
                state(q1)
                accepting(q1)
            }
            consume(q0, sym('a'), q1)
            main("M")
        }
        val automaton = specToAutomaton(spec)
        automaton.setup()
        val dot = exporter.toDot(Topology.of(automaton.transitions, automaton.submachines))

        assertTrue(dot.contains("label=\"estado$hiragana\""))
    }

    @Test
    fun `a character outside the basic multilingual plane is escaped by its code units`() {
        // The mathematical symbol is one code point, but two UTF-16 code
        // units: D835 and DD38. Each one is escaped, so the identifier stays
        // a valid DOT one.
        val spec = automaton {
            val q0 = state("q0")
            val q1 = state("q1")
            submachine("M$mathematical") {
                initial(q0)
                state(q1)
                accepting(q1)
            }
            main("M$mathematical")
        }
        val automaton = specToAutomaton(spec)
        automaton.setup()
        val dot = exporter.toDot(Topology.of(automaton.transitions, automaton.submachines))

        assertTrue(dot.contains("subgraph cluster_M_d835_dd38 {"))
    }

    @Test
    fun `two non-ASCII submachine names do not collide`() {
        val spec = automaton {
            val q0 = state("q0")
            val q1 = state("q1")
            val q2 = state("q2")
            val q3 = state("q3")
            submachine("M$alpha") {
                initial(q0)
                state(q1)
                accepting(q1)
            }
            submachine("M$hiragana") {
                initial(q2)
                state(q3)
                accepting(q3)
            }
            main("M$alpha")
        }
        val automaton = specToAutomaton(spec)
        automaton.setup()
        val dot = exporter.toDot(Topology.of(automaton.transitions, automaton.submachines))

        val found = clusters(dot)
        assertEquals(2, found.size)
        assertEquals(2, found.toSet().size)
    }

    @Test
    fun `a non-ASCII symbol is kept in the edge label`() {
        val spec = automaton {
            val q0 = state("q0")
            val q1 = state("q1")
            submachine("M") {
                initial(q0)
                state(q1)
                accepting(q1)
            }
            consume(q0, sym('a'), q1)
            main("M")
        }
        val automaton = specToAutomaton(spec)
        automaton.setup()
        val dot = exporter.toDot(Topology.of(automaton.transitions, automaton.submachines))

        // The symbol is a letter, and the exporter writes what it reads.
        assertTrue(dot.contains("label=\"a\""))
    }

    @Test
    fun `the export of a non-ASCII topology is deterministic`() {
        val spec = automaton {
            val q0 = state("q0")
            val q1 = state("estado$hiragana")
            submachine("M$alpha") {
                initial(q0)
                state(q1)
                accepting(q1)
            }
            consume(q0, sym('a'), q1)
            main("M$alpha")
        }
        val automaton = specToAutomaton(spec)
        automaton.setup()
        val topology = Topology.of(automaton.transitions, automaton.submachines)

        assertEquals(exporter.toDot(topology), exporter.toDot(topology))
    }
}
