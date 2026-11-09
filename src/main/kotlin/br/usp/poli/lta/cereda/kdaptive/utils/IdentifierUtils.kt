// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.utils

/**
 * Generates unique identifiers for submachines, recognition paths, actions,
 * transitions and kernel threads.
 *
 * Call [reset] in tests to make identifier generation deterministic.
 */
object IdentifierUtils {
    private val counters = mutableMapOf<String, Int>()

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
    fun reset() {
        counters.clear()
    }
}
