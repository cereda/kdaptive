// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.model.predicates.isEpsilon
import br.usp.poli.lta.cereda.kdaptive.model.predicates.isSubmachineCall
import br.usp.poli.lta.cereda.kdaptive.model.predicates.isSymbolConsumption
import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils
import br.usp.poli.lta.cereda.kdaptive.utils.PathOutcome
import br.usp.poli.lta.cereda.kdaptive.utils.RecognitionPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal object Q0 : State()

internal object Q1 : State()

internal object Q2 : State()

class ModelTest {
    @BeforeEach
    fun resetIds() {
        IdentifierUtils.reset()
    }

    @Test
    fun `transition detects epsilon`() {
        val t =
            TransitionEdge(
                source = Q0,
                target = Q1,
                transition = Transition.Epsilon,
            )
        assertTrue(isEpsilon(t))
        assertFalse(isSymbolConsumption(t))
    }

    @Test
    fun `transition detects symbol consumption`() {
        val sym = CharSymbol('a')
        val t =
            TransitionEdge(
                source = Q0,
                target = Q1,
                transition = Transition.SymbolTransition(symbol = sym),
            )
        assertTrue(isSymbolConsumption(t))
        assertFalse(isEpsilon(t))
    }

    @Test
    fun `transition detects submachine call`() {
        val t =
            TransitionEdge(
                source = Q0,
                target = Q1,
                transition = Transition.Call(submachine = "M"),
            )
        assertTrue(isSubmachineCall(t))
        assertFalse(isEpsilon(t))
        assertFalse(isSymbolConsumption(t))
    }

    @Test
    fun `transition ids are unique`() {
        val t1 =
            TransitionEdge(
                source = Q0,
                target = Q1,
                transition = Transition.Epsilon,
            )
        val t2 =
            TransitionEdge(
                source = Q0,
                target = Q1,
                transition = Transition.Epsilon,
            )
        assertNotEquals(t1.id, t2.id)
    }

    @Test
    fun `stack push pop top`() {
        val stack = Stack()
        assertTrue(stack.isEmpty)
        stack.push(Q0)
        stack.push(Q1)
        assertFalse(stack.isEmpty)
        assertEquals(Q1, stack.peek())
        assertEquals(Q1, stack.pop())
        assertEquals(Q0, stack.peek())
        assertEquals(Q0, stack.pop())
        assertTrue(stack.isEmpty)
    }

    @Test
    fun `stack pop on empty throws`() {
        val stack = Stack()
        assertThrows(IllegalStateException::class.java) { stack.pop() }
        assertThrows(IllegalStateException::class.java) { stack.peek() }
    }

    @Test
    fun `submachine validates states`() {
        val states = setOf(Q0, Q1, Q2)
        val sm = Submachine(name = "M", states = states, initialState = Q0, acceptingStates = setOf(Q2))
        assertEquals("M", sm.name)
        assertEquals(Q0, sm.initialState)
        assertEquals(setOf(Q2), sm.acceptingStates)
    }

    @Test
    fun `submachine rejects unknown initial state`() {
        assertThrows(IllegalArgumentException::class.java) {
            Submachine(name = "M", states = setOf(Q0), initialState = Q1, acceptingStates = setOf(Q2))
        }
    }

    @Test
    fun `submachine rejects unknown accepting state`() {
        assertThrows(IllegalArgumentException::class.java) {
            Submachine(name = "M", states = setOf(Q0), initialState = Q0, acceptingStates = setOf(Q1))
        }
    }

    @Test
    fun `recognition path done flag`() {
        val rp = RecognitionPath(state = Q0)
        assertFalse(rp.done)
        val rp2 = rp.copy(outcome = PathOutcome.ACCEPTED)
        assertTrue(rp2.done)
        assertEquals(PathOutcome.ACCEPTED, rp2.outcome)
    }

    @Test
    fun `symbol and state equality`() {
        assertEquals(CharSymbol('a'), CharSymbol('a'))
        assertNotEquals(CharSymbol('a'), CharSymbol('b'))
        assertEquals(Q0, Q0)
    }
}
