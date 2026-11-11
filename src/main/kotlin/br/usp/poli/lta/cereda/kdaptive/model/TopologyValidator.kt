// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

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
 *   return would switch the running submachine.
 */
object TopologyValidator {

    /**
     * Checks the topology of an automaton.
     *
     * @param edges the edges of the automaton
     * @param submachines the submachines of the automaton
     * @param mainSubmachine the name of the main submachine, or null when none
     *   was declared
     * @throws IllegalArgumentException if the topology breaks one of the rules
     */
    fun validate(edges: Collection<TransitionEdge>, submachines: Collection<Submachine>, mainSubmachine: String?) {
        validateMain(submachines, mainSubmachine)
        validateStates(submachines)
        validateTransitions(edges, submachines)
    }

    /**
     * Checks that a main submachine is declared and exists: recognition starts
     * there, so a missing one is a modeling mistake, not a runtime detail.
     */
    private fun validateMain(submachines: Collection<Submachine>, mainSubmachine: String?) {
        val mainName =
            requireNotNull(mainSubmachine) {
                "The automaton must declare a main submachine, for example main(\"M\")."
            }
        val names = submachines.map { it.name }.toSet()
        require(mainName in names) {
            "Main submachine '$mainName' is not declared. " +
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
        edges.forEach { edge ->
            requireConcrete(edge.source)
            requireConcrete(edge.target)
            val sourceOwners = ownersOf(edge.source, submachines)
            val targetOwners = ownersOf(edge.target, submachines)
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
     * Checks that [state] is a concrete state.
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

    /** Returns the names of the submachines owning [state]. */
    private fun ownersOf(state: State, submachines: Collection<Submachine>): Set<String> =
        submachines.filter { state in it.states }.map { it.name }.toSet()

    /** Builds the error message for a state owned by no submachine. */
    private fun unownedStateMessage(state: State): String =
        "State '$state' is not part of any submachine. Declare it inside a submachine block, " +
            "for example submachine(\"M\") { state(\"$state\") }."
}
