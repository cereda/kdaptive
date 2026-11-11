// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Generates unique identifiers for submachines, recognition paths, actions and
 * transitions.
 *
 * Each kind of object has its own counter, and the counters are atomic: two
 * recognitions running concurrently on the same automaton never share a path
 * identifier, and no thread ever waits for another to leave a monitor.
 *
 * The counters are internal state of the library, not of the automaton: an
 * identifier says *which* object it is, not *whose* it is. The generator is
 * therefore internal too — the identifiers it hands out are part of the
 * public API, the generator is not.
 */
@OptIn(ExperimentalAtomicApi::class)
internal object Identifiers {
    /** The counter of submachine identifiers. */
    private val submachineIds = AtomicInt(0)

    /** The counter of recognition path identifiers. */
    private val recognitionPathIds = AtomicInt(0)

    /** The counter of action identifiers. */
    private val actionIds = AtomicInt(0)

    /** The counter of transition identifiers. */
    private val transitionIds = AtomicInt(0)

    /**
     * Takes the next identifier of [counter].
     *
     * The increment is a compare-and-set loop: no thread ever blocks, and two
     * threads racing for an identifier never receive the same one.
     */
    private fun next(counter: AtomicInt): Int {
        while (true) {
            val current = counter.load()
            val next = current + 1
            if (counter.compareAndSet(current, next)) return next
        }
    }

    /** Generates the next unique submachine ID. */
    fun nextSubmachineId(): Int = next(submachineIds)

    /** Generates the next unique recognition path ID. */
    fun nextRecognitionPathId(): Int = next(recognitionPathIds)

    /** Generates the next unique action ID. */
    fun nextActionId(): Int = next(actionIds)

    /** Generates the next unique transition ID. */
    fun nextTransitionId(): Int = next(transitionIds)

    /**
     * Resets all counters to zero.
     *
     * The reset exists for tests, which need identifiers to be reproducible.
     * It is not part of the public API: resetting the counters while other
     * threads generate identifiers may hand the same identifier out twice.
     */
    internal fun reset() {
        submachineIds.store(0)
        recognitionPathIds.store(0)
        actionIds.store(0)
        transitionIds.store(0)
    }
}
