// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine

/**
 * Example: recognises the language `a c* b` using submachine call/return.
 * The submachine "StarC" processes zero or more 'c's, then returns to the
 * main submachine to consume the final 'b'.
 */
class CallReturnExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")
    private val q3 = ExampleState("q3")

    private val m0 = ExampleState("m0")
    private val m1 = ExampleState("m1")

    override fun newInstance(): AdaptiveAutomaton = CallReturnExample()

    override fun setup() {
        mainSubmachine = "Main"

        val mainSm = Submachine(
            name = "Main",
            states = setOf(q0, q1, q2, q3),
            initialState = q0,
            acceptingStates = setOf(q3),
        )
        submachines.add(mainSm)

        val starCSm = Submachine(
            name = "StarC",
            states = setOf(m0, m1),
            initialState = m0,
            acceptingStates = setOf(m0, m1),
        )
        submachines.add(starCSm)

        consume(q0, 'a', q1)
        call(q1, "StarC", q2)
        consume(q2, 'b', q3)

        consume(m0, 'c', m1)
        consume(m1, 'c', m1)
    }

    fun recognize(input: String): Boolean = recognize(convert(input)).accepted
}

fun main() {
    val aa = CallReturnExample()

    val inputs = listOf("acb", "acccb", "ab", "acb", "a", "ac", "b", "abc")
    for (input in inputs) {
        val result = aa.recognize(input)
        println("Input '$input': ${if (result) "accepted" else "rejected"}")
    }
}
