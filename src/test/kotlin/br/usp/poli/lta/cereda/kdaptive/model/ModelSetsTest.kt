// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal object S0 : State()

internal object S1 : State()

internal object S2 : State()

internal data class Sym(val value: Char) : Symbol()

class ModelSetsTest {
    @BeforeEach
    fun resetIds() {
        Identifiers.reset()
    }

    private fun sym(c: Char) = Sym(c)

    @Test
    fun `predicate combinators`() {
        val t1 =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = actionRef("A", S0),
            )
        val t2 =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.Epsilon,
            )
        val t3 =
            TransitionEdge(
                source = S1,
                target = S0,
                transition = Transition.Call(submachine = "M"),
            )

        assertTrue(isEpsilon(t2))
        assertFalse(isEpsilon(t1))
        assertTrue(isSubmachineCall(t3))
        assertFalse(isSubmachineCall(t1))

        val src = sourceState(S0)
        assertTrue(src(t1))
        assertFalse(src(t3))

        val symPred = symbol(sym('a'))
        assertTrue(symPred(t1))
        assertFalse(symPred(t2))

        val combined = allOf(listOf(sourceState(S0), symbol(sym('a'))))
        assertTrue(combined(t1))
        assertFalse(combined(t2))

        val any = anyOf(listOf(isEpsilon, isSubmachineCall))
        assertTrue(any(t2))
        assertTrue(any(t3))
        assertFalse(any(t1))
    }

    @Test
    fun `mapping add and query`() {
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
            )
        val t2 =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.Epsilon,
            )
        mapping.add(t1)
        mapping.add(t2)

        assertEquals(2, mapping.size)
        assertTrue(mapping.contains(t1.id))
        assertFalse(mapping.contains(999))

        val topology = Topology.of(mapping, Submachines())
        assertEquals(listOf(t1), topology.edgesConsuming(sym('a')))
        assertEquals(listOf(t2), topology.epsilonEdges)
        assertEquals(listOf(t1, t2), topology.edgesFrom(S0))
        assertEquals(listOf(t1), topology.matching(TransitionPattern(source = S0, symbol = sym('a'))))
        assertEquals(listOf(t2), topology.epsilonEdgesFrom(S0))
        assertEquals(emptyList<TransitionEdge>(), topology.edgesFrom(S2))
    }

    @Test
    fun `mapping epsilonOrSubmachineFromSourceState`() {
        val mapping = TransitionMapping()
        val eps =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.Epsilon,
            )
        val sub =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.Call(submachine = "M"),
            )
        val sym =
            TransitionEdge(
                source = S1,
                target = S0,
                transition = Transition.SymbolTransition(symbol = Sym('a')),
            )
        mapping.add(eps)
        mapping.add(sub)
        mapping.add(sym)

        val result = Topology.of(mapping, Submachines()).epsilonOrCallEdgesFrom(S0)
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == eps.id })
        assertTrue(result.any { it.id == sub.id })
        assertFalse(result.any { it.id == sym.id })
    }

    @Test
    fun `mapping symbolEpsilonOrSubmachineFromSourceState`() {
        val mapping = TransitionMapping()
        val eps =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.Epsilon,
            )
        val sub =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.Call(submachine = "M"),
            )
        val symA =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.SymbolTransition(symbol = Sym('a')),
            )
        val symB =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.SymbolTransition(symbol = Sym('b')),
            )
        mapping.add(eps)
        mapping.add(sub)
        mapping.add(symA)
        mapping.add(symB)

        val result = Topology.of(mapping, Submachines()).applicableEdgesFrom(S0, Sym('a'))
        assertEquals(3, result.size)
        assertTrue(result.any { it.id == symA.id })
        assertFalse(result.any { it.id == symB.id })
    }

    @Test
    fun `mapping removeFromIdentifier`() {
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
            )
        val t2 =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.Epsilon,
            )
        mapping.add(t1)
        mapping.add(t2)

        mapping.removeFromIdentifier(t1.id)
        assertEquals(1, mapping.size)
        assertFalse(mapping.contains(t1.id))
        assertTrue(mapping.contains(t2.id))
    }

    @Test
    fun `actions fromName and removeByName`() {
        val actions = Actions()
        val a1 = Action(name = "act1", handler = { _, _, _ -> })
        val a2 = Action(name = "act2", handler = { _, _, _ -> })
        actions.add(a1)
        actions.add(a2)

        assertEquals(2, actions.size)
        assertSame(a1, actions.fromName("act1"))
        assertSame(a2, actions.fromName("act2"))

        assertThrows(IllegalStateException::class.java) { actions.fromName("missing") }

        actions.removeByName("act1")
        assertEquals(1, actions.size)
        assertThrows(IllegalStateException::class.java) { actions.fromName("act1") }
    }

    @Test
    fun `submachines fromName and fromState`() {
        val smachines = Submachines()
        val sm = Submachine(name = "M", states = setOf(S0, S1), initialState = S0, acceptingStates = setOf(S1))
        smachines.add(sm)

        assertSame(sm, smachines.fromName("M"))
        assertSame(sm, smachines.fromState(S0))
        assertSame(sm, smachines.fromState(S1))

        assertThrows(IllegalStateException::class.java) { smachines.fromName("missing") }
        assertThrows(IllegalStateException::class.java) { smachines.fromState(S2) }
    }

    @Test
    fun `elementaryActions query binds variables`() {
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
            )
        val t2 =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.SymbolTransition(symbol = sym('b')),
            )
        mapping.add(t1)
        mapping.add(t2)

        val ea = ElementaryActions(mapping, Submachines())

        val result =
            ea.query {
                source = S0
                symbol = sym('a')
                // target omitted → wildcard
            }

        assertEquals(setOf(S1), result.map { it.target }.toSet())
    }

    @Test
    fun `elementaryActions wildcard collects all results`() {
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
            )
        val t2 =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.SymbolTransition(symbol = sym('a')),
            )
        mapping.add(t1)
        mapping.add(t2)

        val ea = ElementaryActions(mapping, Submachines())

        val result =
            ea.query {
                source = S0
                symbol = sym('a')
                // target omitted → wildcard
            }

        assertEquals(setOf(S1, S2), result.map { it.target }.toSet())
    }

    @Test
    fun `elementaryActions epsilon query`() {
        val mapping = TransitionMapping()
        val eps =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.Epsilon,
            )
        mapping.add(eps)

        val ea = ElementaryActions(mapping, Submachines())

        val result =
            ea.query {
                source = S0
                symbol = Symbol.Epsilon
                // target omitted → wildcard
            }

        assertEquals(setOf(S1), result.map { it.target }.toSet())
    }

    @Test
    fun `elementaryActions submachine query`() {
        val mapping = TransitionMapping()
        val call =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.Call(submachine = "M"),
            )
        mapping.add(call)

        val ea = ElementaryActions(mapping, Submachines())

        val result = ea.queryCall(S0, SubmachineRef.Named("M"), State.Any)

        assertEquals(setOf(S1), result.map { it.target }.toSet())
    }

    @Test
    fun `elementaryActions remove`() {
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
            )
        val t2 =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.SymbolTransition(symbol = sym('b')),
            )
        mapping.add(t1)
        mapping.add(t2)

        val ea = ElementaryActions(mapping, Submachines())

        ea.remove {
            source = S0
            symbol = sym('a')
            target = S1
        }

        assertEquals(1, mapping.size)
        assertFalse(mapping.contains(t1.id))
        assertTrue(mapping.contains(t2.id))
    }

    @Test
    fun `elementaryActions remove with prior action`() {
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = actionRef("A", S0),
            )
        val t2 =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.SymbolTransition(symbol = sym('b')),
            )
        mapping.add(t1)
        mapping.add(t2)

        val ea = ElementaryActions(mapping, Submachines())

        ea.remove {
            source = S0
            symbol = sym('a')
            target = S1
            prior = actionRef("A", S0)
        }

        assertEquals(1, mapping.size)
        assertFalse(mapping.contains(t1.id))
        assertTrue(mapping.contains(t2.id))
    }

    @Test
    fun `elementaryActions add with prior action`() {
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = actionRef("A", S0),
            )
        mapping.add(t1)

        val ea = ElementaryActions(mapping, Submachines())

        ea.add(S0, sym('a'), S1, actionRef("A", S0))

        assertEquals(2, mapping.size)
        assertTrue(mapping.contains(t1.id))
    }

    @Test
    fun `mapping source index stays consistent across Mutation and copy`() {
        val q0 = object : State() {
            override fun toString(): String = "q0"
        }
        val q1 = object : State() {
            override fun toString(): String = "q1"
        }
        val r0 = object : State() {
            override fun toString(): String = "r0"
        }
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(source = q0, target = q1, transition = Transition.SymbolTransition(symbol = CharSymbol('a')))
        val t2 = TransitionEdge(source = q0, target = r0, transition = Transition.Epsilon)
        val t3 = TransitionEdge(source = q1, target = q0, transition = Transition.Call(submachine = "M"))
        mapping.add(t1)
        mapping.add(t2)

        // Index built here must see both q0 edges.
        assertEquals(listOf(t1, t2), mapping.edgesFrom(q0))

        mapping.add(t3)
        // Mutation invalidates the index; the rebuilt one must include the new edge.
        assertEquals(listOf(t1, t2), mapping.edgesFrom(q0))
        assertEquals(listOf(t3), mapping.edgesFrom(q1))

        mapping.removeFromIdentifier(t1.id)
        assertEquals(listOf(t2), mapping.edgesFrom(q0))
        assertEquals(emptyList<TransitionEdge>(), mapping.edgesFrom(r0))

        val clone = mapping.copy()
        // The copy holds the same edges, with their own identifiers: the
        // identifier is bookkeeping, not identity.
        assertEquals(
            clone.edgesFrom(q0).map { it.transition },
            mapping.edgesFrom(q0).map { it.transition },
        )
        assertEquals(listOf(t3.transition), clone.edgesFrom(q1).map { it.transition })
        assertEquals(mapping.size, clone.size)
    }

    @Test
    fun `predicate negation and empty combinators`() {
        val eps =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.Epsilon,
            )
        val symEdge =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.SymbolTransition(symbol = sym('a')),
            )

        val nonEpsilon = not(isEpsilon)
        assertTrue(nonEpsilon(symEdge))
        assertFalse(nonEpsilon(eps))

        val emptyAll = allOf(emptyList<TransitionPredicate>())
        val emptyAny = anyOf(emptyList<TransitionPredicate>())
        assertTrue(emptyAll(symEdge))
        assertFalse(emptyAny(symEdge))
    }

    @Test
    fun `elementaryActions wildcard and named submachine queries`() {
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.Call(submachine = "M"),
            )
        val t2 =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.Call(submachine = "N"),
            )
        mapping.add(t1)
        mapping.add(t2)
        val ea = ElementaryActions(mapping, Submachines())

        // Null submachine = wildcard: matches any submachine-call edge.
        val wildcard = ea.queryCall(S0, submachine = SubmachineRef.Any, target = State.Any)
        assertEquals(setOf(S1, S2), wildcard.map { it.target }.toSet())

        val namedM = ea.queryCall(S0, SubmachineRef.Named("M"), State.Any)
        assertEquals(setOf(S1), namedM.map { it.target }.toSet())
    }

    @Test
    fun `elementaryActions post action argument matching`() {
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.Call(submachine = "M"),
                post = actionRef("B", "y"),
            )
        val t2 =
            TransitionEdge(
                source = S0,
                target = S2,
                transition = Transition.Call(submachine = "M"),
                post = actionRef("B", "z"),
            )
        mapping.add(t1)
        mapping.add(t2)
        val ea = ElementaryActions(mapping, Submachines())

        val match = ea.queryCall(S0, SubmachineRef.Named("M"), State.Any, ActionRef.Free, actionRef("B", "y"))
        assertEquals(setOf(S1), match.map { it.target }.toSet())

        val mismatch = ea.queryCall(S0, SubmachineRef.Named("M"), State.Any, ActionRef.Free, actionRef("B", "w"))
        assertTrue(mismatch.isEmpty())

        val arityMismatch =
            ea.queryCall(S0, SubmachineRef.Named("M"), State.Any, ActionRef.Free, actionRef("B", "y", "z"))
        assertTrue(arityMismatch.isEmpty())
    }

    @Test
    fun `elementaryActions prior action argument matching on remove`() {
        val mapping = TransitionMapping()
        val t1 =
            TransitionEdge(
                source = S0,
                target = S1,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = actionRef("A", "x", 1),
            )
        val t2 =
            TransitionEdge(
                source = S1,
                target = S0,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = actionRef("A", "x", 2),
            )
        val t3 =
            TransitionEdge(
                source = S2,
                target = S0,
                transition = Transition.SymbolTransition(symbol = sym('a')),
                prior = actionRef("A", "x"),
            )
        mapping.add(t1)
        mapping.add(t2)
        mapping.add(t3)
        val ea = ElementaryActions(mapping, Submachines())

        // Removing by (source, symbol, target, prior) with exact arguments only
        // removes the edge whose arguments and arity match.
        ea.remove(State.Any, Symbol.Any, State.Any, actionRef("A", "x", 1))
        assertFalse(mapping.contains(t1.id))
        assertTrue(mapping.contains(t2.id))
        assertTrue(mapping.contains(t3.id))

        // An "any action" filter matches every edge carrying a prior action.
        ea.remove(State.Any, Symbol.Any, State.Any, ActionRef.Any)
        assertFalse(mapping.contains(t2.id))
        assertFalse(mapping.contains(t3.id))
    }
}
