// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.dsl

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.Arity
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.Observation
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Submachines
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Topology
import br.usp.poli.lta.cereda.kdaptive.model.TopologyValidator
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge

/**
 * The handler signature for DSL-registered adaptive actions.
 *
 * The receiver ([DslActionContext]) carries the elementary actions over the
 * mapping currently being executed plus a fresh-state factory; the parameters
 * are [Transition] (the edge that triggered the action) and the argument list
 * bound in the [ActionRef] that fired.
 */
public typealias DslActionHandler = DslActionContext.(transition: Transition, parameters: List<Any>) -> Unit

/**
 * Runtime context handed to DSL-registered adaptive actions.
 *
 * Wraps the [ElementaryActions] facade over the running mapping, a read-only
 * view of the states of the automaton and a per-run fresh-state factory, so
 * handlers do not need to manage mutable state-name counters themselves.
 *
 * @param ea the elementary actions over the currently executed mapping
 * @param states the states of the automaton, keyed by name
 * @param newStateSupplier the factory allocating fresh states
 */
public class DslActionContext internal constructor(
    /** The elementary actions over the mapping currently being executed. */
    public val ea: ElementaryActions,
    private val states: Map<String, State>,
    private val newStateSupplier: (String) -> State,
) {
    /**
     * Looks up a state of the automaton by [name].
     *
     * This is a read-only lookup: a name that does not denote a state of the
     * automaton is an error, not a request to create one.
     *
     * @throws IllegalArgumentException if the automaton has no state named [name]
     */
    public fun state(name: String): State = requireNotNull(states[name]) {
        "No state named '$name' in this automaton. Known states: ${states.keys.joinToString(", ")}."
    }

    /**
     * Returns a fresh [State] with [prefix] plus a unique numeric suffix,
     * restarting at zero for each recognition run.
     */
    public fun freshState(prefix: String = "g"): State = newStateSupplier(prefix)

    /**
     * Builds an [ActionRef.Bound] with the given runtime [args], which are
     * passed through unchanged (they are values known only during execution).
     */
    public fun ref(name: String, vararg args: Any): ActionRef.Bound = ActionRef.Bound(name = name, args = args.toList())

    /**
     * Publishes a fact about this action to the observation sink of the
     * mapping currently being executed.
     *
     * The engine reports what it does on its own channel; this is how an
     * action reports what it computed and what the engine cannot observe on
     * its own. The library never interprets the fact: consumers decide what
     * it means.
     *
     * @param observation the fact to publish
     */
    public fun observe(observation: Observation) {
        ea.observe(observation)
    }
}

/**
 * Specification produced by the builder DSL, consumed by [specToAutomaton].
 *
 * @property states the states of the automaton, keyed by name
 * @property submachines the submachines, keyed by name
 * @property transitions the transitions of the main submachine
 * @property actions the DSL-registered adaptive actions, keyed by name
 * @property main the name of the main submachine
 */
public data class AutomatonSpec(
    val states: Map<String, State> = emptyMap(),
    val submachines: Map<String, Submachine> = emptyMap(),
    val transitions: List<TransitionEdge> = emptyList(),
    val actions: Map<String, DslActionHandler> = emptyMap(),
    val main: String,
)

/**
 * Builds an [AdaptiveAutomaton] from an [AutomatonSpec].
 *
 * The built automaton owns a fresh-state counter used by
 * [AutomatonBuilder.action] handlers through [DslActionContext.freshState];
 * the counter starts at zero for each recognition run, since `recognize`
 * executes on a clone built by `newInstance`. Generated names never collide
 * with the states already known to the specification.
 *
 * @param spec the specification to build from
 * @return a fresh automaton whose [AdaptiveAutomaton.setup] materialises [spec]
 * @throws IllegalArgumentException if the specification declares no main
 * submachine, or names one that does not exist
 */
public fun specToAutomaton(spec: AutomatonSpec): AdaptiveAutomaton {
    val mainName = spec.main
    require(mainName in spec.submachines) {
        "Main submachine '$mainName' is not declared in the specification. " +
            "Declared submachines: ${spec.submachines.keys.joinToString(", ")}."
    }
    val declaredSubmachines = Submachines().apply { spec.submachines.values.forEach { add(it) } }
    return object : AdaptiveAutomaton() {
        private var freshStateCounter = 0
        private val reservedStateNames = spec.states.keys.toSet()

        override fun newInstance(): AdaptiveAutomaton = specToAutomaton(spec)

        private fun nextFreshState(prefix: String): State {
            var index = freshStateCounter
            var candidate = "$prefix$index"
            while (candidate in reservedStateNames) {
                index = index + 1
                candidate = "$prefix$index"
            }
            freshStateCounter = index + 1
            val stateName = candidate
            return object : State() {
                override fun toString(): String = stateName
            }
        }

        override fun setup() {
            mainSubmachine = spec.main
            spec.submachines.values.forEach { submachines.add(it) }
            spec.transitions.forEach { transitions.add(it) }
            spec.actions.forEach { (name, handler) ->
                actions.add(
                    Action(name = name) { transitions, transition, parameters ->
                        handler(
                            DslActionContext(
                                ElementaryActions(transitions, declaredSubmachines),
                                spec.states,
                            ) { prefix ->
                                nextFreshState(prefix)
                            },
                            transition,
                            parameters,
                        )
                    },
                )
            }
        }
    }
}

/**
 * Builder for constructing an [AutomatonSpec] using a fluent DSL.
 *
 * State and symbol parameters accept either the typed objects ([State], [Symbol])
 * or convenient raw values ([String] for state names, [Char] for symbols that map
 * to [CharSymbol]). Action references accept [ActionRef] or a plain [String] name.
 */
public class AutomatonBuilder {
    private val states = mutableMapOf<String, State>()
    private val transitions = mutableListOf<TransitionEdge>()
    private val submachineBuilders = mutableMapOf<String, SubmachineBuilder>()
    private val actions = mutableMapOf<String, DslActionHandler>()
    private val actionArities = mutableMapOf<String, Arity>()
    private val actionReferences = mutableMapOf<String, MutableList<Int>>()
    private val argumentStates = mutableListOf<Pair<String, State>>()
    private val declaredStates = mutableSetOf<State>()
    private var mainSlot: String? = null

    /**
     * The name of the main submachine.
     *
     * Reading it before [main] was called fails with a clear message: a null
     * would only spread the mistake.
     *
     * @throws IllegalStateException if no main submachine was declared
     */
    public var main: String
        get() = mainSlot ?: error("Main submachine cannot be null. Call main(\"M\") to declare it.")
        set(value) {
            mainSlot = value
        }

    /** Creates or looks up a plain [State] by name. */
    public fun state(name: String): State = states.getOrPut(name) {
        object : State() {
            override fun toString(): String = name
        }
    }.also { declaredStates.add(it) }

    private fun resolveState(value: Any): State = when (value) {
        is State -> {
            value
        }

        is String -> {
            states.getOrPut(value) {
                object : State() {
                    override fun toString(): String = value
                }
            }
        }

        else -> {
            throw IllegalArgumentException("Cannot resolve '$value' to State")
        }
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

    /** Defines a submachine built by the [SubmachineBuilder] block. */
    public fun submachine(name: String, configure: SubmachineBuilder.() -> Unit) {
        submachineBuilders[name] = SubmachineBuilder(states).also(configure)
    }

    /** Sets the main submachine name. */
    public fun main(name: String) {
        this.main = name
    }
    // -----------------------------------------------------------------------
    // Adaptive actions
    // -----------------------------------------------------------------------

    /**
     * Registers an adaptive action under [name], making previous/post action
     * references with that name resolvable by the built automaton.
     *
     * The [handler] receives a [DslActionContext] as receiver: `ea` exposes the
     * elementary actions over the mapping currently being executed and
     * `freshState(prefix)` allocates a unique state, restarting at zero for
     * each recognition run.
     *
     * When [arity] is [Arity.Exactly], every reference to this action must
     * bind exactly that many arguments; the builder rejects mismatches before
     * the automaton is built. [Arity.Any] leaves the number of arguments free
     * for actions that are legitimately called with different argument counts.
     *
     * @throws IllegalArgumentException if an action with the same name is
     * already registered
     */
    public fun action(name: String, arity: Arity = Arity.Any, handler: DslActionHandler) {
        require(!actions.containsKey(name)) { "Action '$name' is already registered." }
        actions[name] = handler
        if (arity is Arity.Exactly) {
            actionArities[name] = arity
        }
    }

    /**
     * Builds an [ActionRef.Bound] with build-time argument resolution:
     * [String] arguments become the corresponding named [State], [Char]
     * arguments become a [CharSymbol] and any other value is passed through
     * unchanged.
     *
     * A string argument names a state of the automaton: the builder checks at
     * the end of the block that every named state really belongs to the
     * automaton, so typos fail loudly instead of creating a phantom state.
     * Use plain `actionRef` or [ActionRef] directly to pass string payloads
     * or when the arguments are already typed objects.
     */
    public fun ref(name: String, vararg args: Any): ActionRef.Bound {
        val resolved = args.map { argument ->
            when (argument) {
                is State -> argument

                is String -> {
                    val state = resolveState(argument)
                    argumentStates.add(name to state)
                    state
                }

                is Char -> CharSymbol(argument)

                is Symbol -> argument

                else -> argument
            }
        }
        return ActionRef.Bound(name = name, args = resolved)
    }

    // -----------------------------------------------------------------------
    // Symbol-consuming transitions
    // -----------------------------------------------------------------------

    /** Adds a symbol-consuming transition, [prior]/[post] being optional action references. */
    public fun consume(from: Any, symbol: Any, to: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
        val s = resolveState(from)
        val t = resolveState(to)
        val sym = resolveSymbol(symbol)
        val p = resolveActionRef(prior)
        val po = resolveActionRef(post)
        (p as? ActionRef.Bound)?.let { recordReference(it) }
        (po as? ActionRef.Bound)?.let { recordReference(it) }
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

    // -----------------------------------------------------------------------
    // Epsilon transitions
    // -----------------------------------------------------------------------

    /**
     * Adds an epsilon transition, [prior]/[post] being optional action references.
     *
     * The prior action fires before the move, the post action after it.
     */
    public fun epsilon(from: Any, to: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
        val s = resolveState(from)
        val t = resolveState(to)
        val p = resolveActionRef(prior)
        val po = resolveActionRef(post)
        (p as? ActionRef.Bound)?.let { recordReference(it) }
        (po as? ActionRef.Bound)?.let { recordReference(it) }
        transitions.add(
            TransitionEdge(
                source = s,
                target = t,
                transition = Transition.Epsilon,
                prior = p,
                post = po,
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Submachine calls
    // -----------------------------------------------------------------------

    /**
     * Adds a transition calling submachine [submachineName], [prior]/[post]
     * being optional action references.
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
        val p = resolveActionRef(prior)
        val po = resolveActionRef(post)
        (p as? ActionRef.Bound)?.let { recordReference(it) }
        (po as? ActionRef.Bound)?.let { recordReference(it) }
        transitions.add(
            TransitionEdge(
                source = s,
                target = t,
                transition = Transition.Call(submachine = submachineName),
                prior = p,
                post = po,
            ),
        )
    }

    // -----------------------------------------------------------------------
    // Block grouping: transitions from the same source state
    // -----------------------------------------------------------------------

    /** Adds several transitions from the same [source] state using the [configure] block. */
    public fun from(source: Any, configure: TransitionGroup.() -> Unit) {
        TransitionGroup(this, source).configure()
    }

    /** Builder for adding multiple transitions from a single source state. */
    public class TransitionGroup(private val ab: AutomatonBuilder, private val source: Any) {
        /** Adds a symbol-consuming transition from the group's source state. */
        public fun symbol(sym: Any, target: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
            ab.consume(source, sym, target, prior, post)
        }

        /** Adds an epsilon transition from the group's source state. */
        public fun epsilon(target: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
            ab.epsilon(source, target, prior, post)
        }

        /** Adds a submachine-call transition from the group's source state. */
        public fun call(submachine: String, target: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
            ab.call(source, submachine, target, prior, post)
        }
    }

    // -----------------------------------------------------------------------
    // Infix operators for readable transition specifications
    // -----------------------------------------------------------------------

    /** Holds source state awaiting a symbol. */
    public class TransitionStepSource(private val ab: AutomatonBuilder, private val source: Any) {
        /** Infix: `stepSource on 'a'` creates a step awaiting a target. */
        public infix fun on(symbol: Any): TransitionStep = TransitionStep(ab, source, symbol)
    }

    /** Holds source+symbol awaiting a target state. */
    public class TransitionStep(private val ab: AutomatonBuilder, private val source: Any, private val symbol: Any) {
        /** Completes the transition: source --symbol--> target, with optional prior/post actions. */
        public fun to(target: Any, prior: Any = ActionRef.None, post: Any = ActionRef.None) {
            ab.consume(source, symbol, target, prior, post)
        }

        /**
         * Completes the transition infix-style: `this on "q0" on 'a' to "q1"` inside the
         * builder block. Without this overload the chain's trailing `to` silently resolves
         * to the stdlib `Pair` factory (which builds nothing), so the singly-parametrized
         * member overload exists to make the bare chain work. Prior/post actions still go
         * through the three-parameter dotted [to].
         */
        public infix fun to(target: Any): Unit = to(target, ActionRef.None, ActionRef.None)
    }

    /** Infix: `builder on "q0"` creates a step source awaiting a symbol. */
    public infix fun AutomatonBuilder.on(source: Any): TransitionStepSource = TransitionStepSource(this, source)

    // -----------------------------------------------------------------------

    /** Builds the immutable [AutomatonSpec] for this builder. */
    public fun toSpec(): AutomatonSpec {
        val submachines = submachineBuilders.mapValues { (name, builder) -> builder.build(name) }
        validateArities()
        validateArgumentStates(submachines.values)
        val mainName = mainSlot
        require(mainName != null) {
            "The automaton does not declare a main submachine."
        }
        // The topology rules are shared with the imperative API: a topology
        // the DSL accepts is executable, and the other way round.
        TopologyValidator.validate(
            topology = Topology(transitions.toSet(), submachines.values.toSet()),
            mainSubmachine = mainName,
            actionNames = actions.keys,
        )
        return AutomatonSpec(
            states = states.toMap(),
            submachines = submachines,
            transitions = transitions,
            actions = actions.toMap(),
            main = mainName,
        )
    }

    /** Records that an edge refers to [reference], for the arity contract. */
    private fun recordReference(reference: ActionRef.Bound) {
        actionReferences.getOrPut(reference.name) { mutableListOf() }.add(reference.args.size)
    }

    /** Checks every reference against the arity declared by its action. */
    private fun validateArities() {
        actionArities.forEach { (name, declared) ->
            val expected = (declared as Arity.Exactly).count
            actionReferences[name].orEmpty().forEach { actual ->
                require(actual == expected) {
                    "Action '$name' expects $expected argument(s), but a reference binds $actual."
                }
            }
        }
    }

    /**
     * Checks that every state named as an action argument really belongs to
     * the automaton: edge endpoints, submachine members or explicitly declared
     * states. Typos would otherwise create a phantom state and silently
     * miswire the action.
     */
    private fun validateArgumentStates(submachines: Collection<Submachine>) {
        if (argumentStates.isEmpty()) {
            return
        }
        val known = mutableSetOf<State>()
        known.addAll(declaredStates)
        transitions.forEach { edge ->
            known.add(edge.source)
            known.add(edge.target)
        }
        submachines.forEach { submachine -> known.addAll(submachine.states) }
        argumentStates.forEach { (actionName, state) ->
            require(state in known) {
                "Action '$actionName' refers to state '$state', which is not part of the automaton. " +
                    "Known states: ${known.joinToString(", ") { it.toString() }}. " +
                    "Use actionRef(...) to pass a string payload."
            }
        }
    }
}

/**
 * Builder for a single [Submachine] within the automaton DSL.
 *
 * States referenced by name are shared with the parent builder, so submachines
 * may call into states defined elsewhere.
 *
 * @param sharedStates the state map shared with the parent builder
 */
public class SubmachineBuilder(private val sharedStates: MutableMap<String, State>) {
    private val initials = mutableListOf<State>()
    private val accepting = mutableListOf<State>()
    private val states = mutableListOf<State>()

    /** Sets the initial state of the submachine. Accepts a [State] or a state name. */
    public fun initial(value: Any) {
        val s = resolve(value)
        initials.add(s)
        states.add(s)
    }

    /**
     * Adds one or more accepting states. Accepts a [State] or a state name.
     *
     * @throws IllegalArgumentException if no value is given
     */
    public fun accepting(vararg values: Any) {
        require(values.isNotEmpty()) { "A submachine must define at least one accepting state." }
        values.forEach { value ->
            val s = resolve(value)
            accepting.add(s)
            states.add(s)
        }
    }

    /** Adds a plain (non-initial, non-accepting) state. Accepts a [State] or a state name. */
    public fun state(value: Any) {
        states.add(resolve(value))
    }

    /** Resolves a [State] or a state name into the shared state table. */
    private fun resolve(value: Any): State = when (value) {
        is State -> {
            value
        }

        is String -> {
            sharedStates.getOrPut(value) {
                object : State() {
                    override fun toString(): String = value
                }
            }
        }

        else -> {
            throw IllegalArgumentException("Cannot resolve '$value' to State")
        }
    }

    /**
     * Builds the [Submachine]. Requires exactly one initial state to have been set.
     *
     * @throws IllegalArgumentException if no initial state, or more than one, was configured.
     */
    public fun build(name: String): Submachine {
        require(initials.size == 1) {
            if (initials.isEmpty()) {
                "Submachine '$name' must define an initial state."
            } else {
                "Submachine '$name' defines more than one initial state."
            }
        }
        val init = initials.single()
        val allStates = states.toMutableSet()
        allStates.add(init)
        allStates.addAll(accepting)
        return Submachine(
            name = name,
            states = allStates,
            initialState = init,
            acceptingStates = accepting.toSet(),
        )
    }
}

/**
 * Creates an [AutomatonSpec] by configuring a fresh [AutomatonBuilder].
 *
 * Example:
 * ```
 * val spec = automaton {
 *     submachine("M") {
 *         initial("q0")
 *         accepting("q1")
 *     }
 *     consume("q0", 'a', "q1")
 *     main("M")
 * }
 * ```
 */
public inline fun automaton(configure: AutomatonBuilder.() -> Unit): AutomatonSpec {
    val b = AutomatonBuilder()
    b.configure()
    return b.toSpec()
}

/** Creates a [CharSymbol] for the given character. */
public fun sym(c: Char): CharSymbol = CharSymbol(c)
