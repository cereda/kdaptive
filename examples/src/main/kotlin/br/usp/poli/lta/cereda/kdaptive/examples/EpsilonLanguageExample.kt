// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Submachine

/**
 * Example: recognises the language `a* b*` over `{a, b}` using epsilon transitions
 * and nondeterminism. At state q0, the automaton may either consume another 'a'
 * or take an epsilon transition to the b-phase (q1).
 */
class EpsilonLanguageExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")

    override fun newInstance(): AdaptiveAutomaton = EpsilonLanguageExample()

    override fun setup() {
        mainSubmachine = "M"
        val sm = Submachine(
            name = "M",
            states = setOf(q0, q1),
            initialState = q0,
            acceptingStates = setOf(q0, q1),
        )
        submachines.add(sm)

        symbol(q0, 'a', q0)
        epsilon(q0, q1)
        symbol(q1, 'b', q1)
    }

    fun recognize(input: String): Boolean = recognize(convert(input)).accepted
}

fun main() {
    val aa = EpsilonLanguageExample()

    val inputs = listOf("aabb", "bb", "aa", "", "ab", "ba", "aab")
    for (input in inputs) {
        val result = aa.recognize(input)
        println("Input '$input': ${if (result) "accepted" else "rejected"}")
    }
}
