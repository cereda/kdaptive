// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.dot.GraphVizVisitor
import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.TransitionMapping

/**
 * Example: recognises `ab` over `{a, b}`, and uses an adaptive action that
 * dynamically adds a self-loop on the accepting state to demonstrate how
 * [GraphVizVisitor] captures every automaton topology mutation as a GraphViz
 * DOT snapshot.
 *
 * Each snapshot is printed to stdout; redirect to a `.dot` file and render
 * with `dot -Tpng file.dot -o file.png`.
 */
class GraphVizExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")

    override fun newInstance(): AdaptiveAutomaton = GraphVizExample()

    override fun setup() {
        mainSubmachine = "M"
        val sm = Submachine(
            name = "M",
            states = setOf(q0, q1, q2),
            initialState = q0,
            acceptingStates = setOf(q2),
        )
        submachines.add(sm)

        // Action "AddLoop": after consuming 'a', add a self-loop q2 --'a'--> q2
        // on the accepting state, demonstrating dynamic topology mutation.
        actions.add(
            Action(name = "AddLoop") { transitions, _, _ ->
                val ea = ElementaryActions(transitions, submachines)
                ea.add(q2, CharSymbol('a'), q2)
            },
        )

        consume(q0, 'a', q1, post = "AddLoop")
        consume(q1, 'b', q2)
    }

    fun recognize(input: String): Boolean = recognize(convert(input)).accepted
}

/** Entry point for `./gradlew :examples:run -Pmain=...GraphVizExampleKt`. */
fun main() {
    val aa = GraphVizExample()

    val snapshots = mutableListOf<Pair<Int, String>>()
    var step = 0
    val visitor = GraphVizVisitor { pathId, dot ->
        snapshots.add(pathId to dot)
        step++
    }
    aa.visitors.add(visitor)

    val inputs = listOf("ab", "aba", "a", "b", "", "abab")
    for (input in inputs) {
        val result = aa.recognize(input)
        println("Input '$input': ${if (result) "accepted" else "rejected"}")
    }

    println("\nCaptured $step GraphViz snapshots.")
    if (snapshots.isNotEmpty()) {
        val (pathId, dot) = snapshots.first()
        println("First DOT snapshot (path $pathId):\n$dot")
        println("Save to a .dot file and render with: dot -Tpng file.dot -o file.png")
    }
}
