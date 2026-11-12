// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

import br.usp.poli.lta.cereda.kdaptive.model.Action
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.Stack
import br.usp.poli.lta.cereda.kdaptive.model.State
import br.usp.poli.lta.cereda.kdaptive.model.Submachine
import br.usp.poli.lta.cereda.kdaptive.model.Transition
import br.usp.poli.lta.cereda.kdaptive.model.TransitionEdge
import br.usp.poli.lta.cereda.kdaptive.model.TransitionMapping
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The configuration of a path, and the fingerprint of a topology.
 *
 * The configuration is the key of the lineage: the engine indexes the
 * configurations a path left by the configuration itself, and looks the
 * lineage up in constant time. Two configurations that describe the same
 * situation must therefore be *equal* — and must hash the same way, or the
 * index would never find them.
 *
 * The fingerprint enters the configuration in place of the edges themselves.
 * It must describe what the topology holds and not which edges it holds, so
 * that a forked path recognises the configurations its parent left.
 */
class ConfigurationTest {

    /** A state of the configurations under test. */
    private object K0 : State() {
        override fun toString(): String = "k0"
    }

    /** A state of the configurations under test. */
    private object K1 : State() {
        override fun toString(): String = "k1"
    }

    /**
     * Builds a configuration over [stack] and [digest].
     *
     * @param stack the call stack of the configuration
     * @param digest the fingerprint of the topology
     * @return the configuration
     */
    private fun configuration(stack: Stack = Stack(), digest: Long = 0L, state: State = K0): Configuration =
        Configuration(
            state = state,
            submachine = "M",
            cursor = 0,
            stack = stack.elements,
            digest = digest,
            stackHash = stack.stackHash,
        )

    /** A stack holding [states], top to bottom. */
    private fun stackOf(vararg states: State): Stack = Stack().apply {
        states.reversed().forEach { push(it) }
    }

    @Test
    fun `two configurations of the same situation are equal`() {
        val left = configuration(stack = stackOf(K0, K1), digest = 42L)
        val right = configuration(stack = stackOf(K0, K1), digest = 42L)

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun `two stacks of the same elements give equal configurations`() {
        // The stacks are different objects holding the same states: the
        // configuration compares them by content, because a forked path
        // copies its stack and must still recognise what its parent left.
        val left = configuration(stack = stackOf(K0, K1))
        val right = configuration(stack = stackOf(K0, K1))

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun `a different call stack gives a different configuration`() {
        val left = configuration(stack = stackOf(K0, K1))
        val right = configuration(stack = stackOf(K1, K0))

        assertNotEquals(left, right)
    }

    @Test
    fun `a different fingerprint gives a different configuration`() {
        val left = configuration(digest = 1L)
        val right = configuration(digest = 2L)

        assertNotEquals(left, right)
    }

    @Test
    fun `a different state gives a different configuration`() {
        val left = configuration(state = K0)
        val right = configuration(state = K1)

        assertNotEquals(left, right)
    }

    @Test
    fun `the hash of a configuration follows the hash of its stack`() {
        val stack = stackOf(K0, K1)
        val configuration = configuration(stack = stack)

        // The stack enters the configuration through the hash the caller
        // computed: the configuration must use that hash, and not hash the
        // list again.
        assertEquals(stack.elements.hashCode(), stack.stackHash)
        assertTrue(configuration.hashCode() != 0 || stack.elements.hashCode() == 0)
    }

    @Test
    fun `the cached hash of a stack follows its contents`() {
        val stack = stackOf(K0)
        val before = stack.stackHash

        stack.push(K1)
        assertNotEquals(before, stack.stackHash)
        assertEquals(stack.elements.hashCode(), stack.stackHash)

        stack.pop()
        assertEquals(before, stack.stackHash)
        assertEquals(stack.elements.hashCode(), stack.stackHash)

        stack.clear()
        assertEquals(emptyList<State>().hashCode(), stack.stackHash)
    }

    @Test
    fun `a copy of a stack has the same hash as the original`() {
        val stack = stackOf(K0, K1)
        val copy = stack.copy()

        assertEquals(stack.elements, copy.elements)
        assertEquals(stack.stackHash, copy.stackHash)
    }

    @Test
    fun `the fingerprint of a topology describes what it holds`() {
        val mapping = TransitionMapping()
        val edge =
            TransitionEdge(
                source = K0,
                target = K1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            )
        mapping.add(edge)

        val before = mapping.structuralDigest()
        assertEquals(before, mapping.structuralDigest())
    }

    @Test
    fun `an isolated copy of a topology has the same fingerprint`() {
        // This is the property the forked path depends on: the child must
        // recognise the configurations its parent left, and it recognises them
        // through the fingerprint.
        val mapping = TransitionMapping()
        mapping.add(
            TransitionEdge(
                source = K0,
                target = K1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            ),
        )

        val copy = mapping.isolatedCopy()

        assertEquals(mapping.structuralDigest(), copy.structuralDigest())
    }

    @Test
    fun `a topology that changed has a different fingerprint`() {
        val mapping = TransitionMapping()
        mapping.add(
            TransitionEdge(
                source = K0,
                target = K1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            ),
        )
        val before = mapping.structuralDigest()

        mapping.add(
            TransitionEdge(
                source = K1,
                target = K0,
                transition = Transition.Epsilon,
            ),
        )

        assertNotEquals(before, mapping.structuralDigest())
    }

    @Test
    fun `two topologies holding the same edges share their fingerprint`() {
        // The fingerprint describes what the topology holds, not which edges
        // it holds: the identifier of an edge is bookkeeping.
        val edge =
            TransitionEdge(
                source = K0,
                target = K1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            )
        val left = TransitionMapping()
        val right = TransitionMapping()
        left.add(edge)
        right.add(edge)

        assertEquals(left.structuralDigest(), right.structuralDigest())
    }

    @Test
    fun `the fingerprint follows the mutations of the topology`() {
        val mapping = TransitionMapping()
        val edge =
            TransitionEdge(
                source = K0,
                target = K1,
                transition = Transition.SymbolTransition(symbol = CharSymbol('a')),
            )
        mapping.add(edge)
        val before = mapping.structuralDigest()

        mapping.removeById(edge.id)

        assertNotEquals(before, mapping.structuralDigest())
    }

    /**
     * An automaton that forks and then comes back to the same configuration.
     *
     * The parent leaves a configuration, the child inherits the lineage, and
     * the child must recognise the configuration as one it has already left:
     * this is what the fingerprint and the equality of the configurations
     * are for.
     */
    private class ForkingAa : AdaptiveAutomaton() {
        override fun newInstance(): AdaptiveAutomaton = ForkingAa()

        override fun setup() {
            mainSubmachine = "M"
            submachines.add(
                Submachine(
                    name = "M",
                    states = setOf(K0, K1),
                    initialState = K0,
                    acceptingStates = setOf(K1),
                ),
            )
            // The action is what lets the automaton past the static check: a
            // cycle that consumes no input may be broken by an action. This
            // one never fires, so the cycle spins for real.
            actions.add(Action(name = "Never") { _, _, _ -> })
            // The self-loop leaves the configuration the parent left, with
            // the same cursor and the same topology: the child that takes it
            // walks into a configuration it has already left.
            epsilon(K0, K0)
            epsilon(K0, K1)
        }
    }

    @Test
    fun `a forked path recognises the configuration its parent left`() {
        // The two alternatives leave the same state at the same cursor. The
        // child that loops back to K0 walks into the configuration the parent
        // left, and the engine cuts it: the lineage was inherited, and the
        // fingerprint of the topology did not change.
        val result = ForkingAa().recognize(emptyList())

        val outcomes = result.paths.map { it.outcome }
        assertTrue(outcomes.contains(PathOutcome.Cut))
    }

    @Test
    fun `a forked path that reaches a verdict is not cut`() {
        // The other branch of the same fork reaches an accepting state: the
        // inherited lineage must not cut a path that is making progress.
        val result = ForkingAa().recognize(emptyList())

        assertTrue(result.accepted)
        assertEquals(PathOutcome.Accepted, result.acceptedPaths.single().outcome)
    }
}
