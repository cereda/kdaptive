// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Submachine

/**
 * Example: recognises a simple config pattern `a=[01]+;` (a key 'a',
 * equals sign, one or more binary digits, and a terminating semicolon).
 * Demonstrates a small recognizer with multiple phases.
 */
class ConfigParserExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")
    private val q3 = ExampleState("q3")
    private val q4 = ExampleState("q4")

    override fun newInstance(): AdaptiveAutomaton = ConfigParserExample()

    override fun setup() {
        mainSubmachine = "M"
        val sm = Submachine(
            name = "M",
            states = setOf(q0, q1, q2, q3, q4),
            initialState = q0,
            acceptingStates = setOf(q4),
        )
        submachines.add(sm)

        symbol(q0, 'a', q1)
        symbol(q1, '=', q2)
        symbol(q2, '0', q3)
        symbol(q2, '1', q3)
        symbol(q3, '0', q3)
        symbol(q3, '1', q3)
        symbol(q3, ';', q4)
    }

    fun recognize(input: String): Boolean = recognize(convert(input)).accepted
}

fun main() {
    val aa = ConfigParserExample()

    val inputs = listOf("a=0;", "a=1;", "a=01;", "a=1010;", "a=;", "a=0", "b=0;", "a=0;;", "")
    for (input in inputs) {
        val result = aa.recognize(input)
        println("Input '$input': ${if (result) "accepted" else "rejected"}")
    }
}
