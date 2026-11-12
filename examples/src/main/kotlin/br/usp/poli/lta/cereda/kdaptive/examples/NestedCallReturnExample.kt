// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Submachine

/**
 * Example: recognises the language `a c* e d* b` using nested submachine calls.
 *
 * Structure:
 *   Main: q0 --a--> q1, q1 calls "Mid" (push q2), q2 --b--> q3
 *   Mid:  m0 --c--> m1, m1 calls "Inner" (push m2), m2 --d--> m3
 *   Inner: n0 --e--> n1
 *
 * Each submachine call pushes the return state onto the stack; each return
 * pops it and resumes in the caller. This exercises nested call/return with
 * stack depth 2.
 */
class NestedCallReturnExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")
    private val q3 = ExampleState("q3")

    private val m0 = ExampleState("m0")
    private val m1 = ExampleState("m1")
    private val m2 = ExampleState("m2")
    private val m3 = ExampleState("m3")

    private val n0 = ExampleState("n0")
    private val n1 = ExampleState("n1")

    override fun newInstance(): AdaptiveAutomaton = NestedCallReturnExample()

    override fun setup() {
        mainSubmachine = "Main"

        val mainSm = Submachine(
            name = "Main",
            states = setOf(q0, q1, q2, q3),
            initialState = q0,
            acceptingStates = setOf(q3),
        )
        submachines.add(mainSm)

        val midSm = Submachine(
            name = "Mid",
            states = setOf(m0, m1, m2, m3),
            initialState = m0,
            acceptingStates = setOf(m3),
        )
        submachines.add(midSm)

        val innerSm = Submachine(
            name = "Inner",
            states = setOf(n0, n1),
            initialState = n0,
            acceptingStates = setOf(n1),
        )
        submachines.add(innerSm)

        // Main: a, call Mid, b
        symbol(q0, 'a', q1)
        call(q1, "Mid", q2)
        symbol(q2, 'b', q3)

        // Mid: c, call Inner, d*
        symbol(m0, 'c', m1)
        call(m1, "Inner", m2)
        symbol(m2, 'd', m3)
        symbol(m3, 'd', m3)

        // Inner: e
        symbol(n0, 'e', n1)
    }

    fun recognize(input: String): Boolean = recognize(convert(input)).accepted
}

fun main() {
    val aa = NestedCallReturnExample()

    val inputs = listOf("acedb", "aceddb", "acedddb", "acdb", "aeb", "ab", "aced", "cedb")
    for (input in inputs) {
        val result = aa.recognize(input)
        println("Input '$input': ${if (result) "accepted" else "rejected"}")
    }
}
