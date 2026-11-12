// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.benchmarks

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.execution.RecognitionLimits
import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge

/*
 * The automata the benchmarks run on.
 *
 * Every fixture is a small, self-contained automaton whose shape is easy to
 * describe: a chain, a fan-out, or a chain whose adaptive action keeps
 * changing the topology.
 */

/**
 * A chain of [length] states, joined by symbol-consuming transitions.
 *
 * The recognition of [length] symbols walks the chain from end to end: the
 * scenario measures the cost of a *step*, with a topology that never changes.
 */
internal class ChainAutomaton(private val length: Int) : AdaptiveAutomaton() {
    /** The states of the chain, from the first to the last. */
    private val states: List<State> = (0..length).map { index ->
        object : State() {
            override fun toString(): String = "s$index"
        }
    }

    override fun newInstance(): AdaptiveAutomaton = ChainAutomaton(length)

    override fun setup() {
        mainSubmachine = "M"
        submachines.add(
            Submachine(
                name = "M",
                states = states.toSet(),
                initialState = states.first(),
                acceptingStates = setOf(states.last()),
            ),
        )
        for (index in 0 until length) {
            symbol(states[index], CharSymbol('a'), states[index + 1])
        }
    }

    /** The input that walks the whole chain. */
    fun input(): List<CharSymbol> = List(length) { CharSymbol('a') }
}

/**
 * A state with [branches] alternatives, all consuming the same symbol.
 *
 * The recognition of a single symbol forks [branches] paths at once: the
 * scenario measures the cost of a *fork*, with a topology that never changes.
 */
internal class ForkAutomaton(private val branches: Int) : AdaptiveAutomaton() {
    /** The state every branch leaves. */
    private val start: State = object : State() {
        override fun toString(): String = "q0"
    }

    /** The states the branches lead to. */
    private val targets: List<State> = (0 until branches).map { index ->
        object : State() {
            override fun toString(): String = "q$index"
        }
    }

    override fun newInstance(): AdaptiveAutomaton = ForkAutomaton(branches)

    override fun setup() {
        mainSubmachine = "M"
        submachines.add(
            Submachine(
                name = "M",
                states = (targets + start).toSet(),
                initialState = start,
                acceptingStates = emptySet(),
            ),
        )
        for (target in targets) {
            symbol(start, CharSymbol('a'), target)
        }
    }

    /** The input that takes the fork. */
    fun input(): List<CharSymbol> = listOf(CharSymbol('a'))
}

/**
 * A chain of [length] states whose adaptive action adds an edge on every step.
 *
 * This is the scenario the library lives for: the topology grows while the
 * path runs, and the engine reads the edges leaving its current state on
 * every single step. An engine that rebuilt its index on every mutation paid
 * a scan of the whole topology per step, and the recognition was quadratic in
 * the size of the topology.
 */
internal class MutatingAutomaton(private val length: Int) : AdaptiveAutomaton() {
    /** The states of the chain, from the first to the last. */
    private val states: List<State> = (0..length).map { index ->
        object : State() {
            override fun toString(): String = "s$index"
        }
    }

    /** The states the growing edges leave from, one per step of the chain. */
    private val scratches: List<State> = (0..length).map { index ->
        object : State() {
            override fun toString(): String = "g$index"
        }
    }

    /** The edges the adaptive action adds, one per step of the chain. */
    private val growing: List<TransitionEdge> = scratches.zipWithNext { source, target ->
        TransitionEdge(source = source, target = target, transition = Transition.Epsilon)
    }

    override fun newInstance(): AdaptiveAutomaton = MutatingAutomaton(length)

    override fun setup() {
        mainSubmachine = "M"
        submachines.add(
            Submachine(
                name = "M",
                states = (states + scratches).toSet(),
                initialState = states.first(),
                acceptingStates = setOf(states.last()),
            ),
        )
        actions.add(
            Action(name = "grow") { transitions, _, _ ->
                // The mapping starts with the edges of the chain: the index of
                // the action tells which edge of the growing sequence comes
                // next.
                val index = transitions.size - length
                growing.getOrNull(index)?.let { transitions.add(it) }
            },
        )
        for (index in 0 until length) {
            symbol(states[index], CharSymbol('a'), states[index + 1], post = "grow")
        }
    }

    /** The input that walks the whole chain. */
    fun input(): List<CharSymbol> = List(length) { CharSymbol('a') }
}

/**
 * The bounds the benchmarks use.
 *
 * The defaults of the library are meant for ordinary automata: a benchmark
 * that walks forty thousand states would trip them, and the bounds would
 * hide the very cost the benchmark measures.
 */
internal fun benchmarkLimits(): RecognitionLimits = RecognitionLimits(
    maxStepsPerPath = 10_000_000,
    maxLivePaths = 10_000_000,
    maxCallDepth = 10_000,
)
