// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.model.State

/**
 * Example state implementation with value-based equality (by name).
 */
class ExampleState(val name: String) : State() {
    override fun equals(other: Any?): Boolean = other is ExampleState && name == other.name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name
}
