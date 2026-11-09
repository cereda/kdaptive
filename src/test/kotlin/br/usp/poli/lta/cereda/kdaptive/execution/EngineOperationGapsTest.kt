// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.ActionHandler
import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils
import br.usp.poli.lta.cereda.kdaptive.utils.RecognitionPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Exercises adaptive-action firing inside the execution engine (prior actions
 * that keep or remove their own edge, post action execution), termination on
 * accepting states with remaining input, declared `Return` edges being skipped
 * by the applicable-transition filter, the early-stop configuration, the
 * recognition map after a run, and the state/symbol resolver errors of
 * [AdaptiveAutomaton].
 */
class EngineOperationGapsTest {
    @BeforeEach
    fun resetIds() {
        IdentifierUtils.reset()
    }

    private object Aq0 : State() {
        override fun toString(): String = "aq0"
    }

    private object Aq1 : State() {
        override fun toString(): String = "aq1"
    }

    private object Aq2 : State() {
        override fun toString(): String = "aq2"
    }

    private object ARefState : State() {
        override fun toString(): String = "ref"
    }

    /**
     * Recognises 'a' with q1 accepting and q0 not accepting. Builds the edge in
     * `setup()` with either a prior or a post reference to [Action] [handler].
     */
    private open class SimpleAa(private val mode: Mode, private val handler: ActionHandler) : AdaptiveAutomaton() {
        enum class Mode { PRIOR, POST }
        override fun newInstance(): AdaptiveAutomaton = SimpleAa(mode, handler)

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(name = "M", states = setOf(Aq0, Aq1), initialState = Aq0, acceptingStates = setOf(Aq1)),
            )
            actions.add(Action(name = "P", handler = handler))
            val ref = ActionRef(name = "P")
            val edge =
                when (mode) {
                    Mode.PRIOR ->
                        TransitionEdge(
                            source = Aq0,
                            target = Aq1,
                            transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                            prior = ref,
                        )

                    Mode.POST ->
                        TransitionEdge(
                            source = Aq0,
                            target = Aq1,
                            transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                            post = ref,
                        )
                }
            transitions.add(edge)
        }

        fun symbols(vararg chars: Char): List<CharSymbol> = chars.map { CharSymbol(it) }
    }

    @Test
    fun `prior action fires and keeps its edge (acceptance continues)`() {
        val fired = mutableListOf<String>()
        val aa = SimpleAa(SimpleAa.Mode.PRIOR) { _, _, _ -> fired.add("P") }
        assertTrue(aa.recognize(aa.symbols('a')))
        assertEquals(listOf("P"), fired)
        assertTrue(aa.getRecognitionMap().isNotEmpty())
    }

    @Test
    fun `prior action removing its own edge leaves the path stuck and rejected`() {
        val aa =
            SimpleAa(SimpleAa.Mode.PRIOR) { transitions, transition, _ ->
                transitions.removeFromIdentifier(transitions.getTransitions().first { it.transition === transition }.id)
            }
        assertFalse(aa.recognize(aa.symbols('a')))
    }

    @Test
    fun `post action fires after the transition is applied`() {
        val fired = mutableListOf<String>()
        val aa = SimpleAa(SimpleAa.Mode.POST) { _, _, _ -> fired.add("Q") }
        assertTrue(aa.recognize(aa.symbols('a')))
        assertEquals(listOf("Q"), fired)
    }

    @Test
    fun `declared return edge is skipped and acceptance with remaining input rejects`() {
        val aa = StrayReturnAa()
        // 'a': reaches accepting q1; the declared Return edge is filtered out but
        // the configuration is terminal, so the path accepts.
        assertTrue(aa.recognize(listOf(CharSymbol('a'))))
        // 'ab': accepted prefix still carries a remaining symbol: reject.
        assertFalse(aa.recognize(listOf(CharSymbol('a'), CharSymbol('b'))))
        // Empty input never leaves the non-accepting initial state: reject.
        assertFalse(aa.recognize(emptyList()))
    }

    private class StrayReturnAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = StrayReturnAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(name = "M", states = setOf(Aq0, Aq1), initialState = Aq0, acceptingStates = setOf(Aq1)),
            )
            transitions.add(
                TransitionEdge(
                    source = Aq0,
                    target = Aq1,
                    transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                ),
            )
            // A user-declared Return edge: the applicability filter skips it.
            transitions.add(
                TransitionEdge(source = Aq1, target = Aq1, transition = Transition.Return),
            )
        }
    }

    @Test
    fun `stop at first finished path returns promptly and recognition map is queryable`() {
        val aa = EvenAsAutomaton()
        aa.setStopAtFirstResult(true)
        assertTrue(aa.recognize(emptyList()))
        assertTrue(aa.getRecognitionMap().keys.isNotEmpty())
    }

    @Test
    fun `setup resolvers reject strings, junk states and junk symbols`() {
        val aa = object : AdaptiveAutomaton() {
            override fun newInstance(): AdaptiveAutomaton = this

            override fun setup() {
                mainSubmachine = "M"
            }
        }
        val ex1 = assertThrows<IllegalArgumentException> { aa.consume("q0", 'a', "q1") }
        assertTrue(ex1.message!!.contains("String state names are not supported"))

        val q1 = object : State() {}
        val ex2 = assertThrows<IllegalArgumentException> { aa.consume(777, 'a', q1) }
        assertTrue(ex2.message!!.contains("Cannot resolve '777' to State"))

        val q0 = object : State() {}
        val ex3 = assertThrows<IllegalArgumentException> { aa.consume(q0, 42, q1) }
        assertTrue(ex3.message!!.contains("Cannot resolve '42' to Symbol"))

        val ex4 = assertThrows<IllegalArgumentException> { aa.consume(q0, CharSymbol('a'), q1, prior = 7) }
        assertTrue(ex4.message!!.contains("Cannot resolve '7' to ActionRef"))

        // The epsilon and call helpers work with plain state objects.
        aa.epsilon(q0, q1)
        aa.call(q0, "N", q1)
        assertEquals(2, aa.transitions.size())
    }

    @Test
    fun `from-group epsilon helper works`() {
        val aa = object : AdaptiveAutomaton() {
            override fun newInstance(): AdaptiveAutomaton = this

            override fun setup() {
                mainSubmachine = "M"
            }
        }
        aa.from(Aq0) {
            epsilon(Aq2)
        }
        assertEquals(1, aa.transitions.size())
    }

    @Test
    fun `path executor accessors delegate to the config`() {
        val cfg = PathExecutorConfig(
            input = listOf<Symbol>(CharSymbol('a')),
            currentState = null,
            currentSubmachine = "M",
            mainSubmachine = "M",
        )
        val threads = mutableListOf<PathExecutor>()
        val paths = mutableMapOf<Int, RecognitionPath>()
        val executor = PathExecutor(config = cfg, threads = threads, paths = paths)
        executor.run()

        assertEquals("M", executor.mainSubmachine)
        assertEquals("M", executor.currentSubmachine)
        assertEquals(1, executor.input.size)
        assertTrue(executor.stack.isEmpty())
        assertNull(executor.currentState)
        assertTrue(executor.enablePriorAction)
    }
}
