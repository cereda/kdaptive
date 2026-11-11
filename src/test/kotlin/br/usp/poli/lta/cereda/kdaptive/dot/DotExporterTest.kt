// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.dot

import br.usp.poli.lta.cereda.kdaptive.dsl.automaton
import br.usp.poli.lta.cereda.kdaptive.dsl.specToAutomaton
import br.usp.poli.lta.cereda.kdaptive.dsl.sym
import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DotExporterTest {

    @BeforeEach
    fun resetIds() {
        IdentifierUtils.reset()
    }

    private val exporter = DotExporter()

    // Two-state automaton: q0 -a-> q1, q0 -b-> q0; q0 initial+accepting.
    private fun simpleSpec() = automaton {
        val q0 = state("q0")
        val q1 = state("q1")
        submachine("M") {
            initial(q0)
            state(q1)
            accepting(q0)
        }
        consume(q0, sym('a'), q1)
        consume(q0, sym('b'), q0)
        main("M")
    }

    /** Returns the lines of the cluster named [name], boundaries included. */
    private fun clusterLines(dot: String, name: String): List<String> {
        val lines = dot.lines()
        val start = lines.indexOfFirst { line -> line.startsWith("  subgraph cluster_$name {") }
        assertTrue(start >= 0, "cluster $name is missing from the DOT output")
        val end = (start + 1 until lines.size).first { lines[it] == "  }" }
        return lines.subList(start, end + 1)
    }

    @Test
    fun `dot output contains submachine cluster`() {
        val a = specToAutomaton(simpleSpec())
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        assertTrue(dot.contains("subgraph cluster_M"))
        assertTrue(dot.contains("label = \"M\""))
    }

    @Test
    fun `dot output contains all states as nodes`() {
        val a = specToAutomaton(simpleSpec())
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        assertTrue(dot.contains("label=\"q0\""))
        assertTrue(dot.contains("label=\"q1\""))
    }

    @Test
    fun `dot output contains all edges with symbol labels`() {
        val a = specToAutomaton(simpleSpec())
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        assertTrue(dot.contains("label=\"a\""))
        assertTrue(dot.contains("label=\"b\""))
    }

    @Test
    fun `accepting state gets doublecircle shape`() {
        val a = specToAutomaton(simpleSpec())
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        assertTrue(dot.contains("shape=doublecircle"))
        assertTrue(dot.contains("shape=circle"))
    }

    @Test
    fun `epsilon edge is labeled with epsilon character`() {
        val spec = automaton {
            val q0 = state("q0")
            val q1 = state("q1")
            submachine("M") {
                initial(q0)
                state(q1)
                accepting(q1)
            }
            epsilon(q0, q1)
            main("M")
        }
        val a = specToAutomaton(spec)
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        assertTrue(dot.contains("label=\"\u03B5\""))
    }

    @Test
    fun `call edge is labeled with submachine name`() {
        val spec = automaton {
            val m0 = state("m0")
            val m1 = state("m1")
            val s0 = state("s0")
            val s1 = state("s1")
            submachine("M") {
                initial(m0)
                state(m1)
                accepting(m1)
            }
            submachine("S") {
                initial(s0)
                state(s1)
                accepting(s1)
            }
            consume(m0, sym('x'), m1)
            call(m0, "S", m1)
            consume(s0, sym('x'), s1)
            main("M")
        }
        val a = specToAutomaton(spec)
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        assertTrue(dot.contains("label=\"call S\""))
    }

    @Test
    fun `return edge is labeled with return`() {
        val a = specToAutomaton(simpleSpec())
        a.setup()
        val initial = a.submachines.fromName("M").initialState
        a.transitions.add(
            TransitionEdge(source = initial, target = initial, transition = Transition.Return),
        )
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        assertTrue(dot.contains("label=\"return\""))
    }

    @Test
    fun `edge with prior and post actions includes action annotations`() {
        val spec = automaton {
            val q0 = state("q0")
            val q1 = state("q1")
            submachine("M") {
                initial(q0)
                state(q1)
                accepting(q0)
            }
            action("A") { _, _ -> }
            action("B") { _, _ -> }
            consume(q0, sym('a'), q1, prior = "A", post = "B")
            main("M")
        }
        val a = specToAutomaton(spec)
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        assertTrue(dot.contains("prior=A"))
        assertTrue(dot.contains("post=B"))
    }

    @Test
    fun `action arguments are rendered in the edge label`() {
        val a = specToAutomaton(simpleSpec())
        a.setup()
        val initial = a.submachines.fromName("M").initialState
        a.transitions.add(
            TransitionEdge(
                source = initial,
                target = initial,
                transition = Transition.SymbolTransition(symbol = CharSymbol('z')),
                prior = ActionRef.Bound(name = "A", args = listOf("x", 1)),
            ),
        )
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        assertTrue(dot.contains("prior=A(x,1)"))
    }

    @Test
    fun `edges inside a submachine are drawn within its cluster`() {
        val a = specToAutomaton(simpleSpec())
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        val cluster = clusterLines(dot, "M").joinToString("\n")
        assertTrue(cluster.contains("n0 -> n1 [label=\"a\"];"))
        assertTrue(cluster.contains("n0 -> n0 [label=\"b\"];"))
    }

    @Test
    fun `edges crossing submachines are drawn outside the clusters`() {
        val spec = automaton {
            val m0 = state("m0")
            val m1 = state("m1")
            val s0 = state("s0")
            val s1 = state("s1")
            submachine("M") {
                initial(m0)
                state(m1)
                accepting(m1)
            }
            submachine("S") {
                initial(s0)
                state(s1)
                accepting(s1)
            }
            consume(m0, sym('x'), s1)
            consume(s0, sym('y'), s1)
            main("M")
        }
        val a = specToAutomaton(spec)
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        val crossing = dot.lines().first { line -> line.contains("[label=\"x\"]") }
        val internal = dot.lines().first { line -> line.contains("[label=\"y\"]") }

        assertTrue(crossing.matches(Regex("^  n\\d+ -> n\\d+ \\[label=\"x\"\\];$")))
        assertTrue(internal.matches(Regex("^    n\\d+ -> n\\d+ \\[label=\"y\"\\];$")))
    }

    @Test
    fun `node identifiers keep their numeric order`() {
        val spec = automaton {
            val q = (0 until 12).map { index -> state("q$index") }
            submachine("M") {
                initial(q.first())
                q.forEach { s -> state(s) }
                accepting(q.last())
            }
            main("M")
        }
        val a = specToAutomaton(spec)
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        val ids = dot.lines().mapNotNull { line -> NODE_PATTERN.find(line)?.groupValues?.get(1) }
        assertEquals((0 until 12).map { "n$it" }, ids)
    }

    @Test
    fun `labels escape quotes backslashes and newlines`() {
        val spec = automaton {
            val q0 = state("q\"0")
            val q1 = state("q\\0")
            val q2 = state("q\n0")
            submachine("M") {
                initial(q0)
                state(q1)
                state(q2)
                accepting(q0)
            }
            main("M")
        }
        val a = specToAutomaton(spec)
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        assertTrue(dot.contains("label=\"q\\\"0\""))
        assertTrue(dot.contains("label=\"q\\\\0\""))
        assertTrue(dot.contains("label=\"q\\n0\""))
    }

    @Test
    fun `cluster identifiers do not collide`() {
        // "a-b" and "a_b" are two different submachines: their clusters must
        // stay two different clusters.
        val spec = automaton {
            val q0 = state("q0")
            val q1 = state("q1")
            val q2 = state("q2")
            val q3 = state("q3")
            submachine("a-b") {
                initial(q0)
                state(q1)
                accepting(q1)
            }
            submachine("a_b") {
                initial(q2)
                state(q3)
                accepting(q3)
            }
            main("a-b")
        }
        val a = specToAutomaton(spec)
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        val clusters = dot.lines().filter { line -> line.startsWith("  subgraph cluster_") }
        assertEquals(2, clusters.size)
        assertEquals(2, clusters.toSet().size)
    }

    @Test
    fun `dot output is deterministic for same topology`() {
        val a = specToAutomaton(simpleSpec())
        a.setup()
        val dot1 = exporter.toDot(Topology.of(a.transitions, a.submachines))
        val dot2 = exporter.toDot(Topology.of(a.transitions, a.submachines))

        assertEquals(dot1, dot2)
    }

    @Test
    fun `dot output is non-empty and well-formed`() {
        val a = specToAutomaton(simpleSpec())
        a.setup()
        val dot = exporter.toDot(Topology.of(a.transitions, a.submachines))

        assertTrue(dot.startsWith("digraph Automaton {"))
        assertTrue(dot.endsWith("}\n"))
        assertFalse(dot.isBlank())
    }

    private companion object {
        /** Matches a node declaration and captures its identifier. */
        val NODE_PATTERN = Regex("^    (n\\d+) \\[label=")
    }
}
