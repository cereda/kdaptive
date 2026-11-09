// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine

/**
 * Example: recognises the language `a(b|c)` using nondeterminism.
 *
 * From q0, consuming 'a' forks into two paths: one to q1, one to q2.
 *   q1 --b--> q3 (accepting)
 *   q2 --c--> q3 (accepting)
 *
 * Both "ab" and "ac" are accepted via separate recognition paths.
 */
class NondeterminismExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")
    private val q3 = ExampleState("q3")

    override fun newInstance(): AdaptiveAutomaton = NondeterminismExample()

    override fun setup() {
        mainSubmachine = "M"

        val sm = Submachine(
            name = "M",
            states = setOf(q0, q1, q2, q3),
            initialState = q0,
            acceptingStates = setOf(q3),
        )
        submachines.add(sm)

        // Nondeterministic: from q0 on 'a', two paths fork
        consume(q0, 'a', q1)
        consume(q0, 'a', q2)

        consume(q1, 'b', q3)
        consume(q2, 'c', q3)
    }

    fun recognize(input: String): Boolean = recognize(convert(input))
}

fun main() {
    val aa = NondeterminismExample()

    val inputs = listOf("ab", "ac", "a", "b", "c", "abc", "acc")
    for (input in inputs) {
        val result = aa.recognize(input)
        println("Input '$input': ${if (result) "accepted" else "rejected"}")
    }
}
