// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine

/**
 * Example: recognises the context-free language `a^n b^n` (n >= 0) using a
 * recursive submachine call.
 */
class ContextFreeLanguageExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")
    private val q3 = ExampleState("q3")

    override fun newInstance(): AdaptiveAutomaton = ContextFreeLanguageExample()

    override fun setup() {
        mainSubmachine = "M"
        val sm = Submachine(
            name = "M",
            states = setOf(q0, q1, q2, q3),
            initialState = q0,
            acceptingStates = setOf(q0, q3),
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
        call(q1, "M", q2)
        consume(q2, 'b', q3, post = "B")
    }

    fun recognize(input: String): Boolean = recognize(convert(input))
}

fun main() {
    val aa = ContextFreeLanguageExample()

    val result = aa.recognize("aabb")
    println("Result: string ${if (result) "accepted" else "rejected"}")
    print("Deterministic recognition? ")
    println(getAnswer(aa.getRecognitionPaths().size == 1))
    for (rp in aa.getRecognitionPaths()) {
        println(rp)
    }
}
