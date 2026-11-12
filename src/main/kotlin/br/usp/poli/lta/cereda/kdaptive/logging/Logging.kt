// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Idiomatic Kotlin logging extension backed by SLF4J.
 *
 * Usage:
 *   logger.debug("message")   // eager; fine for simple messages
 *   logger.info("value = $x")
 *
 * Replaces the original log4j2/Java logging with a thin SLF4J wrapper so the
 * rest of the code is not coupled to any specific logging backend.
 *
 * The extension is internal: the logging of the library is its own business,
 * and a consumer that wants a logger of its own asks SLF4J for one. What the
 * library logs is not part of the API it promises.
 */
internal val <T : Any> T.logger: Logger
    get() = LoggerFactory.getLogger(this::class.java)
