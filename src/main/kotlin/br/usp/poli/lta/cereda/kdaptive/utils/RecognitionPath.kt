// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.utils

import br.usp.poli.lta.cereda.kdaptive.model.State

/**
 * A recognition path: all transitions traversed during recognition of an input string.
 *
 * @param id the unique identifier for this path; it is the key under which
 *   the path is reported in the recognition map, and it stays the same for
 *   the whole life of the path
 * @param path the list of transition IDs in order
 * @param cursor the current cursor position in the input
 * @param state the current state, or null if none
 * @param result the recognition result, if determined
 */
data class RecognitionPath(
    val id: Int = IdentifierUtils.nextRecognitionPathId(),
    val path: List<String> = emptyList(),
    val cursor: Int = 0,
    val state: State? = null,
    val result: Boolean? = null,
) {
    /** Whether this recognition path has finished (result is known). */
    val done: Boolean get() = result != null

    /** Adds a step to the recognition path. */
    fun addStep(step: String): RecognitionPath = copy(path = path + listOf(step))

    /** Returns a human-readable representation of this recognition path. */
    override fun toString(): String = buildString {
        append("RecognitionPath(id=$id")
        result?.let { append(", result=$it") }
        append(")")
        if (path.isNotEmpty()) {
            append(" { ${path.joinToString(", ")} }")
        }
    }
}
