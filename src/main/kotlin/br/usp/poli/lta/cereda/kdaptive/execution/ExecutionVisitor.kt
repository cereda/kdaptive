// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.MappingOperation
import br.usp.poli.lta.cereda.kdaptive.model.Observation
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge

/**
 * Visitors hook into the recognition execution to observe path lifecycle,
 * traversal, action firing, and topology mutations.
 *
 * All methods have no-op defaults; override only the events you care about.
 * Implementations are invoked synchronously on the recognition thread, so a
 * slow visitor slows recognition. Register visitors via
 * [AdaptiveAutomaton.visitors].
 */
public interface ExecutionVisitor {

    // -----------------------------------------------------------------------
    // Recognition lifecycle
    // -----------------------------------------------------------------------

    /**
     * Called once at the beginning of recognition, on the cloned automaton.
     *
     * The clone has already been through `setup()`, so its transitions and
     * submachines are populated and a visitor may inspect the initial topology.
     */
    public fun onRecognizeStart(automaton: AdaptiveAutomaton): Unit = Unit

    /**
     * Called once after recognition finishes, with the result of the recognition.
     *
     * The result carries the verdict and the paths that reached one, so a
     * visitor can inspect the outcome without asking the automaton again.
     *
     * A recognition that *fails* does not reach this event: it reports
     * [onRecognizeFailed] instead. The two events are exclusive, and a
     * recognition that started always ends with exactly one of them.
     */
    public fun onRecognizeEnd(result: RecognitionResult): Unit = Unit

    /**
     * Called when recognition fails, instead of [onRecognizeEnd].
     *
     * A failure is not a rejection: the engine could not decide anything about
     * the input. The path that died reports [PathOutcome.Failed], and this
     * event carries the cause that killed it.
     *
     * @param cause the exception that killed the recognition
     */
    public fun onRecognizeFailed(cause: Throwable): Unit = Unit

    // -----------------------------------------------------------------------
    // Path lifecycle
    // -----------------------------------------------------------------------

    /** Called when a recognition path begins executing (including forked paths). */
    public fun onPathStart(pathId: Int, input: List<Symbol>): Unit = Unit

    /**
     * Called when a recognition path terminates.
     *
     * The path ends with a verdict: it accepted the input, it rejected it, it
     * was cut because it re-entered a configuration it had already left, or it
     * failed because something went wrong while it ran. A cut is not a
     * rejection, a failure is not a rejection either, and the visitor can
     * tell every outcome apart.
     *
     * @param pathId the identifier of the terminated path
     * @param outcome the verdict of the path
     * @param cursor the input cursor at termination
     */
    public fun onPathEnd(pathId: Int, outcome: PathOutcome, cursor: Int): Unit = Unit

    /**
     * Called when a recognition path forks into one or more child paths.
     *
     * A forking path reaches no verdict of its own: it is replaced by its
     * children. This event therefore terminates the parent path, exactly as
     * [onPathEnd] terminates an accepting or rejecting path. A visitor that
     * tracks open paths must handle both terminations.
     *
     * @param pathId the identifier of the forking (parent) path
     * @param children the paths created by the fork, in the order the
     *   alternatives were considered, each carrying the transition edge it
     *   was created for
     */
    public fun onPathFork(pathId: Int, children: List<ForkedPath>): Unit = Unit

    // -----------------------------------------------------------------------
    // Traversal
    // -----------------------------------------------------------------------

    /** Called immediately before a transition edge is traversed. */
    public fun onBeforeTraverse(pathId: Int, edge: TransitionEdge): Unit = Unit

    /** Called immediately after a transition edge has been traversed. */
    public fun onAfterTraverse(pathId: Int, edge: TransitionEdge): Unit = Unit

    // -----------------------------------------------------------------------
    // Action firing
    // -----------------------------------------------------------------------

    /**
     * Called immediately before an action handler executes.
     *
     * @param actionRef the concrete action about to fire, with its arguments
     * @param isPrior true for a prior action, false for a post action
     */
    public fun onBeforeAction(pathId: Int, actionRef: ActionRef.Bound, isPrior: Boolean): Unit = Unit

    /**
     * Called immediately after an action handler executes.
     *
     * @param actionRef the concrete action that fired, with its arguments
     * @param isPrior true for a prior action, false for a post action
     */
    public fun onAfterAction(pathId: Int, actionRef: ActionRef.Bound, isPrior: Boolean): Unit = Unit

    // -----------------------------------------------------------------------
    // Topology mutation
    // -----------------------------------------------------------------------

    /**
     * Called immediately after an edge is added to this path's transition
     * mapping.
     *
     * The topology is a snapshot: it describes the automaton as it is now,
     * and it cannot be used to change the running recognition. A visitor that
     * wants to keep the topology keeps the snapshot, not the live mapping.
     *
     * @param pathId the identifier of the path that performed the mutation
     * @param edge the edge that was added
     * @param topology the topology of the path after the addition
     */
    public fun onEdgeAdded(pathId: Int, edge: TransitionEdge, topology: Topology): Unit = Unit

    /**
     * Called immediately after an edge is removed from this path's transition
     * mapping.
     *
     * The topology is a snapshot: it describes the automaton as it is now,
     * and it cannot be used to change the running recognition. A visitor that
     * wants to keep the topology keeps the snapshot, not the live mapping.
     *
     * @param pathId the identifier of the path that performed the mutation
     * @param edge the edge that was removed
     * @param topology the topology of the path after the removal
     */
    public fun onEdgeRemoved(pathId: Int, edge: TransitionEdge, topology: Topology): Unit = Unit

    // -----------------------------------------------------------------------
    // Operations over the transition mapping
    // -----------------------------------------------------------------------

    /**
     * Called after an elementary action performs an operation on this path's
     * transition mapping.
     *
     * The operation channel is distinct from the topology channel above: the
     * topology events report what changed, once per edge, while this event
     * reports what was attempted, once per elementary operation. A removal of
     * three edges therefore produces one operation event and three topology
     * events; count removals here and edges removed there, but do not sum
     * them.
     *
     * @param operation the operation that was performed
     */
    public fun onMappingOperation(pathId: Int, operation: MappingOperation): Unit = Unit

    // -----------------------------------------------------------------------
    // Published facts
    // -----------------------------------------------------------------------

    /**
     * Called when instrumentation-aware code publishes a fact about the
     * execution.
     *
     * The engine reports what it does on the operation channel above; this
     * channel carries the facts that only action code can produce, such as
     * the size of a domain the action computed. The library never interprets
     * an observation: the consumer decides what it means.
     *
     * @param observation the fact that was published
     */
    public fun onObservation(pathId: Int, observation: Observation): Unit = Unit

    // -----------------------------------------------------------------------
    // Submachine discipline
    // -----------------------------------------------------------------------

    /** Called when a [Transition.Call] is applied, entering a submachine. */
    public fun onCall(pathId: Int, submachine: String): Unit = Unit

    /** Called when a [Transition.Return] is applied, leaving a submachine. */
    public fun onReturn(pathId: Int): Unit = Unit
}
