// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Submachine

/**
 * Example: recognises simple binary arithmetic expressions `digit op digit`
 * where digit is 0 or 1, and op is + or *.
 * Demonstrates a small expression recognizer with multiple operator paths.
 */
class ArithmeticExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")
    private val q3 = ExampleState("q3")
    private val q4 = ExampleState("q4")

    override fun newInstance(): AdaptiveAutomaton = ArithmeticExample()

    override fun setup() {
        mainSubmachine = "M"
        val sm = Submachine(
            name = "M",
            states = setOf(q0, q1, q2, q3, q4),
            initialState = q0,
            acceptingStates = setOf(q4),
        )
        submachines.add(sm)

        symbol(q0, '0', q1)
        symbol(q0, '1', q1)

        symbol(q1, '+', q2)
        symbol(q1, '*', q3)

        symbol(q2, '0', q4)
        symbol(q2, '1', q4)
        symbol(q3, '0', q4)
        symbol(q3, '1', q4)
    }

    fun recognize(input: String): Boolean = recognize(convert(input)).accepted
}

fun main() {
    val aa = ArithmeticExample()

    val inputs = listOf("0+0", "0+1", "1+0", "1+1", "0*0", "0*1", "1*0", "1*1", "0+", "*1", "0++1", "2+2")
    for (input in inputs) {
        val result = aa.recognize(input)
        println("Input '$input': ${if (result) "accepted" else "rejected"}")
    }
}
