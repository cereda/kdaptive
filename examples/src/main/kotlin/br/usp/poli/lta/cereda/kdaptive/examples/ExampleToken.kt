// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

/** A simplified lexical token: an identifier and a value. */
data class ExampleToken(var id: String? = null, var value: String? = null) {
    override fun toString(): String = "($id, $value)"
}
