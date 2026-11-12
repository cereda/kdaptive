// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * Stack data structure used by the adaptive automaton. Only the top element
 * can be manipulated.
 */
public class Stack {
    private val entries = ArrayDeque<State>()

    /**
     * The elements of the stack, in top-to-bottom order.
     *
     * The list is cached: the engine reads it on every step of a path, and
     * the stack changes far less often than it is read.
     */
    private var elementsCache: List<State>? = null

    /**
     * The cached hash of [elements].
     *
     * The hash of a list costs one pass over it, and the engine hashes the
     * call stack on every step of a path. The hash is therefore computed once
     * and kept until the stack changes.
     */
    private var hashCache: Int? = null

    /**
     * A hash of the elements of this stack.
     *
     * The hash describes *what* the stack holds, not which states it holds:
     * two stacks with the same elements share it, whoever built them.
     */
    internal val stackHash: Int
        get() {
            hashCache?.let { return it }
            val computed = elements.hashCode()
            hashCache = computed
            return computed
        }

    /** Pushes [entry] onto the top of the stack. */
    public fun push(entry: State) {
        entries.addFirst(entry)
        elementsCache = null
        hashCache = null
    }

    /**
     * Pops and returns the top element of the stack.
     *
     * @throws IllegalStateException if the stack is empty.
     */
    public fun pop(): State {
        check(entries.isNotEmpty()) { "Stack is empty." }
        elementsCache = null
        hashCache = null
        return entries.removeFirst()
    }

    /**
     * Returns the top element of the stack without removing it.
     *
     * @throws IllegalStateException if the stack is empty.
     */
    public fun peek(): State {
        check(entries.isNotEmpty()) { "Stack is empty." }
        return entries.first()
    }

    /** True if this stack contains no elements. */
    public val isEmpty: Boolean get() = entries.isEmpty()

    /** Removes all elements from the stack. */
    public fun clear() {
        entries.clear()
        elementsCache = null
        hashCache = null
    }

    /** Creates a copy of this stack with the same top-to-bottom order. */
    public fun copy(): Stack {
        val c = Stack()
        c.entries.addAll(entries)
        return c
    }

    /** The elements of the stack in top-to-bottom order. */
    public val elements: List<State>
        get() {
            elementsCache?.let { return it }
            val snapshot = entries.toList()
            elementsCache = snapshot
            return snapshot
        }

    /** Returns a human-readable representation of this stack. */
    override fun toString(): String = if (isEmpty) {
        "Stack: {}"
    } else {
        "Stack: { ${elements.joinToString(", ")}, top=${entries.first()} }"
    }
}
