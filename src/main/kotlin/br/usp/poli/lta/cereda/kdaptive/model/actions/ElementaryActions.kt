// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model.actions

import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.MappingOperation
import br.usp.poli.lta.cereda.kdaptive.model.Observation
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.StateDomain
import br.usp.poli.lta.cereda.kdaptive.model.SubmachineRef
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.SymbolDomain
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.TransitionPattern
import br.usp.poli.lta.cereda.kdaptive.model.predicates.TransitionPredicate
import br.usp.poli.lta.cereda.kdaptive.model.predicates.allOf
import br.usp.poli.lta.cereda.kdaptive.model.predicates.isEpsilon
import br.usp.poli.lta.cereda.kdaptive.model.predicates.isSubmachineCall
import br.usp.poli.lta.cereda.kdaptive.model.predicates.isSymbolConsumption
import br.usp.poli.lta.cereda.kdaptive.model.predicates.sourceState
import br.usp.poli.lta.cereda.kdaptive.model.predicates.submachineCall
import br.usp.poli.lta.cereda.kdaptive.model.predicates.submachineCallIn
import br.usp.poli.lta.cereda.kdaptive.model.predicates.symbol
import br.usp.poli.lta.cereda.kdaptive.model.predicates.targetState
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping

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
inline fun <reified S : State> Any?.asState(): S = castOrThrow<S>()

/**
 * Convenience alias for [castOrThrow] when extracting [Symbol] parameters.
 */
inline fun <reified S : Symbol> Any?.asSymbol(): S = castOrThrow<S>()

/**
 * Throws [IllegalArgumentException] if [value] is null.
 *
 * Useful as a precondition check.
 */
fun <T : Any> requireNonNull(value: T?): T = value ?: throw IllegalArgumentException("Value must not be null")

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
 */
class ElementaryActions(private val transitions: TransitionMapping) {

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
        val predicates =
            listOf(
                sourcePredicate(source),
                symbolPredicate(symbol),
                targetPredicate(target),
                actionPredicate(prior, isPost = false),
                actionPredicate(post, isPost = true),
            )
        return performQuery(pattern, allOf(predicates))
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
        val predicates =
            listOf(
                sourcePredicate(source),
                symbolPredicate(symbol),
                targetPredicate(target),
                actionPredicate(prior, isPost = false),
                actionPredicate(post, isPost = true),
            )
        performRemove(pattern, allOf(predicates))
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
        val predicates =
            listOf(
                sourcePredicate(source),
                submachinePredicate(submachine),
                targetPredicate(target),
                actionPredicate(prior, isPost = false),
                actionPredicate(post, isPost = true),
            )
        return performQuery(pattern, allOf(predicates))
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
        val predicates =
            listOf(
                sourcePredicate(source),
                submachinePredicate(submachine),
                targetPredicate(target),
                actionPredicate(prior, isPost = false),
                actionPredicate(post, isPost = true),
            )
        performRemove(pattern, allOf(predicates))
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
     */
    fun addCall(
        source: State,
        submachine: SubmachineRef,
        target: State,
        prior: ActionRef = ActionRef.None,
        post: ActionRef = ActionRef.None,
    ) {
        val names = concreteSubmachines(submachine)
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
     * Matches [predicate] against the mapping and reports the outcome as a
     * [MappingOperation.Query].
     *
     * A query changes nothing, so it produces no topology event: this is the
     * only way a consumer can observe a read.
     */
    private fun performQuery(pattern: TransitionPattern, predicate: TransitionPredicate): List<TransitionEdge> {
        val before = transitions.size()
        val matched = transitions.getTransitions().filter(predicate)
        transitions.onOperation(MappingOperation.Query(pattern, matched, before))
        return matched
    }

    /**
     * Removes the edges matching [predicate] and reports the outcome as a
     * [MappingOperation.Remove].
     *
     * The operation is reported after the edges have left the mapping, so the
     * resulting size is observed rather than predicted. A removal that matched
     * nothing is still reported: it scanned the mapping even though it changed
     * nothing.
     */
    private fun performRemove(pattern: TransitionPattern, predicate: TransitionPredicate) {
        val before = transitions.size()
        val matched = transitions.getTransitions().filter(predicate)
        matched.forEach { transitions.removeFromIdentifier(it.id) }
        val after = transitions.size()
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
        val before = transitions.size()
        transitions.add(edge)
        val after = transitions.size()
        transitions.onOperation(MappingOperation.Add(pattern, edge, before, after))
    }

    /**
     * The source-state filter: [State.Any] matches any source state, a domain
     * matches any state it names, and a concrete state matches only itself.
     */
    private fun sourcePredicate(source: State): TransitionPredicate = when {
        source === State.Any -> { _: TransitionEdge -> true }
        source is StateDomain -> { edge: TransitionEdge -> edge.source in source.members }
        else -> sourceState(source)
    }

    /**
     * The target-state filter: [State.Any] matches any target state, a domain
     * matches any state it names, and a concrete state matches only itself.
     */
    private fun targetPredicate(target: State): TransitionPredicate = when {
        target === State.Any -> { _: TransitionEdge -> true }
        target is StateDomain -> { edge: TransitionEdge -> edge.target in target.members }
        else -> targetState(target)
    }

    /**
     * The symbol filter: [Symbol.Epsilon] selects the epsilon transitions,
     * [Symbol.Any] selects every symbol-consuming transition, a domain
     * selects the transitions consuming any of its members — epsilon included
     * — and any other symbol selects the transitions consuming it.
     */
    private fun symbolPredicate(filter: Symbol): TransitionPredicate = when {
        filter === Symbol.Epsilon -> isEpsilon

        filter === Symbol.Any -> isSymbolConsumption

        filter is SymbolDomain -> { edge: TransitionEdge ->
            filter.members.any { member ->
                if (member === Symbol.Epsilon) {
                    edge.transition is Transition.Epsilon
                } else {
                    edge.transition is Transition.SymbolTransition && edge.transition.symbol == member
                }
            }
        }

        else -> symbol(filter)
    }

    /**
     * The submachine filter: the wildcard matches any submachine-call edge, a
     * named filter matches the calls to that submachine only, and a domain
     * matches the calls to any of the submachines it names.
     */
    private fun submachinePredicate(filter: SubmachineRef): TransitionPredicate = when (filter) {
        SubmachineRef.Any -> isSubmachineCall
        is SubmachineRef.Named -> submachineCall(filter.name)
        is SubmachineRef.Domain -> submachineCallIn(filter.names)
        SubmachineRef.None -> { _: TransitionEdge -> false }
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

    /**
     * The action-slot filter, applied to the prior or the post action of the
     * edge.
     *
     * @param filter the constraint on the slot
     * @param isPost whether the constraint applies to the post action
     */
    private fun actionPredicate(filter: ActionRef, isPost: Boolean): TransitionPredicate {
        val slot = { edge: TransitionEdge -> if (isPost) edge.post else edge.prior }
        return when (filter) {
            is ActionRef.Free -> { _: TransitionEdge -> true }
            is ActionRef.Any -> { edge: TransitionEdge -> slot(edge) is ActionRef.Bound }
            is ActionRef.None -> { edge: TransitionEdge -> slot(edge) === ActionRef.None }
            is ActionRef.Named -> { edge: TransitionEdge -> (slot(edge) as? ActionRef.Bound)?.name == filter.name }
            is ActionRef.Bound -> { edge: TransitionEdge -> matchesBinding(slot(edge), filter) }
        }
    }

    /**
     * Checks the actual binding of an action slot against the expected one.
     *
     * The expected binding fixes the arity; an expected argument equal to
     * [ActionRef.Free] leaves that position unconstrained.
     */
    private fun matchesBinding(actual: ActionRef, expected: ActionRef.Bound): Boolean {
        if (actual !is ActionRef.Bound) return false
        if (actual.name != expected.name) return false
        if (actual.args.size != expected.args.size) return false
        return expected.args.indices.all { index ->
            val wanted = expected.args[index]
            wanted === ActionRef.Free || actual.args[index] == wanted
        }
    }
}

/** Type-safe check: is this edge a transition of type [T]? */
inline fun <reified T : Transition> TransitionEdge.isType(): Boolean = transition is T

/** Type-safe filter: transitions of type [T]. */
inline fun <reified T : Transition> TransitionMapping.withType(): List<TransitionEdge> =
    getTransitions().filter { it.transition is T }.toList()
