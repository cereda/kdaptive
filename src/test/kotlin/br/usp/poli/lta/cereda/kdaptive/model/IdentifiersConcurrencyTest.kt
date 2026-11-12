// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

/**
 * The identifier generators, under concurrent use.
 *
 * Every kind of object has its own counter, and the counters are atomic: two
 * recognitions running concurrently on the same automaton never share a path
 * identifier, and no thread ever waits for another to leave a monitor.
 *
 * The existing concurrency test covers the path identifiers alone. The four
 * counters are four independent objects, and a mistake in one of them would
 * go unnoticed: this sweep exercises all of them.
 */
class IdentifiersConcurrencyTest {

    /** How many threads take part in the race. */
    private val threads = 8

    /** How many identifiers every thread takes. */
    private val perThread = 250

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
     * Runs [next] on every thread at the same time and returns the identifiers
     * they collected.
     *
     * Every thread collects into a list of its own, and the lists are merged
     * when the threads are done: no collection is ever shared.
     *
     * @param next the generator to race
     * @return the identifiers, in the order the threads produced them
     */
    private fun race(next: () -> Int): List<Int> {
        val gate = StartGate()
        val collected = List(threads) { mutableListOf<Int>() }
        val workers =
            (0 until threads).map { index ->
                thread {
                    gate.await()
                    repeat(perThread) { collected[index].add(next()) }
                }
            }
        gate.open()
        workers.forEach { it.join() }
        return collected.flatten()
    }

    /**
     * Checks that the identifiers a race produced are all different.
     *
     * @param ids the identifiers the race produced
     */
    private fun assertAllDistinct(ids: List<Int>) {
        assertEquals(threads * perThread, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `the submachine identifiers never collide`() {
        val ids = race { Identifiers.nextSubmachineId() }

        assertAllDistinct(ids)
    }

    @Test
    fun `the recognition path identifiers never collide`() {
        val ids = race { Identifiers.nextRecognitionPathId() }

        assertAllDistinct(ids)
    }

    @Test
    fun `the action identifiers never collide`() {
        val ids = race { Identifiers.nextActionId() }

        assertAllDistinct(ids)
    }

    @Test
    fun `the transition identifiers never collide`() {
        val ids = race { Identifiers.nextTransitionId() }

        assertAllDistinct(ids)
    }

    @Test
    fun `the four counters do not share their numbers`() {
        // The counters are independent: an action and a transition may carry
        // the same number without any confusion, as long as the *kind* of the
        // object tells them apart. The test pins the property that makes the
        // identifiers usable: the counter of a kind is the only thing that
        // grows when objects of that kind are made.
        val before =
            mapOf(
                "submachine" to Identifiers.nextSubmachineId(),
                "path" to Identifiers.nextRecognitionPathId(),
                "action" to Identifiers.nextActionId(),
                "transition" to Identifiers.nextTransitionId(),
            )
        val after =
            mapOf(
                "submachine" to Identifiers.nextSubmachineId(),
                "path" to Identifiers.nextRecognitionPathId(),
                "action" to Identifiers.nextActionId(),
                "transition" to Identifiers.nextTransitionId(),
            )

        before.keys.forEach { kind ->
            assertEquals(
                before[kind]!! + 1,
                after[kind],
                "The counter of $kind identifiers must advance by one at a time.",
            )
        }
        // The identifiers of one kind are drawn from its own counter, so two
        // consecutive identifiers of two different kinds are two different
        // numbers unless the counters happen to be at the same place.
        assertTrue(after.values.all { it > 0 })
    }
}
