// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.sets.ActionsSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.SubmachinesSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils
import br.usp.poli.lta.cereda.kdaptive.utils.RecognitionPath
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
 * [RecognitionPath], [IdentifierUtils], the three sets' `toString()` and CRUD
 * paths, and the remaining [TransitionMapping] filters.
 */
class SetsAndEdgeTest {
    private fun sym(c: Char) = Letter(c)

    @BeforeEach
    fun resetIds() {
        IdentifierUtils.reset()
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
        assertEquals(listOf(M2, M1), clone.elementsList())
        assertEquals(M2, clone.top())
        clone.pop()
        assertEquals(M1, clone.top())
    }

    @Test
    fun `stack empty toString and top throw`() {
        val stack = Stack()
        assertEquals("Stack: {}", stack.toString())
        assertThrows<IllegalStateException> { stack.top() }
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
                prior = ActionRef(name = "A"),
                post = ActionRef(name = "B"),
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
                prior = ActionRef(name = "A"),
                post = ActionRef(name = "B"),
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
        assertEquals("Set of transitions: {}", mapping.toString())

        val t1 =
            TransitionEdge(
                source = M0,
                target = M1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = ActionRef(name = "A"),
                post = ActionRef(name = "B"),
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

        assertEquals(setOf(t1), mapping.withSourceStateAndSymbol(M0, sym('a')).toSet())
        assertEquals(setOf(t1), mapping.withSourceStateSymbolAndTargetState(M0, sym('a'), M1).toSet())

        mapping.add(t4)
        assertEquals(setOf(t1, t4), mapping.withSymbolEpsilonOrSubmachineFromSourceState(M0, sym('a')).toSet())
        assertEquals(setOf(t4), mapping.withEpsilonOrSubmachineFromSourceState(M0).toSet())
        assertEquals(setOf(t3), mapping.withEpsilonOrSubmachineFromSourceState(M1).toSet())

        assertTrue(mapping.toString().contains("TransitionEdge(id="))
    }

    // -----------------------------------------------------------------------
    // ActionsSet / SubmachinesSet
    // -----------------------------------------------------------------------
    @Test
    fun `actionsSet add removeByName copy and toString`() {
        val actions = ActionsSet()
        actions.add(Action(name = "A", handler = { _, _, _ -> }))
        assertEquals(1, actions.size())
        assertTrue(actions.toString().contains("A"))

        val clone = actions.copy()
        assertEquals(1, clone.size())
        assertThrows<IllegalStateException> { clone.fromName("nope") }

        actions.removeByName("A")
        assertEquals(0, actions.size())
        assertThrows<IllegalArgumentException> { actions.removeByName("A") }
    }

    @Test
    fun `submachinesSet lookups copy and toString`() {
        val submachines = SubmachinesSet()
        val main = Submachine(name = "Main", states = setOf(M0, M1), initialState = M0, acceptingStates = setOf(M1))
        submachines.add(main)
        assertEquals(main, submachines.getFromName("Main"))
        assertThrows<IllegalStateException> { submachines.getFromName("nope") }
        assertEquals(main, submachines.getFromState(M1))
        assertThrows<IllegalStateException> { submachines.getFromState(M2) }

        assertTrue(submachines.toString().contains("Main"))

        val clone = submachines.copy()
        clone.removeByName("Main")
        assertEquals(0, clone.size())
        assertEquals(1, submachines.size())
    }

    // -----------------------------------------------------------------------
    // utils
    // -----------------------------------------------------------------------
    @Test
    fun `identifier counters are per key and monotonic`() {
        val first = IdentifierUtils.nextTransitionId()
        val second = IdentifierUtils.nextTransitionId()
        val pathId = IdentifierUtils.nextRecognitionPathId()
        assertEquals(second, first + 1)
        assertEquals(1, pathId)
    }

    @Test
    fun `recognition path addStep and toString`() {
        val path = RecognitionPath()
        assertEquals(emptyList<String>(), path.path)
        val next = path.addStep("step-1").addStep("step-2")
        assertEquals(listOf("step-1", "step-2"), next.path)
        assertTrue(next.toString().contains("step-2"))
    }
}
