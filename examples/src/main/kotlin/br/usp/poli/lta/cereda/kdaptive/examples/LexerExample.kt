// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.actionRef

/**
 * Example: simple lexer that recognises words and numbers in "hello world 123",
 * building tokens through post actions.
 */
class LexerExample : AdaptiveAutomaton() {

    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")
    private val q3 = ExampleState("q3")
    private val q4 = ExampleState("q4")

    internal val tokens = mutableListOf<ExampleToken>()

    override fun newInstance(): AdaptiveAutomaton = LexerExample()

    override fun setup() {
        mainSubmachine = "M"
        val sm = Submachine(
            name = "M",
            states = setOf(q0, q1, q2, q3, q4),
            initialState = q0,
            acceptingStates = setOf(q0, q1, q2, q3, q4),
        )
        submachines.add(sm)

        for (c in "0123456789") {
            symbol(q0, c, q1, post = actionRef("A", "number", c))
            symbol(q1, c, q1, post = actionRef("B", c))
        }
        for (c in "abcdefghijklmnopqrstuvwxyz") {
            symbol(q0, c, q2, post = actionRef("A", "word", c))
            symbol(q2, c, q2, post = actionRef("B", c))
        }
    }

    fun tokenize(input: String): List<ExampleToken> {
        tokens.clear()
        var symbols = convert(input)
        var size = symbols.size
        var compare = 0
        do {
            symbols = symbols.subList(compare, size)
            val result = recognize(symbols)
            size = symbols.size
            val paths = result.paths
            if (paths.isEmpty()) break
            val cursor = paths[0].cursor
            compare = if (cursor == compare) compare + 1 else cursor
        } while (size > compare)
        return tokens.toList()
    }
}

fun main() {
    val aa = LexerExample()

    aa.actions.add(
        Action(name = "A") { _, _, parameters ->
            val token = ExampleToken(parameters[0].toString(), parameters[1].toString())
            aa.tokens.add(token)
        },
    )
    aa.actions.add(
        Action(name = "B") { _, _, parameters ->
            val last = aa.tokens.last()
            last.value = (last.value ?: "") + parameters[0].toString()
        },
    )

    val result = aa.tokenize("hello world 123")
    println(result)
}
