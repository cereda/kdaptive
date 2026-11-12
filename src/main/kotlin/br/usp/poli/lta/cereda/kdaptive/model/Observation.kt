// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * A fact about the execution of an adaptive automaton.
 *
 * Observations are the extension point of the instrumentation facility. The
 * engine reports what it does on its own channel (see
 * [br.usp.poli.lta.cereda.kdaptive.model.MappingOperation]), while
 * instrumentation-aware action code may publish facts of its own through
 * [br.usp.poli.lta.cereda.kdaptive.model.TransitionMapping.observe].
 *
 * Consumers receive observations through
 * [br.usp.poli.lta.cereda.kdaptive.execution.ExecutionVisitor.onObservation] and
 * decide what they mean: the library never interprets an observation, and never
 * turns one into a metric. Implementations should therefore be immutable value
 * types carrying only the data the fact is about.
 *
 * Example:
 * ```
 * data class DomainSize(val states: Int) : Observation
 * ```
 */
public interface Observation
