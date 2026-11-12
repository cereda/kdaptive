// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

import br.usp.poli.lta.cereda.kdaptive.logging.logger

/**
 * The rules every topology must satisfy, whatever built it.
 *
 * The builder DSL and the imperative API describe an automaton in two
 * different ways, but they describe the *same* thing: a set of submachines,
 * a set of edges and a main submachine. The rules that make such a topology
 * executable therefore live here, and both APIs apply them — a topology that
 * the DSL accepts is executable, and a topology the imperative API accepts is
 * executable too.
 *
 * The rules are:
 *
 * - a main submachine is declared, and it exists;
 * - every state of the topology is concrete: a domain names several states at
 *   once and cannot belong to a submachine or endpoint an edge;
 * - every edge endpoint belongs to a submachine, otherwise the executor
 *   cannot tell which submachine is running when the edge is taken;
 * - a call names a declared submachine;
 * - a call returns to a state owned by the calling submachine, otherwise the
 *   return would switch the running submachine;
 * - no reachable cycle consumes no input, unless an adaptive action may break
 *   it.
 */
public object TopologyValidator {

    /**
     * Checks the topology of an automaton.
     *
     * @param topology the topology of the automaton
     * @param mainSubmachine the name of the main submachine
     * @param actionNames the names of the adaptive actions registered in the
     *   automaton; an automaton that registers none can never change its own
     *   topology
     * @throws IllegalArgumentException if the topology breaks one of the rules
     */
    public fun validate(topology: Topology, mainSubmachine: String, actionNames: Collection<String>) {
        validateMain(topology.submachines, mainSubmachine)
        validateStates(topology.submachines)
        validateTransitions(topology.edges, topology.submachines)
        validateCycles(topology, mainSubmachine, actionNames)
    }

    /**
     * Checks that a main submachine is declared and exists: recognition starts
     * there, so a missing one is a modeling mistake, not a runtime detail.
     */
    private fun validateMain(submachines: Collection<Submachine>, mainSubmachine: String) {
        val names = submachines.map { it.name }.toSet()
        require(mainSubmachine in names) {
            "Main submachine '$mainSubmachine' is not declared. " +
                "Declared submachines: ${names.joinToString(", ")}."
        }
    }

    /**
     * Checks that the states of the submachines are concrete.
     *
     * A domain is a filter, not a state: it names several states at once, and
     * the executor would have no way to tell which one is running.
     */
    private fun validateStates(submachines: Collection<Submachine>) {
        submachines.forEach { submachine ->
            submachine.states.forEach { state -> requireConcrete(state) }
            requireConcrete(submachine.initialState)
        }
    }

    /**
     * Checks the shape of every edge.
     *
     * - both endpoints are concrete and belong to a submachine, otherwise the
     *   executor cannot tell which submachine is running when the edge is
     *   taken;
     * - a call names a declared submachine;
     * - a call returns to a state owned by the calling submachine, otherwise
     *   the return would switch the running submachine.
     */
    private fun validateTransitions(edges: Collection<TransitionEdge>, submachines: Collection<Submachine>) {
        val names = submachines.map { it.name }.toSet()
        val owners = ownersByState(submachines)
        edges.forEach { edge ->
            requireConcrete(edge.source)
            requireConcrete(edge.target)
            val sourceOwners = owners[edge.source].orEmpty()
            val targetOwners = owners[edge.target].orEmpty()
            require(sourceOwners.isNotEmpty()) { unownedStateMessage(edge.source) }
            require(targetOwners.isNotEmpty()) { unownedStateMessage(edge.target) }
            val call = edge.transition as? Transition.Call ?: return@forEach
            require(call.submachine in names) {
                "Call to undeclared submachine '${call.submachine}'. " +
                    "Declared submachines: ${names.joinToString(", ")}."
            }
            require(targetOwners.any { it in sourceOwners }) {
                "Call to submachine '${call.submachine}' returns to '${edge.target}', owned by " +
                    "${targetOwners.joinToString(", ")} and not by the calling submachine " +
                    "${sourceOwners.joinToString(", ")}. A call must return to a state of its caller."
            }
        }
    }

    /**
     * Checks that no reachable cycle consumes no input.
     *
     * An epsilon transition and a submachine call consume nothing: a path
     * that follows them never runs out of input and never gets stuck, so a
     * cycle of such moves loops forever. The engine explores every branch, so
     * such a cycle keeps the whole recognition from ever finishing.
     *
     * The cycle is only *fatal* when the automaton registers no adaptive
     * action: nothing may then change the topology, and the loop is certain.
     * When an action is registered, the action may legitimately break the
     * cycle — the documented one-shot idiom removes the very edge that loops
     * — so the engine reports the cycle and cuts the branches that loop.
     */
    private fun validateCycles(topology: Topology, mainSubmachine: String, actionNames: Collection<String>) {
        val byName = topology.submachines.associateBy { it.name }
        val main = byName[mainSubmachine] ?: return
        val moves = mutableMapOf<State, MutableList<State>>()
        val freeMoves = mutableMapOf<State, MutableList<State>>()
        topology.edges.forEach { edge ->
            when (val transition = edge.transition) {
                is Transition.Epsilon -> {
                    moves.add(edge.source, edge.target)
                    freeMoves.add(edge.source, edge.target)
                }

                is Transition.Call -> {
                    val callee = byName[transition.submachine] ?: return@forEach
                    moves.add(edge.source, callee.initialState)
                    freeMoves.add(edge.source, callee.initialState)
                }

                is Transition.SymbolTransition -> {
                    moves.add(edge.source, edge.target)
                }

                is Transition.Return -> {
                    // A return pops the stack: it never closes a cycle.
                }
            }
        }

        // Reachability ignores what the input holds: a state reachable by
        // *some* input is reachable, and a cycle behind it is just as fatal.
        val reachable = mutableSetOf<State>()
        val pending = ArrayDeque<State>()
        pending.add(main.initialState)
        while (pending.isNotEmpty()) {
            val state = pending.removeFirst()
            if (!reachable.add(state)) continue
            moves[state].orEmpty().forEach { pending.add(it) }
        }

        val cycle = findCycle(freeMoves, reachable) ?: return
        val description = cycle.joinToString(" -> ") { it.toString() }
        if (actionNames.isEmpty()) {
            throw IllegalArgumentException(
                "The automaton has a reachable cycle that consumes no input: $description. " +
                    "Such a cycle never reaches a verdict, and an automaton without adaptive actions " +
                    "can never break it. Remove the cycle, or register an action that changes the topology.",
            )
        }
        logger.warn(
            "The automaton has a reachable cycle that consumes no input: $description. " +
                "An adaptive action may break it; if it does not, the engine cuts the branches that loop.",
        )
    }

    /**
     * Finds a cycle of the graph [adjacency] restricted to [reachable].
     *
     * @param adjacency the successors of every state
     * @param reachable the states the search may visit
     * @return the states of the cycle, or null when the graph has none
     */
    private fun findCycle(adjacency: Map<State, List<State>>, reachable: Set<State>): List<State>? {
        val colors = mutableMapOf<State, Int>()
        val parents = mutableMapOf<State, State>()
        for (start in reachable) {
            if (colors.containsKey(start)) continue
            colors[start] = VISITING
            val stack = ArrayDeque<Pair<State, Iterator<State>>>()
            stack.addLast(start to successors(adjacency, start, reachable))
            while (stack.isNotEmpty()) {
                val (node, remaining) = stack.last()
                if (!remaining.hasNext()) {
                    colors[node] = VISITED
                    stack.removeLast()
                    continue
                }
                val next = remaining.next()
                when (colors[next] ?: UNVISITED) {
                    UNVISITED -> {
                        colors[next] = VISITING
                        parents[next] = node
                        stack.addLast(next to successors(adjacency, next, reachable))
                    }

                    VISITING -> return cycleOf(next, node, parents)

                    else -> Unit
                }
            }
        }
        return null
    }

    /** Returns the successors of [state] the search may visit. */
    private fun successors(adjacency: Map<State, List<State>>, state: State, reachable: Set<State>): Iterator<State> =
        adjacency[state].orEmpty().filter { it in reachable }.iterator()

    /** Rebuilds the cycle closed by the edge from [node] to [ancestor]. */
    private fun cycleOf(ancestor: State, node: State, parents: Map<State, State>): List<State> {
        val cycle = mutableListOf(ancestor)
        var current = node
        while (current != ancestor) {
            cycle.add(current)
            current = parents[current] ?: return cycle
        }
        cycle.add(ancestor)
        return cycle
    }

    /** Checks that [state] is a concrete state.
     *
     * A domain is a filter: it names several states at once, and no edge can
     * start or end at all of them.
     */
    private fun requireConcrete(state: State) {
        require(state !is StateDomain) {
            "State '$state' is a domain, not a concrete state. " +
                "A domain names several states at once and cannot belong to the topology."
        }
    }

    /**
     * Returns the names of the submachines owning each state.
     *
     * The index is built once and answers the question of the owner of a
     * state in constant time. Asking the question of the collection instead
     * would scan every submachine, for every endpoint of every edge, and the
     * cost of the validation would grow with the square of the topology.
     *
     * The names of a state keep the order the submachines were declared in:
     * the error messages of the validator list them.
     *
     * @param submachines the submachines of the topology
     * @return the names of the submachines owning each state
     */
    private fun ownersByState(submachines: Collection<Submachine>): Map<State, Set<String>> {
        val owners = LinkedHashMap<State, MutableSet<String>>()
        for (submachine in submachines) {
            for (state in submachine.states) {
                owners.getOrPut(state) { LinkedHashSet() }.add(submachine.name)
            }
        }
        return owners
    }

    /** Builds the error message for a state owned by no submachine. */
    private fun unownedStateMessage(state: State): String =
        "State '$state' is not part of any submachine. Declare it inside a submachine block, " +
            "for example submachine(\"M\") { state(\"$state\") }."

    /** Adds [target] to the successors of [source]. */
    private fun MutableMap<State, MutableList<State>>.add(source: State, target: State) {
        getOrPut(source) { mutableListOf() }.add(target)
    }

    /** The state has not been visited yet. */
    private const val UNVISITED = 0

    /** The state is on the path currently being explored. */
    private const val VISITING = 1

    /** The state has been fully explored. */
    private const val VISITED = 2
}
