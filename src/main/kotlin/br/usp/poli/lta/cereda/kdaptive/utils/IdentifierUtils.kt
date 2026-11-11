// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.utils

/**
 * Generates unique identifiers for submachines, recognition paths, actions,
 * transitions and kernel threads.
 *
 * The counters are guarded by the monitor of this object, so identifiers stay
 * unique when several threads generate them at the same time: two
 * recognitions running concurrently on the same automaton never share a path
 * identifier.
 *
 * Call [reset] in tests to make identifier generation deterministic. Resetting
 * while other threads generate identifiers is safe, but it may hand the same
 * identifier out twice: reset between tests, not during a recognition.
 */
object IdentifierUtils {
    private val counters = mutableMapOf<String, Int>()

    /**
     * Generates the next identifier of the given [key].
     *
     * The method is synchronized: the read and the update of the counter
     * happen as one step, so two threads never receive the same identifier.
     */
    @Synchronized
    private fun nextId(key: String): Int {
        val current = counters.getOrPut(key) { 0 }
        counters[key] = current + 1
        return current + 1
    }

    /** Generates the next unique submachine ID. */
    fun nextSubmachineId(): Int = nextId("submachine")

    /** Generates the next unique recognition path ID. */
    fun nextRecognitionPathId(): Int = nextId("recognitionPath")

    /** Generates the next unique action ID. */
    fun nextActionId(): Int = nextId("action")

    /** Generates the next unique transition ID. */
    fun nextTransitionId(): Int = nextId("transition")

    /** Generates the next unique kernel ID. */
    fun nextKernelId(): Int = nextId("kernel")

    /** Resets all counters to zero. */
    @Synchronized
    fun reset() {
        counters.clear()
    }
}
