// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.Actions
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.Stack
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachines
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import br.usp.poli.lta.cereda.kdaptive.model.TopologyValidator
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.TransitionMapping
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/**
 * The adaptive automaton proper.
 *
 * Subclasses implement [setup] to define transitions, submachines and actions.
 * [recognize] runs the recognition process on a clone so the original automaton
 * is not mutated by adaptive actions during execution.
 */
public abstract class AdaptiveAutomaton {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * The mapping of transitions of the adaptive automaton.
     *
     * The mapping is replaced wholesale when the automaton is cloned, and
     * never from the outside: the topology of a running automaton is the
     * engine's business.
     */
    public var transitions: TransitionMapping = TransitionMapping()
        private set

    /** The submachines of the adaptive automaton. */
    public var submachines: Submachines = Submachines()
        private set

    /** The actions of the adaptive automaton. */
    public var actions: Actions = Actions()
        private set

    /**
     * The stack a recognition starts from.
     *
     * Recognition runs on a clone, and each path keeps its own copy of the
     * stack; this field holds the stack the clone starts from. It is a seed,
     * not the stack of a running path.
     */
    public var initialStack: Stack = Stack()

    /**
     * The slot holding the name of the main submachine.
     *
     * The slot is private: "not configured yet" is a real state, but it is
     * not a state a consumer may observe. The public accessor fails fast
     * instead of answering with a null.
     */
    private var mainSubmachineSlot: String? = null

    /**
     * The name of the main submachine, where recognition starts.
     *
     * Reading it before it was declared fails with a clear message: a null
     * would only spread the mistake.
     *
     * @throws IllegalStateException if no main submachine was declared
     */
    public var mainSubmachine: String
        get() = mainSubmachineSlot
            ?: error(
                "Main submachine was not declared. " +
                    "Declare one with mainSubmachine = \"M\" before recognising.",
            )
        set(value) {
            logger.debug("mainSubmachine changed: {} -> {}", mainSubmachineSlot, value)
            mainSubmachineSlot = value
        }

    /**
     * The structural bounds the engine enforces while it recognises.
     *
     * The bounds are a backstop for the topologies the engine cannot prove to
     * be bounded; they never fire on an automaton that terminates.
     */
    public var limits: RecognitionLimits = RecognitionLimits()

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
     * The three builders of a transition are named after the kind of move they
     * describe: [symbol] consumes a symbol, [epsilon] consumes nothing, and
     * [call] enters another submachine.
     *
     * @throws IllegalArgumentException if the symbol is a wildcard ([Symbol.Any])
     *   or [Symbol.Epsilon]; use [epsilon] for an epsilon transition
     */
    public fun symbol(from: Any, symbol: Any, to: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
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
    public fun epsilon(from: Any, to: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
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
    public fun call(
        from: Any,
        submachineName: String,
        to: Any,
        prior: Any = ActionRef.None,
        post: Any = ActionRef.None,
    ) {
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
    public fun from(source: Any, configure: FromGroup.() -> Unit) {
        FromGroup(this, source).configure()
    }

    /** Builder for transitions from a single source state within [AdaptiveAutomaton.setup()]. */
    public class FromGroup(private val aa: AdaptiveAutomaton, private val source: Any) {
        /** Adds a symbol-consuming transition. */
        public fun symbol(sym: Any, target: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
            aa.symbol(source, sym, target, prior, post)
        }

        /** Adds an epsilon transition from the group's source state. */
        public fun epsilon(target: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
            aa.epsilon(source, target, prior, post)
        }

        /** Adds a submachine call transition from the group's source state. */
        public fun call(submachine: String, target: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
            aa.call(source, submachine, target, prior, post)
        }
    }

    /** The worklist of the paths that still have to run. */
    private val worklist = mutableListOf<PathExecutor>()

    /** The paths of the running recognition, by identifier. */
    private val paths = mutableMapOf<Int, RecognitionPath>()

    /**
     * When true, recognition stops as soon as a path *accepts* the input.
     *
     * Only an acceptance is decisive: under the existence semantics of the
     * automaton, a rejecting path says nothing about the other branches still
     * in flight, so stopping on one would answer "rejected" for an input the
     * automaton does accept.
     */
    public var stopOnFirstAcceptance: Boolean = false

    /**
     * Visitors observing recognition lifecycle and path execution events.
     *
     * The list is mutable so that a visitor may be registered after the
     * automaton is built, but the property itself is not: replacing the whole
     * list would silently drop the visitors a subclass registered in its
     * constructor.
     */
    public val visitors: MutableList<ExecutionVisitor> = mutableListOf()

    /**
     * Defines the initial configuration of the adaptive automaton.
     *
     * The engine calls [setup] on a clone for every recognition, so it must
     * be safe to call more than once: declaring the same transition, the same
     * submachine or the same action twice does not duplicate it.
     */
    public abstract fun setup()

    /** Creates a new empty instance of the concrete subclass (for cloning). */
    protected abstract fun newInstance(): AdaptiveAutomaton

    /**
     * Recognises a list of symbols representing the input string.
     *
     * The recognition runs on a clone, so the automaton itself is never
     * mutated: several recognitions may run concurrently on the same
     * automaton, and each of them sees the topology the automaton had when it
     * started. The result of one recognition therefore never depends on
     * another one running at the same time.
     *
     * @param input the symbols to recognise
     * @return the result of the recognition: the verdict, and the paths it was made of
     * @throws IllegalStateException if the recognition failed
     */
    public fun recognize(input: List<Symbol>): RecognitionResult {
        val ref = clone()
        return ref.recognizeOnce(input)
    }

    private fun clone(): AdaptiveAutomaton {
        val instance = newInstance()
        require(instance !== this) { "newInstance() must return a fresh automaton, not this one." }
        instance.transitions = transitions.isolatedCopy()
        instance.submachines = submachines.copy()
        instance.actions = actions.copy()
        instance.initialStack = initialStack.copy()
        instance.mainSubmachineSlot = mainSubmachineSlot
        instance.stopOnFirstAcceptance = stopOnFirstAcceptance
        instance.limits = limits
        instance.visitors.addAll(visitors)
        return instance
    }

    @Suppress("TooGenericExceptionCaught")
    private fun recognizeOnce(input: List<Symbol>): RecognitionResult {
        setup()

        // The main submachine must exist before recognition starts. Checking it
        // here guarantees that the lifecycle events always pair: no start
        // without an end, and no end without a start.
        val mainSub =
            mainSubmachineSlot ?: throw IllegalArgumentException(
                "The automaton does not declare a main submachine.",
            )

        // The topology follows the same rules as the one the builder DSL
        // produces: the imperative API and the declarative API accept exactly
        // the same automata.
        TopologyValidator.validate(
            topology = Topology.of(transitions, submachines),
            mainSubmachine = mainSub,
            actionNames = actions.map { it.name },
        )

        // The lifecycle events fire on the clone, after setup() materialised its
        // topology, so a visitor sees the automaton it is about to execute.
        val observers = visitors.toList()
        observers.forEach { it.onRecognizeStart(this) }

        worklist.clear()
        paths.clear()

        // The path starts at the initial state of the main submachine; there
        // is no synthetic edge to traverse before the first real move.
        val initialState = submachines.get(mainSub).initialState
        val root =
            PathExecutor(
                state =
                PathState(
                    currentState = initialState,
                    currentSubmachine = mainSub,
                    mainSubmachine = mainSub,
                    stack = initialStack.copy(),
                    actions = actions,
                    transitions = transitions.isolatedCopy(),
                    submachines = submachines,
                    input = input,
                    cursor = 0,
                    enablePriorAction = true,
                    limits = limits,
                ),
                worklist = worklist,
                paths = paths,
                visitors = observers,
            )

        paths[root.id] = RecognitionPath(id = root.id, state = initialState)
        worklist.add(root)

        while (worklist.isNotEmpty() && !anAcceptanceHappened()) {
            // The pass is taken as a snapshot, and the paths that finish are
            // dropped from the worklist in one sweep at the end of it.
            //
            // Removing a path as it finishes would shift the tail of the list
            // on every removal, and a pass over k paths would cost a
            // quadratic number of moves. The sweep costs one pass over the
            // list, whatever the number of paths.
            //
            // The worklist holds every live path while the pass runs — the
            // paths of the pass and the children they fork — so that the
            // bound on the number of live paths counts what it says it
            // counts.
            val pass = worklist.toList()
            for (index in pass.indices.reversed()) {
                val path = pass[index]
                try {
                    path.run()
                } catch (cause: Throwable) {
                    // Every throwable is caught, errors included: a path that
                    // dies must still close its own lifecycle events before
                    // the failure reaches the caller.
                    failPath(path, cause)
                }
            }
            worklist.removeAll { it.finished }
        }

        val result =
            RecognitionResult(
                accepted = paths.values.any { it.outcome == PathOutcome.Accepted },
                paths = paths.values.filter { it.done },
            )
        observers.forEach { it.onRecognizeEnd(result) }
        return result
    }

    /**
     * Reports the failure of [failed] and closes the recognition.
     *
     * A failing path terminates like any other: the path-end and the
     * recognition-failed events are fired before the failure propagates, so
     * every path ends exactly once and the lifecycle events always pair.
     *
     * The failure is reported as a failure, never as a rejection: the path
     * died before it could decide anything, and saying "rejected" would
     * claim a verdict nobody reached.
     *
     * The failure keeps its nature on the way out. An [Error] is rethrown as
     * it is, because the engine did not cause it and hiding it behind a
     * wrapper would only bury it. An [InterruptedException] restores the
     * interrupt flag of the thread before it leaves, so that whoever owns the
     * thread may notice the interruption. A [CancellationException] is
     * rethrown untouched, because a cancelled coroutine must stay cancelled.
     * Every other failure is wrapped, so that the caller may tell which path
     * died and why.
     *
     * @param failed the path that failed
     * @param cause the throwable that killed it
     */
    private fun failPath(failed: PathExecutor, cause: Throwable): Nothing {
        failed.markFailed()
        val observers = visitors.toList()
        observers.forEach { it.onPathEnd(failed.id, PathOutcome.Failed, failed.cursor) }
        observers.forEach { it.onRecognizeFailed(cause) }
        throw failureOf(failed, cause)
    }

    /**
     * The failure the caller receives when [cause] killed [failed].
     *
     * The failure keeps its nature on the way out. An [Error] is passed
     * through as it is, because the engine did not cause it and hiding it
     * behind a wrapper would only bury it. An [InterruptedException] restores
     * the interrupt flag of the thread before it leaves, so that whoever owns
     * the thread may notice the interruption. A [CancellationException] is
     * passed through untouched, because a cancelled coroutine must stay
     * cancelled. Every other failure is wrapped, so that the caller may tell
     * which path died and why.
     *
     * A failure that is already a [RecognitionFailedException] is passed
     * through: the engine raises it when a structural bound trips, and
     * wrapping it again would hide the bound that tripped.
     *
     * @param failed the path that failed
     * @param cause the throwable that killed it
     * @return the throwable the caller receives
     */
    private fun failureOf(failed: PathExecutor, cause: Throwable): Throwable = when (cause) {
        is InterruptedException -> {
            Thread.currentThread().interrupt()
            cause
        }

        is CancellationException -> cause

        is Error -> cause

        is RecognitionFailedException -> cause

        else -> RecognitionFailedException(
            pathId = failed.id,
            reason = cause.message ?: cause::class.simpleName ?: "unknown cause",
            cause = cause,
        )
    }

    /**
     * Tells whether an acceptance has already happened.
     *
     * Only an acceptance stops the recognition: a rejecting path says nothing
     * about the branches still in flight.
     */
    private fun anAcceptanceHappened(): Boolean {
        if (!stopOnFirstAcceptance) return false
        return paths.values.any { it.outcome == PathOutcome.Accepted }
    }

    /** Returns a human-readable representation of this automaton, with labeled sections. */
    override fun toString(): String {
        fun String.indented() = lineSequence().joinToString("\n  ")
        return buildString {
            append("Adaptive automaton {\n")
            append("  main: ").append(mainSubmachineSlot ?: "undefined").append("\n")
            append("  submachines: ").append(submachines.toString().indented()).append("\n")
            append("  transitions: ").append(transitions.toString().indented()).append("\n")
            append("  actions: ").append(actions.toString().indented()).append("\n")
            append("  stack: ").append(initialStack).append("\n")
            append("}")
        }
    }
}
