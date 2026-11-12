// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.execution

/**
 * The failure of a recognition.
 *
 * The engine could not decide anything about the input: a path died before
 * it reached a verdict, or a structural bound tripped. The exception says
 * *which* path died and *why*, so that a consumer may tell one failure from
 * another without reading the message.
 *
 * A failure is not a rejection. A rejection is a verdict the engine reached;
 * a failure is the absence of a verdict, and the two must never be confused.
 *
 * The exception extends [IllegalStateException] because that is what the
 * engine threw before it had a type of its own: a consumer that catches the
 * broad exception keeps working, and a consumer that cares may catch this
 * one instead.
 *
 * @property pathId the identifier of the path that failed
 * @property reason why the path failed
 */
public class RecognitionFailedException(
    /** The identifier of the path that failed. */
    public val pathId: Int,
    /** Why the path failed. */
    public val reason: String,
    cause: Throwable? = null,
) : IllegalStateException("Recognition path $pathId failed: $reason", cause)
