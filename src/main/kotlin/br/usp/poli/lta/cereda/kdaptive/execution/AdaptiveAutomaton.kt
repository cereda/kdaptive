// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.Stack
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.TopologyValidator
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.sets.ActionsSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.SubmachinesSet
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import br.usp.poli.lta.cereda.kdaptive.utils.PathOutcome
import br.usp.poli.lta.cereda.kdaptive.utils.RecognitionPath
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.properties.Delegates

/**
 * The adaptive automaton proper.
 *
 * Subclasses implement [setup] to define transitions, submachines and actions.
 * [recognize] runs the recognition process on a clone so the original automaton
 * is not mutated by adaptive actions during execution.
 */
abstract class AdaptiveAutomaton {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /** The mapping of transitions of the adaptive automaton. */
    var transitions: TransitionMapping = TransitionMapping()

    /** The set of submachines of the adaptive automaton. */
    var submachines: SubmachinesSet = SubmachinesSet()

    /** The set of actions of the adaptive automaton. */
    var actions: ActionsSet = ActionsSet()

    /**
     * The stack used during recognition.
     *
     * Recognition runs on a clone, and each path keeps its own copy of the
     * stack; this field holds the stack the clone starts from.
     */
    var stack: Stack = Stack()

    /**
     * Main submachine name. Changes are logged via [Delegates.observable].
     */
    var mainSubmachine: String? by Delegates.observable(null) { prop, old, new ->
        logger.debug("mainSubmachine changed: $old -> $new")
    }

    // -----------------------------------------------------------------------
    // Ergonomic transition DSL (mirrors AutomatonBuilder for direct use in setup())
    // -----------------------------------------------------------------------

    private fun resolveState(value: Any): State = when (value) {
        is State -> value

        is String -> throw IllegalArgumentException(
            "String state names are not supported in AdaptiveAutomaton.setup(). " +
                "Use state objects or build via automaton { } + specToAutomaton().",
        )

        else -> throw IllegalArgumentException("Cannot resolve '$value' to State")
    }

    private fun resolveSymbol(value: Any): Symbol = when (value) {
        is Symbol -> value
        is Char -> CharSymbol(value)
        else -> throw IllegalArgumentException("Cannot resolve '$value' to Symbol")
    }

    private fun resolveActionRef(value: Any): ActionRef = when (value) {
        ActionRef.None -> ActionRef.None

        is ActionRef.Bound -> value

        is String -> ActionRef.Bound(name = value)

        is ActionRef -> throw IllegalArgumentException(
            "An edge must bind a concrete action, but '$value' is a filter. " +
                "Use ActionRef.Bound(name, args) to attach an action to an edge.",
        )

        else -> throw IllegalArgumentException("Cannot resolve '$value' to ActionRef")
    }

    /**
     * Adds a symbol-consuming transition. Accepts raw [Char] for symbols.
     *
     * @throws IllegalArgumentException if the symbol is a wildcard ([Symbol.Any])
     *   or [Symbol.Epsilon]; use [epsilon] for an epsilon transition
     */
    fun consume(from: Any, symbol: Any, to: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
        val s = resolveState(from)
        val t = resolveState(to)
        val sym = resolveSymbol(symbol)
        require(sym !== Symbol.Any && sym !== Symbol.Epsilon) {
            "Cannot consume '$sym': a transition consumes a concrete symbol. " +
                "Use epsilon(from, to) for an epsilon transition."
        }
        val p = resolveActionRef(prior)
        val po = resolveActionRef(post)
        transitions.add(
            TransitionEdge(
                source = s,
                target = t,
                transition = Transition.SymbolTransition(symbol = sym),
                prior = p,
                post = po,
            ),
        )
    }

    /**
     * Adds an epsilon transition, [prior]/[post] being optional action references.
     *
     * The prior action fires before the move, the post action after it.
     */
    fun epsilon(from: Any, to: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
        val s = resolveState(from)
        val t = resolveState(to)
        transitions.add(
            TransitionEdge(
                source = s,
                target = t,
                transition = Transition.Epsilon,
                prior = resolveActionRef(prior),
                post = resolveActionRef(post),
            ),
        )
    }

    /**
     * Adds a submachine call transition, [prior]/[post] being optional action
     * references.
     *
     * The prior action fires before the call (the return state is not pushed
     * yet), the post action after the jump into the called submachine.
     */
    fun call(from: Any, submachineName: String, to: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
        val s = resolveState(from)
        val t = resolveState(to)
        transitions.add(
            TransitionEdge(
                source = s,
                target = t,
                transition = Transition.Call(submachine = submachineName),
                prior = resolveActionRef(prior),
                post = resolveActionRef(post),
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Block grouping: transitions from the same source state (in setup())
    // -----------------------------------------------------------------------

    /**
     * Groups transitions from the same source state using a builder block.
     *
     * Example:
     * ```
     * from(q0) {
     *     symbol('a', q1, post = "A")
     *     symbol('b', q2)
     *     epsilon(q3)
     *     call("M", q4, prior = "X")
     * }
     * ```
     */
    fun from(source: Any, configure: FromGroup.() -> Unit) {
        FromGroup(this, source).configure()
    }

    /** Builder for transitions from a single source state within [AdaptiveAutomaton.setup()]. */
    class FromGroup(private val aa: AdaptiveAutomaton, private val source: Any) {
        /** Adds a symbol-consuming transition. */
        fun symbol(sym: Any, target: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
            aa.consume(source, sym, target, prior, post)
        }

        /** Adds an epsilon transition from the group's source state. */
        fun epsilon(target: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
            aa.epsilon(source, target, prior, post)
        }

        /** Adds a submachine call transition from the group's source state. */
        fun call(submachine: String, target: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
            aa.call(source, submachine, target, prior, post)
        }
    }

    private val threads = mutableListOf<PathExecutor>()
    private val paths = mutableMapOf<Int, RecognitionPath>()

    /**
     * The clone that ran the last recognition, published once that recognition
     * is over.
     *
     * The field is volatile: it is written by the thread that ran the
     * recognition and read by whoever asks for the results, possibly from
     * another thread.
     */
    @Volatile
    private var reference: AdaptiveAutomaton? = null

    /** When true, recognition stops as soon as the first path finishes (whether accepted or rejected). */
    var stopAtFirstFinishedRecognitionPath: Boolean = false

    /** Visitors observing recognition lifecycle and path execution events. */
    var visitors: MutableList<ExecutionVisitor> = mutableListOf()

    /**
     * Defines the initial configuration of the adaptive automaton.
     *
     * The engine calls [setup] on a clone for every recognition, so it must
     * be safe to call more than once: declaring the same transition, the same
     * submachine or the same action twice does not duplicate it.
     */
    abstract fun setup()

    /** Creates a new empty instance of the concrete subclass (for cloning). */
    protected abstract fun newInstance(): AdaptiveAutomaton

    /**
     * Returns the recognition paths collected during the last recognition,
     * keyed by path ID.
     *
     * Only the paths that reached a verdict are reported: a path replaced by
     * its children is not one of them.
     *
     * When several recognitions run concurrently on the same automaton, the
     * paths of the most recent one are reported.
     *
     * @throws IllegalStateException if [recognize] has not been called yet.
     */
    fun getRecognitionMap(): Map<Int, RecognitionPath> = reference?.getRecognitionMapOnce()
        ?: error("recognize() must be called before querying recognition paths.")

    /**
     * Returns the recognition paths collected during the last recognition.
     *
     * Only the paths that reached a verdict are reported: a path replaced by
     * its children is not one of them.
     *
     * When several recognitions run concurrently on the same automaton, the
     * paths of the most recent one are reported.
     *
     * @throws IllegalStateException if [recognize] has not been called yet.
     */
    fun getRecognitionPaths(): List<RecognitionPath> = reference?.getRecognitionPathsOnce()
        ?: error("recognize() must be called before querying recognition paths.")

    private fun getRecognitionMapOnce(): Map<Int, RecognitionPath> = paths.filterValues { it.done }

    private fun getRecognitionPathsOnce(): List<RecognitionPath> = paths.values.filter { it.done }

    /**
     * Recognises a list of symbols representing the input string.
     *
     * The recognition runs on a clone, so the automaton itself is never
     * mutated: several recognitions may run concurrently on the same
     * automaton, and each of them sees the topology the automaton had when it
     * started.
     *
     * @return true if the adaptive automaton recognised the input string.
     */
    fun recognize(input: List<Symbol>): Boolean {
        val ref = clone()
        try {
            return ref.recognizeOnce(input)
        } finally {
            // The results are published once the recognition is over, so a
            // reader never sees a half-finished run.
            reference = ref
        }
    }

    private fun clone(): AdaptiveAutomaton {
        val instance = newInstance()
        require(instance !== this) { "newInstance() must return a fresh automaton, not this one." }
        instance.transitions = transitions.copy()
        instance.submachines = submachines.copy()
        instance.actions = actions.copy()
        instance.stack = stack.copy()
        instance.mainSubmachine = mainSubmachine
        instance.stopAtFirstFinishedRecognitionPath = stopAtFirstFinishedRecognitionPath
        instance.visitors = visitors.toMutableList()
        return instance
    }

    private fun recognizeOnce(input: List<Symbol>): Boolean {
        setup()

        // The topology follows the same rules as the one the builder DSL
        // produces: the imperative API and the declarative API accept exactly
        // the same automata.
        TopologyValidator.validate(
            edges = transitions.getTransitions(),
            submachines = submachines.getSubmachines(),
            mainSubmachine = mainSubmachine,
        )

        // The main submachine must exist before recognition starts. Checking it
        // here guarantees that the lifecycle events always pair: no start
        // without an end, and no end without a start.
        val mainSub = requireNotNull(mainSubmachine) { "Main submachine cannot be null." }

        // The lifecycle events fire on the clone, after setup() materialised its
        // topology, so a visitor sees the automaton it is about to execute.
        visitors.toList().forEach { it.onRecognizeStart(this) }

        threads.clear()
        paths.clear()

        val k =
            PathExecutor(
                config =
                PathExecutorConfig(
                    stack = stack.copy(),
                    actions = actions.copy(),
                    transitions = transitions.copy(),
                    submachines = submachines.copy(),
                    input = input,
                    cursor = 0,
                    enablePriorAction = true,
                    currentSubmachine = mainSub,
                    mainSubmachine = mainSub,
                    visitors = visitors.toMutableList(),
                ),
                threads = threads,
                paths = paths,
            )

        val initialState = submachines.getFromName(mainSub).initialState
        val t =
            TransitionEdge(
                source = initialState,
                target = initialState,
                transition = Transition.Epsilon,
            )
        k.config.transition = t
        k.config.currentState = null

        paths[k.id] = RecognitionPath(id = k.id)
        threads.add(k)

        while (threads.isNotEmpty() && !atLeastOneRecognitionPathIsDone()) {
            var i = threads.size - 1
            while (i >= 0) {
                try {
                    threads[i].run()
                } catch (e: Exception) {
                    failPath(threads[i], e)
                }
                // When run() returns, the path is finished (accepted, rejected,
                // or forked). Remove it from the worklist.
                threads.removeAt(i)
                i--
            }
        }

        val result = paths.values.any { it.outcome == PathOutcome.ACCEPTED }
        visitors.toList().forEach { it.onRecognizeEnd(result) }
        return result
    }

    /**
     * Reports the failure of [failed] and closes the recognition.
     *
     * A failing path terminates like any other: the path-end and the
     * recognition-end events are fired before the failure propagates, so every
     * path ends exactly once and the lifecycle events always pair.
     *
     * @param failed the path that failed
     * @param cause the exception that killed it
     */
    private fun failPath(failed: PathExecutor, cause: Exception): Nothing {
        visitors.toList().forEach { it.onPathEnd(failed.id, accepted = false, cursor = failed.cursor) }
        visitors.toList().forEach { it.onRecognizeEnd(false) }
        // Surface path failures to the caller instead of killing the JVM.
        throw IllegalStateException(
            "Recognition path ${failed.id} failed: ${cause.message}",
            cause,
        )
    }

    private fun atLeastOneRecognitionPathIsDone(): Boolean {
        if (!stopAtFirstFinishedRecognitionPath) return false
        return paths.values.any { it.done }
    }

    /** Returns a human-readable representation of this automaton, with labeled sections. */
    override fun toString(): String {
        fun String.indented() = lineSequence().joinToString("\n  ")
        return buildString {
            append("Adaptive automaton {\n")
            append("  main: ").append(mainSubmachine ?: "undefined").append("\n")
            append("  submachines: ").append(submachines.toString().indented()).append("\n")
            append("  transitions: ").append(transitions.toString().indented()).append("\n")
            append("  actions: ").append(actions.toString().indented()).append("\n")
            append("  stack: ").append(stack).append("\n")
            append("}")
        }
    }

    /** Sets whether recognition should stop when the first path finishes (see [stopAtFirstFinishedRecognitionPath]). */
    fun setStopAtFirstResult(flag: Boolean) {
        this.stopAtFirstFinishedRecognitionPath = flag
    }
}
