// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.execution.RecognitionPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private object M0 : State() {
    override fun toString(): String = "m0"
}

private object M1 : State() {
    override fun toString(): String = "m1"
}

private object M2 : State() {
    override fun toString(): String = "m2"
}

/** Test-local symbol type so filter equality checks cannot collide with other suites. */
private data class Letter(val value: Char) : Symbol() {
    override fun toString(): String = "Letter($value)"
}

/**
 * Coverage tests for the small structural pieces: [Stack], [TransitionEdge],
 * [RecognitionPath], [Identifiers], the three collections' `toString()` and CRUD
 * paths, and the remaining [TransitionMapping] filters.
 */
class SetsAndEdgeTest {
    private fun sym(c: Char) = Letter(c)

    @BeforeEach
    fun resetIds() {
        Identifiers.reset()
    }

    // -----------------------------------------------------------------------
    // Stack
    // -----------------------------------------------------------------------
    @Test
    fun `stack copy preserves order`() {
        val stack = Stack()
        stack.push(M1)
        stack.push(M2)

        val clone = stack.copy()
        assertEquals(listOf(M2, M1), clone.elements)
        assertEquals(M2, clone.peek())
        clone.pop()
        assertEquals(M1, clone.peek())
    }

    @Test
    fun `stack empty toString and top throw`() {
        val stack = Stack()
        assertEquals("Stack: {}", stack.toString())
        assertThrows<IllegalStateException> { stack.peek() }
    }

    // -----------------------------------------------------------------------
    // TransitionEdge / Transition
    // -----------------------------------------------------------------------
    @Test
    fun `edge toString covers all transition kinds`() {
        val symbol =
            TransitionEdge(source = M0, target = M1, transition = Transition.SymbolTransition(symbol = sym('a')))
        val epsilon = TransitionEdge(source = M0, target = M1, transition = Transition.Epsilon)
        val call = TransitionEdge(source = M0, target = M1, transition = Transition.Call(submachine = "M"))
        val ret = TransitionEdge(source = M0, target = M1, transition = Transition.Return)
        val actioned =
            TransitionEdge(
                source = M0,
                target = M1,
                transition = Transition.SymbolTransition(symbol = sym('b')),
                prior = ActionRef.Bound(name = "A"),
                post = ActionRef.Bound(name = "B"),
            )

        assertTrue(symbol.toString().contains("-[Letter(a)]->"))
        assertTrue(epsilon.toString().contains("-->"))
        assertTrue(call.toString().contains("-[call M]->"))
        assertTrue(ret.toString().contains("-[return]->"))
        assertTrue(actioned.toString().contains("prior=A"))
        assertTrue(actioned.toString().contains("post=B"))
        assertFalse(symbol.toString().contains("prior="))
    }

    @Test
    fun `edge helpers detect prior and post actions by name`() {
        val edge =
            TransitionEdge(
                source = M0,
                target = M1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = ActionRef.Bound(name = "A"),
                post = ActionRef.Bound(name = "B"),
            )
        assertTrue(edge.hasPriorAction("A"))
        assertFalse(edge.hasPriorAction("X"))
        assertTrue(edge.hasPostAction("B"))
        assertFalse(edge.hasPostAction("X"))
    }

    // -----------------------------------------------------------------------
    // TransitionMapping remaining filters + toString
    // -----------------------------------------------------------------------
    @Test
    fun `mapping toString and remaining filters`() {
        val mapping = TransitionMapping()
        assertEquals("Transitions: {}", mapping.toString())

        val t1 =
            TransitionEdge(
                source = M0,
                target = M1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = ActionRef.Bound(name = "A"),
                post = ActionRef.Bound(name = "B"),
            )
        val t2 =
            TransitionEdge(
                source = M0,
                target = M2,
                transition = Transition.SymbolTransition(symbol = sym('b')),
            )
        val t3 =
            TransitionEdge(
                source = M1,
                target = M0,
                transition = Transition.Call(submachine = "M"),
            )
        val t4 =
            TransitionEdge(
                source = M0,
                target = M2,
                transition = Transition.Epsilon,
            )
        mapping.add(t1)
        mapping.add(t2)
        mapping.add(t3)

        assertEquals(
            setOf(t1),
            Topology.of(mapping, Submachines())
                .matching(TransitionPattern(source = M0, symbol = sym('a')))
                .toSet(),
        )
        assertEquals(
            setOf(t1),
            Topology.of(mapping, Submachines())
                .matching(TransitionPattern(source = M0, symbol = sym('a'), target = M1))
                .toSet(),
        )

        mapping.add(t4)
        val topology = Topology.of(mapping, Submachines())
        assertEquals(setOf(t1, t4), topology.applicableEdgesFrom(M0, sym('a')).toSet())
        assertEquals(setOf(t4), topology.epsilonOrCallEdgesFrom(M0).toSet())
        assertEquals(setOf(t3), topology.epsilonOrCallEdgesFrom(M1).toSet())

        assertTrue(mapping.toString().contains("TransitionEdge(id="))
    }

    // -----------------------------------------------------------------------
    // Actions / Submachines
    // -----------------------------------------------------------------------
    @Test
    fun `actions add removeByName copy and toString`() {
        val actions = Actions()
        actions.add(Action(name = "A", handler = { _, _, _ -> }))
        assertEquals(1, actions.size)
        assertTrue(actions.toString().contains("A"))

        val clone = actions.copy()
        assertEquals(1, clone.size)
        assertThrows<NoSuchElementException> { clone.get("nope") }

        actions.removeByName("A")
        assertEquals(0, actions.size)
        assertThrows<NoSuchElementException> { actions.removeByName("A") }
    }

    @Test
    fun `submachines lookups copy and toString`() {
        val submachines = Submachines()
        val main = Submachine(name = "Main", states = setOf(M0, M1), initialState = M0, acceptingStates = setOf(M1))
        submachines.add(main)
        assertEquals(main, submachines.get("Main"))
        assertThrows<NoSuchElementException> { submachines.get("nope") }
        assertEquals(main, submachines.ownerOf(M1))
        assertThrows<NoSuchElementException> { submachines.ownerOf(M2) }

        assertTrue(submachines.toString().contains("Main"))

        val clone = submachines.copy()
        clone.removeByName("Main")
        assertEquals(0, clone.size)
        assertEquals(1, submachines.size)
    }

    // -----------------------------------------------------------------------
    // utils
    // -----------------------------------------------------------------------
    @Test
    fun `identifier counters are per key and monotonic`() {
        val first = Identifiers.nextTransitionId()
        val second = Identifiers.nextTransitionId()
        val pathId = Identifiers.nextRecognitionPathId()
        assertEquals(second, first + 1)
        assertEquals(1, pathId)
    }

    @Test
    fun `recognition path addStep and toString`() {
        val q0 = object : State() {
            override fun toString(): String = "q0"
        }
        val step =
            TransitionEdge(
                source = q0,
                target = q0,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            )
        val path = RecognitionPath(state = q0)
        assertEquals(emptyList<TransitionEdge>(), path.trail)
        val next = path.addStep(step).addStep(step)
        assertEquals(listOf(step, step), next.trail)
        assertTrue(next.toString().contains("q0"))
    }
}
