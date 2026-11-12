// SPDX-License-Identifier: GPL-3.0-or-later
package br.usp.poli.lta.cereda.kdaptive.model

/**
 * Handler executed when an adaptive action fires: it receives the transition
 * mapping (which may be mutated by elementary actions), the current transition
 * as reference, and the action parameters.
 */
public typealias ActionHandler = (transitions: TransitionMapping, transition: Transition, parameters: List<Any>) -> Unit

/**
 * An action of the adaptive automaton, executed when a transition references
 * it by name.
 *
 * Two actions are equal when they have the same name and the same handler.
 * The [id] is bookkeeping, not identity: it is not part of equality, so
 * registering the same action twice does not produce two different actions.
 * The set of actions identifies an action by its name anyway, and a name
 * registered twice replaces the previous handler.
 *
 * @property name the name of this action
 * @property handler the handler function executed when this action fires
 */
public data class Action(
    /** The name of this action. */
    val name: String,
    /** The handler function executed when this action fires. */
    val handler: ActionHandler,
) {
    /**
     * The unique identifier for this action.
     *
     * The identifier is deliberately outside the equality of the action: it
     * tells this particular action apart from the others, but it does not
     * make two otherwise identical actions different.
     */
    val id: Int = Identifiers.nextActionId()

    /** Returns a human-readable representation of this action. */
    override fun toString(): String = "Action(id=$id, name=$name)"
}

/**
 * A reference to an action, serving two roles.
 *
 * As a **binding**, it is the concrete action an edge fires: [Bound].
 *
 * As a **filter**, it constrains an action slot (the prior or the post action
 * of an edge) in a query or a removal:
 *
 * - [Free]: the slot is not constrained — the edge may or may not carry an
 *   action there;
 * - [Any]: the slot must carry some action, whatever its name and arguments;
 * - [None]: the slot must carry no action at all;
 * - [Named]: the slot must carry the named action, its arguments unconstrained;
 * - [Bound]: the slot must carry the named action, called with exactly the
 *   listed arguments.
 *
 * The two roles never disagree: [Bound] means the same thing on an edge and in
 * a filter. In particular, there is no spelling whose arity behaviour has to
 * be inferred — [Named] says "arguments unconstrained", [Bound] says "exactly
 * these arguments".
 *
 * A [Bound] may carry [Free] among its arguments, marking a position that is
 * unconstrained while the arity itself stays fixed. This mirrors the free
 * variables of the theory: the pattern constrains the arity, but leaves that
 * particular argument open.
 */
public sealed interface ActionRef {
    /** The slot is unconstrained: the edge may or may not carry an action there. */
    public data object Free : ActionRef

    /** The slot must carry some action — any name, any arguments. */
    public data object Any : ActionRef

    /** The slot must carry no action at all. */
    public data object None : ActionRef

    /** The slot must carry the named action; its arguments are not constrained. */
    public data class Named(
        /** The name of the action the slot must carry. */
        val name: String,
    ) : ActionRef

    /**
     * A concrete reference to the named action, called with exactly these
     * arguments.
     *
     * @property name the name of the action
     * @property args the arguments the action is called with; an entry equal
     *   to [Free] marks an unconstrained position
     */
    public data class Bound(val name: String, val args: List<kotlin.Any> = emptyList()) : ActionRef
}
