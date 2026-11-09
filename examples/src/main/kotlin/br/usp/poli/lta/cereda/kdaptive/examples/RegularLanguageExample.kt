// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine

/**
 * Example: recognises the regular language `(ab)+` over `{a, b}`.
 */
class RegularLanguageExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")

    override fun newInstance(): AdaptiveAutomaton = RegularLanguageExample()

    override fun setup() {
        mainSubmachine = "M"
        val sm = Submachine(
            name = "M",
            states = setOf(q0, q1, q2),
            initialState = q0,
            acceptingStates = setOf(q2),
        )
        submachines.add(sm)

        actions.add(
            Action(name = "A") { _, _, _ ->
                println("Consumed A")
            },
        )
        actions.add(
            Action(name = "B") { _, _, _ ->
                println("Consumed B")
            },
        )

        consume(q0, 'a', q1, post = "A")
        consume(q1, 'b', q2, post = "B")
        consume(q2, 'a', q1, post = "A")
    }

    fun recognize(input: String): Boolean = recognize(convert(input))
}

fun main() {
    val aa = RegularLanguageExample()

    val result = aa.recognize("ab")
    println("Result: string ${if (result) "accepted" else "rejected"}")
    print("Deterministic recognition? ")
    println(getAnswer(aa.getRecognitionPaths().size == 1))
    for (rp in aa.getRecognitionPaths()) {
        println(rp)
    }
}
