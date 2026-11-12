// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.Submachine

/**
 * Example: recognises the language {w | w contains "ab"} over {a, b}.
 *
 * States:
 * - q0: haven't seen 'a' yet
 * - q1: have seen 'a', waiting for 'b'
 * - q2: have seen "ab" - accepting
 *
 * Transitions:
 * - q0 --a--> q1 (start of potential "ab")
 * - q0 --b--> q0 (still haven't seen 'a')
 * - q1 --a--> q1 (another 'a', still waiting for 'b')
 * - q1 --b--> q2 (found "ab"!)
 * - q2 --a--> q2 (already found "ab", stay accepting)
 * - q2 --b--> q2 (already found "ab", stay accepting)
 */
class SubstringExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")

    override fun newInstance(): AdaptiveAutomaton = SubstringExample()

    override fun setup() {
        mainSubmachine = "M"

        val sm = Submachine(
            name = "M",
            states = setOf(q0, q1, q2),
            initialState = q0,
            acceptingStates = setOf(q2),
        )
        submachines.add(sm)

        symbol(q0, CharSymbol('a'), q1)
        symbol(q0, CharSymbol('b'), q0)
        symbol(q1, CharSymbol('a'), q1)
        symbol(q1, CharSymbol('b'), q2)
        symbol(q2, CharSymbol('a'), q2)
        symbol(q2, CharSymbol('b'), q2)
    }

    fun recognize(input: String): Boolean = recognize(convert(input)).accepted
}

fun main() {
    val aa = SubstringExample()

    val inputs = listOf("ab", "ba", "aab", "abb", "baa", "baba", "aaabbb", "bbbaaa", "ababab", "")
    for (input in inputs) {
        val result = aa.recognize(input)
        println("Input '$input': ${if (result) "accepted" else "rejected"}")
    }
}
