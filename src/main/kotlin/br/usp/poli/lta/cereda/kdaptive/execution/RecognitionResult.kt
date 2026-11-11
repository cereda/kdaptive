// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.utils.PathOutcome
import br.usp.poli.lta.cereda.kdaptive.utils.RecognitionPath

/**
 * The result of a recognition.
 *
 * The result is what [AdaptiveAutomaton.recognize] returns: the verdict of
 * the recognition, and the paths it was made of.
 *
 * The paths are the ones that reached a verdict. A path that was replaced by
 * its children is not one of them: it never decided anything. A path that was
 * cut is one of them, and says so: [cutPaths] tells apart "the input was
 * refused" from "this branch was pruned".
 *
 * @property accepted whether the automaton recognised the input
 * @property paths the paths that reached a verdict
 */
data class RecognitionResult(
    /** Whether the automaton recognised the input. */
    val accepted: Boolean,
    /** The paths that reached a verdict. */
    val paths: List<RecognitionPath>,
) {
    /** The paths that accepted the input. */
    val acceptedPaths: List<RecognitionPath> get() = paths.filter { it.outcome == PathOutcome.ACCEPTED }

    /** The paths that rejected the input. */
    val rejectedPaths: List<RecognitionPath> get() = paths.filter { it.outcome == PathOutcome.REJECTED }

    /** The paths that were cut, because they re-entered a configuration they had already left. */
    val cutPaths: List<RecognitionPath> get() = paths.filter { it.outcome == PathOutcome.CUT }
}
