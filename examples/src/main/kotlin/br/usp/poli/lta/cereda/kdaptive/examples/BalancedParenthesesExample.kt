// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.actions.actionRef

/**
 * Example: recognises balanced parentheses over `{ (, ) }` using submachine
 * call/return with stack-based matching.
 *
 * Grammar: S -> ( S ) S | epsilon
 *
 * The main submachine consumes '(' and calls itself recursively to match
 * nested parentheses, then consumes ')' and returns.
 */
class BalancedParenthesesExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")
    private val q3 = ExampleState("q3")

    override fun newInstance(): AdaptiveAutomaton = BalancedParenthesesExample()

    override fun setup() {
        mainSubmachine = "S"

        val sm = Submachine(
            name = "S",
            states = setOf(q0, q1, q2, q3),
            initialState = q0,
            acceptingStates = setOf(q0, q3),
        )
        submachines.add(sm)

        // q0 --'('--> q1, then call S to match inner, then q2 --')'--> q3
        consume(q0, CharSymbol('('), q1)
        call(q1, "S", q2)
        consume(q2, CharSymbol(')'), q3)

        // From q3, can continue with another balanced group or accept
        consume(q3, CharSymbol('('), q1)
        epsilon(q3, q0)
    }

    fun recognize(input: String): Boolean = recognize(convert(input)).accepted
}

fun main() {
    val aa = BalancedParenthesesExample()

    val inputs = listOf("", "()", "(())", "()()", "(()())", "((()))", "(", ")", "(()", "())", ")(", "()()(")
    for (input in inputs) {
        val result = aa.recognize(input)
        println("Input '$input': ${if (result) "accepted" else "rejected"}")
    }
}
