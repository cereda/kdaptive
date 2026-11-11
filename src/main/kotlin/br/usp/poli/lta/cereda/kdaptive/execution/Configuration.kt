// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.State

/**
 * The configuration a recognition path is in.
 *
 * Two configurations are equal when the path would behave identically from
 * both: the same state, in the same submachine, at the same place in the
 * input, with the same call stack, over the same topology.
 *
 * The topology enters the configuration as a *fingerprint* rather than as the
 * edges themselves: the fingerprint describes what the topology holds, not
 * which edges it holds, so two copies of the same topology share it.
 *
 * The configuration is the key of the lineage: the engine indexes the
 * configurations a path has left by the configuration itself, and looks up
 * the lineage in constant time.
 */
internal data class Configuration(
    /** The state the path is in. */
    val state: State,
    /** The name of the submachine the path is running. */
    val submachine: String,
    /** The position of the path in the input. */
    val cursor: Int,
    /** The call stack of the path, from the top to the bottom. */
    val stack: List<State>,
    /** The structural fingerprint of the topology the path runs over. */
    val digest: Long,
)
