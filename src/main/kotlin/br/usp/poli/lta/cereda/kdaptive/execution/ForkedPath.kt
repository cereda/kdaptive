// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge

/**
 * A child path created by a fork, together with the edge it was created for.
 *
 * When a path meets several applicable transitions it forks: the parent is
 * replaced by one child per alternative, and each child starts by considering
 * the alternative it was created for.
 *
 * @property pathId the identifier of the child path
 * @property edge the transition edge the child was created for
 */
data class ForkedPath(
    /** The identifier of the child path. */
    val pathId: Int,
    /** The transition edge the child was created for. */
    val edge: TransitionEdge,
)
