// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge

/**
 * Recognises strings over {a, b} with an even number of 'a's.
 * States: Q0 (even, initial, accepting), Q1 (odd).
 */
class EvenAsAutomaton : AdaptiveAutomaton() {
    private val q0 = object : State() {}
    private val q1 = object : State() {}

    override fun newInstance(): AdaptiveAutomaton = EvenAsAutomaton()

    override fun setup() {
        mainSubmachine = "M"
        val sm =
            Submachine(
                name = "M",
                states = setOf(q0, q1),
                initialState = q0,
                acceptingStates = setOf(q0),
            )
        submachines.add(sm)

        transitions.add(
            TransitionEdge(
                source = q0,
                target = q1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            ),
        )
        transitions.add(
            TransitionEdge(
                source = q0,
                target = q0,
                transition = Transition.SymbolTransition(symbol = CharSymbol('b')),
            ),
        )
        transitions.add(
            TransitionEdge(
                source = q1,
                target = q0,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            ),
        )
        transitions.add(
            TransitionEdge(
                source = q1,
                target = q1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('b')),
            ),
        )
    }

    internal fun symbols(vararg chars: Char): List<CharSymbol> = chars.map { CharSymbol(it) }
}
