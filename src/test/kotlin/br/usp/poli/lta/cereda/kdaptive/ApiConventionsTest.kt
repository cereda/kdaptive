// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive

import br.usp.poli.lta.cereda.kdaptive.dsl.automaton
import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.execution.PathOutcome
import br.usp.poli.lta.cereda.kdaptive.execution.RecognitionFailedException
import br.usp.poli.lta.cereda.kdaptive.execution.RecognitionLimits
import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.Actions
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Submachines
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.TransitionMapping
import br.usp.poli.lta.cereda.kdaptive.model.TransitionPattern
import br.usp.poli.lta.cereda.kdaptive.model.TransitionPatternKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The conventions of the 6.0.0 idiom pass, pinned down.
 *
 * The release renamed what it renamed and moved what it moved. These tests
 * are the contract: they say which spellings the library promises, and they
 * fail loudly if one of them ever changes again.
 *
 * The renames themselves are changes of *spelling*. The one change of
 * *meaning* is the exception of a failed lookup: a lookup that finds nothing
 * now throws [NoSuchElementException], the exception the collections of the
 * standard library throw, instead of the [IllegalStateException] of the
 * older releases.
 */
class ApiConventionsTest {

    private object Q0 : State() {
        override fun toString(): String = "q0"
    }

    private object Q1 : State() {
        override fun toString(): String = "q1"
    }

    private val submachine =
        Submachine(
            name = "M",
            states = setOf(Q0, Q1),
            initialState = Q0,
            acceptingStates = setOf(Q1),
        )

    private fun edge(source: State, target: State): TransitionEdge = TransitionEdge(
        source = source,
        target = target,
        transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
    )

    /** A minimal automaton: one submachine, one symbol transition. */
    private class TinyAutomaton : AdaptiveAutomaton() {
        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(Q0, Q1),
                    initialState = Q0,
                    acceptingStates = setOf(Q1),
                ),
            )
            symbol(Q0, 'a', Q1)
        }

        override fun newInstance(): AdaptiveAutomaton = TinyAutomaton()
    }

    /** An automaton that loops on 'a', so that a structural bound may trip. */
    private class LoopingAutomaton : AdaptiveAutomaton() {
        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(Q0, Q1),
                    initialState = Q0,
                    acceptingStates = setOf(Q1),
                ),
            )
            symbol(Q0, 'a', Q0)
            symbol(Q0, 'b', Q1)
        }

        override fun newInstance(): AdaptiveAutomaton = LoopingAutomaton()
    }

    // -----------------------------------------------------------------------
    // Enums are PascalCase
    // -----------------------------------------------------------------------

    @Test
    fun `the outcomes of a path are named in PascalCase`() {
        assertEquals(
            listOf("Pending", "Accepted", "Rejected", "Cut", "Failed"),
            PathOutcome.entries.map { it.name },
        )
    }

    @Test
    fun `the families of a pattern are named in PascalCase`() {
        assertEquals(
            listOf("Symbol", "Epsilon", "Submachine", "Mixed"),
            TransitionPatternKind.entries.map { it.name },
        )
    }

    @Test
    fun `the kind of a pattern is the one the family names`() {
        assertEquals(TransitionPatternKind.Symbol, TransitionPattern().kind)
        assertEquals(TransitionPatternKind.Epsilon, TransitionPattern(symbol = Symbol.Epsilon).kind)
    }

    // -----------------------------------------------------------------------
    // Lookups are reached through the get operator
    // -----------------------------------------------------------------------

    @Test
    fun `an action is reached through the get operator`() {
        val actions = Actions()
        val action = Action(name = "A") { _, _, _ -> }
        actions.add(action)

        assertEquals(action, actions["A"])
    }

    @Test
    fun `a submachine is reached through the get operator`() {
        val submachines = Submachines()
        submachines.add(submachine)

        assertEquals(submachine, submachines["M"])
    }

    @Test
    fun `the owner of a state is asked for with ownerOf`() {
        val submachines = Submachines()
        submachines.add(submachine)

        assertEquals(submachine, submachines.ownerOf(Q0))
    }

    @Test
    fun `the topology answers for the owner of a state with ownerOf`() {
        val topology = Topology(edges = setOf(edge(Q0, Q1)), submachines = setOf(submachine))

        assertEquals(submachine, topology.ownerOf(Q0))
    }

    // -----------------------------------------------------------------------
    // A lookup that finds nothing throws NoSuchElementException
    // -----------------------------------------------------------------------

    @Test
    fun `every lookup that finds nothing throws NoSuchElementException`() {
        val actions = Actions()
        val submachines = Submachines()
        val topology = Topology(edges = emptySet(), submachines = emptySet())

        assertThrows<NoSuchElementException> { actions["missing"] }
        assertThrows<NoSuchElementException> { actions.removeByName("missing") }
        assertThrows<NoSuchElementException> { submachines["missing"] }
        assertThrows<NoSuchElementException> { submachines.removeByName("missing") }
        assertThrows<NoSuchElementException> { submachines.ownerOf(object : State() {}) }
        assertThrows<NoSuchElementException> { topology.ownerOf(object : State() {}) }
        assertThrows<NoSuchElementException> { topology.fromId(-1) }
    }

    @Test
    fun `a failed lookup is not an IllegalStateException any more`() {
        // The older releases threw IllegalStateException. The exception of a
        // missing element is now the one the standard library throws.
        val thrown = assertThrows<NoSuchElementException> { Actions()["missing"] }

        assertTrue(!IllegalStateException::class.java.isInstance(thrown))
    }

    // -----------------------------------------------------------------------
    // The mapping is pruned by identifier
    // -----------------------------------------------------------------------

    @Test
    fun `an edge is removed from the mapping by its identifier`() {
        val mapping = TransitionMapping()
        val first = edge(Q0, Q1)
        mapping.add(first)

        assertTrue(mapping.contains(first.id))
        mapping.removeById(first.id)
        assertEquals(0, mapping.size)
        assertTrue(!mapping.contains(first.id))
    }

    // -----------------------------------------------------------------------
    // The transition builders are named after the kind of move they describe
    // -----------------------------------------------------------------------

    @Test
    fun `the automaton builds a symbol transition with symbol`() {
        val aa = TinyAutomaton()
        aa.setup()

        val built = aa.transitions.transitions.single()
        assertEquals(Q0, built.source)
        assertEquals(Q1, built.target)
        assertEquals(CharSymbol('a'), (built.transition as Transition.SymbolTransition).symbol)
    }

    @Test
    fun `the builder builds a symbol transition with symbol`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                main("M")
                symbol("q0", 'a', "q1")
            }

        val built = spec.transitions.single()
        assertEquals(CharSymbol('a'), (built.transition as Transition.SymbolTransition).symbol)
    }

    // -----------------------------------------------------------------------
    // The failure of a recognition has a type of its own
    // -----------------------------------------------------------------------

    @Test
    fun `a recognition that trips a bound fails with a RecognitionFailedException`() {
        val aa = LoopingAutomaton()
        aa.limits = RecognitionLimits(maxStepsPerPath = 1)

        val thrown =
            assertThrows<RecognitionFailedException> {
                aa.recognize(listOf(CharSymbol('a'), CharSymbol('a')))
            }

        assertTrue(thrown.pathId >= 0)
        assertTrue(thrown.reason.isNotEmpty())
    }

    @Test
    fun `the failure of a recognition is still an IllegalStateException`() {
        // The broad exception of the older releases keeps working: a consumer
        // that catches it is not broken by the new type.
        val aa = LoopingAutomaton()
        aa.limits = RecognitionLimits(maxStepsPerPath = 1)

        val thrown =
            assertThrows<IllegalStateException> {
                aa.recognize(listOf(CharSymbol('a'), CharSymbol('a')))
            }

        assertNotNull(thrown.message)
    }

    @Test
    fun `a failure names the path that died`() {
        val failure = RecognitionFailedException(pathId = 42, reason = "the reason")

        assertEquals(42, failure.pathId)
        assertEquals("the reason", failure.reason)
        assertTrue(failure.message!!.contains("42"))
        assertTrue(failure.message!!.contains("the reason"))
    }
}
