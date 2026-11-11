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

/**
 * A configuration a recognition lineage has arrived at.
 *
 * The nodes form the lineage of a path: the path arrives at a configuration,
 * the node remembers it, and the next node remembers the one after. A forked
 * path inherits the lineage of its parent and extends it with its own nodes,
 * so a path sees the configurations its ancestors arrived at — and only those.
 *
 * @property configuration the configuration the lineage arrived at
 * @property epoch the number of actions that had fired when it arrived there
 * @property previous the configuration the lineage arrived at before this one
 */
internal class LineageNode(
    /** The configuration the lineage arrived at. */
    val configuration: Configuration,
    /** The number of actions that had fired when it arrived there. */
    val epoch: Int,
    /** The configuration the lineage arrived at before this one. */
    val previous: LineageNode?,
) {
    /**
     * Finds the most recent configuration of the lineage equal to [configuration].
     *
     * @param configuration the configuration to look for
     * @return the most recent node holding it, or null when the lineage never arrived there
     */
    fun find(configuration: Configuration): LineageNode? {
        var node: LineageNode? = this
        while (node != null) {
            if (node.configuration == configuration) return node
            node = node.previous
        }
        return null
    }
}
