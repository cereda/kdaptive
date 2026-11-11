// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.dsl.automaton
import br.usp.poli.lta.cereda.kdaptive.dsl.specToAutomaton
import br.usp.poli.lta.cereda.kdaptive.dsl.sym
import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Submachines
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.utils.PathOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The two layers that keep a recognition from spinning forever.
 *
 * The *static* layer refuses to build an automaton whose reachable cycle
 * consumes no input and whose topology can never change: nothing could ever
 * break such a cycle, so the recognition could never finish.
 *
 * The *runtime* layer prunes the branches that do spin: a path that arrives
 * at a configuration its lineage already left, with no action fired since,
 * can only go round in circles, and the engine cuts it.
 */
class EpsilonCycleTest {

    private object C0 : State() {
        override fun toString(): String = "c0"
    }

    private object C1 : State() {
        override fun toString(): String = "c1"
    }

    private object C2 : State() {
        override fun toString(): String = "c2"
    }

    // -----------------------------------------------------------------------
    // The static layer
    // -----------------------------------------------------------------------

    @Test
    fun `a reachable epsilon cycle without actions is rejected`() {
        val error =
            assertThrows<IllegalArgumentException> {
                automaton {
                    val q0 = state("q0")
                    val q1 = state("q1")
                    submachine("M") {
                        initial(q0)
                        state(q1)
                        accepting(q1)
                    }
                    epsilon(q0, q1)
                    epsilon(q1, q0)
                    main("M")
                }
            }

        assertTrue(error.message!!.contains("consumes no input"))
    }

    @Test
    fun `the same cycle with an action is accepted`() {
        // An action may legitimately break the cycle — the documented one-shot
        // idiom removes the very edge that loops — so the cycle is reported
        // but does not stop the automaton from being built.
        val spec =
            automaton {
                val q0 = state("q0")
                val q1 = state("q1")
                submachine("M") {
                    initial(q0)
                    state(q1)
                    accepting(q1)
                }
                action("Break") { _, _ ->
                    ea.remove {
                        source = q1
                        symbol = Symbol.Epsilon
                    }
                }
                epsilon(q0, q1)
                epsilon(q1, q0)
                main("M")
            }

        assertEquals(2, spec.transitions.size)
    }

    @Test
    fun `an unreachable epsilon cycle is accepted`() {
        // Nothing reaches the cycle: it can never spin.
        val spec =
            automaton {
                val q0 = state("q0")
                val q1 = state("q1")
                val q2 = state("q2")
                submachine("M") {
                    initial(q0)
                    state(q1)
                    state(q2)
                    accepting(q1)
                }
                epsilon(q1, q2)
                epsilon(q2, q1)
                main("M")
            }

        assertEquals(2, spec.transitions.size)
    }

    @Test
    fun `an input-gated epsilon cycle is detected`() {
        // The cycle is only entered after a symbol was consumed. The check
        // ignores what the input holds, because a cycle behind it is just as
        // fatal for the input that does reach it.
        val error =
            assertThrows<IllegalArgumentException> {
                automaton {
                    val q0 = state("q0")
                    val q1 = state("q1")
                    submachine("M") {
                        initial(q0)
                        state(q1)
                        accepting(q1)
                    }
                    consume(q0, sym('a'), q1)
                    epsilon(q1, q0)
                    epsilon(q0, q1)
                    main("M")
                }
            }

        assertTrue(error.message!!.contains("consumes no input"))
    }

    @Test
    fun `a call cycle that consumes no input is detected`() {
        // A call consumes nothing either: a cycle of calls never reaches a
        // verdict.
        val error =
            assertThrows<IllegalArgumentException> {
                automaton {
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
                    call(m0, "S", m1)
                    call(s0, "M", s1)
                    main("M")
                }
            }

        assertTrue(error.message!!.contains("consumes no input"))
    }

    @Test
    fun `a cycle that consumes input is accepted`() {
        // The loop goes round once per symbol: the input runs out and the
        // path reaches a verdict.
        val spec =
            automaton {
                val q0 = state("q0")
                val q1 = state("q1")
                submachine("M") {
                    initial(q0)
                    state(q1)
                    accepting(q1)
                }
                consume(q0, sym('a'), q1)
                consume(q1, sym('a'), q0)
                main("M")
            }

        assertEquals(2, spec.transitions.size)
    }

    // -----------------------------------------------------------------------
    // The runtime layer
    // -----------------------------------------------------------------------

    /**
     * An automaton with an epsilon cycle and an action that never fires.
     *
     * The action is what lets the automaton past the static check; it never
     * runs, so the cycle spins for real and the runtime layer has to cut it.
     */
    private class CyclicAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = CyclicAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(C0, C1, C2),
                    initialState = C0,
                    acceptingStates = setOf(C1),
                ),
            )
            actions.add(Action(name = "Never") { _, _, _ -> })
            epsilon(C0, C1)
            epsilon(C1, C0)
            consume(C2, CharSymbol('a'), C2, post = "Never")
        }
    }

    @Test
    fun `a lone epsilon cycle is cut instead of spinning`() {
        val result = CyclicAa().recognize(emptyList())

        assertFalse(result.accepted)
        assertEquals(1, result.cutPaths.size)
        assertEquals(PathOutcome.CUT, result.cutPaths.single().outcome)
        assertTrue(result.rejectedPaths.isEmpty())
    }

    /** An automaton whose initial state offers a cycling branch and an escaping one. */
    private class ForkBombAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = ForkBombAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(C0, C1, C2),
                    initialState = C0,
                    acceptingStates = setOf(C1),
                ),
            )
            actions.add(Action(name = "Never") { _, _, _ -> })
            epsilon(C0, C0)
            epsilon(C0, C1)
            consume(C2, CharSymbol('a'), C2, post = "Never")
        }
    }

    @Test
    fun `the cycling branch of a fork is cut while the escaping one accepts`() {
        val result = ForkBombAa().recognize(emptyList())

        assertTrue(result.accepted)
        assertEquals(1, result.acceptedPaths.size)
        assertEquals(1, result.cutPaths.size)
        assertEquals(PathOutcome.CUT, result.cutPaths.single().outcome)
    }

    /** An automaton whose prior action removes the edge closing the cycle. */
    private class BreakingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = BreakingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(C0, C1, C2),
                    initialState = C0,
                    acceptingStates = setOf(C1),
                ),
            )
            actions.add(
                Action(name = "Break") { transitions, _, _ ->
                    ElementaryActions(transitions, Submachines())
                        .remove(C1, Symbol.Epsilon, C0)
                },
            )
            epsilon(C0, C1, prior = "Break")
            epsilon(C1, C0)
            consume(C2, CharSymbol('a'), C2, post = "Break")
        }
    }

    @Test
    fun `an action that breaks the cycle lets the path reach a verdict`() {
        val result = BreakingAa().recognize(emptyList())

        assertTrue(result.accepted)
        assertTrue(result.cutPaths.isEmpty())
        assertEquals(PathOutcome.ACCEPTED, result.acceptedPaths.single().outcome)
    }

    /** An automaton whose action fires on every pass and eventually breaks the cycle. */
    private class CountingAa : AdaptiveAutomaton() {
        private var passes = 0

        override fun newInstance(): AdaptiveAutomaton = CountingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(C0, C1, C2),
                    initialState = C0,
                    acceptingStates = setOf(C1),
                ),
            )
            actions.add(
                Action(name = "Count") { transitions, _, _ ->
                    passes = passes + 1
                    if (passes >= 3) {
                        ElementaryActions(transitions, Submachines())
                            .remove(C1, Symbol.Epsilon, C0)
                    }
                },
            )
            epsilon(C0, C1, prior = "Count")
            epsilon(C1, C0)
            consume(C2, CharSymbol('a'), C2, post = "Count")
        }
    }

    @Test
    fun `a path whose action fires on every pass is not cut`() {
        // The action changed the future of the configuration on every pass, so
        // the engine must not mistake the repetition for a loop.
        val result = CountingAa().recognize(emptyList())

        assertTrue(result.accepted)
        assertTrue(result.cutPaths.isEmpty())
        assertEquals(PathOutcome.ACCEPTED, result.acceptedPaths.single().outcome)
    }

    /** An automaton whose action fires on every pass and never changes anything. */
    private class SpinningAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = SpinningAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(C0, C1, C2),
                    initialState = C0,
                    acceptingStates = setOf(C1),
                ),
            )
            actions.add(Action(name = "Count") { _, _, _ -> })
            epsilon(C0, C1, prior = "Count")
            epsilon(C1, C0)
            consume(C2, CharSymbol('a'), C2, post = "Count")
        }
    }

    @Test
    fun `an action that keeps the cycle spinning trips the step bound`() {
        // Nothing ever changes: the branch is not provably bounded, so the
        // backstop refuses to answer "rejected" and throws instead.
        val aa = SpinningAa()
        aa.limits = RecognitionLimits(maxStepsPerPath = 100)

        val error = assertThrows<IllegalStateException> { aa.recognize(emptyList()) }
        assertTrue(error.message!!.contains("maxStepsPerPath"))
    }

    @Test
    fun `the static check and the runtime cut agree on the same automaton`() {
        // The DSL rejects the cycle when no action may break it; the same
        // topology with an action is built, and the engine prunes the branch
        // that loops.
        val spec =
            automaton {
                val q0 = state("q0")
                val q1 = state("q1")
                submachine("M") {
                    initial(q0)
                    state(q1)
                    accepting(q1)
                }
                action("Never") { _, _ -> }
                epsilon(q0, q1)
                epsilon(q1, q0)
                main("M")
            }
        val automaton = specToAutomaton(spec)

        val result = automaton.recognize(emptyList())
        assertEquals(1, result.cutPaths.size)
        assertFalse(result.accepted)
    }
}
