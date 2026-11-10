// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.sets.SubmachinesSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping

/**
 * Visitors hook into the recognition execution to observe path lifecycle,
 * traversal, action firing, and topology mutations.
 *
 * All methods have no-op defaults; override only the events you care about.
 * Implementations are invoked synchronously on the recognition thread, so a
 * slow visitor slows recognition. Register visitors via
 * [PathExecutorConfig.visitors] or [AdaptiveAutomaton.visitors].
 */
interface ExecutionVisitor {

    // -----------------------------------------------------------------------
    // Recognition lifecycle
    // -----------------------------------------------------------------------

    /**
     * Called once at the beginning of recognition, on the cloned automaton.
     *
     * The clone has already been through `setup()`, so its transitions and
     * submachines are populated and a visitor may inspect the initial topology.
     */
    fun onRecognizeStart(automaton: AdaptiveAutomaton) = Unit

    /** Called once after recognition finishes, with the overall boolean result. */
    fun onRecognizeEnd(result: Boolean) = Unit

    // -----------------------------------------------------------------------
    // Path lifecycle
    // -----------------------------------------------------------------------

    /** Called when a recognition path begins executing (including forked paths). */
    fun onPathStart(pathId: Int, input: List<Symbol>) = Unit

    /**
     * Called when a recognition path terminates.
     * @param accepted whether the path accepted the input
     * @param cursor the input cursor at termination
     */
    fun onPathEnd(pathId: Int, accepted: Boolean, cursor: Int) = Unit

    /**
     * Called when a recognition path forks into one or more child paths.
     *
     * A forking path reaches no verdict of its own: it is replaced by its
     * children. This event therefore terminates the parent path, exactly as
     * [onPathEnd] terminates an accepting or rejecting path. A visitor that
     * tracks open paths must handle both terminations.
     *
     * @param pathId the identifier of the forking (parent) path
     * @param childPathIds the identifiers of the paths created by the fork, in
     *   the order the alternatives were considered
     */
    fun onPathFork(pathId: Int, childPathIds: List<Int>) = Unit

    // -----------------------------------------------------------------------
    // Traversal
    // -----------------------------------------------------------------------

    /** Called immediately before a transition edge is traversed. */
    fun onBeforeTraverse(pathId: Int, edge: TransitionEdge) = Unit

    /** Called immediately after a transition edge has been traversed. */
    fun onAfterTraverse(pathId: Int, edge: TransitionEdge) = Unit

    // -----------------------------------------------------------------------
    // Action firing
    // -----------------------------------------------------------------------

    /**
     * Called immediately before an action handler executes.
     * @param isPrior true for a prior action, false for a post action
     */
    fun onBeforeAction(pathId: Int, actionRef: ActionRef, isPrior: Boolean) = Unit

    /**
     * Called immediately after an action handler executes.
     * @param isPrior true for a prior action, false for a post action
     */
    fun onAfterAction(pathId: Int, actionRef: ActionRef, isPrior: Boolean) = Unit

    // -----------------------------------------------------------------------
    // Topology mutation
    // -----------------------------------------------------------------------

    /**
     * Called immediately after an edge is added to this path's transition
     * mapping. Includes the full mapping and submachines so a visitor can
     * snapshot the topology (e.g. GraphViz DOT export).
     */
    fun onEdgeAdded(pathId: Int, edge: TransitionEdge, transitions: TransitionMapping, submachines: SubmachinesSet) =
        Unit

    /**
     * Called immediately after an edge is removed from this path's transition
     * mapping. Includes the full mapping and submachines so a visitor can
     * snapshot the topology (e.g. GraphViz DOT export).
     */
    fun onEdgeRemoved(pathId: Int, edge: TransitionEdge, transitions: TransitionMapping, submachines: SubmachinesSet) =
        Unit

    // -----------------------------------------------------------------------
    // Submachine discipline
    // -----------------------------------------------------------------------

    /** Called when a [Transition.Call] is applied, entering a submachine. */
    fun onCall(pathId: Int, submachine: String) = Unit

    /** Called when a [Transition.Return] is applied, leaving a submachine. */
    fun onReturn(pathId: Int) = Unit
}
