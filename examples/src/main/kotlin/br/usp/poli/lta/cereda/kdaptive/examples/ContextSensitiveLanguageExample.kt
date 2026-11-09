// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.dsl.automaton
import br.usp.poli.lta.cereda.kdaptive.dsl.specToAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.actions.asState
import br.usp.poli.lta.cereda.kdaptive.utils.RecognitionPath

/**
 * Example: recognises the context-sensitive language `a^n b^n c^n` (n > 0)
 * using adaptive actions that dynamically add and remove transitions.
 *
 * The whole automaton is declared with the `automaton { }` builder DSL: the
 * static structure (states and transitions) and the adaptive action live in
 * the same declaration, and the action allocates its intermediate states
 * through the DSL's fresh-state factory instead of a global counter.
 */
class ContextSensitiveLanguageExample {
    private val spec =
        automaton {
            submachine("M") {
                initial("q0")
                state("q1")
                state("q2")
                accepting("q3")
            }

            // The loop state is captured here; the action's arguments carry
            // the counters (post) or the fresh states (prior).
            val loop = state("q1")

            // The adaptive action 'A': rewires the 'b' and 'c' edges through
            // fresh states, so the counts of 'a's, 'b's and 'c's must match.
            // Both references bind two arguments, which the arity contract checks.
            action("A", arity = 2) { _, args ->
                val p1: State = args[0].asState()
                val p2: State = args[1].asState()
                val g1 = freshState()
                val g2 = freshState()
                val a = CharSymbol('a')
                val b = CharSymbol('b')
                val c = CharSymbol('c')

                val bInto = ea.query {
                    symbol = b
                    target = p1
                }
                val cInto = ea.query {
                    symbol = c
                    target = p2
                }
                ea.remove {
                    symbol = b
                    target = p1
                }
                ea.remove {
                    symbol = c
                    target = p2
                }
                ea.remove {
                    source = loop
                    symbol = a
                    target = loop
                    prior = ref("A", p1, p2)
                }

                bInto.forEach { edge ->
                    ea.add(edge.source, b, g1)
                    ea.add(g1, b, p1)
                }
                cInto.forEach { edge ->
                    ea.add(edge.source, c, g2)
                    ea.add(g2, c, p2)
                }
                ea.add(loop, a, loop, ref("A", g1, g2))
            }

            on("q0").on('a').to("q1")
            on("q1").on('a').to("q1", post = ref("A", "q2", "q3"))
            on("q1").on('b').to("q2")
            on("q2").on('c').to("q3")
            main("M")
        }

    private val aa = specToAutomaton(spec)

    /** Recognises [input] over the alphabet {a, b, c}. */
    fun recognize(input: String): Boolean = aa.recognize(convert(input))

    /** Returns the recognition paths collected during the last recognition. */
    fun getRecognitionPaths(): List<RecognitionPath> = aa.getRecognitionPaths()
}

fun main() {
    val aa = ContextSensitiveLanguageExample()

    val result = aa.recognize("aaabbbccc")
    println("Result: string ${if (result) "accepted" else "rejected"}")
    print("Deterministic recognition? ")
    println(getAnswer(aa.getRecognitionPaths().size == 1))
    for (rp in aa.getRecognitionPaths()) {
        println(rp)
    }
}
