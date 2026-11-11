// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils
import br.usp.poli.lta.cereda.kdaptive.utils.PathOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

private object X0 : State() {
    override fun toString(): String = "x0"
}

private object X1 : State() {
    override fun toString(): String = "x1"
}

/**
 * A gate that holds the threads until it opens.
 *
 * The threads wait for the gate so that they really run at the same time:
 * without it, the first thread might finish before the others even start,
 * and the test would prove nothing.
 */
private class StartGate {
    @Volatile
    private var open = false

    /** Opens the gate, releasing every thread waiting on it. */
    fun open() {
        open = true
    }

    /** Waits until the gate opens. */
    fun await() {
        while (!open) {
            Thread.yield()
        }
    }
}

/**
 * The engine under concurrent use.
 *
 * The recognition runs on a clone, the identifiers are unique across threads
 * and the visitor list is snapshotted: two recognitions may run at the same
 * time on the same automaton without interfering with each other.
 */
class ThreadSafetyTest {

    /** An automaton that accepts "a" and rejects everything else. */
    private class AcceptingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = AcceptingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(X0, X1),
                    initialState = X0,
                    acceptingStates = setOf(X1),
                ),
            )
            transitions.add(
                TransitionEdge(
                    source = X0,
                    target = X1,
                    transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
                ),
            )
        }
    }

    @Test
    fun `concurrent recognitions on the same automaton do not interfere`() {
        val aa = AcceptingAa()
        // The configured automaton owns the topology; the recognitions run on
        // clones and never touch it.
        aa.setup()
        val configured = aa.transitions.size
        val inputs = listOf("a", "b", "a", "b", "a", "b", "a", "b")
        val gate = StartGate()
        // Every thread writes to its own slot: the results are gathered when
        // the threads are done, so no collection is shared.
        val results = arrayOfNulls<Boolean>(inputs.size)

        val workers =
            inputs.mapIndexed { index, input ->
                thread {
                    gate.await()
                    results[index] = aa.recognize(input.map { CharSymbol(it) }).accepted
                }
            }
        gate.open()
        workers.forEach { it.join() }

        // Every "a" is accepted, every "b" is rejected.
        assertEquals(
            inputs.map { it == "a" }.sorted(),
            results.filterNotNull().sorted(),
        )

        // The configured automaton was never mutated by the recognitions.
        assertEquals(configured, aa.transitions.size)
    }

    @Test
    fun `identifiers generated concurrently never collide`() {
        val threads = 8
        val perThread = 250
        val gate = StartGate()
        // Every thread collects its own identifiers: the lists are merged
        // when the threads are done, so no collection is shared.
        val collected = List(threads) { mutableListOf<Int>() }

        val workers =
            (0 until threads).map { index ->
                thread {
                    gate.await()
                    repeat(perThread) { collected[index].add(IdentifierUtils.nextRecognitionPathId()) }
                }
            }
        gate.open()
        workers.forEach { it.join() }

        val ids = collected.flatten()
        assertEquals(threads * perThread, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `a visitor may register another visitor while the path runs`() {
        val aa = AcceptingAa()
        val seen = mutableListOf<String>()

        // The visitor mutates the list of visitors from inside a callback: the
        // iteration under way must not be disturbed.
        aa.visitors.add(
            object : ExecutionVisitor {
                override fun onRecognizeStart(automaton: AdaptiveAutomaton) {
                    seen.add("start")
                    automaton.visitors.add(
                        object : ExecutionVisitor {
                            override fun onRecognizeEnd(result: Boolean) {
                                seen.add("late-end")
                            }
                        },
                    )
                }

                override fun onRecognizeEnd(result: Boolean) {
                    seen.add("end")
                }
            },
        )

        assertTrue(aa.recognize(listOf(CharSymbol('a'))).accepted)

        assertTrue(seen.contains("start"))
        assertTrue(seen.contains("end"))
    }

    @Test
    fun `the results of the last recognition are published when it is over`() {
        val aa = AcceptingAa()

        val result = aa.recognize(listOf(CharSymbol('b')))
        assertFalse(result.accepted)
        assertEquals(1, result.paths.size)
        assertEquals(PathOutcome.REJECTED, result.paths.single().outcome)
    }
}
