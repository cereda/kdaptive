// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * Abstract representation of a symbol belonging to the input alphabet of an
 * adaptive automaton.
 *
 * Users typically create concrete symbols as data classes, e.g.:
 *     data class CharSymbol(val value: Char) : Symbol()
 */
abstract class Symbol
