// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The semantics of a path that dies before reaching a verdict.
 *
 * A failure is not a rejection: the engine could not decide anything about
 * the input, and saying "rejected" would claim a verdict nobody reached.
 */
class FailureOutcomeTest {
    /** A state of the failing automaton. */
    private object C0 : State() {
        override fun toString(): String = "c0"
    }

    /** A state of the failing automaton. */
    private object C1 : State() {
        override fun toString(): String = "c1"
    }

    /** An automaton whose only action throws. */
    private class FailingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = FailingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(C0, C1),
                    initialState = C0,
                    acceptingStates = setOf(C1),
                ),
            )
            actions.add(Action(name = "Boom") { _, _, _ -> error("boom") })
            symbol(C0, CharSymbol('a'), C1, prior = "Boom")
        }
    }

    /** An automaton that accepts a single symbol. */
    private class AcceptingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = AcceptingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(C0, C1),
                    initialState = C0,
                    acceptingStates = setOf(C1),
                ),
            )
            symbol(C0, CharSymbol('a'), C1)
        }
    }

    @Test
    fun `a failing path is reported as a failure, not a rejection`() {
        val aa = FailingAa()
        val outcomes = mutableListOf<PathOutcome>()
        aa.visitors.add(
            object : ExecutionVisitor {
                override fun onPathEnd(pathId: Int, outcome: PathOutcome, cursor: Int) {
                    outcomes.add(outcome)
                }
            },
        )

        assertFailsWith<RecognitionFailedException> { aa.recognize(listOf(CharSymbol('a'))) }

        assertEquals(listOf(PathOutcome.Failed), outcomes)
        assertFalse(outcomes.contains(PathOutcome.Rejected))
    }

    @Test
    fun `a failed recognition reports the cause instead of a result`() {
        val aa = FailingAa()
        val causes = mutableListOf<Throwable>()
        var ended = false
        aa.visitors.add(
            object : ExecutionVisitor {
                override fun onRecognizeEnd(result: RecognitionResult) {
                    ended = true
                }

                override fun onRecognizeFailed(cause: Throwable) {
                    causes.add(cause)
                }
            },
        )

        val thrown = assertFailsWith<RecognitionFailedException> { aa.recognize(listOf(CharSymbol('a'))) }

        // The two events are exclusive: a failed recognition never reports a
        // result, and never reports a rejection.
        assertFalse(ended)
        assertEquals(1, causes.size)
        assertTrue(thrown.message.orEmpty().contains("boom"))
    }

    @Test
    fun `the failed path keeps the trail it took before it died`() {
        val aa = FailingAa()
        val seen = mutableListOf<Int>()
        aa.visitors.add(
            object : ExecutionVisitor {
                override fun onPathEnd(pathId: Int, outcome: PathOutcome, cursor: Int) {
                    seen.add(pathId)
                }
            },
        )

        assertFailsWith<RecognitionFailedException> { aa.recognize(listOf(CharSymbol('a'))) }

        assertEquals(1, seen.size)
    }

    @Test
    fun `a recognition that ends reports the result it reached`() {
        val aa = AcceptingAa()
        var captured: RecognitionResult? = null
        var failed = false
        aa.visitors.add(
            object : ExecutionVisitor {
                override fun onRecognizeEnd(result: RecognitionResult) {
                    captured = result
                }

                override fun onRecognizeFailed(cause: Throwable) {
                    failed = true
                }
            },
        )

        val outcome = aa.recognize(listOf(CharSymbol('a')))

        assertFalse(failed)
        assertTrue(outcome.accepted)
        assertEquals(outcome, captured)
        assertEquals(1, captured?.acceptedPaths?.size)
    }
}
