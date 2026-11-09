// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.examples

import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol
import br.usp.poli.lta.cereda.kdaptive.model.Symbol

/** Converts a string into a list of [CharSymbol]s. */
fun convert(text: String): List<Symbol> = text.map { CharSymbol(it) }

/** Returns "yes" or "no" for a boolean value. */
fun getAnswer(value: Boolean): String = if (value) "yes" else "no"
