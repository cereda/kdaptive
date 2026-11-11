// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.logging.logger
import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.Actions
import br.usp.poli.lta.cereda.kdaptive.model.Stack
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachines
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.TransitionMapping
import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils
import br.usp.poli.lta.cereda.kdaptive.utils.PathOutcome
import br.usp.poli.lta.cereda.kdaptive.utils.RecognitionPath
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
 * Configuration for a [PathExecutor], grouping the automaton's mutable state.
 *
 * The configuration never holds a null: a path that runs always has a state,
 * a submachine and a main submachine. "Not configured yet" is not a state a
 * path can be in.
 *
 * @property currentState the current state of the path
 * @property currentSubmachine the name of the submachine currently executing
 * @property mainSubmachine the name of the main submachine
 * @property stack the call stack used for submachine calls and returns
 * @property actions the set of actions registered in the automaton
 * @property transitions the mapping of transitions being traversed
 * @property submachines the set of submachines of the automaton
 * @property input the input symbols being recognised
 * @property cursor the current position in [input]
 * @property enablePriorAction whether prior actions still have to fire for the
 *   selected transition (set to false by a prior action so it does not re-fire)
 * @property actionEpoch the number of actions that fired in this lineage; it
 *   tells whether the future of a configuration is still the one it was
 * @property limits the structural bounds the engine enforces
 * @property visitors Visitors observing this path's execution events.
 */
data class PathExecutorConfig(
    var currentState: State,
    var currentSubmachine: String,
    var mainSubmachine: String,
    var stack: Stack = Stack(),
    var actions: Actions = Actions(),
    var transitions: TransitionMapping = TransitionMapping(),
    var submachines: Submachines = Submachines(),
    var input: List<Symbol> = emptyList(),
    var cursor: Int = 0,
    var enablePriorAction: Boolean = true,
    var actionEpoch: Int = 0,
    var limits: RecognitionLimits = RecognitionLimits(),
    var visitors: MutableList<ExecutionVisitor> = mutableListOf(),
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
    /** The unique identifier of this recognition path. */
    val id: Int = IdentifierUtils.nextKernelId(),
    /** The configuration holding this path's mutable state. */
    val config: PathExecutorConfig,
    /** The shared worklist of path executors. */
    internal val threads: MutableList<PathExecutor>,
    /** The shared recognition paths collected so far. */
    internal val paths: MutableMap<Int, RecognitionPath>,
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
    private val observers: List<ExecutionVisitor> = config.visitors.toList()

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
    private val consumerOnEdgeAdded = config.transitions.onEdgeAdded
    private val consumerOnEdgeRemoved = config.transitions.onEdgeRemoved
    private val consumerOnOperation = config.transitions.onOperation
    private val consumerOnObservation = config.transitions.onObservation

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
     * The configurations this lineage has left.
     *
     * A forked path inherits the lineage of its parent and extends it with
     * its own configurations, so it sees what its ancestors left and nothing
     * else. Two siblings therefore never see each other's moves.
     */
    private var lineage: LineageNode? = null

    /** The number of transitions this path has traversed. */
    private var steps: Int = 0

    // Convenience accessors delegating to [config].

    /** The call stack used for submachine calls and returns. */
    val stack: Stack get() = config.stack

    /** The set of actions registered in the automaton. */
    val actions: Actions get() = config.actions

    /** The mapping of transitions being traversed. */
    val transitions: TransitionMapping get() = config.transitions

    /** The set of submachines of the automaton. */
    val submachines: Submachines get() = config.submachines

    /** The input symbols being recognised. */
    val input: List<Symbol> get() = config.input

    /** The current position in [input]. */
    val cursor: Int get() = config.cursor

    /** The current state of the path. */
    val currentState: State get() = config.currentState

    /** Whether a prior action still has to fire for the selected transition. */
    val enablePriorAction: Boolean get() = config.enablePriorAction

    /** The name of the submachine currently executing. */
    val currentSubmachine: String get() = config.currentSubmachine

    /** The name of the main submachine. */
    val mainSubmachine: String get() = config.mainSubmachine

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

        observers.forEach { it.onPathStart(id, config.input) }

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
     */
    private fun wireCallbacks() {
        config.transitions.onEdgeAdded = { edge ->
            consumerOnEdgeAdded(edge)
            val topology = Topology.of(config.transitions, config.submachines)
            observers.forEach { it.onEdgeAdded(id, edge, topology) }
        }
        config.transitions.onEdgeRemoved = { edge ->
            consumerOnEdgeRemoved(edge)
            val topology = Topology.of(config.transitions, config.submachines)
            observers.forEach { it.onEdgeRemoved(id, edge, topology) }
        }
        config.transitions.onOperation = { operation ->
            consumerOnOperation(operation)
            observers.forEach { it.onMappingOperation(id, operation) }
        }
        config.transitions.onObservation = { observation ->
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
        config.enablePriorAction = true

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
        val query = applicableTransitions(config.currentState, config.cursor)
        return when {
            query.isEmpty() -> handleNoTransition()

            isRepeatedConfiguration() -> cut()

            else -> {
                // The path leaves this configuration: it is now part of the lineage.
                leaveConfiguration()
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
        if (ref !is ActionRef.Bound || !config.enablePriorAction) return PathStep.Continue(tr)

        observers.forEach { it.onBeforeAction(id, ref, isPrior = true) }

        logger.debug("[Path $id] Executing prior action ${ref.name} on transition ${tr.id}.")
        actions.fromName(ref.name).handler(transitions, tr.transition, ref.args)
        config.actionEpoch = config.actionEpoch + 1
        config.enablePriorAction = false
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
        config.currentState = tr.source
        return chooseMove()
    }

    /** Applies the transition, updating state, cursor, and submachine. */
    private fun applyTransition(tr: TransitionEdge) {
        steps = steps + 1
        checkStepBound()
        when (tr.transition) {
            is Transition.SymbolTransition -> {
                config.currentState = tr.target
                config.cursor = config.cursor + 1
            }

            is Transition.Call -> {
                stack.push(tr.target)
                checkCallDepth()
                config.currentSubmachine = tr.transition.submachine
                config.currentState = submachines.fromName(tr.transition.submachine).initialState
                observers.forEach { it.onCall(id, tr.transition.submachine) }
            }

            is Transition.Epsilon -> {
                config.currentState = tr.target
            }

            is Transition.Return -> {
                val returned = stack.pop()
                config.currentState = returned
                config.currentSubmachine = submachines.fromState(returned).name
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
        config.actionEpoch = config.actionEpoch + 1
        observers.forEach { it.onAfterAction(id, ref, isPrior = false) }
    }

    /**
     * Handles termination when no applicable transition exists.
     *
     * @return [PathStep.Stop] if the path accepted or rejected, or
     *   [PathStep.Continue] with a return transition for submachine returns.
     */
    private fun handleNoTransition(): PathStep {
        val result = terminationResult(config.currentState, config.cursor, config.currentSubmachine)
        return when (result) {
            PathVerdict.Accept -> {
                accept()
                observers.forEach { it.onPathEnd(id, PathOutcome.ACCEPTED, config.cursor) }
                PathStep.Stop
            }

            PathVerdict.Reject -> {
                reject()
                observers.forEach { it.onPathEnd(id, PathOutcome.REJECTED, config.cursor) }
                PathStep.Stop
            }

            PathVerdict.ReturnFromSubmachine -> {
                PathStep.Continue(
                    TransitionEdge(
                        source = config.currentState,
                        target = config.currentState,
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
                fork(alt, config.currentState, config.cursor, config.currentSubmachine, config.enablePriorAction)
            }
        observers.forEach { it.onPathFork(id, children) }
    }

    /**
     * Creates a child path for [transition], inheriting this path's stack,
     * actions, topology and visitor chain.
     *
     * The child owns a private copy of the topology, so a mutation on one
     * path never reaches its siblings. Every edge of that copy has its own
     * identifier, so the transition the child takes is the *copy* of
     * [transition] that lives in the child's own mapping: the checks and the
     * removals that refer to an edge by its identifier must refer to the
     * edges the child really owns.
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
        val childTransitions = transitions.copy()
        childTransitions.onEdgeAdded = consumerOnEdgeAdded
        childTransitions.onEdgeRemoved = consumerOnEdgeRemoved
        childTransitions.onOperation = consumerOnOperation
        childTransitions.onObservation = consumerOnObservation
        val childTransition = childTransitions.transitions.firstOrNull { it == transition } ?: transition
        val child =
            PathExecutor(
                config =
                PathExecutorConfig(
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
                    actionEpoch = config.actionEpoch,
                    limits = config.limits,
                    visitors = config.visitors.toMutableList(),
                ),
                threads = threads,
                paths = paths,
            )
        child.designated = childTransition
        child.lineage = lineage
        val parent = paths[id]
        paths[child.id] =
            RecognitionPath(
                id = child.id,
                path = parent?.path.orEmpty(),
                cursor = curCursor,
                state = curState,
            )
        threads.add(child)
        logger.debug("[Path $id] Forked path ${child.id} for transition ${transition.id}.")
        return ForkedPath(child.id, childTransition)
    }

    /**
     * Records the traversal of [edge] in the trail of this path.
     *
     * The trail records every traversal, for every path: it is the history of
     * the path, not a decoration of the forked ones.
     */
    private fun recordTraversal(edge: TransitionEdge) {
        val current = paths[id] ?: return
        paths[id] = current.addStep(edge).copy(state = config.currentState, cursor = config.cursor)
    }

    /** The configuration this path is currently in. */
    private fun currentConfiguration(): Configuration = Configuration(
        state = config.currentState,
        submachine = config.currentSubmachine,
        cursor = config.cursor,
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
    private fun isRepeatedConfiguration(): Boolean {
        val node = lineage?.find(currentConfiguration()) ?: return false
        return node.epoch == config.actionEpoch
    }

    /** Records the configuration this path is leaving. */
    private fun leaveConfiguration() {
        lineage = LineageNode(currentConfiguration(), config.actionEpoch, lineage)
    }

    /**
     * Cuts this path: it has re-entered a configuration it had already left,
     * so it can only go round in circles.
     *
     * A cut is not a rejection: the path never reached a verdict, and saying
     * otherwise would hide a branch the engine pruned from the consumer.
     */
    private fun cut(): PathStep {
        paths[id] =
            paths[id]?.copy(outcome = PathOutcome.CUT, cursor = config.cursor, state = config.currentState)
                ?: RecognitionPath(
                    id = id,
                    outcome = PathOutcome.CUT,
                    cursor = config.cursor,
                    state = config.currentState,
                )
        observers.forEach { it.onPathEnd(id, PathOutcome.CUT, config.cursor) }
        logger.debug("[Path $id] Cut: the path re-entered a configuration it had already left.")
        return PathStep.Stop
    }

    /** Records the acceptance of this path. */
    private fun accept() {
        paths[id] =
            paths[id]?.copy(outcome = PathOutcome.ACCEPTED, cursor = config.cursor, state = config.currentState)
                ?: RecognitionPath(
                    id = id,
                    outcome = PathOutcome.ACCEPTED,
                    cursor = config.cursor,
                    state = config.currentState,
                )
        logger.debug("[Path $id] String accepted.")
    }

    /** Records the rejection of this path. */
    private fun reject() {
        paths[id] =
            paths[id]?.copy(outcome = PathOutcome.REJECTED, cursor = config.cursor, state = config.currentState)
                ?: RecognitionPath(
                    id = id,
                    outcome = PathOutcome.REJECTED,
                    cursor = config.cursor,
                    state = config.currentState,
                )
        logger.debug("[Path $id] String rejected.")
    }

    /** Checks that this path has not traversed more transitions than allowed. */
    private fun checkStepBound() {
        val bound = config.limits.maxStepsPerPath
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
        val bound = config.limits.maxCallDepth
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
        val bound = config.limits.maxLivePaths
        val live = threads.size
        if (live <= bound) return
        throw IllegalStateException(
            "The recognition had $live live paths, more than the limit of $bound. " +
                "The automaton probably keeps forking without ever reaching a verdict. " +
                "Raise RecognitionLimits.maxLivePaths if the automaton is known to terminate.",
        )
    }
}
