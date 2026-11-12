// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.Submachine

/**
 * Example: recognises the language {a^n | n mod 3 = 0} over {a}.
 *
 * Uses three states representing the count of 'a's modulo 3:
 * - q0: count ≡ 0 (mod 3) - accepting
 * - q1: count ≡ 1 (mod 3)
 * - q2: count ≡ 2 (mod 3)
 *
 * On each 'a', the state cycles q0 → q1 → q2 → q0.
 * On any other symbol, the automaton rejects.
 */
class ModuloExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")

    override fun newInstance(): AdaptiveAutomaton = ModuloExample()

    override fun setup() {
        mainSubmachine = "M"

        val sm = Submachine(
            name = "M",
            states = setOf(q0, q1, q2),
            initialState = q0,
            acceptingStates = setOf(q0),
        )
        submachines.add(sm)

        symbol(q0, CharSymbol('a'), q1)
        symbol(q1, CharSymbol('a'), q2)
        symbol(q2, CharSymbol('a'), q0)
    }

    fun recognize(input: String): Boolean = recognize(convert(input)).accepted
}

fun main() {
    val aa = ModuloExample()

    val inputs = listOf("", "a", "aa", "aaa", "aaaa", "aaaaa", "aaaaaa", "b", "ab", "aba")
    for (input in inputs) {
        val result = aa.recognize(input)
        println("Input '$input': ${if (result) "accepted" else "rejected"}")
    }
}
