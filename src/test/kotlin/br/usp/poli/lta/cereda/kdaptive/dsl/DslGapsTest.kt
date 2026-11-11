// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.dsl

import br.usp.poli.lta.cereda.kdaptive.model.ActionRef
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions
import br.usp.poli.lta.cereda.kdaptive.model.actions.asState
import br.usp.poli.lta.cereda.kdaptive.model.sets.TransitionMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Covers the remaining [AutomatonDsl] pieces: the grouped `call` helper, the
 * dotted `to` completing through default arguments, and the three
 * resolver-validation error branches of [SubmachineBuilder].
 */
class DslGapsTest {
    @Test
    fun `grouped call helper adds a call edge`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                submachine("N") {
                    initial("n0")
                    accepting("n1")
                }
                from("q0") {
                    call("N", "q1")
                }
                main("M")
            }
        assertEquals(1, spec.transitions.size)
        val call = spec.transitions.single()
        assertEquals("q0", call.source.toString())
        assertEquals("q1", call.target.toString())
        assertTrue(call.transition is br.usp.poli.lta.cereda.kdaptive.model.Transition.Call)
    }

    @Test
    fun `dotted to through default arguments`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                on("q0").on('a').to("q1", post = "X")
                main("M")
            }
        val edge = spec.transitions.single()
        assertEquals("q0", edge.source.toString())
        assertEquals("q1", edge.target.toString())
        assertEquals("X", edge.post?.name)
    }

    @Test
    fun `submachine builder rejects unresolvable state values`() {
        val builder1 = SubmachineBuilder(mutableMapOf())
        assertThrows<IllegalArgumentException> { builder1.initial(999) }

        val builder2 = SubmachineBuilder(mutableMapOf())
        assertThrows<IllegalArgumentException> { builder2.accepting(999) }

        val builder3 = SubmachineBuilder(mutableMapOf())
        assertThrows<IllegalArgumentException> { builder3.state(999) }
    }

    // -----------------------------------------------------------------------
    // Adaptive actions in the builder DSL (oc49)
    // -----------------------------------------------------------------------

    @Test
    fun `dsl action drives dynamic transition addition`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    state("q1")
                    state("q2")
                    accepting("q3")
                }
                val q2 = state("q2")
                val q3 = state("q3")
                action("AddC") { _, _ ->
                    ea.add(q2, CharSymbol('c'), q3)
                }
                on("q0").on('a').to("q1", post = "AddC")
                on("q1").on('b').to("q2")
                main("M")
            }
        val aa = specToAutomaton(spec)
        val input = { chars: String -> chars.map { CharSymbol(it) } }

        assertTrue(aa.recognize(input("abc")))
        assertTrue(input("ab").let { !aa.recognize(it) })
    }

    @Test
    fun `ref resolves string and char argument values at build time`() {
        val seen = mutableListOf<List<Any>>()
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    state("q1")
                    state("q2")
                    accepting("q3")
                }
                action("Mark") { _, args ->
                    seen.add(args)
                    ea.add(args[0].asState(), CharSymbol('c'), args[1].asState())
                }
                on("q0").on('a').to("q1", post = ref("Mark", "q2", "q3", 'x'))
                on("q1").on('b').to("q2")
                main("M")
            }
        val aa = specToAutomaton(spec)
        val input = { chars: String -> chars.map { CharSymbol(it) } }

        assertTrue(aa.recognize(input("abc")))
        assertEquals(1, seen.size)
        assertEquals(3, seen[0].size)
        assertTrue(seen[0][0] is State)
        assertEquals("q2", seen[0][0].toString())
        assertTrue(seen[0][1] is State)
        assertEquals("q3", seen[0][1].toString())
        assertTrue(seen[0][2] is CharSymbol)
    }

    @Test
    fun `dsl calls with a char argument resolve to a symbol edge`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                on("q0").on('a').to("q1")
                main("M")
            }
        val edge = spec.transitions.single()
        val symbol = (edge.transition as Transition.SymbolTransition).symbol
        assertEquals(CharSymbol::class, symbol::class)
        assertEquals("a", symbol.toString())
    }

    @Test
    fun `fresh states restart at zero for each recognition run`() {
        val names = mutableListOf<String>()
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    state("q1")
                }
                action("Mark") { _, _ ->
                    names.add(freshState().toString())
                    names.add(freshState().toString())
                }
                on("q0").on('a').to("q1", post = "Mark")
                on("q1").on('a').to("q1", post = "Mark")
                main("M")
            }
        val aa = specToAutomaton(spec)
        val input = listOf(CharSymbol('a'), CharSymbol('a'))

        aa.recognize(input)
        aa.recognize(input)

        // Each 'a' consumption fires the post action, which allocates two
        // fresh states; the counter restarts on every run.
        assertEquals(listOf("g0", "g1", "g2", "g3", "g0", "g1", "g2", "g3"), names)
    }

    @Test
    fun `duplicate action names are rejected`() {
        val builder = AutomatonBuilder()
        builder.action("A") { _, _ -> }
        assertThrows<IllegalArgumentException> { builder.action("A") { _, _ -> } }
    }

    @Test
    fun `a state name outside the automaton is rejected as an action argument`() {
        val error =
            assertThrows<IllegalArgumentException> {
                automaton {
                    submachine("M") {
                        initial("q0")
                        state("q1")
                        accepting("q2")
                    }
                    action("AddC") { _, args ->
                        ea.add(state("q1"), CharSymbol('c'), args[0].asState())
                    }
                    on("q0").on('a').to("q1", post = ref("AddC", "qTYPO"))
                    on("q1").on('b').to("q2")
                    main("M")
                }
            }
        assertTrue(error.message!!.contains("qTYPO"))
        assertTrue(error.message!!.contains("not part of the automaton"))
        assertTrue(error.message!!.contains("actionRef"))
    }

    @Test
    fun `action arguments may name edge endpoints and declared states`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    state("q1")
                    state("qX")
                    accepting("q2")
                }
                action("AddC") { _, args ->
                    ea.add(state("q1"), CharSymbol('c'), args[0].asState())
                }
                // q2 is declared in the submachine, qX is an edge endpoint.
                on("q0").on('a').to("q1", post = ref("AddC", "q2"))
                on("q1").on('b').to("qX")
                main("M")
            }
        assertEquals(2, spec.transitions.size)
    }

    @Test
    fun `an arity mismatch is rejected at build time`() {
        val error =
            assertThrows<IllegalArgumentException> {
                automaton {
                    submachine("M") {
                        initial("q0")
                        state("q1")
                        accepting("q2")
                    }
                    action("A", arity = 2) { _, _ -> }
                    on("q0").on('a').to("q1", post = ref("A", "q1"))
                    main("M")
                }
            }
        assertTrue(error.message!!.contains("expects 2"))
        assertTrue(error.message!!.contains("binds 1"))
    }

    @Test
    fun `an arity match is accepted`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    state("q1")
                    accepting("q2")
                }
                action("A", arity = 2) { _, _ -> }
                on("q0").on('a').to("q1", post = ref("A", "q1", "q2"))
                main("M")
            }
        assertEquals(1, spec.transitions.size)
    }

    @Test
    fun `a negative arity is rejected`() {
        val builder = AutomatonBuilder()
        assertThrows<IllegalArgumentException> { builder.action("A", arity = -1) { _, _ -> } }
    }

    @Test
    fun `an action without references is accepted by the builder`() {
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                action("Unused", arity = 2) { _, _ -> }
                on("q0").on('a').to("q1")
                main("M")
            }
        assertEquals(1, spec.transitions.size)
        assertEquals(1, spec.actions.size)
    }

    @Test
    fun `a filter reference is rejected as an edge binding`() {
        // An edge fires one concrete action: a filter has no place there.
        assertThrows<IllegalArgumentException> {
            automaton {
                submachine("M") {
                    initial("q0")
                    accepting("q1")
                }
                on("q0").on('a').to("q1", post = ActionRef.Named("A"))
                main("M")
            }
        }
    }

    @Test
    fun `fresh states skip names already taken by the automaton`() {
        val names = mutableListOf<String>()
        val spec =
            automaton {
                submachine("M") {
                    initial("q0")
                    state("g0")
                    accepting("q1")
                }
                action("Mark") { _, _ ->
                    names.add(freshState().toString())
                }
                on("q0").on('a').to("q1", post = "Mark")
                main("M")
            }
        val aa = specToAutomaton(spec)
        aa.recognize(listOf(CharSymbol('a')))

        assertEquals(listOf("g1"), names)
    }

    @Test
    fun `ref keeps typed state symbol and payload arguments`() {
        val builder = AutomatonBuilder()
        val state = builder.state("q0")
        val symbol = CharSymbol('x')
        val reference = builder.ref("A", state, symbol, 42)

        assertEquals("A", reference.name)
        assertEquals(3, reference.args.size)
        assertSame(state, reference.args[0])
        assertSame(symbol, reference.args[1])
        assertEquals(42, reference.args[2])
    }

    @Test
    fun `local ref passes arguments through unchanged`() {
        val context =
            DslActionContext(ElementaryActions(TransitionMapping()), emptyMap()) { prefix ->
                object : State() {
                    override fun toString(): String = prefix
                }
            }
        val reference = context.ref("A", 42)
        assertEquals("A", reference.name)
        assertEquals(listOf<Any>(42), reference.args)
    }
}
