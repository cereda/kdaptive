// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.utils

import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge

/**
 * The verdict of a recognition path.
 *
 * A path has four possible outcomes: it has not decided yet ([PENDING]), it
 * accepted the input ([ACCEPTED]), it rejected it ([REJECTED]), or it was cut
 * ([CUT]) because it had entered a configuration it had already left. The
 * verdict is a value in its own right, never a null: a path that has not
 * decided says so.
 *
 * A cut path is not a rejection. The engine cuts a branch it proved cannot
 * reach a verdict, and it reports the cut as such, so that a consumer can
 * tell "the input was refused" from "this branch was pruned".
 */
enum class PathOutcome {
    /** The path has not reached a verdict yet. */
    PENDING,

    /** The path accepted the input. */
    ACCEPTED,

    /** The path rejected the input. */
    REJECTED,

    /** The path was cut: it entered a configuration it had already left. */
    CUT,
}

/**
 * A recognition path: all transitions traversed during recognition of an input string.
 *
 * @param id the unique identifier for this path; it is the key under which
 *   the path is reported in the recognition map, and it stays the same for
 *   the whole life of the path
 * @param path the edges traversed by this path, in order
 * @param cursor the current cursor position in the input
 * @param state the state the path is currently in
 * @param outcome the verdict of the path ([PathOutcome.PENDING] while it runs)
 */
data class RecognitionPath(
    val id: Int = IdentifierUtils.nextRecognitionPathId(),
    val path: List<TransitionEdge> = emptyList(),
    val cursor: Int = 0,
    val state: State,
    val outcome: PathOutcome = PathOutcome.PENDING,
) {
    /** Whether this recognition path has finished (the outcome is known). */
    val done: Boolean get() = outcome != PathOutcome.PENDING

    /** Adds a traversed edge to the recognition path. */
    fun addStep(step: TransitionEdge): RecognitionPath = copy(path = path + step)

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
