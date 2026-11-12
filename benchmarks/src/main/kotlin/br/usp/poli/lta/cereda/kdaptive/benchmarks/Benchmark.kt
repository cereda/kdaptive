// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.benchmarks

import br.usp.poli.lta.cereda.kdaptive.execution.AdaptiveAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.Symbol
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * The benchmarks of the engine.
 *
 * The runner measures three scenarios, each at several sizes, and reports the
 * cost *per unit of work*. The ratio between two consecutive sizes is what
 * tells a linear engine from a quadratic one: it stays near one when the
 * engine scales, and doubles when it does not.
 *
 * Run it with:
 * ```
 * ./gradlew :benchmarks:run
 * ```
 *
 * The numbers are wall-clock times of a warmed-up JVM, and they are only
 * meaningful compared to one another: they say how the engine scales, not
 * how fast it is on a given machine.
 */

/** How many times each measurement is repeated; the best one is kept. */
private const val REPETITIONS = 5

/** How many times a scenario runs before the measurements start. */
private const val WARMUPS = 5

/** The sizes every scenario is measured at; each one doubles the previous. */
private val SIZES = List(SIZE_COUNT) { SMALLEST_SIZE shl it }

/** The smallest size a scenario is measured at. */
private const val SMALLEST_SIZE = 1_000

/** The number of sizes a scenario is measured at. */
private const val SIZE_COUNT = 4

/** The width of the rules framing the table. */
private const val RULE_WIDTH = 72

/** The number of decimals every measurement is written with. */
private const val DECIMALS = 2

/** The factor a number is multiplied by before it is rounded to its decimals. */
private const val HUNDRED = 100L

/** The factor the nanoseconds are divided by to become milliseconds. */
private const val NANOS_PER_MILLI = 1_000_000.0

/**
 * Runs every scenario and prints the table of measurements.
 *
 * @param args ignored: the runner takes no argument
 */
fun main(args: Array<String>) {
    println("kdaptive benchmarks")
    println("=".repeat(RULE_WIDTH))

    println()
    println("A long deterministic run: the cost of one step")
    println("-".repeat(RULE_WIDTH))
    println("steps\twall (ms)\tns per step\tratio")
    scaling { length -> ChainAutomaton(length) }

    println()
    println("A fan-out: the cost of one fork")
    println("-".repeat(RULE_WIDTH))
    println("forks\twall (ms)\tus per fork\tratio")
    scaling { branches -> ForkAutomaton(branches) }

    println()
    println("A growing topology: the cost of one step of a path that mutates it")
    println("-".repeat(RULE_WIDTH))
    println("steps\twall (ms)\tns per step\tratio")
    scaling { length -> MutatingAutomaton(length) }
}

/**
 * Runs a scenario at several sizes and prints one row per size.
 *
 * The ratio between two consecutive rows is the cost of the last unit of
 * work divided by the cost of the previous one. A linear engine keeps it
 * near one; a quadratic one doubles it at every step.
 *
 * @param sizes the sizes to measure
 * @param build the automaton of a given size
 */
private fun scaling(sizes: List<Int> = SIZES, build: (Int) -> AdaptiveAutomaton) {
    // The JVM compiles the code of the scenario while it runs for the first
    // time, and the first measurement would carry the cost of the
    // compilation. The scenario therefore runs at its largest size before
    // any measurement starts.
    val largest = build(sizes.last())
    largest.limits = benchmarkLimits()
    repeat(WARMUPS) { largest.recognize(inputOf(largest)) }

    var previous = 0.0
    for (size in sizes) {
        val automaton = build(size)
        automaton.limits = benchmarkLimits()
        val input = inputOf(automaton)
        val elapsed = measure(automaton, input)
        val perUnit = elapsed.toDouble() / size
        val ratio = if (previous == 0.0) 0.0 else perUnit / previous
        println(
            "$size\t${format(elapsed / NANOS_PER_MILLI)}\t" +
                "${format(perUnit)}\t${format(ratio)}",
        )
        previous = perUnit
    }
}

/**
 * Formats a number the same way everywhere.
 *
 * The locale of the machine must not change the shape of the table: the
 * numbers are read by people, and a decimal comma would make the columns
 * disagree with one another. The formatter therefore writes the digits
 * itself, instead of asking the platform for a rendering.
 *
 * @param value the number to format
 * @return the number, with a dot as its decimal separator
 */
private fun format(value: Double): String {
    val scaled = (value * HUNDRED).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val digits = abs(scaled)
    val whole = digits / HUNDRED
    val fraction = (digits % HUNDRED).toString().padStart(DECIMALS, '0')
    return "$sign$whole.$fraction"
}

/**
 * Measures how long [automaton] takes to recognise [input].
 *
 * The scenario runs a few times before the measurement starts, so that the
 * JIT has compiled the paths the measurement exercises. The best of the
 * measurements is kept: the others carry the noise of the garbage collector
 * and of the scheduler.
 *
 * @param automaton the automaton to run
 * @param input the input to recognise
 * @return the best wall-clock time, in nanoseconds
 */
private fun measure(automaton: AdaptiveAutomaton, input: List<Symbol>): Long {
    repeat(WARMUPS) { automaton.recognize(input) }
    var best = Long.MAX_VALUE
    repeat(REPETITIONS) {
        val start = System.nanoTime()
        automaton.recognize(input)
        best = minOf(best, System.nanoTime() - start)
    }
    return best
}

/**
 * The input that exercises [automaton].
 *
 * @param automaton the automaton to run
 * @return the input to recognise
 */
private fun inputOf(automaton: AdaptiveAutomaton): List<Symbol> = when (automaton) {
    is ChainAutomaton -> automaton.input()
    is MutatingAutomaton -> automaton.input()
    is ForkAutomaton -> automaton.input()
    else -> error("The benchmark does not know the input of ${automaton::class.simpleName}.")
}
