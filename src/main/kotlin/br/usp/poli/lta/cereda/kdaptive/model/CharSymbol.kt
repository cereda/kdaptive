// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * A character symbol with value-based equality, useful for alphabets over
 * characters (e.g. {@code 'a', 'b'}).
 *
 * @property value the character this symbol represents
 */
public data class CharSymbol(val value: Char) : Symbol() {
    /** Returns the character value as this symbol's textual representation. */
    override fun toString(): String = value.toString()
}
