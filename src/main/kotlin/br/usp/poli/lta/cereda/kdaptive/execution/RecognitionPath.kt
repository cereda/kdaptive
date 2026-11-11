// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.Identifiers
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge

/**
 * The verdict of a recognition path.
 *
 * A path has five possible outcomes: it has not decided yet ([PENDING]), it
 * accepted the input ([ACCEPTED]), it rejected it ([REJECTED]), it was cut
 * ([CUT]) because it had entered a configuration it had already left, or it
 * failed ([FAILED]) because something went wrong while it ran. The verdict is
 * a value in its own right, never a null: a path that has not decided says so.
 *
 * A cut path is not a rejection. The engine cuts a branch it proved cannot
 * reach a verdict, and it reports the cut as such, so that a consumer can
 * tell "the input was refused" from "this branch was pruned".
 *
 * A failed path is not a rejection either. It died before reaching any
 * verdict — an action threw, or a structural bound tripped — so the engine
 * could not decide anything about it. Reporting a failure as a rejection
 * would claim the input was refused when the truth is "unknown".
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

    /** The path failed: it died before reaching a verdict. */
    FAILED,
}

/**
 * A recognition path: all transitions traversed during recognition of an input string.
 *
 * @param id the unique identifier for this path; it is the key under which
 *   the path is reported in the recognition map, and it stays the same for
 *   the whole life of the path
 * @param trail the edges traversed by this path, in order
 * @param cursor the current cursor position in the input
 * @param state the state the path is currently in
 * @param outcome the verdict of the path ([PathOutcome.PENDING] while it runs)
 */
data class RecognitionPath(
    val id: Int = Identifiers.nextRecognitionPathId(),
    val trail: List<TransitionEdge> = emptyList(),
    val cursor: Int = 0,
    val state: State,
    val outcome: PathOutcome = PathOutcome.PENDING,
) {
    /** Whether this recognition path has finished (the outcome is known). */
    val done: Boolean get() = outcome != PathOutcome.PENDING

    /**
     * Returns a copy of this path with [edge] appended to its trail.
     *
     * The trail is copied, so the method costs as much as the trail is long.
     * A consumer that appends edge after edge should keep its own list and
     * build the path once, at the end.
     */
    fun addStep(edge: TransitionEdge): RecognitionPath = copy(trail = trail + edge)

    /** Returns a human-readable representation of this recognition path. */
    override fun toString(): String = buildString {
        append("RecognitionPath(id=$id")
        if (outcome != PathOutcome.PENDING) {
            append(", outcome=$outcome")
        }
        append(")")
        if (trail.isNotEmpty()) {
            append(" { ${trail.joinToString(", ")} }")
        }
    }
}
