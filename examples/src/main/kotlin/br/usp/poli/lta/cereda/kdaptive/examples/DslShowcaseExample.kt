// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Submachines
import br.usp.poli.lta.cereda.kdaptive.model.TransitionMapping
import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions

/**
 * Example: recognises `a b c` where the `c` transition is added dynamically by
 * an adaptive action after consuming `a`. Demonstrates dynamic transition
 * addition at runtime using ElementaryActions.
 */
class DslShowcaseExample : AdaptiveAutomaton() {

    val q0 = ExampleState("q0")
    val q1 = ExampleState("q1")
    val q2 = ExampleState("q2")
    val q3 = ExampleState("q3")

    override fun newInstance(): AdaptiveAutomaton = DslShowcaseExample()

    override fun setup() {
        mainSubmachine = "M"
        val sm = Submachine(
            name = "M",
            states = setOf(q0, q1, q2, q3),
            initialState = q0,
            acceptingStates = setOf(q3),
        )
        submachines.add(sm)

        actions.add(
            Action(name = "AddC") { transitions, _, _ ->
                addCAction(transitions, submachines, q2, q3)
            },
        )

        consume(q0, 'a', q1, post = "AddC")
        consume(q1, 'b', q2)
    }

    fun recognize(input: String): Boolean = recognize(convert(input)).accepted
}

/**
 * Adaptive action "AddC": dynamically adds a `q2 --'c'--> q3` transition.
 */
private fun addCAction(transitions: TransitionMapping, submachines: Submachines, q2: State, q3: State) {
    val ea = ElementaryActions(transitions, submachines)
    ea.add(q2, CharSymbol('c'), q3)
}

fun main() {
    val aa = DslShowcaseExample()

    val inputs = listOf("abc", "ab", "ac", "a", "", "abcd")
    for (input in inputs) {
        val result = aa.recognize(input)
        println("Input '$input': ${if (result) "accepted" else "rejected"}")
    }
}
