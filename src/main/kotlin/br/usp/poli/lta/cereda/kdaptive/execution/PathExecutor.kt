// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.logging.logger
import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.Actions
import br.usp.poli.lta.cereda.kdaptive.model.Identifiers
import br.usp.poli.lta.cereda.kdaptive.model.Stack
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachines
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.TransitionMapping
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** The verdict the executor reaches when a path gets stuck. */
private enum class PathVerdict { Accept, Reject, ReturnFromSubmachine }

/** What the executor does next. */
private sealed class PathStep {
    /** The path is over. */
    object Stop : PathStep()

    /** The path goes on with the given transition. */
    data class Continue(val transition: TransitionEdge) : PathStep()
}

/**
 * The mutable state of a recognition path.
 *
 * The state never holds a null: a path that runs always has a state, a
 * submachine and a main submachine. "Not configured yet" is not a state a
 * path can be in.
 *
 * The class is deliberately *not* a data class. It groups the mutable state
 * of a path, and a generated `equals`, `hashCode` and `copy` over that state
 * would say more about the internals of a running path than a consumer needs
 * to know — and would invite a consumer to take a snapshot of a path that is
 * still running.
 *
 * @property currentState the current state of the path
 * @property currentSubmachine the name of the submachine currently executing
 * @property mainSubmachine the name of the main submachine
 * @property stack the call stack used for submachine calls and returns
 * @property actions the actions registered in the automaton
 * @property transitions the mapping of transitions being traversed
 * @property submachines the submachines of the automaton
 * @property input the input symbols being recognised
 * @property cursor the current position in [input]
 * @property enablePriorAction whether prior actions still have to fire for the
 *   selected transition (set to false by a prior action so it does not re-fire)
 * @property actionEpoch the number of actions that fired in this lineage; it
 *   tells whether the future of a configuration is still the one it was
 * @property limits the structural bounds the engine enforces
 */
@Suppress("LongParameterList")
class PathState(
    /** The current state of the path. */
    var currentState: State,
    /** The name of the submachine currently executing. */
    var currentSubmachine: String,
    /** The name of the main submachine. */
    var mainSubmachine: String,
    /** The call stack used for submachine calls and returns. */
    var stack: Stack = Stack(),
    /** The actions registered in the automaton. */
    var actions: Actions = Actions(),
    /** The mapping of transitions being traversed. */
    var transitions: TransitionMapping = TransitionMapping(),
    /** The submachines of the automaton. */
    var submachines: Submachines = Submachines(),
    /** The input symbols being recognised. */
    var input: List<Symbol> = emptyList(),
    /** The current position in [input]. */
    var cursor: Int = 0,
    /** Whether prior actions still have to fire for the selected transition. */
    var enablePriorAction: Boolean = true,
    /** The number of actions that fired in this lineage. */
    var actionEpoch: Int = 0,
    /** The structural bounds the engine enforces. */
    var limits: RecognitionLimits = RecognitionLimits(),
)

/**
 * Executes one recognition path of the adaptive automaton.
 *
 * Replaces the original 3335-line [Thread]-based kernel with a compact,
 * iterative recogniser. Each [PathExecutor] owns one path: [run] loops
 * until the path terminates (accept/reject/cut) or forks new paths for
 * non-deterministic choices.
 *
 * Semantics preserved from the original:
 * - prior actions run before a transition is applied and may mutate the map
 * - symbol consumption advances the cursor and moves to the target state
 * - epsilon transitions move to the target state without consuming input
 * - submachine calls push the return state and jump to the submachine
 *   initial state
 * - submachine returns pop the stack and resume in the caller submachine
 * - acceptance: end of input + accepting state + empty stack + main submachine
 *
 * On top of that, the executor refuses to spin forever: a path that arrives
 * at a configuration its lineage already left, with no action fired since,
 * is cut. See [PathOutcome.CUT].
 */
class PathExecutor(
    /** The unique identifier for this recognition path. */
    val id: Int = Identifiers.nextRecognitionPathId(),
    /** The mutable state of this recognition path. */
    val state: PathState,
    /** The shared worklist of path executors. */
    internal val worklist: MutableList<PathExecutor>,
    /** The shared recognition paths collected so far. */
    internal val paths: MutableMap<Int, RecognitionPath>,
    /** The visitors observing this path's execution events. */
    visitors: List<ExecutionVisitor> = emptyList(),
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * The visitors observing this path, snapshotted when the path is created.
     *
     * The snapshot keeps the path stable: a visitor that registers another
     * visitor (or removes one) while the path runs does not disturb the
     * iteration that is under way, and the path never sees a visitor that
     * arrived halfway through.
     */
    private val observers: List<ExecutionVisitor> = visitors.toList()

    /**
     * The consumer's own callbacks, captured before the engine chains its
     * visitor dispatch onto them.
     *
     * The engine reports every mutation of the path's mapping to its
     * visitors; the callbacks the consumer registered on the mapping must
     * keep firing, exactly once per event. A forked path inherits a copy of
     * the mapping, and therefore a copy of these callbacks — it must not
     * inherit the parent's dispatch, which reports under the parent's path
     * identifier.
     */
    private val consumerOnEdgeAdded = state.transitions.onEdgeAdded
    private val consumerOnEdgeRemoved = state.transitions.onEdgeRemoved
    private val consumerOnOperation = state.transitions.onOperation
    private val consumerOnObservation = state.transitions.onObservation

    /**
     * The edge this path was forked for.
     *
     * A forked path takes the edge it was created for, and only that edge.
     * The root path of a recognition has none: it chooses its first move
     * among the transitions applicable at the initial state of the main
     * submachine.
     */
    private var designated: TransitionEdge? = null

    /**
     * The configurations this lineage has left, and the action epoch of each.
     *
     * A forked path inherits the lineage of its parent and extends it with
     * its own configurations, so it sees what its ancestors left and nothing
     * else. Two siblings therefore never see each other's moves.
     *
     * The index is shared with the children of this path until one of them
     * changes it: a child that leaves a configuration of its own takes a
     * private index, and its siblings keep the one they were given.
     */
    private var lineage: MutableMap<Configuration, Int> = mutableMapOf()

    /** Whether [lineage] is shared with another path and must be copied before it changes. */
    private var lineageShared: Boolean = false

    /** The number of transitions this path has traversed. */
    private var steps: Int = 0

    /**
     * The edges this path has traversed, in order.
     *
     * The trail is the history of the path, not a decoration of the forked
     * ones: a forked path inherits the trail of its parent and extends it.
     * The trail is kept as a list the path appends to, and it is flattened
     * into an immutable list only when the path reaches a verdict.
     */
    private val trail: MutableList<TransitionEdge> = mutableListOf()

    // Convenience accessors delegating to [state].

    /** The call stack used for submachine calls and returns. */
    val stack: Stack get() = state.stack

    /** The actions registered in the automaton. */
    val actions: Actions get() = state.actions

    /** The mapping of transitions being traversed. */
    val transitions: TransitionMapping get() = state.transitions

    /** The submachines of the automaton. */
    val submachines: Submachines get() = state.submachines

    /** The input symbols being recognised. */
    val input: List<Symbol> get() = state.input

    /** The current position in [input]. */
    val cursor: Int get() = state.cursor

    /** The current state of the path. */
    val currentState: State get() = state.currentState

    /** Whether a prior action still has to fire for the selected transition. */
    val enablePriorAction: Boolean get() = state.enablePriorAction

    /** The name of the submachine currently executing. */
    val currentSubmachine: String get() = state.currentSubmachine

    /** The name of the main submachine. */
    val mainSubmachine: String get() = state.mainSubmachine

    /**
     * Runs this recognition path to completion.
     *
     * The path loops until it accepts, rejects, is cut, or forks new paths for
     * non-deterministic choices. When [run] returns, the path is finished and
     * should be removed from the worklist.
     */
    fun run() {
        logger.debug("Path $id started run()")

        wireCallbacks()

        observers.forEach { it.onPathStart(id, state.input) }

        // The first move: a forked path takes the edge it was forked for; the
        // root path chooses among the transitions applicable where it starts.
        var pending: TransitionEdge? = designated
        while (true) {
            val next = pending
            val step = if (next == null) chooseMove() else processStep(next)
            when (step) {
                is PathStep.Stop -> return
                is PathStep.Continue -> pending = step.transition
            }
        }
    }

    /**
     * Wires the topology, operation and fact channels of this path.
     *
     * The consumer's own callbacks are chained, not replaced: they still
     * fire, once per event.
     *
     * The topology snapshot is only taken when someone is there to receive
     * it: a recognition nobody observes pays no price for the snapshots it
     * would never show.
     */
    private fun wireCallbacks() {
        state.transitions.onEdgeAdded = { edge ->
            consumerOnEdgeAdded(edge)
            if (observers.isNotEmpty()) {
                val topology = Topology.of(state.transitions, state.submachines)
                observers.forEach { it.onEdgeAdded(id, edge, topology) }
            }
        }
        state.transitions.onEdgeRemoved = { edge ->
            consumerOnEdgeRemoved(edge)
            if (observers.isNotEmpty()) {
                val topology = Topology.of(state.transitions, state.submachines)
                observers.forEach { it.onEdgeRemoved(id, edge, topology) }
            }
        }
        state.transitions.onOperation = { operation ->
            consumerOnOperation(operation)
            observers.forEach { it.onMappingOperation(id, operation) }
        }
        state.transitions.onObservation = { observation ->
            consumerOnObservation(observation)
            observers.forEach { it.onObservation(id, observation) }
        }
    }

    /** Processes one computational step. */
    private fun processStep(tr: TransitionEdge): PathStep {
        observers.forEach { it.onBeforeTraverse(id, tr) }

        val afterPrior = applyPriorAction(tr)
        val current =
            when (afterPrior) {
                is PathStep.Stop -> return PathStep.Stop
                is PathStep.Continue -> afterPrior.transition
            }

        applyTransition(current)
        executePostAction(current)
        state.enablePriorAction = true

        observers.forEach { it.onAfterTraverse(id, current) }

        recordTraversal(current)

        return chooseMove()
    }

    /**
     * Chooses the next move of the path among the applicable transitions.
     *
     * A path that is stuck reaches its verdict here: it accepts, it rejects,
     * or it returns from the submachine it is running. A path that is not
     * stuck goes on — unless it has come back to a configuration its lineage
     * already left, in which case it is cut.
     */
    private fun chooseMove(): PathStep {
        val query = applicableTransitions(state.currentState, state.cursor)
        if (query.isEmpty()) return handleNoTransition()

        val configuration = currentConfiguration()
        return when {
            isRepeatedConfiguration(configuration) -> cut()

            else -> {
                // The path leaves this configuration: it is now part of the lineage.
                leaveConfiguration(configuration)
                if (query.size > 1) {
                    forkAll(query)
                    PathStep.Stop
                } else {
                    PathStep.Continue(query.first())
                }
            }
        }
    }

    /**
     * Applies a prior action if present and enabled.
     *
     * @return the next transition to process, or [PathStep.Stop] if the path terminated.
     */
    private fun applyPriorAction(tr: TransitionEdge): PathStep {
        val ref = tr.prior
        if (ref !is ActionRef.Bound || !state.enablePriorAction) return PathStep.Continue(tr)

        observers.forEach { it.onBeforeAction(id, ref, isPrior = true) }

        logger.debug("[Path $id] Executing prior action ${ref.name} on transition ${tr.id}.")
        actions.fromName(ref.name).handler(transitions, tr.transition, ref.args)
        state.actionEpoch = state.actionEpoch + 1
        state.enablePriorAction = false
        observers.forEach { it.onAfterAction(id, ref, isPrior = true) }

        return if (transitions.contains(tr.id)) PathStep.Continue(tr) else replan(tr)
    }

    /**
     * Re-plans the path after its prior action removed the transition that
     * was about to be taken.
     *
     * The transition never happened: the path is still at the source state of
     * the removed transition, and it must choose again among the transitions
     * that are applicable there. This is what the original implementation
     * did — a prior action that removes its own edge does not kill the path,
     * it makes the path choose again.
     *
     * The prior action does not fire again on the transition chosen here: the
     * path has just run it, and running it again would loop forever.
     *
     * @param tr the transition the prior action removed
     * @return the transition to take next, or [PathStep.Stop] if the path ended
     */
    private fun replan(tr: TransitionEdge): PathStep {
        logger.debug("[Path $id] Transition ${tr.id} was removed by its prior action; replanning.")
        state.currentState = tr.source
        return chooseMove()
    }

    /** Applies the transition, updating state, cursor, and submachine. */
    private fun applyTransition(tr: TransitionEdge) {
        steps = steps + 1
        checkStepBound()
        when (tr.transition) {
            is Transition.SymbolTransition -> {
                state.currentState = tr.target
                state.cursor = state.cursor + 1
            }

            is Transition.Call -> {
                stack.push(tr.target)
                checkCallDepth()
                state.currentSubmachine = tr.transition.submachine
                state.currentState = submachines.fromName(tr.transition.submachine).initialState
                observers.forEach { it.onCall(id, tr.transition.submachine) }
            }

            is Transition.Epsilon -> {
                state.currentState = tr.target
            }

            is Transition.Return -> {
                val returned = stack.pop()
                state.currentState = returned
                state.currentSubmachine = submachines.fromState(returned).name
                observers.forEach { it.onReturn(id) }
            }
        }
    }

    /** Executes the post action after the transition has been applied. */
    private fun executePostAction(tr: TransitionEdge) {
        val ref = tr.post
        if (ref !is ActionRef.Bound) return
        observers.forEach { it.onBeforeAction(id, ref, isPrior = false) }

        logger.debug("[Path $id] Executing post action ${ref.name} on transition ${tr.id}.")
        actions.fromName(ref.name).handler(transitions, tr.transition, ref.args)
        state.actionEpoch = state.actionEpoch + 1
        observers.forEach { it.onAfterAction(id, ref, isPrior = false) }
    }

    /**
     * Handles termination when no applicable transition exists.
     *
     * @return [PathStep.Stop] if the path accepted or rejected, or
     *   [PathStep.Continue] with a return transition for submachine returns.
     */
    private fun handleNoTransition(): PathStep {
        val result = terminationResult(state.currentState, state.cursor, state.currentSubmachine)
        return when (result) {
            PathVerdict.Accept -> {
                accept()
                observers.forEach { it.onPathEnd(id, PathOutcome.ACCEPTED, state.cursor) }
                PathStep.Stop
            }

            PathVerdict.Reject -> {
                reject()
                observers.forEach { it.onPathEnd(id, PathOutcome.REJECTED, state.cursor) }
                PathStep.Stop
            }

            PathVerdict.ReturnFromSubmachine -> {
                PathStep.Continue(
                    TransitionEdge(
                        source = state.currentState,
                        target = state.currentState,
                        transition = Transition.Return,
                    ),
                )
            }
        }
    }

    /**
     * Returns the transitions applicable from [state] at [cursor].
     * Inside the input, symbol-consuming transitions for the current symbol are
     * eligible; epsilon and submachine-call transitions are always eligible.
     *
     * Uses the cached index of the mapping for efficiency.
     */
    private fun applicableTransitions(state: State, cursor: Int): List<TransitionEdge> = transitions
        .edgesFrom(state)
        .filter { tr ->
            when (tr.transition) {
                is Transition.SymbolTransition -> {
                    cursor < input.size && tr.transition.symbol == input[cursor]
                }

                is Transition.Epsilon, is Transition.Call -> {
                    true
                }

                is Transition.Return -> {
                    false
                }
            }
        }

    /** Tells the verdict of a path that is stuck in [state] at [cursor]. */
    private fun terminationResult(state: State, cursor: Int, submachine: String): PathVerdict {
        val sm = submachines.fromName(submachine)
        val isAccepting = sm.acceptingStates.contains(state)

        return when {
            !isAccepting -> PathVerdict.Reject
            cursor < input.size && stack.isEmpty -> PathVerdict.Reject
            cursor < input.size -> PathVerdict.ReturnFromSubmachine
            cursor == input.size && stack.isEmpty && submachine == mainSubmachine -> PathVerdict.Accept
            cursor == input.size && stack.isEmpty -> PathVerdict.Reject
            else -> PathVerdict.ReturnFromSubmachine
        }
    }

    /** Creates one child path per alternative and reports the fork. */
    private fun forkAll(query: List<TransitionEdge>) {
        val children =
            query.map { alt ->
                fork(alt, state.currentState, state.cursor, state.currentSubmachine, state.enablePriorAction)
            }
        observers.forEach { it.onPathFork(id, children) }
    }

    /**
     * Creates a child path for [transition], inheriting this path's stack,
     * actions, topology and visitor chain.
     *
     * The child owns a private copy of the topology, so a mutation on one
     * path never reaches its siblings. The copy is lazy: the two mappings
     * hold the same edges until one of them is mutated, and the copy then
     * takes a set of its own. The edges themselves are shared, because an
     * edge is immutable and its identifier is unique across the process —
     * the checks and the removals that refer to an edge by its identifier
     * therefore refer to the right edge in either mapping.
     *
     * The copy also carries the consumer's callbacks, but not the parent's
     * visitor dispatch: the child reports under its own path identifier.
     *
     * @return the child path, carrying the edge it was created for
     */
    private fun fork(
        transition: TransitionEdge,
        curState: State,
        curCursor: Int,
        curSubmachine: String,
        enablePrior: Boolean,
    ): ForkedPath {
        checkLivePathBound()
        transitions.shareEdges()
        val childTransitions = transitions.isolatedCopy()
        childTransitions.onEdgeAdded = consumerOnEdgeAdded
        childTransitions.onEdgeRemoved = consumerOnEdgeRemoved
        childTransitions.onOperation = consumerOnOperation
        childTransitions.onObservation = consumerOnObservation
        val child =
            PathExecutor(
                state =
                PathState(
                    currentState = curState,
                    currentSubmachine = curSubmachine,
                    mainSubmachine = mainSubmachine,
                    stack = stack.copy(),
                    actions = actions.copy(),
                    transitions = childTransitions,
                    submachines = submachines.copy(),
                    input = input,
                    cursor = curCursor,
                    enablePriorAction = enablePrior,
                    actionEpoch = state.actionEpoch,
                    limits = state.limits,
                ),
                worklist = worklist,
                paths = paths,
                visitors = observers,
            )
        child.designated = transition
        child.lineage = lineage
        child.lineageShared = true
        lineageShared = true
        child.trail.addAll(trail)
        paths[child.id] =
            RecognitionPath(
                id = child.id,
                cursor = curCursor,
                state = curState,
            )
        worklist.add(child)
        logger.debug("[Path $id] Forked path ${child.id} for transition ${transition.id}.")
        return ForkedPath(child.id, transition)
    }

    /**
     * Records the traversal of [edge] in the trail of this path.
     *
     * The trail records every traversal, for every path: it is the history of
     * the path, not a decoration of the forked ones.
     */
    private fun recordTraversal(edge: TransitionEdge) {
        trail.add(edge)
    }

    /** The configuration this path is currently in. */
    private fun currentConfiguration(): Configuration = Configuration(
        state = state.currentState,
        submachine = state.currentSubmachine,
        cursor = state.cursor,
        stack = stack.elements,
        digest = transitions.structuralDigest(),
    )

    /**
     * Tells whether this path has come back to a configuration its lineage
     * already left.
     *
     * The answer is only *yes* when nothing happened in between: an action
     * that fired since the earlier visit may have changed the future of the
     * configuration, and the engine must let it have its chance.
     */
    private fun isRepeatedConfiguration(configuration: Configuration): Boolean {
        val epoch = lineage[configuration] ?: return false
        return epoch == state.actionEpoch
    }

    /** Records the configuration this path is leaving. */
    private fun leaveConfiguration(configuration: Configuration) {
        if (lineageShared) {
            lineage = lineage.toMutableMap()
            lineageShared = false
        }
        lineage[configuration] = state.actionEpoch
    }

    /**
     * Cuts this path: it has re-entered a configuration it had already left,
     * so it can only go round in circles.
     *
     * A cut is not a rejection: the path never reached a verdict, and saying
     * otherwise would hide a branch the engine pruned from the consumer.
     */
    private fun cut(): PathStep {
        recordOutcome(PathOutcome.CUT)
        observers.forEach { it.onPathEnd(id, PathOutcome.CUT, state.cursor) }
        logger.debug("[Path $id] Cut: the path re-entered a configuration it had already left.")
        return PathStep.Stop
    }

    /** Records the acceptance of this path. */
    private fun accept() {
        recordOutcome(PathOutcome.ACCEPTED)
        logger.debug("[Path $id] String accepted.")
    }

    /** Records the rejection of this path. */
    private fun reject() {
        recordOutcome(PathOutcome.REJECTED)
        logger.debug("[Path $id] String rejected.")
    }

    /**
     * Records the failure of this path.
     *
     * A failed path reached no verdict: it died before it could decide
     * anything. Reporting it as a rejection would claim the input was
     * refused, which is not what happened.
     */
    internal fun markFailed() {
        recordOutcome(PathOutcome.FAILED)
    }

    /** Records the verdict of this path, together with the trail it took to reach it. */
    private fun recordOutcome(outcome: PathOutcome) {
        val history = trail.toList()
        paths[id] =
            paths[id]?.copy(
                outcome = outcome,
                trail = history,
                cursor = state.cursor,
                state = state.currentState,
            ) ?: RecognitionPath(
                id = id,
                trail = history,
                outcome = outcome,
                cursor = state.cursor,
                state = state.currentState,
            )
    }

    /** Checks that this path has not traversed more transitions than allowed. */
    private fun checkStepBound() {
        val bound = state.limits.maxStepsPerPath
        if (steps <= bound) return
        throw IllegalStateException(
            "Recognition path $id traversed $steps transitions, more than the limit of $bound. " +
                "The automaton probably recurses without ever consuming input or reaching a verdict. " +
                "An adaptive action may be keeping the topology changing, so the engine cannot prove " +
                "the branch is bounded; raise RecognitionLimits.maxStepsPerPath if the automaton " +
                "is known to terminate.",
        )
    }

    /** Checks that the call stack has not grown deeper than allowed. */
    private fun checkCallDepth() {
        val bound = state.limits.maxCallDepth
        val depth = stack.elements.size
        if (depth <= bound) return
        throw IllegalStateException(
            "Recognition path $id reached a call stack of depth $depth, more than the limit of $bound. " +
                "The automaton probably calls itself without ever consuming input. " +
                "Raise RecognitionLimits.maxCallDepth if the automaton is known to terminate.",
        )
    }

    /** Checks that the automaton does not have too many paths alive at once. */
    private fun checkLivePathBound() {
        val bound = state.limits.maxLivePaths
        val live = worklist.size
        if (live <= bound) return
        throw IllegalStateException(
            "The recognition had $live live paths, more than the limit of $bound. " +
                "The automaton probably keeps forking without ever reaching a verdict. " +
                "Raise RecognitionLimits.maxLivePaths if the automaton is known to terminate.",
        )
    }
}
