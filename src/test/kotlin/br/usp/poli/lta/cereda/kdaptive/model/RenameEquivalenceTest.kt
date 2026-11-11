// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The renames of the 4.0.0 idiom pass.
 *
 * Every rename in this release is a change of *spelling*, never of
 * *meaning*. These tests pin the meaning down: the new name answers
 * exactly what the old one answered.
 */
class RenameEquivalenceTest {

    private object N0 : State() {
        override fun toString(): String = "n0"
    }

    private object N1 : State() {
        override fun toString(): String = "n1"
    }

    private val symbolA = CharSymbol('a')

    private fun edge(source: State, target: State): TransitionEdge = TransitionEdge(
        source = source,
        target = target,
        transition = Transition.SymbolTransition(symbol = symbolA),
    )

    // -----------------------------------------------------------------------
    // Actions: getActions()/setActions() -> the collection itself
    // -----------------------------------------------------------------------

    @Test
    fun `the actions of a collection are the collection`() {
        val actions = Actions()
        val first = Action(name = "A") { _, _, _ -> }
        actions.add(first)
        actions.add(Action(name = "B") { _, _, _ -> })

        // The old `getActions()` returned a snapshot of the set; iterating
        // the collection answers the same question.
        assertEquals(setOf("A", "B"), actions.map { it.name }.toSet())
        assertEquals(2, actions.size)
        assertTrue(first in actions)
    }

    @Test
    fun `replaceAll replaces the actions instead of merging them`() {
        val actions = Actions()
        actions.add(Action(name = "A") { _, _, _ -> })
        actions.add(Action(name = "B") { _, _, _ -> })

        actions.replaceAll(setOf(Action(name = "C") { _, _, _ -> }))

        // The old `setActions()` cleared the set before filling it.
        assertEquals(setOf("C"), actions.map { it.name }.toSet())
        assertEquals(1, actions.size)
    }

    @Test
    fun `fromName answers what the action lookup answered`() {
        val actions = Actions()
        val action = Action(name = "A") { _, _, _ -> }
        actions.add(action)

        assertEquals(action, actions.fromName("A"))
        assertThrows<IllegalStateException> { actions.fromName("missing") }
    }

    // -----------------------------------------------------------------------
    // Submachines: getSubmachines()/setSubmachines() -> the collection itself
    // -----------------------------------------------------------------------

    @Test
    fun `the submachines of a collection are the collection`() {
        val submachines = Submachines()
        submachines.add(
            Submachine(
                name = "M",
                states = setOf(N0, N1),
                initialState = N0,
                acceptingStates = setOf(N1),
            ),
        )

        assertEquals(setOf("M"), submachines.map { it.name }.toSet())
        assertEquals(1, submachines.size)
    }

    @Test
    fun `replaceAll replaces the submachines instead of merging them`() {
        val submachines = Submachines()
        submachines.add(
            Submachine(
                name = "M",
                states = setOf(N0),
                initialState = N0,
                acceptingStates = setOf(N0),
            ),
        )
        submachines.add(
            Submachine(
                name = "N",
                states = setOf(N1),
                initialState = N1,
                acceptingStates = setOf(N1),
            ),
        )

        submachines.replaceAll(
            setOf(
                Submachine(
                    name = "P",
                    states = setOf(N0),
                    initialState = N0,
                    acceptingStates = setOf(N0),
                ),
            ),
        )

        assertEquals(setOf("P"), submachines.map { it.name }.toSet())
        assertEquals(1, submachines.size)
    }

    @Test
    fun `fromState and fromName answer what the submachine lookups answered`() {
        val submachines = Submachines()
        val submachine =
            Submachine(
                name = "M",
                states = setOf(N0, N1),
                initialState = N0,
                acceptingStates = setOf(N1),
            )
        submachines.add(submachine)

        assertEquals(submachine, submachines.fromState(N0))
        assertEquals(submachine, submachines.fromName("M"))
        assertThrows<IllegalStateException> { submachines.fromState(object : State() {}) }
        assertThrows<IllegalStateException> { submachines.fromName("missing") }
    }

    // -----------------------------------------------------------------------
    // TransitionMapping: getTransitions()/size()/hasIdentifier()
    // -----------------------------------------------------------------------

    @Test
    fun `the transitions of a mapping are the collection`() {
        val mapping = TransitionMapping()
        val first = edge(N0, N1)
        mapping.add(first)

        assertEquals(setOf(first), mapping.transitions)
        assertEquals(1, mapping.size)
    }

    @Test
    fun `contains agrees with the old identifier check`() {
        val mapping = TransitionMapping()
        val first = edge(N0, N1)
        mapping.add(first)

        assertTrue(mapping.contains(first.id))
        assertFalse(mapping.contains(first.id + 1000))
    }

    @Test
    fun `the size of a mapping follows its edges`() {
        val mapping = TransitionMapping()
        assertEquals(0, mapping.size)

        val first = edge(N0, N1)
        mapping.add(first)
        assertEquals(1, mapping.size)

        mapping.removeFromIdentifier(first.id)
        assertEquals(0, mapping.size)
    }

    // -----------------------------------------------------------------------
    // Stack: isEmpty()/top()/elementsList()
    // -----------------------------------------------------------------------

    @Test
    fun `the stack answers what it answered before`() {
        val stack = Stack()
        assertTrue(stack.isEmpty)

        stack.push(N0)
        stack.push(N1)

        assertFalse(stack.isEmpty)
        assertEquals(N1, stack.peek())
        assertEquals(listOf(N1, N0), stack.elements)
    }
}
