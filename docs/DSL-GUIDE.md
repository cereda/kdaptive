<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# DSL guide: building adaptive automata

This guide covers everything you can use to *describe* an adaptive automaton with the
library: the **subclass + `setup()`** style, the **`automaton { }` builder DSL**, the raw
model API underneath them, and the submachine call/return discipline. For what the
automaton can do to *itself* while running, see the companion
[adaptive-actions guide](ADAPTIVE-ACTIONS-GUIDE.md).

Below, "the DSL" refers to all authoring styles collectively; each section marks which
style it applies to.

## 1. The mental model

An automaton is made of:

- **states**: plain objects; the library compares them by **equality**, so give them
  value-based `equals`/`hashCode` (or make them singletons);
- **a transition mapping** (`TransitionMapping`): a mutable set of edges;
- **submachines**: named fragments with their own initial/accepting states;
- **actions**: named Kotlin lambdas that transitions can reference with arguments;
- a **main submachine** name, where recognition starts.

Recognition starts at the main submachine's initial state and proceeds until every
recognition path terminates. Edges are of exactly four kinds (the sealed class `Transition`):

| Edge | Consumes | Effect |
|---|---|---|
| `SymbolTransition(sym)` | 1 symbol | cursor +1, state → target |
| `Epsilon` | 0 | state → target, **unbounded; beware of self-loops** (see §4) |
| `Call(name)` | 0 | pushes the return state, jumps to submachine `name`'s initial state |
| `Return` | 0 | pops the stack (normally synthesized by the engine, not declared by you) |

> **Hint**: you almost never construct a `TransitionEdge` by hand; use `consume` /
> `epsilon` / `call` below. `Return` transitions are created by the executor when a
> submachine cannot continue but outer work remains.

## 2. Style A: subclass `AdaptiveAutomaton` (imperative)

```kotlin
class CallReturnExample : AdaptiveAutomaton() {

    // States are YOUR objects: subclasses of State with value equality.
    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")
    private val q2 = ExampleState("q2")
    private val q3 = ExampleState("q3")
    private val m0 = ExampleState("m0")

    // Required: how to produce a pristine twin for cloning during recognize().

    override fun newInstance(): AdaptiveAutomaton = CallReturnExample()

    override fun setup() {
        mainSubmachine = "Main"                       // where recognition starts

        val mainSm = Submachine(
            name = "Main",
            states = setOf(q0, q1, q2, q3),
            initialState = q0,
            acceptingStates = setOf(q3),
        )
        submachines.add(mainSm)                       // register before referencing!

        submachines.add(
            // acceptingStates = setOf(m0): the submachine returns immediately when
            // no 'c' was consumed; consuming self-loops let it absorb any number.
            Submachine(name = "StarC", states = setOf(m0), initialState = m0, acceptingStates = setOf(m0)),
        )

        from(q0) {                    // ← the `from { }` group block (§5)
            symbol('a', q1)
            call("StarC", q2)         // jump into the submachine
        }
        from(q2) {
            symbol('b', q3)           // accepted after the submachine returns
        }
        from(m0) {
            symbol('c', m0)           // consuming self-loop: c* has no hang, input shrinks
        }
    }
}
```

What `setup()` may use:

| Member | Effect |
|---|---|
| `mainSubmachine = "..."` | sets the main submachine (**must** be set, `recognize` fails fast otherwise) |
| `submachines.add(Submachine(...))` | registers a submachine |
| `symbol(from, symbol, to, prior = ActionRef.None, post = ActionRef.None)` | adds a symbol edge (symbol as `Char` or `Symbol`) |
| `epsilon(from, to, prior = ActionRef.None, post = ActionRef.None)` | adds an epsilon edge |
| `call(from, "Name", to, prior = ActionRef.None, post = ActionRef.None)` | adds a submachine-call edge |
| `from(source) { ... }` | groups several edges sharing a source state |
| `actions.add(Action(name) { ... })` | registers an adaptive action |

> **Pitfall**: **string states do NOT work here**. `symbol("q0", 'a', "q1")` throws
> `IllegalArgumentException` ("String state names are not supported in
> `AdaptiveAutomaton.setup()`"). Names are a builder (Style B) feature.

> **Remark**: `setup()` runs on the *clone* produced by `recognize()`, i.e. once per
> recognition call. Keep it free of global side effects that assume it runs exactly once
> per process (details in the adaptive-actions guide, §2).

## 3. Style B: the `automaton { }` builder DSL (declarative)

```kotlin
import br.usp.poli.lta.cereda.kdaptive.dsl.automaton
import br.usp.poli.lta.cereda.kdaptive.dsl.specToAutomaton

val spec = automaton {
    main("M")
    submachine("M") {
        initial("q0")
        accepting("q1", "q2")     // vararg: one or more accepting states
        state("qTemp")            // optional plain (non-initial, non-accepting) state
    }
    symbol("q0", 'a', "q1")
    symbol("q1", 'b', "q2")
}
val ka = specToAutomaton(spec)    // AdaptiveAutomaton, ready to recognize
```

Rules and conveniences of the builder:

- **Every state belongs to a submachine.** Declare it inside a `submachine("M") { ... }`
  block (`initial`, `accepting` or `state`); `toSpec()` rejects an edge endpoint that no
  submachine owns, because the executor could not tell which submachine is running.
- **The state table is shared** between the top level and every `submachine("M") { }`
  block, so a submachine may reference (or create) the same `"q0"` by name. A top-level
  `state("x")` is a lookup (or creation) helper: it only names a state, it does not
  give that state an owner.
- **Symbols accept raw `Char`** (mapped to `CharSymbol`), `Symbol` objects, or `sym('c')`.
- **Action references accept a name `String`** (`prior = "A"`) or an
  `ActionRef.Bound` built by `actionRef("A", p1, p2)`. A reference always names one
  registered action: the filter cases of `ActionRef` belong to queries and removals,
  never to an edge.
- **Adaptive actions are registered with `action("A") { ... }`** (see the next
  subsection) and referenced by name or through `ref("A", ...)`.
- `toSpec()` produces an immutable `AutomatonSpec`, a data class with
  `states` / `submachines` / `transitions` / `actions` / `main`, which can be inspected,
  transformed and materialized with `specToAutomaton` as many times as you want; every
  materialization is a **fresh automaton**. The builder also validates the whole
  specification while producing it (see below).

> **Hint**: prefer the builder when the automaton shape is known in advance (regular
> languages, templates, config formats); prefer the subclass when handlers need rich
> access to surrounding Kotlin state (lexers grabbing your objects by reference).

### Adaptive actions in Style B

Style B can host adaptive actions too: `action(name) { ... }` registers a named
handler in the specification, exactly like `actions.add(Action(name) { ... })` does
in Style A, but the whole automaton stays in one declaration.

```kotlin
val spec = automaton {
    submachine("M") {
        initial("q0")
        state("q1")
        accepting("q2")
    }
    action("AddC") { _, _ ->          // receiver: DslActionContext, parameters: transition, args
        ea.add(state("q1"), CharSymbol('c'), state("q2"))
    }
    on("q0").on('a').to("q1", post = "AddC")
    on("q1").on('b').to("q2")
    main("M")
}
```

What the handler gets:

| Member | Effect |
|---|---|
| `ea` | the `ElementaryActions` facade over the mapping currently being executed (queries, removals, insertions). `ea.query`/`ea.remove`/`ea.add` work on symbol transitions, `ea.queryCall`/`ea.removeCall`/`ea.addCall` on submachine-call edges |
| `state(name)` | a **read-only** lookup of a state of the automaton by name; an unknown name is an error, never a new state |
| `freshState(prefix = "g")` | a new state named `prefix` plus a number, unique within the run, restarting at zero for every recognition and skipping names the automaton already uses |
| `ref(name, args...)` | an `ActionRef` built at runtime, for arguments only known while running |
| `observe(fact)` | publishes a fact about this action to the consumer observing the run |
| `transition` (parameter) | the edge that triggered the action |
| `args` (parameter) | the argument list bound in the `ActionRef` that fired |

> **Note**: inside a handler, `state("q1")` resolves to the context lookup above, not
> to the builder's helper: the innermost receiver wins. A typo therefore fails loudly
> instead of creating a phantom state.

Build-time references use the builder's own `ref(name, args...)`: `String` arguments
become the corresponding named state, `Char` arguments become a `CharSymbol`, and
everything else is passed through unchanged.

```kotlin
on("q1").on('a').to("q1", post = ref("A", "q2", "q3"))   // names resolved now
```

#### Named arguments are checked

A string passed to `ref` names a **state of the automaton**. The builder verifies
that at the end of the block, and a typo fails the build instead of quietly
creating a state that nothing else knows:

```kotlin
on("q0").on('a').to("q1", post = ref("AddC", "qTYPO"))
// IllegalArgumentException: Action 'AddC' refers to state 'qTYPO', which is not
// part of the automaton. Known states: q0, q1, q2. Use actionRef(...) to pass a
// string payload.
```

A name is accepted when it denotes an edge endpoint, a submachine member or an
explicitly declared state. Typed `State` objects and non-state payloads are passed
through unchecked. To pass a string as data, use `actionRef("A", "some text")` or
the runtime `ref` of the handler context.

> **Note**: this strictness covers action arguments. Edge endpoints follow the state
> ownership rule described above, and the handler context offers only a read-only
> lookup.

#### Declaring the argument count

`action(name, arity = Arity.Exactly(n)) { ... }` states how many arguments every
reference to that action must bind; the builder checks the references before the
automaton is built:

```kotlin
action("A", arity = Arity.Exactly(2)) { _, args -> ... }
on("q1").on('a').to("q1", post = ref("A", "q2", "q3"))   // 2 arguments: ok
on("q1").on('a').to("q1", prior = ref("A", g1, g2))       // 2 arguments: ok
on("q1").on('a').to("q1", post = ref("A", "q2"))          // 1 argument:
// IllegalArgumentException: Action 'A' expects 2 argument(s), but a reference binds 1.
```

Without `arity` (the default, `Arity.Any`) nothing is checked, which suits actions that
are legitimately called with and without arguments. The check covers the references
written in the builder, on symbol, epsilon and call edges alike; edges added at
runtime by a handler are outside it.

> **Changed in 4.0.0**: the arity used to be a nullable `Int` (`arity = n`), and `null`
> meant "any arity". It is now an `Arity` marker — `Arity.Any` or `Arity.Exactly(n)` —
> in the style of `SubmachineRef`: a wildcard is a value in its own right, and the
> public API is null-free.

> **Pitfall**: even without `arity`, the argument list must match what the handler
> expects, **for both firing directions**. A prior action and a post action deliver
> the same list, so a handler reading `args[2]` breaks as soon as some reference
> binds fewer arguments. Keep the arity uniform across every reference to a given
> action, or declare it with `arity`.
>
> **Pitfall**: registering the same action name twice throws
> `IllegalArgumentException` ("Action 'A' is already registered."), because the
> executor looks actions up by name and a silent overwrite would hide a typo.
>
> **Remark**: `freshState` is the declarative replacement for the "generate a state
> name from a counter" idiom of the subclass style. The counter lives in the
> automaton instance, so each recognition run starts from zero, two automata
> built from the same spec never share names, and a generated name never collides
> with a state the specification already declares.

#### Publishing facts from a handler

`observe(fact)` publishes something only the action knows — the projection of a query
result, a domain size, a counter — to whoever is observing the run. The fact travels
through the instrumentation facility to the consumer, which decides what it is worth;
the library never interprets it.

```kotlin
data class DomainCount(val count: Int) : Observation

action("Reshape") { _, _ ->
    val matched = ea.query(state("q0"), CharSymbol('b'), State.Any)
    observe(DomainCount(matched.map { it.source }.distinct().size))
}
```

`Observation` is an open marker interface, so the fact type is yours to define. See
§7.3 of the [adaptive actions guide](ADAPTIVE-ACTIONS-GUIDE.md) for the whole facility.

#### Searching a set of states at once

The filters of the elementary actions accept **domains** — sets of states or symbols
built with `State.anyOf(...)` and `Symbol.anyOf(...)`. A domain selects every member it
names, and an addition over domains produces their Cartesian product:

```kotlin
action("Spread") { _, _ ->
    // one edge per combination: q0 -a-> q1, q0 -a-> q2, q3 -a-> q1, q3 -a-> q2
    ea.add(State.anyOf(state("q0"), state("q3")), CharSymbol('a'), State.anyOf(state("q1"), state("q2")))
}
```

The domain is a **filter**, not a state: it never belongs to the state set of the
automaton, and it is rejected wherever a concrete state is required — an edge endpoint,
for instance. See §4.0.1 of the [adaptive actions guide](ADAPTIVE-ACTIONS-GUIDE.md).

### Fluent step chain (optional)

The builder also exposes a formula-style alternative for writing single edges:

```kotlin
val spec = automaton {
    main("M")
    submachine("M") {
        initial("q0")
        state("q2")
        accepting("q1")
    }
    on("q0").on('a').to("q1")               // source -> symbol -> target, step by step
    on("q1").on('b').to("q0", prior = "X")  // prior/post pass through the dotted `to`
    this on "q0" on 'b' to "q2"             // the same edge in bare infix form (see below)
}
```

- `on("q0")` starts a step at that source (state object or name; internally the
  extension is `this on "q0"`, so it only exists inside the builder block).
- `stepSource.on('a')` attaches the expected symbol: a raw `Char`, a `Symbol`, anything
  resolvable as a symbol.
- `step.to(target, prior = ActionRef.None, post = ActionRef.None)` completes the edge. Only this call turns
  the accumulated pieces into a real edge; a chain that never reaches `.to` declares
  nothing at all, so double-check you did not lose an edge by mistake.
- The plain `consume` / `epsilon` / `call` calls remain the always-available option;
  the chain is sugar over those, not a separate engine.

#### The bare infix chain and the silent `Pair` trap

Because both `on` steps are declared `infix`, you may write them without any dot or
parentheses:

```kotlin
this on "q0" on 'a' to "q1"     // valid, builds a real edge (see the overload note)
(this on "q0" on 'a').to("q1")  // equivalent: infix `on`s, then a normal dotted `to`
```

Two things to know before you prefer this style:

1. **`this` is mandatory.** The chain's first infix is a builder extension, so a bare
   `"q0" on 'a'` has no receiver to go through and will not compile.
2. **On today's library the trailing `to` works** because `TransitionStep` declares a
   one-argument `infix fun to(target: Any)` overload. Without it (or on editions of this
   library predating that overload) the chain still **compiles clean and builds nothing**:
   the trailing `to` silently resolves to the stdlib `Pair` factory (`infix fun Any.to(other)`)
   instead, the expression evaluates to a discarded `Pair<TransitionStep, String>`, and
   no edge is added. The same silence affects the ungrouped dotted variant: in
   `this on "q0" on 'a'.to("q1")` the dot-call binds to the character `'a'`, not to the
   chain, and the resulting `Pair` is passed as the *symbol* argument. Both variants are
   pinned down by `AutomatonDslTest`, whose bare-chain test must keep expecting real edges.
3. **Prior and post actions need the dotted form.** Infix functions take exactly one
   parameter, so `to("q1", prior = "X")` may only be written as a normal call; the bare
   chain covers parameterless completions.

## 4. Transition kinds in detail

### `consume`, symbol edges

```kotlin
symbol(q0, 'a', q1)                                         // char → CharSymbol
symbol(q0, CharSymbol('a'), q1, prior = "A")                // with a prior adaptive action
symbol(q0, myToken, q1, post = actionRef("Lexer", token))   // with an argument
```

- `from` / `to`: `State` objects (Style A) or names (Style B).
- `symbol`: anything `Symbol` or `Char`. Subclass `Symbol()` for your own algebra,
  see `LexerExampleKt`, where tokens like lexer words are first-class symbols.
- `prior` / `post`: action references (`String` name, `ActionRef.Bound`, or
  `ActionRef.None`). `null` no longer compiles: the empty slot is a value.

#### Types: what `consume` keeps, converts, and rejects

The parameters are typed `Any`, so resolution is the type contract; here is exactly
what happens to what you pass:

- **Your objects are kept as-is.** The `State` you pass is stored verbatim in the edge
  and is matched during recognition by your `equals`/`hashCode` (this is why states
  should carry value equality, as `ExampleState` does). Likewise a custom `Symbol`
  subclass instance is kept and matched with its own `equals`: recognition compares the
  edge's symbol against the input symbol structurally.
- **One silent conversion: `Char` becomes `CharSymbol`.** Passing the character `c`
  stores exactly `CharSymbol('c')`. Everything else that is neither a `Symbol` nor a
  `Char` is rejected loudly at `setup()` time ("Cannot resolve ... to Symbol");
  nothing is guessed and nothing is dropped.
- **Beware cross-class symbols.** `CharSymbol('a')` never matches a custom `Letter('a')`:
  different classes means different `equals`. Pick one symbol algebra per automaton.
- **No compile-time checking.** The `Any`-typed signature means arg swaps
  (`symbol(q0, q1, 'a')`) or wrong element types compile fine and only fail when
  `setup()` first runs inside `recognize()`. Keep a smoke test that calls `recognize(...)`
  once so this surface is exercised in CI rather than in production.

> **Pitfall**: several symbol edges from the same state consuming the *same* symbol
> make the automaton **nondeterministic** and fork one path per alternative (intended,
> see `NondeterminismExampleKt`). If that was accidental, note that fork order follows
> mapping insertion order; do not rely on that order in tests or post-processing.

### `epsilon`, spontaneous edges

```kotlin
epsilon(q0, q1)
epsilon(q0, q1, prior = "A", post = "B")   // with adaptive actions
```

Moves without consuming. Use it to make a state optional, to publish a zero-length-word
path, or to chain submachines. An epsilon edge may carry a prior and a post action
exactly like a symbol edge: the prior fires before the move, the post after it.

> **Pitfall**: an epsilon **cycle** is a modelling mistake. Since 4.0.0 it is no longer
> a hang: the validator **rejects** a reachable epsilon-only cycle when the automaton
> registers no adaptive actions (nothing could change the topology, so the cycle is
> provably non-terminating), and **warns** about it when actions are present (an action
> may legitimately break it). At runtime, a path that re-enters a configuration it
> already left is **cut** — its outcome becomes `PathOutcome.Cut` — and the other
> branches carry on. Two mutually referential edges, `epsilon(q, r)` plus
> `epsilon(r, q)`, behave the same way. Make every epsilon cycle include at least one
> consuming edge, or keep the epsilon-only subgraph acyclic.

### `call`, submachine calls

```kotlin
call(q1, "StarC", q2)
call(q1, "StarC", q2, prior = "A", post = "B")   // with adaptive actions
```

Runtime effect: **push the target state `q2` as the return address**, set the current
submachine to `StarC`, and jump to `StarC`'s initial state. When `StarC` cannot continue,
the executor synthesizes a `Return` edge: pop the stack, land back on `q2`, switch the
current submachine to whichever submachine owns `q2`.

A call may carry a prior and a post action: the prior fires **before** the return
address is pushed, the post **after** the jump into the called submachine.

> **Pitfall**: in Style A (the subclass style) calling an **unregistered submachine
> name** fails only at runtime, when the first path reaches the call ("No submachine
> with name 'Typo'."). Style B (the builder) rejects it at build time instead, and it
> also requires the return target to belong to the calling submachine: returning to a
> state owned by another submachine would switch the running submachine on return.
> Keep submachine names in constants.

## 5. The `from(source) { ... }` group block

```kotlin
from(q0) {
    symbol('a', q1, post = "AddC")   // same parameters as consume / epsilon / call
    symbol('b', q2)
    epsilon(q3, prior = "X")
    call("M", q4, prior = "X")
}
```

Purely a readability device: each statement expands to an individual edge and nothing
else. Groups nest freely; there is no semantic coupling between a group's edges.
Every member of the group takes the same parameters as its standalone counterpart,
`prior` and `post` included.

## 6. Submachines: the full discipline

Style B shape:

```kotlin
submachine("StarC") {
    initial("m0")
    accepting("m0", "m1")     // vararg
}
symbol("m0", 'c', "m1")      // referenced by the same shared names
```

- Submachines exist to be **called**. In Style B, names are shared with the parent state
  table; in Style A you pass the state objects explicitly. Reusing the same state objects
  in two submachines is legal but muddies `submachines.ownerOf(state)`; look-up
  returns merely the **first registered** submachine containing that state.
- `accepting(...)` takes several states at once (`accepting("m0", "m1")`), but at least
  one: an empty call is rejected. `initial(...)` must be given exactly once, a second
  call is rejected instead of silently replacing the first.
- Acceptance is checked **in the currently executing submachine**: being in an accepting
  state of a non-main submachine does NOT mean recognition success. Full acceptance
  requires: end of input, current state accepting **in the current submachine**, empty
  stack, **and** the current submachine == `mainSubmachine`. When a submachine runs out
  of moves, two outcomes exist: if its current state is accepting and the caller's
  frame is on the stack, the executor synthesizes a `Return` and the path resumes in the
  caller; if the state is not accepting, the path rejects right there.
- `Return` needs a non-empty stack; popping an empty stack throws. A return with nothing
  to return from is a modeling mistake, surfaced loudly on purpose.

> **Hint**: two idiomatic ways to express "the sub-structure may be absent": an extra
> epsilon edge around the call, or making the submachine's initial state accepting (it
> then returns immediately without consuming). Pick one convention and use it
> consistently across your project.

## 7. Checklist: what people usually trip on

1. **Forgetting `mainSubmachine = "..."`** (Style A) → `recognize()` throws
   `IllegalArgumentException` with "The automaton does not declare a main submachine".
   Reading `mainSubmachine` before `setup()` declared it throws
   `IllegalStateException` ("Main submachine was not declared."). Set it first (both
   styles).
2. **Forgetting `main("...")`** (Style B) → `toSpec()` throws "The automaton does not
   declare a main submachine". Reading the builder's `main` property before `main(...)`
   was called throws `IllegalStateException` ("Main submachine cannot be null. Call
   main(\"M\") to declare it.").
3. **A state owned by no submachine** (Style B) → `toSpec()` throws "State 'q1' is not
   part of any submachine". Declare every state inside a `submachine("M") { ... }`
   block; the executor needs the owner to know which submachine is running.
4. **A call to an unknown submachine** (Style B) → `toSpec()` throws "Call to
   undeclared submachine 'Typo'". In Style A the same mistake explodes at runtime.
5. **A call returning to a state of another submachine** (Style B) → `toSpec()` throws:
   the return would switch the running submachine. Return to a state of the caller.
6. **Epsilon cycles** (§4) → no longer a hang: the validator rejects them when the
   automaton has no adaptive actions, warns about them when it has, and the engine cuts
   a path that re-enters a configuration it already left. Still a modelling mistake:
   make every epsilon cycle include at least one consuming edge.
7. **String states in `setup()`** (§2) → `IllegalArgumentException` with a helpful message.
8. **Symbols and states compared by `equals`**: a custom `Symbol`/`State` without value
   equality makes filter lookups match by reference only. Use `data class` or hand-write
   `equals`/`hashCode` (`ExampleState` shows the state idiom).
9. **Same state in several submachines** (legal, usually a mistake; you probably meant
   to *call* a submachine. See §6 for the lookup semantics.
10. **Submachine without an initial state** (Style B): `toSpec()` throws
    "Submachine 'X' must define an initial state."
11. **The `from { }` block is description-time only**: it does not "run"; runtime
    behaviour is entirely defined by the resulting edge set.
12. **Adaptive action arity** (Style B, §3): every reference to an action must bind
    the same number of arguments, because prior and post firing deliver the same
    list; a handler reading `args[2]` fails loudly with an index error otherwise.
    Declare the contract with `action("A", arity = Arity.Exactly(2))` to have the
    builder reject mismatches before the automaton runs.
13. **A state name in `ref(...)`** (Style B, §3) must denote a state of the
    automaton: the builder rejects unknown names instead of creating a phantom
    state. Use `actionRef(...)` for string payloads.
14. **A state name in a handler** (Style B, §3) is a read-only lookup: an unknown
    name throws instead of inventing a state.

## 8. A complete, runnable Style B example

```kotlin
import br.usp.poli.lta.cereda.kdaptive.dsl.automaton
import br.usp.poli.lta.cereda.kdaptive.dsl.specToAutomaton
import br.usp.poli.lta.cereda.kdaptive.model.CharSymbol

// (ab)*, regular, so ONE submachine suffices.
val spec = automaton {
    main("M")
    submachine("M") {
        initial("q0")
        state("q1")
        accepting("q0")
    }
    symbol("q0", 'a', "q1")
    symbol("q1", 'b', "q0")
}
val ka = specToAutomaton(spec)

fun chars(input: String) = input.map { CharSymbol(it) }

fun main() {
    println(ka.recognize(chars(""))     .accepted)  // true, zero repetitions
    println(ka.recognize(chars("abab")) .accepted)  // true
    println(ka.recognize(chars("aba"))  .accepted)  // false, trailing unpaired 'a'
}
```

`recognize()` answers with a **result object**: `result.accepted` is the verdict, and
`result.paths` holds the paths that reached one.

## 9. Where to go next

- **Adaptive actions**: `docs/ADAPTIVE-ACTIONS-GUIDE.md`. Queries, removals, insertions
  (`ElementaryActions`), argument passing, firing order and per-path isolation.
- **Watching a run**: the "Observing recognition: the visitor pattern" section of
  `README.md` (and §7.1 of the adaptive-actions guide) documents `ExecutionVisitor` and
  the GraphViz DOT export built on top of it.
- **Examples**: `examples/src/main/kotlin/br/usp/poli/lta/cereda/kdaptive/examples/`:
  15 small programs covering regular → context-sensitive languages, lexing, DSL usage
  and GraphViz export; each has a corresponding test in `examples/src/test/`.
- **API docs**: `./gradlew dokkaGenerateHtml`, then open `build/dokka/html/index.html`.
