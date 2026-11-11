// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * Builds a concrete reference to the action named [name], called with [args].
 *
 * The result is a binding: it can be attached to an edge, and it constrains
 * the action slots of a query exactly as it describes them.
 *
 * @param name the name of the action
 * @param args the arguments the action is called with
 * @return the binding to [name]
 */
fun actionRef(name: String, vararg args: Any): ActionRef.Bound = ActionRef.Bound(name = name, args = args.toList())

/**
 * Specification for querying or removing symbol and epsilon transitions using
 * the builder DSL.
 *
 * Every field carries an explicit value: a wildcard is a value in its own
 * right, never a null.
 *
 * @property source the source state filter ([State.Any] = wildcard)
 * @property symbol the symbol filter ([Symbol.Any] = any symbol-consuming
 *   transition, [Symbol.Epsilon] = an epsilon transition)
 * @property target the target state filter ([State.Any] = wildcard)
 * @property prior the prior action filter ([ActionRef.Free] = no constraint)
 * @property post the post action filter ([ActionRef.Free] = no constraint)
 */
data class SymbolSpec(
    /** The source state filter. */
    var source: State = State.Any,
    /** The symbol filter. */
    var symbol: Symbol = Symbol.Any,
    /** The target state filter. */
    var target: State = State.Any,
    /** The prior action filter. */
    var prior: ActionRef = ActionRef.Free,
    /** The post action filter. */
    var post: ActionRef = ActionRef.Free,
)

/**
 * Specification for querying or removing submachine-call transitions using
 * the builder DSL.
 *
 * @property source the source state filter ([State.Any] = wildcard)
 * @property submachine the submachine name filter ([SubmachineRef.Any] = any
 *   submachine call, [SubmachineRef.None] = not applicable)
 * @property target the target state filter ([State.Any] = wildcard)
 * @property prior the prior action filter ([ActionRef.Free] = no constraint)
 * @property post the post action filter ([ActionRef.Free] = no constraint)
 */
data class CallSpec(
    /** The source state filter. */
    var source: State = State.Any,
    /** The submachine name filter; [SubmachineRef.Any] matches any submachine call. */
    var submachine: SubmachineRef = SubmachineRef.Any,
    /** The target state filter. */
    var target: State = State.Any,
    /** The prior action filter. */
    var prior: ActionRef = ActionRef.Free,
    /** The post action filter. */
    var post: ActionRef = ActionRef.Free,
)

/**
 * Casts a value to [T], throwing [ClassCastException] if it is not an instance.
 *
 * Useful for extracting typed parameters from an action's `parameters: List<Any>`.
 *
 * @throws ClassCastException if the value is not an instance of [T]
 * @throws NullPointerException if the value is null and [T] is not nullable
 */
inline fun <reified T> Any?.castOrThrow(): T = this as T

/**
 * Convenience alias for [castOrThrow] when extracting [State] parameters.
 */
inline fun <reified S : State> Any.asState(): S = castOrThrow<S>()

/**
 * Convenience alias for [castOrThrow] when extracting [Symbol] parameters.
 */
inline fun <reified S : Symbol> Any.asSymbol(): S = castOrThrow<S>()

/**
 * Elementary actions: query, insertion and removal of transitions according to
 * a search pattern.
 *
 * The API is organised in two **families**, one per kind of payload a
 * transition may carry:
 *
 * - [query], [remove] and [add] work on symbol-consuming transitions, and on
 *   epsilon transitions, which are named by [Symbol.Epsilon];
 * - [queryCall], [removeCall] and [addCall] work on submachine-call
 *   transitions.
 *
 * Each family offers **one method per operation**, whose filters are all
 * optional, so no call is ever ambiguous. Every filter carries an explicit
 * value: a wildcard is a value in its own right ([State.Any], [Symbol.Any],
 * [ActionRef.Free]), never a null. Epsilon is a value too ([Symbol.Epsilon]),
 * because it denotes the empty word and is not part of the input alphabet.
 *
 * The families never overlap: the symbol family never selects a submachine
 * call, and the call family never selects a symbol-consuming transition.
 * Neither of them selects the engine's internal return transitions.
 *
 * The state, symbol and submachine filters may also name a **domain** — a set
 * built with [State.anyOf], [Symbol.anyOf] or [SubmachineRef.anyOf]. A domain
 * selects every member it names, and an addition over domains produces the
 * Cartesian product of its domains.
 *
 * Builder-style DSL is available through the `query`, `remove`, `queryCall`
 * and `removeCall` lambdas, which hide the wildcards behind named fields.
 *
 * @param transitions the mapping the operations are performed on
 * @param submachines the submachines of the automaton; an addition of a call
 *   checks the submachine it names against them, so that a typo fails where
 *   it is written instead of at recognition time
 */
class ElementaryActions(private val transitions: TransitionMapping, private val submachines: Submachines) {

    /**
     * Publishes a fact about the running action to the observation sink of the
     * underlying mapping.
     *
     * The engine cannot observe what an action computes internally; this is
     * how action code reports it, so that a consumer can take it into account.
     *
     * @param observation the fact to publish
     */
    fun observe(observation: Observation) {
        transitions.observe(observation)
    }

    // -----------------------------------------------------------------------
    // symbol family: query
    // -----------------------------------------------------------------------

    /**
     * Queries symbol-consuming or epsilon transitions.
     *
     * The [symbol] filter selects the kind of transition: [Symbol.Any] selects
     * every symbol-consuming transition, [Symbol.Epsilon] selects the epsilon
     * transitions, and any other symbol selects the transitions consuming it.
     *
     * The [prior] and [post] filters constrain the action slots of the edge;
     * [ActionRef.Free] leaves them unconstrained.
     *
     * @param source the source state filter ([State.Any] = wildcard)
     * @param symbol the symbol filter ([Symbol.Any] = wildcard)
     * @param target the target state filter ([State.Any] = wildcard)
     * @param prior the prior action filter ([ActionRef.Free] = no constraint)
     * @param post the post action filter ([ActionRef.Free] = no constraint)
     * @return list of matching [TransitionEdge] instances
     */
    fun query(
        source: State = State.Any,
        symbol: Symbol = Symbol.Any,
        target: State = State.Any,
        prior: ActionRef = ActionRef.Free,
        post: ActionRef = ActionRef.Free,
    ): List<TransitionEdge> {
        val pattern =
            TransitionPattern(
                source = source,
                symbol = symbol,
                target = target,
                prior = prior,
                post = post,
            )
        return performQuery(pattern)
    }

    /**
     * Queries symbol-consuming or epsilon transitions using a builder DSL.
     * Unset fields carry their wildcard.
     *
     * Example:
     * ```
     * val results = ea.query {
     *     symbol = CharSymbol('a')
     *     target = p1
     *     // source omitted → State.Any
     * }
     *
     * val epsilons = ea.query {
     *     symbol = Symbol.Epsilon
     * }
     * ```
     *
     * @param configure the block that fills the [SymbolSpec]
     * @return list of matching [TransitionEdge] instances
     */
    fun query(configure: SymbolSpec.() -> Unit): List<TransitionEdge> {
        val spec = SymbolSpec().also(configure)
        return query(spec.source, spec.symbol, spec.target, spec.prior, spec.post)
    }

    // -----------------------------------------------------------------------
    // symbol family: remove
    // -----------------------------------------------------------------------

    /**
     * Removes the symbol-consuming or epsilon transitions matching the filters.
     *
     * The filters mean exactly what they mean in [query].
     *
     * @param source the source state filter ([State.Any] = wildcard)
     * @param symbol the symbol filter ([Symbol.Any] = wildcard)
     * @param target the target state filter ([State.Any] = wildcard)
     * @param prior the prior action filter ([ActionRef.Free] = no constraint)
     * @param post the post action filter ([ActionRef.Free] = no constraint)
     */
    fun remove(
        source: State = State.Any,
        symbol: Symbol = Symbol.Any,
        target: State = State.Any,
        prior: ActionRef = ActionRef.Free,
        post: ActionRef = ActionRef.Free,
    ) {
        val pattern =
            TransitionPattern(
                source = source,
                symbol = symbol,
                target = target,
                prior = prior,
                post = post,
            )
        performRemove(pattern)
    }

    /**
     * Removes the symbol-consuming or epsilon transitions matching the filters
     * given in the builder DSL. Unset fields carry their wildcard.
     *
     * Example:
     * ```
     * ea.remove {
     *     symbol = CharSymbol('b')
     *     target = p1
     * }
     *
     * ea.remove {
     *     source = q1
     *     symbol = CharSymbol('a')
     *     target = q1
     *     prior = ActionRef.Named("A")
     * }
     * ```
     *
     * @param configure the block that fills the [SymbolSpec]
     */
    fun remove(configure: SymbolSpec.() -> Unit) {
        val spec = SymbolSpec().also(configure)
        remove(spec.source, spec.symbol, spec.target, spec.prior, spec.post)
    }

    // -----------------------------------------------------------------------
    // symbol family: add
    // -----------------------------------------------------------------------

    /**
     * Adds a transition to the mapping.
     *
     * A [Symbol.Epsilon] symbol adds an epsilon transition, which consumes no
     * input; any other symbol adds a symbol-consuming transition. The
     * wildcard [Symbol.Any] is not a symbol and is rejected.
     *
     * The endpoints and the symbol may be **domains** — sets built with
     * [State.anyOf] and [Symbol.anyOf]. An addition over domains produces
     * the Cartesian product of its domains: one edge per combination, and one
     * [MappingOperation.Add] operation per edge inserted. A domain naming
     * [Symbol.Epsilon] among its members produces an epsilon edge for that
     * member.
     *
     * @param source the source state, or a domain of source states
     * @param symbol the symbol to consume, or [Symbol.Epsilon], or a domain
     * @param target the target state, or a domain of target states
     * @param prior the prior action executed before the edge is traversed
     *   ([ActionRef.None] for none)
     * @param post the post action executed after the edge is traversed
     *   ([ActionRef.None] for none)
     * @throws IllegalArgumentException if a wildcard state or symbol is given
     */
    fun add(
        source: State,
        symbol: Symbol,
        target: State,
        prior: ActionRef = ActionRef.None,
        post: ActionRef = ActionRef.None,
    ) {
        val sources = concreteStates(source, "source")
        val symbols = concreteSymbols(symbol)
        val targets = concreteStates(target, "target")
        for (from in sources) {
            for (consumed in symbols) {
                for (to in targets) {
                    addOne(from, consumed, to, prior, post)
                }
            }
        }
    }

    /**
     * Adds a single transition with concrete endpoints, and reports it as one
     * [MappingOperation.Add] operation.
     */
    private fun addOne(source: State, symbol: Symbol, target: State, prior: ActionRef, post: ActionRef) {
        val transition =
            if (symbol === Symbol.Epsilon) {
                Transition.Epsilon
            } else {
                Transition.SymbolTransition(symbol = symbol)
            }
        val edge =
            TransitionEdge(
                source = source,
                target = target,
                transition = transition,
                prior = prior,
                post = post,
            )
        val pattern =
            TransitionPattern(
                source = source,
                symbol = symbol,
                target = target,
                prior = prior,
                post = post,
            )
        performAdd(pattern, edge)
    }

    // -----------------------------------------------------------------------
    // call family: queryCall
    // -----------------------------------------------------------------------

    /**
     * Queries submachine-call transitions.
     *
     * The [submachine] filter selects the called submachine:
     * [SubmachineRef.Any] matches every call edge, [SubmachineRef.Named]
     * matches the calls to one submachine, and [SubmachineRef.Domain] matches
     * the calls to any submachine of the domain.
     *
     * @param source the source state filter ([State.Any] = wildcard)
     * @param submachine the submachine filter ([SubmachineRef.Any] = any call)
     * @param target the target state filter ([State.Any] = wildcard)
     * @param prior the prior action filter ([ActionRef.Free] = no constraint)
     * @param post the post action filter ([ActionRef.Free] = no constraint)
     * @return list of matching [TransitionEdge] instances
     * @throws IllegalArgumentException if the submachine filter is
     *   [SubmachineRef.None], which names no submachine at all
     */
    fun queryCall(
        source: State = State.Any,
        submachine: SubmachineRef = SubmachineRef.Any,
        target: State = State.Any,
        prior: ActionRef = ActionRef.Free,
        post: ActionRef = ActionRef.Free,
    ): List<TransitionEdge> {
        requireApplicable(submachine, "query")
        val pattern =
            TransitionPattern(
                source = source,
                target = target,
                submachine = submachine,
                prior = prior,
                post = post,
            )
        return performQuery(pattern)
    }

    /**
     * Queries submachine-call transitions using a builder DSL. Unset fields
     * carry their wildcard.
     *
     * Example:
     * ```
     * val results = ea.queryCall {
     *     submachine = SubmachineRef.Named("M")
     *     target = p1
     * }
     * ```
     *
     * @param configure the block that fills the [CallSpec]
     * @return list of matching [TransitionEdge] instances
     */
    fun queryCall(configure: CallSpec.() -> Unit): List<TransitionEdge> {
        val spec = CallSpec().also(configure)
        return queryCall(spec.source, spec.submachine, spec.target, spec.prior, spec.post)
    }

    // -----------------------------------------------------------------------
    // call family: removeCall
    // -----------------------------------------------------------------------

    /**
     * Removes the submachine-call transitions matching the filters.
     *
     * The filters mean exactly what they mean in [queryCall].
     *
     * @param source the source state filter ([State.Any] = wildcard)
     * @param submachine the submachine filter ([SubmachineRef.Any] = any call)
     * @param target the target state filter ([State.Any] = wildcard)
     * @param prior the prior action filter ([ActionRef.Free] = no constraint)
     * @param post the post action filter ([ActionRef.Free] = no constraint)
     * @throws IllegalArgumentException if the submachine filter is
     *   [SubmachineRef.None], which names no submachine at all
     */
    fun removeCall(
        source: State = State.Any,
        submachine: SubmachineRef = SubmachineRef.Any,
        target: State = State.Any,
        prior: ActionRef = ActionRef.Free,
        post: ActionRef = ActionRef.Free,
    ) {
        requireApplicable(submachine, "removal")
        val pattern =
            TransitionPattern(
                source = source,
                target = target,
                submachine = submachine,
                prior = prior,
                post = post,
            )
        performRemove(pattern)
    }

    /**
     * Removes the submachine-call transitions matching the filters given in
     * the builder DSL. Unset fields carry their wildcard.
     *
     * @param configure the block that fills the [CallSpec]
     */
    fun removeCall(configure: CallSpec.() -> Unit) {
        val spec = CallSpec().also(configure)
        removeCall(spec.source, spec.submachine, spec.target, spec.prior, spec.post)
    }

    // -----------------------------------------------------------------------
    // call family: addCall
    // -----------------------------------------------------------------------

    /**
     * Adds a submachine-call transition to the mapping.
     *
     * The [submachine] filter names the submachines to call: a
     * [SubmachineRef.Named] adds one call edge, and a [SubmachineRef.Domain]
     * adds one edge per submachine it names. The endpoints may also be
     * **domains** — sets built with [State.anyOf]. An addition over domains
     * produces the Cartesian product of its domains: one edge per combination,
     * and one [MappingOperation.Add] operation per edge inserted.
     *
     * @param source the source state, or a domain of source states
     * @param submachine the submachine to call, or a domain of submachines
     * @param target the return state, owned by the calling submachine
     * @param prior the prior action executed before the call ([ActionRef.None]
     *   for none)
     * @param post the post action executed after the jump into the submachine
     *   ([ActionRef.None] for none)
     * @throws IllegalArgumentException if a wildcard state is given, or if the
     *   submachine filter is [SubmachineRef.Any] or [SubmachineRef.None],
     *   which name no concrete submachine
     * @throws IllegalArgumentException if a named submachine is not declared
     *   in the automaton
     */
    fun addCall(
        source: State,
        submachine: SubmachineRef,
        target: State,
        prior: ActionRef = ActionRef.None,
        post: ActionRef = ActionRef.None,
    ) {
        val names = concreteSubmachines(submachine)
        requireDeclared(names)
        val sources = concreteStates(source, "source")
        val targets = concreteStates(target, "target")
        for (from in sources) {
            for (name in names) {
                for (to in targets) {
                    addCallOne(from, name, to, prior, post)
                }
            }
        }
    }

    /**
     * Checks that every named submachine is declared in the automaton.
     *
     * A call to a submachine that does not exist fails at recognition time,
     * with a message about a missing submachine — far from the addition that
     * caused it. The check happens where the addition is written instead.
     *
     * An automaton that declares no submachine at all has nothing to check the
     * names against, and the names are let through.
     *
     * @param names the names of the submachines the call names
     */
    private fun requireDeclared(names: List<String>) {
        if (submachines.size == 0) return
        val declared = submachines.map { it.name }
        names.forEach { name ->
            require(name in declared) {
                "Call to undeclared submachine '$name'. " +
                    "Declared submachines: ${declared.joinToString(", ")}."
            }
        }
    }

    /**
     * Adds a single submachine-call transition with concrete endpoints, and
     * reports it as one [MappingOperation.Add] operation.
     */
    private fun addCallOne(source: State, submachine: String, target: State, prior: ActionRef, post: ActionRef) {
        val edge =
            TransitionEdge(
                source = source,
                target = target,
                transition = Transition.Call(submachine = submachine),
                prior = prior,
                post = post,
            )
        val pattern =
            TransitionPattern(
                source = source,
                target = target,
                submachine = SubmachineRef.Named(submachine),
                prior = prior,
                post = post,
            )
        performAdd(pattern, edge)
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    /**
     * Matches [pattern] against the mapping and reports the outcome as a
     * [MappingOperation.Query].
     *
     * A query changes nothing, so it produces no topology event: this is the
     * only way a consumer can observe a read.
     */
    private fun performQuery(pattern: TransitionPattern): List<TransitionEdge> {
        val before = transitions.size
        val matched = transitions.transitions.filter(pattern::matches)
        transitions.onOperation(MappingOperation.Query(pattern, matched, before))
        return matched
    }

    /**
     * Removes the edges matching [pattern] and reports the outcome as a
     * [MappingOperation.Remove].
     *
     * The operation is reported after the edges have left the mapping, so the
     * resulting size is observed rather than predicted. A removal that matched
     * nothing is still reported: it scanned the mapping even though it changed
     * nothing.
     */
    private fun performRemove(pattern: TransitionPattern) {
        val before = transitions.size
        val matched = transitions.transitions.filter(pattern::matches)
        matched.forEach { transitions.removeFromIdentifier(it.id) }
        val after = transitions.size
        transitions.onOperation(MappingOperation.Remove(pattern, matched, before, after))
    }

    /**
     * Adds [edge] to the mapping and reports the outcome as a
     * [MappingOperation.Add].
     *
     * The operation is reported after the edge has entered the mapping, so the
     * resulting size is observed rather than predicted.
     */
    private fun performAdd(pattern: TransitionPattern, edge: TransitionEdge) {
        val before = transitions.size
        transitions.add(edge)
        val after = transitions.size
        transitions.onOperation(MappingOperation.Add(pattern, edge, before, after))
    }

    /**
     * Checks that the submachine filter applies to the call family.
     *
     * [SubmachineRef.None] says "the filter does not apply", and a call
     * operation always has a submachine filter: accepting it would build a
     * pattern belonging to another family.
     *
     * @param filter the submachine filter given to the operation
     * @param operation the name of the operation, used in the error message
     */
    private fun requireApplicable(filter: SubmachineRef, operation: String) {
        require(filter !== SubmachineRef.None) {
            "The submachine filter of a call $operation cannot be 'None': it names no submachine."
        }
    }

    /**
     * Expands a state filter into the concrete states an addition may use.
     *
     * The wildcard [State.Any] is not a state and is rejected; a domain
     * expands into the states it names.
     *
     * @param filter the filter given to the addition
     * @param role the role of the filter, used in the error message
     */
    private fun concreteStates(filter: State, role: String): List<State> {
        require(filter !== State.Any) { "The $role of an added transition must be a concrete state." }
        return if (filter is StateDomain) filter.members.toList() else listOf(filter)
    }

    /**
     * Expands a symbol filter into the concrete symbols an addition may use.
     *
     * The wildcard [Symbol.Any] is not a symbol and is rejected; a domain
     * expands into the symbols it names, the empty word included.
     *
     * @param filter the filter given to the addition
     */
    private fun concreteSymbols(filter: Symbol): List<Symbol> {
        require(filter !== Symbol.Any) { "The symbol of an added transition must be a concrete symbol." }
        return if (filter is SymbolDomain) filter.members.toList() else listOf(filter)
    }

    /**
     * Expands a submachine filter into the concrete submachines an addition
     * may call.
     *
     * The wildcard [SubmachineRef.Any] names no concrete submachine and is
     * rejected, and so is [SubmachineRef.None]; a named filter expands into
     * the one submachine it names, and a domain into the ones it names.
     *
     * @param filter the filter given to the addition
     */
    private fun concreteSubmachines(filter: SubmachineRef): List<String> = when (filter) {
        is SubmachineRef.Named -> listOf(filter.name)

        is SubmachineRef.Domain -> filter.names.toList()

        SubmachineRef.Any, SubmachineRef.None -> throw IllegalArgumentException(
            "The submachine of an added call must name a submachine, but '$filter' is a filter.",
        )
    }
}

/** Type-safe check: is this edge a transition of type [T]? */
inline fun <reified T : Transition> TransitionEdge.isType(): Boolean = transition is T

/** Type-safe filter: transitions of type [T]. */
inline fun <reified T : Transition> TransitionMapping.withType(): List<TransitionEdge> =
    transitions.filter { it.transition is T }.toList()
