// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.logging.logger
import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.Stack
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.sets.ActionsSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.SubmachinesSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils
import br.usp.poli.lta.cereda.kdaptive.utils.RecognitionPath
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private enum class PathResult { Accept, Reject, ReturnFromSubmachine }

private sealed class PathStep {
    object Stop : PathStep()

    data class Continue(val transition: TransitionEdge) : PathStep()
}

/**
 * Configuration for a [PathExecutor], grouping the automaton's mutable state.
 *
 * @property stack the call stack used for submachine calls and returns
 * @property actions the set of actions registered in the automaton
 * @property transitions the mapping of transitions being traversed
 * @property submachines the set of submachines of the automaton
 * @property input the input symbols being recognised
 * @property cursor the current position in [input]
 * @property transition the currently-selected transition edge
 * @property currentState the current state of the path
 * @property enablePriorAction whether prior actions still have to fire for the
 *   selected transition (set to false by a prior action so it does not re-fire)
 * @property currentSubmachine the name of the submachine currently executing
 * @property mainSubmachine the name of the main submachine
 */
data class PathExecutorConfig(
    var stack: Stack = Stack(),
    var actions: ActionsSet = ActionsSet(),
    var transitions: TransitionMapping = TransitionMapping(),
    var submachines: SubmachinesSet = SubmachinesSet(),
    var input: List<Symbol> = emptyList(),
    var cursor: Int = 0,
    var transition: TransitionEdge? = null,
    var currentState: State? = null,
    var enablePriorAction: Boolean = true,
    var currentSubmachine: String? = null,
    var mainSubmachine: String? = null,
    /** Visitors observing this path's execution events. */
    var visitors: MutableList<ExecutionVisitor> = mutableListOf(),
)

/**
 * Executes one recognition path of the adaptive automaton.
 *
 * Replaces the original 3335-line [Thread]-based kernel with a compact,
 * iterative recogniser. Each [PathExecutor] owns one path: [run] loops
 * until the path terminates (accept/reject) or forks new paths for
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

    // Convenience accessors delegating to [config].

    /** The call stack used for submachine calls and returns. */
    val stack: Stack get() = config.stack

    /** The set of actions registered in the automaton. */
    val actions: ActionsSet get() = config.actions

    /** The mapping of transitions being traversed. */
    val transitions: TransitionMapping get() = config.transitions

    /** The set of submachines of the automaton. */
    val submachines: SubmachinesSet get() = config.submachines

    /** The input symbols being recognised. */
    val input: List<Symbol> get() = config.input

    /** The current position in [input]. */
    val cursor: Int get() = config.cursor

    /** The current state of the path. */
    val currentState: State? get() = config.currentState

    /** Whether a prior action still has to fire for the selected transition. */
    val enablePriorAction: Boolean get() = config.enablePriorAction

    /** The name of the submachine currently executing. */
    val currentSubmachine: String? get() = config.currentSubmachine

    /** The name of the main submachine. */
    val mainSubmachine: String? get() = config.mainSubmachine

    /**
     * Runs this recognition path to completion.
     *
     * The path loops until it accepts, rejects, or forks new paths for
     * non-deterministic choices. When [run] returns, the path is finished
     * and should be removed from the worklist.
     */
    fun run() {
        logger.debug("Path $id started run()")

        // Wire topology mutation callbacks so visitors observe every edge
        // add/remove on this path's transition mapping.
        config.transitions.onEdgeAdded = { edge ->
            config.visitors.forEach { it.onEdgeAdded(id, edge, config.transitions, config.submachines) }
        }
        config.transitions.onEdgeRemoved = { edge ->
            config.visitors.forEach { it.onEdgeRemoved(id, edge, config.transitions, config.submachines) }
        }

        config.visitors.forEach { it.onPathStart(id, config.input) }

        var tr = config.transition ?: return

        while (true) {
            val step = processStep(tr)
            when (step) {
                is PathStep.Stop -> return
                is PathStep.Continue -> tr = step.transition
            }
        }
    }

    /** Processes one computational step. Returns PathStep.Stop if the path
     * terminated, or PathStep.Continue with the next transition. */
    private fun processStep(tr: TransitionEdge): PathStep {
        config.visitors.forEach { it.onBeforeTraverse(id, tr) }

        // Apply prior action if present and enabled
        val afterPrior = applyPriorAction(tr)
        val current =
            when (afterPrior) {
                is PathStep.Stop -> return PathStep.Stop
                is PathStep.Continue -> afterPrior.transition
            }

        applyTransition(current)
        executePostAction(current)
        config.enablePriorAction = true

        config.visitors.forEach { it.onAfterTraverse(id, current) }

        val query = applicableTransitions(config.currentState, config.cursor)

        return when {
            query.isEmpty() -> {
                handleNoTransition()
            }

            query.size > 1 -> {
                forkAll(query)
                PathStep.Stop
            }

            else -> {
                PathStep.Continue(query.first())
            }
        }
    }

    /** Applies a prior action if present and enabled. Returns the next transition
     * edge to process, or PathStep.Stop if the path terminated. */
    private fun applyPriorAction(tr: TransitionEdge): PathStep {
        if (tr.prior == null || !config.enablePriorAction) return PathStep.Continue(tr)

        val ref = tr.prior
        config.visitors.forEach { it.onBeforeAction(id, ref, isPrior = true) }

        logger.debug("[Path $id] Executing prior action ${ref.name} on transition ${tr.id}.")
        val name = ref.name
            ?: error(
                "Prior action on transition ${tr.id} has no name; an action reference must name a registered action.",
            )
        actions.fromName(name).handler(transitions, tr.transition, ref.args)
        config.enablePriorAction = false
        config.visitors.forEach { it.onAfterAction(id, ref, isPrior = true) }

        return if (transitions.hasIdentifier(tr.id)) PathStep.Continue(tr) else handleNoTransition()
    }

    /** Applies the transition, updating state, cursor, and submachine. */
    private fun applyTransition(tr: TransitionEdge) {
        when (tr.transition) {
            is Transition.SymbolTransition -> {
                config.currentState = tr.target
                config.cursor = config.cursor + 1
            }

            is Transition.Call -> {
                stack.push(tr.target)
                config.currentSubmachine = tr.transition.submachine
                config.currentState = submachines.getFromName(tr.transition.submachine).initialState
                config.visitors.forEach { it.onCall(id, tr.transition.submachine) }
            }

            is Transition.Epsilon -> {
                config.currentState = tr.target
            }

            is Transition.Return -> {
                val returned = stack.pop()
                config.currentState = returned
                config.currentSubmachine = submachines.getFromState(returned).name
                config.visitors.forEach { it.onReturn(id) }
            }
        }
        config.transition = tr
    }

    /** Executes the post action after the transition has been applied. */
    private fun executePostAction(tr: TransitionEdge) {
        val ref = tr.post ?: return
        config.visitors.forEach { it.onBeforeAction(id, ref, isPrior = false) }

        val name = ref.name
            ?: error(
                "Post action on transition ${tr.id} has no name; an action reference must name a registered action.",
            )
        logger.debug("[Path $id] Executing post action $name on transition ${tr.id}.")
        actions.fromName(name).handler(transitions, tr.transition, ref.args)
        config.visitors.forEach { it.onAfterAction(id, ref, isPrior = false) }
    }

    /**
     * Handles termination when no applicable transition exists.
     * Returns PathStep.Stop if the path accepted or rejected, or
     * PathStep.Continue with a return transition for submachine returns.
     */
    private fun handleNoTransition(): PathStep {
        val result = terminationResult(config.currentState, config.cursor, config.currentSubmachine)
        return when (result) {
            PathResult.Accept -> {
                accept(config.currentState, config.cursor)
                config.visitors.forEach { it.onPathEnd(id, accepted = true, cursor = config.cursor) }
                PathStep.Stop
            }

            PathResult.Reject -> {
                reject(config.currentState, config.cursor)
                config.visitors.forEach { it.onPathEnd(id, accepted = false, cursor = config.cursor) }
                PathStep.Stop
            }

            PathResult.ReturnFromSubmachine -> {
                val currentState = config.currentState ?: return PathStep.Stop
                PathStep.Continue(
                    TransitionEdge(
                        source = currentState,
                        target = currentState,
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
     * Uses a single pass over the transition sequence for efficiency.
     */
    private fun applicableTransitions(state: State?, cursor: Int): List<TransitionEdge> {
        val s = state ?: return emptyList()
        return transitions
            .withSourceState(s)
            .asSequence()
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
            }.toList()
    }

    private fun terminationResult(state: State?, cursor: Int, submachine: String?): PathResult {
        // Defensive fallback: a null current submachine is treated as the main one.
        val smName = submachine ?: mainSubmachine ?: return PathResult.Reject
        val sm = submachines.getFromName(smName)
        val isAccepting = state != null && sm.acceptingStates.contains(state)

        return when {
            !isAccepting -> PathResult.Reject
            cursor < input.size && stack.isEmpty() -> PathResult.Reject
            cursor < input.size -> PathResult.ReturnFromSubmachine
            cursor == input.size && stack.isEmpty() && submachine == mainSubmachine -> PathResult.Accept
            cursor == input.size && stack.isEmpty() -> PathResult.Reject
            else -> PathResult.ReturnFromSubmachine
        }
    }

    private fun forkAll(query: List<TransitionEdge>) {
        val childIds =
            query.map { alt ->
                fork(alt, config.currentState, config.cursor, config.currentSubmachine, config.enablePriorAction)
            }
        config.visitors.forEach { it.onPathFork(id, childIds) }
    }

    /**
     * Creates a child path for [transition], inheriting this path's stack,
     * actions, topology and visitor chain.
     *
     * @return the identifier of the newly created child path
     */
    private fun fork(
        transition: TransitionEdge,
        curState: State?,
        curCursor: Int,
        curSubmachine: String?,
        enablePrior: Boolean,
    ): Int {
        val k =
            PathExecutor(
                config =
                PathExecutorConfig(
                    stack = stack.copy(),
                    actions = actions.copy(),
                    transitions = transitions.copy(),
                    submachines = submachines.copy(),
                    input = input,
                    cursor = curCursor,
                    transition = transition,
                    currentState = curState,
                    enablePriorAction = enablePrior,
                    currentSubmachine = curSubmachine,
                    mainSubmachine = mainSubmachine,
                    visitors = config.visitors.toMutableList(),
                ),
                threads = threads,
                paths = paths,
            )
        paths[k.id] = paths[id]?.addStep(transition.toString()) ?: RecognitionPath().addStep(transition.toString())
        threads.add(k)
        logger.debug("[Path $id] Forked path ${k.id} for transition ${transition.id}.")
        return k.id
    }

    private fun accept(state: State?, cursor: Int) {
        paths[id] = paths[id]?.copy(result = true, cursor = cursor, state = state)
            ?: RecognitionPath(result = true, cursor = cursor, state = state)
        logger.debug("[Path $id] String accepted.")
    }

    private fun reject(state: State?, cursor: Int) {
        paths[id] = paths[id]?.copy(result = false, cursor = cursor, state = state)
            ?: RecognitionPath(result = false, cursor = cursor, state = state)
        logger.debug("[Path $id] String rejected.")
    }
}
