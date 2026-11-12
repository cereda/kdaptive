// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

/**
 * The structural bounds the engine enforces while it recognises.
 *
 * The bounds are a backstop, not a policy: they exist for the topologies the
 * engine cannot *prove* to be bounded, namely the ones whose configuration
 * never repeats because an adaptive action keeps changing it. For every other
 * topology the engine decides on its own, and these bounds never fire.
 *
 * When a bound fires, the engine throws: the answer there is "unknown", not
 * "rejected". Silently refusing the input would be a lie, and a timeout
 * would be a wall-clock policy the engine has no business making.
 *
 * @property maxLivePaths the largest number of paths that may be alive at once
 * @property maxStepsPerPath the largest number of transitions a single path may traverse
 * @property maxCallDepth the largest depth the call stack may reach
 */
public data class RecognitionLimits(
    /** The largest number of paths that may be alive at once. */
    val maxLivePaths: Int = 10_000,
    /** The largest number of transitions a single path may traverse. */
    val maxStepsPerPath: Int = 10_000,
    /** The largest depth the call stack may reach. */
    val maxCallDepth: Int = 1_000,
) {
    init {
        require(maxLivePaths > 0) { "The maximum number of live paths must be positive, but it was $maxLivePaths." }
        require(maxStepsPerPath > 0) {
            "The maximum number of steps per path must be positive, but it was $maxStepsPerPath."
        }
        require(maxCallDepth > 0) { "The maximum call depth must be positive, but it was $maxCallDepth." }
    }
}
