// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * Stack data structure used by the adaptive automaton. Only the top element
 * can be manipulated.
 */
class Stack {
    private val entries = ArrayDeque<State>()

    /** Pushes [entry] onto the top of the stack. */
    fun push(entry: State) {
        entries.addFirst(entry)
    }

    /**
     * Pops and returns the top element of the stack.
     *
     * @throws IllegalStateException if the stack is empty.
     */
    fun pop(): State {
        check(entries.isNotEmpty()) { "Stack is empty." }
        return entries.removeFirst()
    }

    /**
     * Returns the top element of the stack without removing it.
     *
     * @throws IllegalStateException if the stack is empty.
     */
    fun peek(): State {
        check(entries.isNotEmpty()) { "Stack is empty." }
        return entries.first()
    }

    /** True if this stack contains no elements. */
    val isEmpty: Boolean get() = entries.isEmpty()

    /** Removes all elements from the stack. */
    fun clear() {
        entries.clear()
    }

    /** Creates a copy of this stack with the same top-to-bottom order. */
    fun copy(): Stack {
        val c = Stack()
        for (e in entries.toList().reversed()) {
            c.push(e)
        }
        return c
    }

    /** The elements of the stack in top-to-bottom order. */
    val elements: List<State> get() = entries.toList()

    /** Returns a human-readable representation of this stack. */
    override fun toString(): String = if (isEmpty) {
        "Stack: {}"
    } else {
        "Stack: { ${elements.joinToString(", ")}, top=${entries.first()} }"
    }
}
