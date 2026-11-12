// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.cancellation.CancellationException

/**
 * The lifecycle of a path that dies with an [Error].
 *
 * An error is not an exception: it says the JVM itself is in trouble. The
 * engine does not hide it behind a wrapper — that would bury it — but it
 * still closes the path that died, so that a visitor tracking open paths
 * never holds one that will never end.
 */
class ErrorLifecycleTest {

    /** A state of the failing automaton. */
    private object E0 : State() {
        override fun toString(): String = "e0"
    }

    /** A state of the failing automaton. */
    private object E1 : State() {
        override fun toString(): String = "e1"
    }

    /** The error the action of the automaton throws. */
    private class Fatal : Error("the JVM is in trouble")

    /**
     * An automaton whose only action throws [cause].
     *
     * The action fires on the only transition of the automaton, so the path
     * dies on its first step.
     */
    private class FailingAa(private val cause: Throwable) : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = FailingAa(cause)

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(E0, E1),
                    initialState = E0,
                    acceptingStates = setOf(E1),
                ),
            )
            actions.add(Action(name = "Boom") { _, _, _ -> throw cause })
            consume(E0, CharSymbol('a'), E1, prior = "Boom")
        }
    }

    /** The events a visitor saw during a recognition. */
    private class Events {
        /** The identifiers of the paths that ended, with their outcome. */
        val pathEnds = mutableListOf<Pair<Int, PathOutcome>>()

        /** The causes the recognition failed with. */
        val failures = mutableListOf<Throwable>()

        /** Whether the recognition reported a result. */
        var ended = false

        /** Registers the observer on [automaton]. */
        fun watch(automaton: AdaptiveAutomaton) {
            automaton.visitors.add(
                object : ExecutionVisitor {
                    override fun onPathEnd(pathId: Int, outcome: PathOutcome, cursor: Int) {
                        pathEnds.add(pathId to outcome)
                    }

                    override fun onRecognizeFailed(cause: Throwable) {
                        failures.add(cause)
                    }

                    override fun onRecognizeEnd(result: RecognitionResult) {
                        ended = true
                    }
                },
            )
        }
    }

    @Test
    fun `an error is rethrown unchanged`() {
        val aa = FailingAa(Fatal())

        val thrown = assertThrows<Fatal> { aa.recognize(listOf(CharSymbol('a'))) }

        assertEquals("the JVM is in trouble", thrown.message)
    }

    @Test
    fun `an error closes the path that died`() {
        val aa = FailingAa(Fatal())
        val events = Events()
        events.watch(aa)

        assertThrows<Fatal> { aa.recognize(listOf(CharSymbol('a'))) }

        assertEquals(1, events.pathEnds.size)
        assertEquals(PathOutcome.FAILED, events.pathEnds.single().second)
    }

    @Test
    fun `an error is reported as the cause of the failure`() {
        val aa = FailingAa(Fatal())
        val events = Events()
        events.watch(aa)

        assertThrows<Fatal> { aa.recognize(listOf(CharSymbol('a'))) }

        assertEquals(1, events.failures.size)
        assertTrue(events.failures.single() is Fatal)
    }

    @Test
    fun `an error never reports a result`() {
        val aa = FailingAa(Fatal())
        val events = Events()
        events.watch(aa)

        assertThrows<Fatal> { aa.recognize(listOf(CharSymbol('a'))) }

        assertFalse(events.ended)
    }

    @Test
    fun `an ordinary failure is wrapped, and keeps its cause`() {
        val aa = FailingAa(IllegalArgumentException("something went wrong"))

        val thrown = assertThrows<IllegalStateException> { aa.recognize(listOf(CharSymbol('a'))) }

        assertTrue(thrown.message!!.contains("Recognition path"))
        assertTrue(thrown.cause is IllegalArgumentException)
        assertEquals("something went wrong", thrown.cause?.message)
    }

    @Test
    fun `an interruption restores the interrupt flag and is rethrown unchanged`() {
        val interruption = InterruptedException("the thread was interrupted")
        val aa = FailingAa(interruption)

        // The flag of the test thread is restored by the engine, so it has to
        // be cleared before the next test observes it.
        Thread.interrupted()
        val thrown = assertThrows<InterruptedException> { aa.recognize(listOf(CharSymbol('a'))) }

        assertSame(interruption, thrown)
        assertTrue(Thread.interrupted(), "The engine must restore the interrupt flag.")
    }

    @Test
    fun `a cancellation is rethrown untouched`() {
        val cancellation = CancellationException("the coroutine was cancelled")
        val aa = FailingAa(cancellation)

        val thrown = assertThrows<CancellationException> { aa.recognize(listOf(CharSymbol('a'))) }

        assertSame(cancellation, thrown)
    }

    @Test
    fun `a cancellation closes the path that died`() {
        val aa = FailingAa(CancellationException("the coroutine was cancelled"))
        val events = Events()
        events.watch(aa)

        assertThrows<CancellationException> { aa.recognize(listOf(CharSymbol('a'))) }

        assertEquals(1, events.pathEnds.size)
        assertEquals(PathOutcome.FAILED, events.pathEnds.single().second)
        assertEquals(1, events.failures.size)
        assertFalse(events.ended)
    }

    @Test
    fun `an error thrown by a visitor is rethrown unchanged`() {
        val aa = FailingAa(Fatal())
        aa.visitors.add(
            object : ExecutionVisitor {
                override fun onRecognizeStart(automaton: AdaptiveAutomaton): Unit = throw Fatal()
            },
        )

        // The visitor failed before the path even started: the error is
        // still an error, and it is still not wrapped.
        assertThrows<Fatal> { aa.recognize(listOf(CharSymbol('a'))) }
    }
}
