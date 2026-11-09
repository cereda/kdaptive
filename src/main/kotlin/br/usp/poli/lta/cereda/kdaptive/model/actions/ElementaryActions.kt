// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model.actions

import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.predicates.TransitionPredicate
import br.usp.poli.lta.cereda.kdaptive.model.predicates.allOf
import br.usp.poli.lta.cereda.kdaptive.model.predicates.anyOf
import br.usp.poli.lta.cereda.kdaptive.model.predicates.hasPostAction
import br.usp.poli.lta.cereda.kdaptive.model.predicates.hasPriorAction
import br.usp.poli.lta.cereda.kdaptive.model.predicates.isEpsilon
import br.usp.poli.lta.cereda.kdaptive.model.predicates.isSubmachineCall
import br.usp.poli.lta.cereda.kdaptive.model.predicates.sourceState
import br.usp.poli.lta.cereda.kdaptive.model.predicates.submachineCall
import br.usp.poli.lta.cereda.kdaptive.model.predicates.symbol
import br.usp.poli.lta.cereda.kdaptive.model.predicates.targetState
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * A reference to an action with optional arguments.
 */
fun actionRef(name: String, vararg args: Any): ActionRef = ActionRef(name, args.toList())

/**
 * Specification for querying symbol transitions using the builder DSL.
 *
 * All fields are nullable: `null` means wildcard (matches anything).
 *
 * @property source the source state filter (null = wildcard)
 * @property symbol the symbol filter (null = wildcard)
 * @property target the target state filter (null = wildcard)
 */
data class SymbolQuerySpec(var source: State? = null, var symbol: Symbol? = null, var target: State? = null)

/**
 * Specification for removing symbol transitions using the builder DSL.
 *
 * All fields are nullable: `null` means wildcard (matches anything).
 *
 * @property source the source state filter (null = wildcard)
 * @property symbol the symbol filter (null = wildcard)
 * @property target the target state filter (null = wildcard)
 * @property prior the prior action filter (null = wildcard)
 */
data class SymbolRemoveSpec(
    var source: State? = null,
    var symbol: Symbol? = null,
    var target: State? = null,
    var prior: ActionRef? = null,
)

/**
 * Casts a value to [T], throwing [ClassCastException] if it is not an instance.
 *
 * Useful for extracting typed parameters from an action's `parameters: List<Any>`.
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
 * Useful as a contract-enabled precondition check: after a successful call,
 * the compiler knows the value is non-null via smart cast.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T : Any> requireNonNull(value: T?): T {
    contract { returns() implies (value != null) }
    return value ?: throw IllegalArgumentException("Value must not be null")
}

/**
 * Elementary actions: query, insert and removal of transitions according to a
 * search pattern, using nullable parameters instead of logic variables.
 *
 * A `null` parameter acts as a wildcard (matches anything). A non-null
 * parameter acts as a filter. [query] methods return the matching transitions;
 * callers extract the fields they need from the result.
 *
 * Builder-style DSL is available via [query] and [remove] lambdas, which hide
 * wildcard `null`s behind named fields.
 */
class ElementaryActions(private val transitions: TransitionMapping) {
    // -----------------------------------------------------------------------
    // query: source, symbol, target
    // -----------------------------------------------------------------------

    /**
     * Queries symbol transitions by source, symbol, and target states.
     *
     * @param source the source state filter (nullable = wildcard)
     * @param symbol the symbol filter (nullable = wildcard)
     * @param target the target state filter (nullable = wildcard)
     * @return list of matching [TransitionEdge] instances
     */
    fun query(source: State?, symbol: Symbol?, target: State?): List<TransitionEdge> {
        val sourcePred = if (source == null) { _: TransitionEdge -> true } else sourceState(source)
        val symbolPred = if (symbol == null) { _: TransitionEdge -> true } else anyOf(listOf(symbol(symbol), isEpsilon))
        val targetPred = if (target == null) { _: TransitionEdge -> true } else targetState(target)

        return transitions
            .getTransitions()
            .filter(allOf(listOf(sourcePred, symbolPred, targetPred)))
    }

    // -----------------------------------------------------------------------
    // builder DSL: query { ... }
    // -----------------------------------------------------------------------

    /**
     * Queries symbol transitions using a builder DSL. Unset fields are
     * wildcards (match anything).
     *
     * Example:
     * ```
     * val results = ea.query {
     *     symbol = CharSymbol('a')
     *     target = p1
     *     // source omitted → wildcard
     * }
     * ```
     */
    fun query(configure: SymbolQuerySpec.() -> Unit): List<TransitionEdge> {
        val spec = SymbolQuerySpec().also(configure)
        return query(spec.source, spec.symbol, spec.target)
    }

    // -----------------------------------------------------------------------
    // query: source, submachine, target
    // -----------------------------------------------------------------------

    /**
     * Queries submachine transitions by source, submachine name, and target states.
     *
     * @param source the source state filter (nullable = wildcard)
     * @param submachine the submachine name filter (nullable = wildcard)
     * @param target the target state filter (nullable = wildcard)
     * @return list of matching [TransitionEdge] instances
     */
    fun query(source: State?, submachine: String?, target: State?): List<TransitionEdge> {
        val sourcePred = if (source == null) { _: TransitionEdge -> true } else sourceState(source)
        val submachinePred = if (submachine == null) isSubmachineCall else submachineCall(submachine)
        val targetPred = if (target == null) { _: TransitionEdge -> true } else targetState(target)

        return transitions
            .getTransitions()
            .filter(allOf(listOf(sourcePred, submachinePred, targetPred)))
    }

    // -----------------------------------------------------------------------
    // query: source, submachine, target, postAction
    // -----------------------------------------------------------------------

    /**
     * Queries submachine transitions by source, submachine name, target, and post action.
     *
     * @param source the source state filter (nullable = wildcard)
     * @param submachine the submachine name filter (nullable = wildcard)
     * @param target the target state filter (nullable = wildcard)
     * @param postAction the post action filter (nullable = wildcard)
     * @return list of matching [TransitionEdge] instances
     */
    fun query(source: State?, submachine: String?, target: State?, postAction: ActionRef?): List<TransitionEdge> {
        val sourcePred = if (source == null) { _: TransitionEdge -> true } else sourceState(source)
        val submachinePred = if (submachine == null) isSubmachineCall else submachineCall(submachine)
        val targetPred = if (target == null) { _: TransitionEdge -> true } else targetState(target)
        val postActionPred = actionRefPredicate(postAction, isPost = true)

        return transitions
            .getTransitions()
            .filter(allOf(listOf(sourcePred, submachinePred, targetPred, postActionPred)))
    }

    // -----------------------------------------------------------------------
    // query: priorAction, source, submachine, target
    // -----------------------------------------------------------------------

    /**
     * Queries submachine transitions by prior action, source, submachine name, and target.
     *
     * @param priorAction the prior action filter (nullable = wildcard)
     * @param source the source state filter (nullable = wildcard)
     * @param submachine the submachine name filter (nullable = wildcard)
     * @param target the target state filter (nullable = wildcard)
     * @return list of matching [TransitionEdge] instances
     */
    fun query(priorAction: ActionRef?, source: State?, submachine: String?, target: State?): List<TransitionEdge> {
        val priorActionPred = actionRefPredicate(priorAction, isPost = false)
        val sourcePred = if (source == null) { _: TransitionEdge -> true } else sourceState(source)
        val submachinePred = if (submachine == null) isSubmachineCall else submachineCall(submachine)
        val targetPred = if (target == null) { _: TransitionEdge -> true } else targetState(target)

        return transitions
            .getTransitions()
            .filter(allOf(listOf(sourcePred, submachinePred, targetPred, priorActionPred)))
    }

    // -----------------------------------------------------------------------
    // query: priorAction, source, submachine, target, postAction
    // -----------------------------------------------------------------------

    /**
     * Queries submachine transitions by prior action, source, submachine name,
     * target, and post action.
     *
     * @param priorAction the prior action filter (nullable = wildcard)
     * @param source the source state filter (nullable = wildcard)
     * @param submachine the submachine name filter (nullable = wildcard)
     * @param target the target state filter (nullable = wildcard)
     * @param postAction the post action filter (nullable = wildcard)
     * @return list of matching [TransitionEdge] instances
     */
    fun query(
        priorAction: ActionRef?,
        source: State?,
        submachine: String?,
        target: State?,
        postAction: ActionRef?,
    ): List<TransitionEdge> {
        val priorActionPred = actionRefPredicate(priorAction, isPost = false)
        val postActionPred = actionRefPredicate(postAction, isPost = true)
        val sourcePred = if (source == null) { _: TransitionEdge -> true } else sourceState(source)
        val submachinePred = if (submachine == null) isSubmachineCall else submachineCall(submachine)
        val targetPred = if (target == null) { _: TransitionEdge -> true } else targetState(target)

        return transitions
            .getTransitions()
            .filter(allOf(listOf(sourcePred, submachinePred, targetPred, priorActionPred, postActionPred)))
    }

    // -----------------------------------------------------------------------
    // remove: source, symbol, target
    // -----------------------------------------------------------------------

    /**
     * Removes symbol transitions by source, symbol, and target states.
     *
     * @param source the source state filter (nullable = wildcard)
     * @param symbol the symbol filter (nullable = wildcard)
     * @param target the target state filter (nullable = wildcard)
     */
    fun remove(source: State?, symbol: Symbol?, target: State?) {
        val sourcePred = if (source == null) { _: TransitionEdge -> true } else sourceState(source)
        val symbolPred = if (symbol == null) { _: TransitionEdge -> true } else anyOf(listOf(symbol(symbol), isEpsilon))
        val targetPred = if (target == null) { _: TransitionEdge -> true } else targetState(target)

        val ids =
            transitions
                .getTransitions()
                .filter(allOf(listOf(sourcePred, symbolPred, targetPred)))
                .map { it.id }

        ids.forEach { transitions.removeFromIdentifier(it) }
    }

    // -----------------------------------------------------------------------
    // builder DSL: remove { ... }
    // -----------------------------------------------------------------------

    /**
     * Removes symbol transitions using a builder DSL. Unset fields are
     * wildcards (match anything).
     *
     * Example:
     * ```
     * ea.remove {
     *     symbol = CharSymbol('b')
     *     target = p1
     *     // source omitted → wildcard
     * }
     *
     * ea.remove {
     *     source = q1
     *     symbol = CharSymbol('a')
     *     target = q1
     *     prior = actionRef("A", p1, p2)
     * }
     * ```
     */
    fun remove(configure: SymbolRemoveSpec.() -> Unit) {
        val spec = SymbolRemoveSpec().also(configure)
        when {
            spec.prior != null -> remove(spec.prior, spec.source, spec.symbol, spec.target)
            else -> remove(spec.source, spec.symbol, spec.target)
        }
    }

    // -----------------------------------------------------------------------
    // remove: priorAction, source, symbol, target
    // -----------------------------------------------------------------------

    /**
     * Removes symbol transitions by prior action, source, symbol, and target states.
     *
     * @param priorAction the prior action filter (nullable = wildcard)
     * @param source the source state filter (nullable = wildcard)
     * @param symbol the symbol filter (nullable = wildcard)
     * @param target the target state filter (nullable = wildcard)
     */
    fun remove(priorAction: ActionRef?, source: State?, symbol: Symbol?, target: State?) {
        val priorActionPred = actionRefPredicate(priorAction, isPost = false)
        val sourcePred = if (source == null) { _: TransitionEdge -> true } else sourceState(source)
        val symbolPred = if (symbol == null) { _: TransitionEdge -> true } else anyOf(listOf(symbol(symbol), isEpsilon))
        val targetPred = if (target == null) { _: TransitionEdge -> true } else targetState(target)

        val ids =
            transitions
                .getTransitions()
                .filter(allOf(listOf(sourcePred, symbolPred, targetPred, priorActionPred)))
                .map { it.id }

        ids.forEach { transitions.removeFromIdentifier(it) }
    }

    // -----------------------------------------------------------------------
    // add: source, symbol, target
    // -----------------------------------------------------------------------

    /**
     * Adds a symbol-consuming transition.
     *
     * @param source the source state
     * @param symbol the symbol to consume
     * @param target the target state
     */
    fun add(source: State, symbol: Symbol, target: State) {
        transitions.add(
            TransitionEdge(
                source = source,
                target = target,
                transition = Transition.SymbolTransition(symbol = symbol, sourceState = source, targetState = target),
            ),
        )
    }

    // -----------------------------------------------------------------------
    // add: source, symbol, target, priorAction
    // -----------------------------------------------------------------------

    /**
     * Adds a symbol-consuming transition with a prior action.
     *
     * @param source the source state
     * @param symbol the symbol to consume
     * @param target the target state
     * @param priorAction the prior action reference
     */
    fun add(source: State, symbol: Symbol, target: State, priorAction: ActionRef) {
        transitions.add(
            TransitionEdge(
                source = source,
                target = target,
                transition = Transition.SymbolTransition(symbol = symbol, sourceState = source, targetState = target),
                prior = priorAction,
            ),
        )
    }

    // -----------------------------------------------------------------------
    // remove: source, symbol, target, priorAction
    // -----------------------------------------------------------------------

    /**
     * Removes symbol transitions by source, symbol, target, and prior action.
     *
     * @param source the source state filter (nullable = wildcard)
     * @param symbol the symbol filter (nullable = wildcard)
     * @param target the target state filter (nullable = wildcard)
     * @param priorAction the prior action filter (nullable = wildcard)
     */
    fun remove(source: State?, symbol: Symbol?, target: State?, priorAction: ActionRef?) {
        val sourcePred = if (source == null) { _: TransitionEdge -> true } else sourceState(source)
        val symbolPred = if (symbol == null) { _: TransitionEdge -> true } else anyOf(listOf(symbol(symbol), isEpsilon))
        val targetPred = if (target == null) { _: TransitionEdge -> true } else targetState(target)
        val priorActionPred = actionRefPredicate(priorAction, isPost = false)

        val ids =
            transitions
                .getTransitions()
                .filter(allOf(listOf(sourcePred, symbolPred, targetPred, priorActionPred)))
                .map { it.id }

        ids.forEach { transitions.removeFromIdentifier(it) }
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private fun actionRefPredicate(aq: ActionRef?, isPost: Boolean): TransitionPredicate {
        if (aq == null) return { _: TransitionEdge -> true }

        val namePred =
            if (aq.name == null) {
                if (isPost) hasPostAction else hasPriorAction
            } else {
                if (isPost) {
                    { t: TransitionEdge -> t.post?.name == aq.name }
                } else {
                    { t: TransitionEdge -> t.prior?.name == aq.name }
                }
            }

        val argPreds =
            if (aq.args.isEmpty()) {
                emptyList<TransitionPredicate>()
            } else {
                aq.args.mapIndexed { i, arg ->
                    { t: TransitionEdge ->
                        val args = if (isPost) t.post?.args ?: emptyList() else t.prior?.args ?: emptyList()
                        args.getOrElse(i) { null } == arg
                    }
                } +
                    listOf(
                        { t: TransitionEdge ->
                            val args = if (isPost) t.post?.args ?: emptyList() else t.prior?.args ?: emptyList()
                            args.size == aq.args.size
                        },
                    )
            }

        return allOf(listOf(namePred) + argPreds)
    }
}

/** Type-safe check: is this edge a transition of type [T]? */
inline fun <reified T : Transition> TransitionEdge.isType(): Boolean = transition is T

/** Type-safe filter: transitions of type [T]. */
inline fun <reified T : Transition> TransitionMapping.withType(): List<TransitionEdge> =
    getTransitions().filter { it.transition is T }.toList()
