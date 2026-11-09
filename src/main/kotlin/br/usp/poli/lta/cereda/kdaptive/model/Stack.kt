// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * Stack data structure used by the adaptive automaton. Only the top element
 * can be manipulated.
 */
class Stack {
    private val elements = ArrayDeque<State>()

    /** Pushes [entry] onto the top of the stack. */
    fun push(entry: State) {
        elements.addFirst(entry)
    }

    /**
     * Pops and returns the top element of the stack.
     *
     * @throws IllegalStateException if the stack is empty.
     */
    fun pop(): State {
        check(elements.isNotEmpty()) { "Stack is empty." }
        return elements.removeFirst()
    }

    /**
     * Returns the top element of the stack without removing it.
     *
     * @throws IllegalStateException if the stack is empty.
     */
    fun top(): State {
        check(elements.isNotEmpty()) { "Stack is empty." }
        return elements.first()
    }

    /** Returns true if this stack contains no elements. */
    fun isEmpty(): Boolean = elements.isEmpty()

    /** Removes all elements from the stack. */
    fun clear() {
        elements.clear()
    }

    /** Creates a copy of this stack with the same top-to-bottom order. */
    fun copy(): Stack {
        val c = Stack()
        for (e in elements.toList().reversed()) {
            c.push(e)
        }
        return c
    }

    /** Returns the elements in top-to-bottom order (without exposing the deque). */
    fun elementsList(): List<State> = elements.toList()

    /** Returns a human-readable representation of this stack. */
    override fun toString(): String = if (isEmpty()) {
        "Stack: {}"
    } else {
        "Stack: { ${elements.joinToString(", ")}, top=${elements.first()} }"
    }
}
