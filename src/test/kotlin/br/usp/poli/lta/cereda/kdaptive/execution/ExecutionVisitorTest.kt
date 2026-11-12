// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.dsl.automaton
import br.usp.poli.lta.cereda.kdaptive.dsl.specToAutomaton
import br.usp.poli.lta.cereda.kdaptive.dsl.sym
import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.Identifiers
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExecutionVisitorTest {

    @BeforeEach
    fun resetIds() {
        Identifiers.reset()
    }

    /**
     * A visitor overriding nothing, so every event falls through to the
     * no-op defaults of [ExecutionVisitor].
     */
    private class DefaultVisitor : ExecutionVisitor

    // Even number of 'a's over {a, b}, built with the DSL.
    private fun evenAsSpec() = automaton {
        val q0 = state("q0")
        val q1 = state("q1")
        submachine("M") {
            initial(q0)
            state(q1)
            accepting(q0)
        }
        symbol(q0, sym('a'), q1)
        symbol(q0, sym('b'), q0)
        symbol(q1, sym('a'), q0)
        symbol(q1, sym('b'), q1)
        main("M")
    }

    // An automaton whose adaptive action both removes and adds a transition.
    private fun mutatingSpec() = automaton {
        val q0 = state("q0")
        val q1 = state("q1")
        submachine("M") {
            initial(q0)
            state(q1)
            accepting(q0)
        }
        action("Mutate") { _, _ ->
            ea.remove {
                source = q1
                symbol = sym('z')
            }
            ea.add(q1, sym('y'), q0)
        }
        symbol(q0, sym('a'), q1, prior = "Mutate")
        symbol(q0, sym('b'), q0)
        symbol(q1, sym('a'), q0)
        symbol(q1, sym('b'), q1)
        symbol(q1, sym('z'), q0)
        main("M")
    }

    // Main calls submachine "S", which consumes 'x' and returns.
    private fun callSpec() = automaton {
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
        symbol(m0, sym('x'), m1)
        call(m0, "S", m1)
        symbol(s0, sym('x'), s1)
        main("M")
    }

    // Nondeterministic: two 'a' transitions leave the initial state.
    private fun forkSpec() = automaton {
        val q0 = state("q0")
        val q1 = state("q1")
        val q2 = state("q2")
        submachine("M") {
            initial(q0)
            state(q1)
            state(q2)
            accepting(q1)
            accepting(q2)
        }
        symbol(q0, sym('a'), q1)
        symbol(q0, sym('a'), q2)
        main("M")
    }

    @Test
    fun `visitor receives recognition lifecycle events`() {
        val events = mutableListOf<String>()
        val visitor = object : ExecutionVisitor {
            override fun onRecognizeStart(automaton: AdaptiveAutomaton) {
                events.add("start")
            }
            override fun onRecognizeEnd(result: RecognitionResult) {
                events.add("end:${result.accepted}")
            }
        }
        val a = specToAutomaton(evenAsSpec())
        a.visitors.add(visitor)

        assertTrue(a.recognize(listOf(CharSymbol('a'), CharSymbol('a'))).accepted)

        assertEquals(listOf("start", "end:true"), events)
    }

    @Test
    fun `recognize start event exposes the already populated topology`() {
        var transitionCount = -1
        var submachineCount = -1
        val visitor = object : ExecutionVisitor {
            override fun onRecognizeStart(automaton: AdaptiveAutomaton) {
                transitionCount = automaton.transitions.size
                submachineCount = automaton.submachines.size
            }
        }
        val a = specToAutomaton(evenAsSpec())
        a.visitors.add(visitor)

        a.recognize(listOf(CharSymbol('a'), CharSymbol('a')))

        assertEquals(4, transitionCount)
        assertEquals(1, submachineCount)
    }

    @Test
    fun `visitor receives path lifecycle and traversal events`() {
        val events = mutableListOf<String>()
        val visitor = object : ExecutionVisitor {
            override fun onPathStart(pathId: Int, input: List<Symbol>) {
                events.add("pathStart:$pathId")
            }
            override fun onPathEnd(pathId: Int, outcome: PathOutcome, cursor: Int) {
                events.add("pathEnd:$pathId:$outcome:$cursor")
            }
            override fun onBeforeTraverse(pathId: Int, edge: TransitionEdge) {
                events.add("before:$pathId")
            }
            override fun onAfterTraverse(pathId: Int, edge: TransitionEdge) {
                events.add("after:$pathId")
            }
        }
        val a = specToAutomaton(evenAsSpec())
        a.visitors.add(visitor)

        assertTrue(a.recognize(listOf(CharSymbol('a'), CharSymbol('a'))).accepted)

        assertTrue(events.any { it.startsWith("pathStart:") })
        assertTrue(events.any { it.startsWith("pathEnd:") })
        assertTrue(events.any { it.startsWith("before:") })
        assertTrue(events.any { it.startsWith("after:") })
        // Path end must come after traversals on the same path.
        val lastBefore = events.indexOfLast { it.startsWith("before:") }
        val pathEnd = events.indexOfFirst { it.startsWith("pathEnd:") }
        assertTrue(lastBefore < pathEnd)
    }

    @Test
    fun `visitor receives topology mutation events on adaptive action`() {
        val addEvents = mutableListOf<String>()
        val removeEvents = mutableListOf<String>()

        // An automaton where action "A" adds a fresh edge without removing the
        // current one, so the path continues and accepts.
        val spec = automaton {
            val q0 = state("q0")
            val q1 = state("q1")
            submachine("M") {
                initial(q0)
                state(q1)
                accepting(q0)
            }
            action("A") { _, _ ->
                ea.add(q1, sym('c'), q0)
            }
            symbol(q0, sym('a'), q1, prior = "A")
            symbol(q0, sym('b'), q0)
            symbol(q1, sym('a'), q0)
            symbol(q1, sym('b'), q1)
            main("M")
        }

        val visitor = object : ExecutionVisitor {
            override fun onEdgeAdded(pathId: Int, edge: TransitionEdge, topology: Topology) {
                addEvents.add("${edge.source}->${edge.target}")
            }
            override fun onEdgeRemoved(pathId: Int, edge: TransitionEdge, topology: Topology) {
                removeEvents.add("${edge.source}->${edge.target}")
            }
        }

        val a = specToAutomaton(spec)
        a.visitors.add(visitor)

        // "aba": q0 -a-> q1 (action A fires: adds q1-a->q0), q1 -b-> q1, q1 -a-> q0
        // Two a's → even → accept.
        assertTrue(a.recognize(listOf(CharSymbol('a'), CharSymbol('b'), CharSymbol('a'))).accepted)

        assertFalse(addEvents.isEmpty())
        // The added edge is q1-a->q0.
        assertTrue(addEvents.any { it == "q1->q0" })
    }

    @Test
    fun `visitor receives action lifecycle events`() {
        val events = mutableListOf<String>()
        val visitor = object : ExecutionVisitor {
            override fun onBeforeAction(pathId: Int, actionRef: ActionRef.Bound, isPrior: Boolean) {
                events.add("before:${actionRef.name}:$isPrior")
            }
            override fun onAfterAction(pathId: Int, actionRef: ActionRef.Bound, isPrior: Boolean) {
                events.add("after:${actionRef.name}:$isPrior")
            }
        }

        val a = specToAutomaton(mutatingSpec())
        a.visitors.add(visitor)

        a.recognize(listOf(CharSymbol('a'), CharSymbol('b')))

        assertTrue(events.contains("before:Mutate:true"))
        assertTrue(events.contains("after:Mutate:true"))
    }

    @Test
    fun `visitor receives edge removal events`() {
        val removed = mutableListOf<String>()
        val visitor = object : ExecutionVisitor {
            override fun onEdgeRemoved(pathId: Int, edge: TransitionEdge, topology: Topology) {
                removed.add("${edge.source}->${edge.target}")
            }
        }

        val a = specToAutomaton(mutatingSpec())
        a.visitors.add(visitor)

        a.recognize(listOf(CharSymbol('a'), CharSymbol('b')))

        assertTrue(removed.any { it == "q1->q0" })
    }

    @Test
    fun `visitor receives submachine call and return events`() {
        val events = mutableListOf<String>()
        val visitor = object : ExecutionVisitor {
            override fun onCall(pathId: Int, submachine: String) {
                events.add("call:$submachine")
            }
            override fun onReturn(pathId: Int) {
                events.add("return")
            }
        }

        val a = specToAutomaton(callSpec())
        a.visitors.add(visitor)

        // "x" can be matched by either the direct path or the submachine path.
        // The submachine path produces call/return events.
        assertTrue(a.recognize(listOf(CharSymbol('x'))).accepted)
        assertTrue(events.any { it == "call:S" })
        assertTrue(events.any { it == "return" })
    }

    @Test
    fun `visitor receives fork events with the child path identifiers`() {
        val forks = mutableListOf<Pair<Int, List<Int>>>()
        val visitor = object : ExecutionVisitor {
            override fun onPathFork(pathId: Int, children: List<ForkedPath>) {
                forks.add(pathId to children.map { it.pathId })
            }
        }

        val a = specToAutomaton(forkSpec())
        a.visitors.add(visitor)

        assertTrue(a.recognize(listOf(CharSymbol('a'))).accepted)

        assertEquals(1, forks.size)
        val (parentId, childIds) = forks.first()
        assertEquals(2, childIds.size)
        assertEquals(childIds.distinct().size, childIds.size)
        assertTrue(parentId != childIds.first())
        assertTrue(parentId != childIds.last())
    }

    @Test
    fun `fork events carry the edge each child was created for`() {
        val forks = mutableListOf<Pair<Int, List<ForkedPath>>>()
        val visitor = object : ExecutionVisitor {
            override fun onPathFork(pathId: Int, children: List<ForkedPath>) {
                forks.add(pathId to children)
            }
        }

        val a = specToAutomaton(forkSpec())
        a.visitors.add(visitor)

        assertTrue(a.recognize(listOf(CharSymbol('a'))).accepted)

        val (parentId, children) = forks.first()
        assertEquals(2, children.size)

        // Each child carries the alternative it was created for, and the
        // alternatives are distinct from one another.
        val edges = children.map { it.edge }
        assertEquals(edges.distinct().size, edges.size)

        // The edge of a child is the first edge that child traverses.
        val traversed = mutableMapOf<Int, TransitionEdge>()
        val tracer = object : ExecutionVisitor {
            override fun onBeforeTraverse(pathId: Int, edge: TransitionEdge) {
                traversed.putIfAbsent(pathId, edge)
            }
        }
        val b = specToAutomaton(forkSpec())
        b.visitors.add(visitor)
        b.visitors.add(tracer)
        assertTrue(b.recognize(listOf(CharSymbol('a'))).accepted)

        val second = forks.last()
        second.second.forEach { child ->
            assertEquals(child.edge, traversed[child.pathId])
        }
        assertTrue(parentId != second.first)
    }

    @Test
    fun `default visitor implementations handle a plain recognition`() {
        val a = specToAutomaton(evenAsSpec())
        a.visitors.add(DefaultVisitor())

        assertTrue(a.recognize(listOf(CharSymbol('a'), CharSymbol('a'))).accepted)
    }

    @Test
    fun `default visitor implementations handle adaptive actions`() {
        val a = specToAutomaton(mutatingSpec())
        a.visitors.add(DefaultVisitor())

        assertFalse(a.recognize(listOf(CharSymbol('a'), CharSymbol('b'))).accepted)
    }

    @Test
    fun `default visitor implementations handle submachine calls`() {
        val a = specToAutomaton(callSpec())
        a.visitors.add(DefaultVisitor())

        assertTrue(a.recognize(listOf(CharSymbol('x'))).accepted)
    }

    @Test
    fun `default visitor implementations handle forks`() {
        val a = specToAutomaton(forkSpec())
        a.visitors.add(DefaultVisitor())

        assertTrue(a.recognize(listOf(CharSymbol('a'))).accepted)
    }
}
