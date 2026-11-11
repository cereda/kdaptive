// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.utils

import br.usp.poli.lta.cereda.kdaptive.model.State

/**
 * The verdict of a recognition path.
 *
 * A path has three possible outcomes: it has not decided yet
 * ([PENDING]), it accepted the input ([ACCEPTED]), or it rejected it
 * ([REJECTED]). The verdict is a value in its own right, never a null: a
 * path that has not decided says so.
 */
enum class PathOutcome {
    /** The path has not reached a verdict yet. */
    PENDING,

    /** The path accepted the input. */
    ACCEPTED,

    /** The path rejected the input. */
    REJECTED,
}

/**
 * A recognition path: all transitions traversed during recognition of an input string.
 *
 * @param id the unique identifier for this path; it is the key under which
 *   the path is reported in the recognition map, and it stays the same for
 *   the whole life of the path
 * @param path the list of transition IDs in order
 * @param cursor the current cursor position in the input
 * @param state the current state, or null if none
 * @param outcome the verdict of the path ([PathOutcome.PENDING] while it runs)
 */
data class RecognitionPath(
    val id: Int = IdentifierUtils.nextRecognitionPathId(),
    val path: List<String> = emptyList(),
    val cursor: Int = 0,
    val state: State? = null,
    val outcome: PathOutcome = PathOutcome.PENDING,
) {
    /** Whether this recognition path has finished (the outcome is known). */
    val done: Boolean get() = outcome != PathOutcome.PENDING

    /** Adds a step to the recognition path. */
    fun addStep(step: String): RecognitionPath = copy(path = path + listOf(step))

    /** Returns a human-readable representation of this recognition path. */
    override fun toString(): String = buildString {
        append("RecognitionPath(id=$id")
        if (outcome != PathOutcome.PENDING) {
            append(", outcome=$outcome")
        }
        append(")")
        if (path.isNotEmpty()) {
            append(" { ${path.joinToString(", ")} }")
        }
    }
}
