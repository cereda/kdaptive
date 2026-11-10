// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.Observation
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.actions.actionRef

/**
 * A fact published by the adaptive action: the size of the domain it will
 * search next time.
 *
 * The engine reports the edges a query matched, but not the projection the
 * action derives from them — the number of distinct source states, which is
 * the domain the action will narrow its next query to. The action computes it
 * and publishes it; the consumer prices it if it wants to.
 */
data class DomainSize(val count: Int) : Observation

/**
 * Example: an adaptive automaton that reshapes its own topology while it
 * recognises, and a consumer that prices the work without touching the
 * library.
 *
 * The automaton recognises strings of the form `a* b`. Every time it consumes
 * an `a`, the adaptive action queries the mapping for the `b` transitions
 * leaving the starting state, retires the bookkeeping edge left by the
 * previous firing, and installs a fresh one.
 *
 * The interesting part is not the automaton but the separation: [CostVisitor]
 * computes the cost of the run from the facts the library exposes, and the
 * library knows nothing about cost at all.
 */
class CostExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")

    /** A bookkeeping state: it is never reached, it only carries the marker edge. */
    private val k = ExampleState("k")

    override fun newInstance(): AdaptiveAutomaton = CostExample()

    override fun setup() {
        mainSubmachine = "M"
        val sm =
            Submachine(
                name = "M",
                states = setOf(q0, q1, k),
                initialState = q0,
                acceptingStates = setOf(q1),
            )
        submachines.add(sm)

        // Action "Reshape": after consuming an 'a', inspect the 'b' transitions
        // leaving q0, retire the marker edge installed by the previous firing,
        // and install a fresh one. The first firing retires nothing: the
        // removal matches no edge, and is still reported.
        actions.add(
            Action(name = "Reshape") { transitions, _, _ ->
                val ea = ElementaryActions(transitions)
                val matched = ea.query(q0, CharSymbol('b'), null)
                transitions.observe(DomainSize(matched.map { it.source }.distinct().size))
                ea.remove(k, CharSymbol('a'), null)
                ea.add(k, CharSymbol('a'), k)
            },
        )

        consume(q0, 'a', q0, post = "Reshape")
        consume(q0, 'b', q1)
    }

    fun recognize(input: String): Boolean = recognize(convert(input))
}

/** Entry point for `./gradlew :examples:run -Pmain=...CostExampleKt`. */
fun main() {
    val aa = CostExample()
    val visitor = CostVisitor()
    aa.visitors.add(visitor)

    for (input in listOf("b", "ab", "aab", "aaab", "aabb", "")) {
        val before = visitor.cost
        val result = aa.recognize(input)
        println("Input '$input': ${getAnswer(result)}, cost ${visitor.cost - before}")
    }

    println()
    print(visitor.report())
}
