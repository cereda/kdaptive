<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# kdaptive: adaptive automata for Kotlin

**kdaptive** is a Kotlin library that implements an *adaptive automaton*, a Turing-equivalent
formal recognition device. It recognizes formal languages: regular languages, context-free
languages, and beyond (context-sensitive and even non-context-free ones), because the
automaton can **modify itself while it runs**.

The library is the modern successor to a 2016 Java implementation of the same model
(`aa`), keeping the original lineage and its formal semantics, now under the package
`br.usp.poli.lta.cereda.kdaptive`. The
original Java implementation is merged with modern tooling (Gradle, Kotlin 2.4, detekt,
Spotless, Dokka, Kover) and a much lighter, iterative execution engine.

---

## Toolchain at a glance

| Piece | Choice |
|---|---|
| Language | Kotlin 2.4 (language/api level 2.4.0) |
| Bytecode | Java 11 (major version 55) |
| Build | Gradle wrapper 9.8.2, Kotlin DSL build script |
| Tests | `kotlin("test")` over JUnit 5 (295 tests: 182 library, 113 examples) |
| Static analysis | detekt 1.23.8, **zero-tolerance** config |
| Format / style | Spotless, ktlint rules, 120-col, SPDX license header |
| Docs | Dokka HTML (`./gradlew dokkaGenerateHtml`, fail-on-warning) |
| Coverage | Kover: line ≥ 95%, branch ≥ 78% enforced on `check` |
| Logging | SLF4J API only (backend chosen by the consumer) |

Coordinates: `br.usp.poli.lta.cereda:kdaptive:1.1.0`.

> **Remark**: the build currently **does not ship a publishing plugin**. The library jar
> lands in `./build/libs/kdaptive-1.1.0.jar` after `./gradlew build`, and `:examples`
> consumes `:kdaptive` (the root module) from the same multi-module build. To use the
> library in another project, either include it (composite build / `includeBuild`) or
> install the jar into a local repository of your choice.

---

## What is an adaptive automaton?

A finite automaton whose **any transition can carry code actions that alter the very
transition set the recognizer is currently traversing**. This makes the device strictly
more powerful than a pushdown automaton:

| Device | aⁿbⁿ | aⁿbⁿcⁿ |
|---|:---:|:---:|
| Finite automaton | ✗ | ✗ |
| Pushdown automaton | ✓ | ✗ |
| **Adaptive automaton** | ✓ | ✓ |

Concretely, a transition may carry a *prior* and/or *post* action (a Kotlin lambda).
When the recognizer is about to traverse that edge, the prior action fires **before**
the edge is applied and the post action fires **after**. Inside these actions you can:

- **query** the current transition set (find edges matching a pattern),
- **remove** matching edges (e.g. one-shot edges used once and discarded),
- **insert** fresh edges (e.g. spawn the next copy of a recursive pattern),
- and do anything else Kotlin allows: accumulate data, build tokens, count.

The result is that a single, possibly *tiny*, initial automaton (the *inset*) can describe
the entire unfolding of a formal language: the automaton grows and mutates only when
needed. A classic theoretical result: aⁿbⁿcⁿ is recognizable from an inset with **one
state and no transitions at all**, because the first adaptive action bootstraps the
expansion. Our companion example takes the safer, practical route: a small explicit
inset of four states that its action `A` grows and prunes while the cursor advances.

---

## Two ways to build an automaton

The library offers two complementary authoring styles. Both live on top of the same model
classes, so they can be mixed freely in the same project.

### 1. Subclassing `AdaptiveAutomaton`, imperative setup

```kotlin
class EvenA : AdaptiveAutomaton() {
    private val q0 = ExampleState("q0")
    private val q1 = ExampleState("q1")

    override fun newInstance(): AdaptiveAutomaton = EvenA()

    override fun setup() {
        mainSubmachine = "M"
        submachines.add(Submachine(name = "M", states = setOf(q0, q1), initialState = q0, acceptingStates = setOf(q0)))
        consume(q0, 'a', q1)
        consume(q1, 'a', q0)   // an even number of 'a's returns to q0
    }
}
```

### 2. The `automaton { }` builder DSL, declarative spec

```kotlin
val spec = automaton {
    main("M")
    submachine("M") {
        initial("q0")
        state("q1")
        accepting("q0")
    }
    consume("q0", 'a', "q1")
    consume("q1", 'a', "q0")
}
val ka = specToAutomaton(spec)
```

`specToAutomaton` returns an `AdaptiveAutomaton` whose `setup()` materializes the spec,
so both styles converge onto the same object. Every state belongs to a submachine:
declare it inside a `submachine("M") { ... }` block, otherwise `toSpec()` rejects the
specification. The builder also hosts adaptive actions:
`action("A") { ... }` registers a named handler whose receiver exposes `ea` (the live
`ElementaryActions` facade), `freshState(prefix)` (a unique state per run),
`state(name)` (a read-only lookup of a state of the automaton) and
`ref(name, args...)`, so a self-modifying automaton can be declared in one block. The
builder validates the specification while producing it: a state name in `ref(...)` must
denote a state of the automaton, `action("A", arity = n)` pins the number of arguments
every reference must bind, every edge endpoint must belong to a submachine, a call must
name a declared submachine and return to a state of its caller, and a main submachine
must be declared. See
**[docs/DSL-GUIDE.md](docs/DSL-GUIDE.md)**
for the complete guide (state naming rules, action references, adaptive actions,
submachines, the `from { }` group block, infix `on`/`to` operators, pitfalls).

### Running it

```kotlin
val accepted = ka.recognize(convert("aaaa"))   // convert(): String -> List<Symbol>
println(accepted)                              // true
```

> **Hint**: `convert()` is the examples-module helper that maps `String` to
> `List<Symbol>`; the library itself takes a list of `Symbol`s (so a *lexer-like* input
> can be richer than characters). Without it:
>
> ```kotlin
> ka.recognize(listOf(CharSymbol('a'), CharSymbol('a')))
> ```

---

## The adaptive-actions engine

Actions are what turn a bare automaton into an *adaptive* one. The engine,
`ElementaryActions` provides wildcard-based queries, removals and insertions over the
transition mapping, driven from inside your action handlers:

```kotlin
actions.add(Action(name = "A") { transitions, transition, parameters ->
    val ea = ElementaryActions(transitions)
    // Remove the 'a'-edge I am currently traversing (one-shot), then re-insert
    // a fresh copy elsewhere so the automaton can explode on demand.
    val p1: State = parameters[0].asState()
    val p2: State = parameters[1].asState()
    ea.remove(p1, CharSymbol('a'), p2)
    ea.add(p2, CharSymbol('a'), p1)
})
```

See **[docs/ADAPTIVE-ACTIONS-GUIDE.md](docs/ADAPTIVE-ACTIONS-GUIDE.md)** for the full
guide: firing order (prior/post), the per-path mutation-isolation model, argument
matching rules, the DSL-style `query { }` / `remove { }` blocks, transition predicates,
and a catalogue of pitfalls and warnings.

---

## Observing recognition: the visitor pattern

An adaptive automaton is a program that rewrites itself while it runs, so "what actually
happened?" is a fair question. The library answers it with `ExecutionVisitor`: an
interface whose methods are **no-ops by default**. Implement the events you care about,
ignore the rest.

```kotlin
import br.usp.poli.lta.cereda.kdaptive.execution.ExecutionVisitor

val events = mutableListOf<String>()

val visitor = object : ExecutionVisitor {
    override fun onEdgeAdded(
        pathId: Int,
        edge: TransitionEdge,
        transitions: TransitionMapping,
        submachines: SubmachinesSet,
    ) {
        events.add("path $pathId added $edge")
    }
}

ka.visitors.add(visitor)
ka.recognize(convert("ab"))
```

### The event catalogue

| Event | Fires when |
|---|---|
| `onRecognizeStart(automaton)` | once per `recognize()`, on the clone, **after** `setup()` populated its topology |
| `onRecognizeEnd(result)` | once per `recognize()`, with the overall verdict |
| `onPathStart(pathId, input)` | a path begins; forked children included |
| `onPathEnd(pathId, accepted, cursor)` | a path accepts or rejects |
| `onPathFork(pathId, children)` | a path forks: it reaches no verdict, its children carry on. Each child carries the edge it was created for |
| `onBeforeTraverse(pathId, edge)` / `onAfterTraverse(pathId, edge)` | around each edge traversal |
| `onBeforeAction(pathId, actionRef, isPrior)` / `onAfterAction(pathId, actionRef, isPrior)` | around each adaptive action handler |
| `onEdgeAdded(...)` / `onEdgeRemoved(...)` | after each insertion/deletion in the path's mapping, together with the full mapping and submachines |
| `onCall(pathId, submachine)` / `onReturn(pathId)` | entering or leaving a submachine |

Two rules are worth remembering:

- **A path terminates exactly once**, either with `onPathEnd` (accept or reject) or with
  `onPathFork` (the path was replaced by its children). A visitor that tracks open paths
  must handle both.
- **Callbacks are synchronous**, on the recognition thread: a slow visitor slows
  recognition down. With no visitor registered the engine skips every callback, so the
  feature costs nothing when unused.

### Per-path isolation

Forked paths own independent copies of the transition mapping, so a mutation on one path
never shows up on a sibling. The `onEdgeAdded` / `onEdgeRemoved` events carry the
`pathId` of the path that mutated, so the streams of different paths stay apart.

### GraphViz export

`br.usp.poli.lta.cereda.kdaptive.dot` ships a ready-made consumer of those events.
`GraphVizVisitor` renders the topology as
[GraphViz DOT](https://graphviz.org/doc/info/lang.html) text on every mutation, and
`DotExporter` is the renderer behind it: one cluster per submachine, accepting states as
`doublecircle`, edges labeled with the symbol (or ε / call / return) plus the prior and
post actions.

```kotlin
import br.usp.poli.lta.cereda.kdaptive.dot.GraphVizVisitor

val frames = mutableListOf<Pair<Int, String>>()
ka.visitors.add(GraphVizVisitor { pathId, dot -> frames.add(pathId to dot) })
ka.recognize(convert("ab"))
// frames: one (pathId, DOT document) per topology mutation
```

The output is plain text: there is **no dependency on the `dot` binary** nor on any
GraphViz library. Save a frame as `frame.dot` and render it with
`dot -Tpng frame.dot -o frame.png`, or feed it to any GraphViz-compatible tool.
`DotExporter.toDot(transitions, submachines)` is public too, so a topology can also be
snapshotted on demand.

A runnable example lives in `br.usp.poli.lta.cereda.kdaptive.examples.GraphVizExample`:

```bash
./gradlew :examples:run -Pmain=br.usp.poli.lta.cereda.kdaptive.examples.GraphVizExampleKt
```

---

## Instrumentation: measuring what you want

The visitor pattern observes **what happened**. The instrumentation facility goes one step
further and exposes **what the elementary actions did to the topology** — including the
reads, which change nothing and would otherwise be invisible.

The design rule is one sentence: **the library reports facts; the consumer decides what
they are worth.** There is no cost model in the engine, no `TimeAnalysis`, no metric to
configure. A consumer that wants a different metric writes a different visitor.

### Three channels, three questions

| Channel | Question | Events |
|---|---|---|
| **Topology** | *What changed in the mapping?* | `onEdgeAdded` / `onEdgeRemoved`, once per edge that entered or left |
| **Operation** | *What did the elementary actions do?* | `onMappingOperation`, once per elementary operation |
| **Fact** | *What did the action itself compute?* | `onObservation`, once per fact published by action code |

The topology and operation channels answer different questions, and the counts differ:
a removal of three edges produces **one** `MappingOperation.Remove` and **three**
`onEdgeRemoved` events. Count removals on the operation channel and edges removed on the
topology channel — but do not sum them.

### Operations

`MappingOperation` is a sealed, symmetric hierarchy of the three things an elementary
action can do to the mapping:

```kotlin
sealed interface MappingOperation {
    val edges: List<TransitionEdge>
    val mappingSizeBefore: Int
    val mappingSizeAfter: Int

    data class Query(val pattern: TransitionPattern, /* ... */) : MappingOperation
    data class Remove(val pattern: TransitionPattern, /* ... */) : MappingOperation
    data class Add(val pattern: TransitionPattern, val edge: TransitionEdge, /* ... */) : MappingOperation
}
```

- a **`Query`** carries the pattern that was matched and the edges it selected — the only
  way to observe a read;
- a **`Remove`** carries the pattern, the edges it took out, and the mapping size before
  and after. A removal that matched **nothing** is still reported: it scanned the mapping
  even though it changed nothing, and a metric that cannot see it is blind to exactly the
  case it exists to catch;
- an **`Add`** carries the edge it inserted and the mapping size before and after.

The `pattern` says what was *asked for*, not what was found. Its filters are documented
per family: `TransitionPatternKind.SYMBOL`, `TransitionPatternKind.EPSILON` and
`TransitionPatternKind.SUBMACHINE` give the same filter different meanings, which is why
the kind is part of the pattern. Every filter is an explicit value — a wildcard is
`State.Any`, `Symbol.Any` or `ActionRef.Free`, never a null; the only null left is the
`submachine` filter of the call family, where it means "any call".

### Facts published by an action

The engine cannot see inside an action. When an action computes something only it knows —
the projection of a query result, say — it publishes a fact, and the consumer decides
whether it is worth anything:

```kotlin
data class DomainSize(val count: Int) : Observation

action("Reshape") { _, _ ->
    val matched = ea.query(q0, sym('b'), State.Any)
    observe(DomainSize(matched.map { it.source }.distinct().size))
    ea.remove(k, sym('a'), State.Any)
    ea.add(k, sym('a'), k)
}
```

`Observation` is an open marker interface, so a consumer defines its own fact types; the
library never has to learn about them. The imperative API reaches the same channel through
`transitions.observe(fact)`.

### A consumer, end to end

Nothing here is known to the engine. `br.usp.poli.lta.cereda.kdaptive.examples.CostVisitor`
prices the work — a query and a removal scan the mapping, an addition inserts one edge:

```kotlin
val visitor = CostVisitor()
ka.visitors.add(visitor)
ka.recognize(convert("aab"))

println(visitor.cost)        // the metric
println(visitor.costPerPath())
println(visitor.report())
```

```bash
./gradlew :examples:run -Pmain=br.usp.poli.lta.cereda.kdaptive.examples.CostExampleKt
```

Changing that cost model means changing `CostVisitor` and nothing else.

---

## Model API tour

| Type | Role |
|---|---|
| `Symbol` / `CharSymbol` | Input alphabet. `CharSymbol('a')` is the bread-and-butter symbol; subclass `Symbol()` for richer tokens (lexer-like recognizers). |
| `State` | Abstract base. Subclass with value-based `equals`/`hashCode` (see `ExampleState`); transitions compare states by equality. |
| `Transition` (sealed) | Exactly one of `SymbolTransition`, `Epsilon`, `Call`, `Return`. |
| `TransitionEdge` | An edge of the mapping: source, target, a `Transition`, and optional prior/post `ActionRef.Bound`s. |
| `Submachine` | A named automaton fragment with its own initial/accepting states; called via `call(...)` and returning via a synthetic return transition. |
| `Action` / `ActionRef` / `ActionHandler` | A named action with its Kotlin handler; `ActionRef.Bound(name, args)` references it with arguments on an edge, while `Free`/`Any`/`None`/`Named` filter an action slot in a query or a removal. |
| `Stack` | The call stack backing submachine call/return discipline. |
| `TransitionMapping` | The mutable set of edges; O(1) filtered lookups via `withSourceState` and friends. |
| `RecognitionPath` | The trail of one recognizer path: transition `toString()`s, termination cursor/state, accepted/rejected result. |
| `AdaptiveAutomaton` | The automaton itself, plus the recognition driver. |
| `ExecutionVisitor` | The recognition-event interface: no-op defaults for every event, registered through `AdaptiveAutomaton.visitors`. |
| `DotExporter` / `GraphVizVisitor` | GraphViz DOT rendering of a topology, and the visitor that emits a snapshot on every mutation. |

---

## The recognition model, precisely

These semantics come straight from the original Java implementation and are preserved:

1. **Input** is a list of `Symbol`; no string parsing is imposed on you.
2. A **prior action** runs *before* its edge is applied and **may mutate the transition
   mapping**; if the action removes the very edge the path was about to traverse, the
   path simply considers itself stuck at that point (normal termination rules apply
   again from the current configuration).
3. Symbol consumption **advances the cursor**; `epsilon` moves without consuming;
   `call` pushes the return state and jumps to the called submachine's initial state;
   the **implicit return** pops the stack when a submachine cannot continue but input
   (or an outer frame) remains.
4. **Acceptance** requires: end of input reached, current state accepting in the current
   submachine, empty stack, **and** executing the *main* submachine.
5. **Nondeterminism** means forking: from a configuration with more than one applicable
   transition, one recognition path per alternative is spawned; each receives *copies*
   of the transition mapping, action set, submachine set and stack (mutations are
   isolated per path. Input is shared (read-only).
6. `recognize()` **clones the automaton before running** it: your configured original is
   never mutated, and repeated `recognize()` calls are completely independent.

> **Call-out**: because forked paths get copies, an adaptive action executed on one path
> cannot undo a mutation performed on a sibling path. Effects are per-path, which is
> precisely what gives the device its expressive power (a path can "check" a hypothesis
> without leaving traces on the others).

---

## Non-determinism and path control

Anything ambiguous forks paths. Use `stopAtFirstFinishedRecognitionPath = true` (or
`setStopAtFirstResult(true)`) to stop as soon as **one path terminates (accepted OR
rejected**. That is *not* an "exists an accepting path" oracle: a fast rejection also
halts everything. Use it when only one path is expected to complete anyway (e.g.
deterministic automata), or when you deliberately want first-outcome semantics.
The default is exhaustive: every path runs to completion.

After `recognize()` you can inspect every path:

```kotlin
val result = ka.recognize(convert("abab"))
val paths = ka.getRecognitionPaths()   // List<RecognitionPath>
println(paths.joinToString("\n"))      // e.g. RecognitionPath(id=1, result=true) { ... }
```
Calling `getRecognitionPaths()` *before* `recognize()` throws `IllegalStateException`
by design (fail fast instead of returning "no paths yet").

---

## Module layout

```
kdaptive/                    the library (root module)
  src/main/kotlin/br/usp/poli/lta/cereda/kdaptive/
    model/                   State, Symbol, CharSymbol, Transition, TransitionEdge,
                             Submachine, Action, Stack + Observation, MappingOperation,
                             TransitionPattern + predicates/ + sets/ + actions/
    execution/               AdaptiveAutomaton (the public entry), PathExecutor, PathExecutorConfig,
                             ExecutionVisitor (the recognition-event interface), ForkedPath
    dot/                     DotExporter, GraphVizVisitor (GraphViz DOT export)
    dsl/                     AutomatonDsl.kt: automaton { } / specToAutomaton
    logging/                 SLF4J facade (`val <T> Any.logger`)
  src/test/kotlin/           mirrors main, with unit, error-path and coverage tests
examples/                    :examples, 16 executable examples, depends on the root module
```

The **16 examples** double as the living documentation: each is small, prints its result,
and has a test that keeps it honest:

| Example | Language / behaviour |
|---|---|
| `RegularLanguageExampleKt` | `(ab)+` (regular) |
| `EpsilonLanguageExampleKt` | epsilon-driven language |
| `ContextFreeLanguageExampleKt` | `aⁿbⁿ` (pushdown-like, via adaptive action) |
| `ContextSensitiveLanguageExampleKt` | `aⁿbⁿcⁿ` (the full adaptive bootstrap, declared with the `automaton { }` DSL) |
| `CallReturnExampleKt` | `ac*b` via submachine call/return |
| `NestedCallReturnExampleKt` | nested submachine calls (balanced structure) |
| `BalancedParenthesesExampleKt` | balanced parentheses |
| `NondeterminismExampleKt` | `a(b\|c)` via forks |
| `LexerExampleKt` | simplified tokenizer (identifiers, numbers, spaces) |
| `ArithmeticExampleKt` | simple binary expressions `digit op digit` (`0`/`1`, `+`/`*`) |
| `ConfigParserExampleKt` | a config line like `a=[01]+;` (multi-phase recognizer) |
| `ModuloExampleKt` | `{aⁿ \| n mod 3 = 0}` via a three-state ring counter |
| `SubstringExampleKt` | `{ w \| w contains "ab" }` over `{a, b}` |
| `DslShowcaseExampleKt` | dynamic edge insertion (`ElementaryActions.add`) |
| `GraphVizExampleKt` | GraphViz DOT snapshots of the topology, taken on every mutation |
| `CostExampleKt` | a cost model computed by a consumer, with no change to the library |

Run any of them:

```bash
./gradlew :examples:run -Pmain=br.usp.poli.lta.cereda.kdaptive.examples.RegularLanguageExampleKt
```

---

## Build & develop

All commands assume the Gradle wrapper:

```bash
./gradlew build                 # compile + test + detekt + Spotless check + Kover verify
./gradlew clean build           # full rebuild
./gradlew test                  # library tests only
./gradlew dokkaGenerateHtml     # API docs in ./build/dokka/html, warnings fail the build
./gradlew koverHtmlReport       # coverage report → build/reports/kover/html/index.html
./gradlew :examples:run -Pmain=<FullyQualifiedMainKt>   # run an example
```

> **Pitfall**: testing only fires through `./gradlew test` / `build`. After changing test
> dependencies (especially JUnit wiring), verify tests actually **execute**, not just that
> the build goes green:
>
> ```bash
> find build/test-results/test examples/build/test-results/test -name "TEST-*.xml" | wc -l
> ```
>
> A silently missing JUnit engine is reported as "zero tests ran", not as an error.

### Invariants kept by the build

- **detekt**: zero issues tolerated (`maxIssues: 0`).
- **Kover**: line coverage ≥ 95% and branch coverage ≥ 78%, verified inside `check`.
- **Dokka**: zero warnings (every public member is documented), verified inside `check`.
- **tapmoc**: dependency class-file and Kotlin-metadata floors are checked inside `check`
  (the library claims Kotlin api/language 2.4.0 and Java 11 bytecode, and it means it).
- **Spotless**: license header + ktlint on every file.

---

## Compatibility

- The bytecode is Java 11; Kotlin language/api level 2.4.0. Kotlin 2.4.21 is the compiler.
- No Android-specific code; it should run on the JVM and in Android modules that ship
  Java 11+ or desugared Kotlin stdlib.
- Choose any SLF4J backend you like (`slf4j-simple` is prewired for tests/examples).

### Changes in 2.0.0

This is a **breaking** release: the elementary actions were reworked, epsilon got an
explicit marker, and six engine defects were fixed.

- **`ElementaryActions` has two families and one method per operation.** The symbol
  family is `query` / `remove` / `add`, the call family is `queryCall` / `removeCall` /
  `addCall`. Every method lays the transition filters out first and the action filters
  out last, and every filter has a default. What this breaks:
  - the prior-action filter no longer comes **first**: it now comes **after** the
    transition filters, so `remove(priorAction, source, symbol, target)` becomes
    `remove(source, symbol, target, prior)`;
  - the parameter names `priorAction` / `postAction` are now `prior` / `post`;
  - the submachine family is spelled `queryCall` / `removeCall` / `addCall`.
- **No filter is a null any more.** A wildcard is a value in its own right:
  `State.Any` (any state), `Symbol.Any` (any symbol-consuming transition),
  `Symbol.Epsilon` (an epsilon transition) and `ActionRef.Free` (an unconstrained
  action slot). The only null left is the `submachine` filter of the call family, where
  it means "any call".
- **`ActionRef` is a sealed interface.** `Bound` is the concrete reference an edge
  carries; `Free`, `Any`, `None` and `Named` are the filters a query or a removal uses.
  `Named("A")` matches an action by name whatever its arguments; `Bound("A", x)` matches
  exactly these arguments. The old "a reference with zero arguments matches any arity"
  rule is gone — and with it the ambiguity between the two roles.
- **Epsilon is named explicitly.** `Symbol.Epsilon` selects the epsilon transitions and
  `Symbol.Any` selects the symbol-consuming ones; a symbol filter no longer matches
  epsilon edges.
- **The additions gained symmetry.** `add` accepts a prior and a post action,
  `add(source, Symbol.Epsilon, target)` inserts an epsilon edge, and `addCall` inserts
  a submachine call.
- **Six engine defects were fixed**: `setup()` no longer duplicates the topology; every
  edge that leaves a mapping fires its own `onEdgeRemoved`; a copied mapping keeps its
  callbacks; a symbol filter no longer matches epsilon; `getRecognitionPaths()` reports
  only the paths that reached a verdict (a path replaced by its children is not one of
  them); and the recognition lifecycle events always pair, even when a path fails.
- `ExecutionVisitor.onBeforeAction` / `onAfterAction` now receive an
  `ActionRef.Bound` instead of an `ActionRef`, and `TransitionEdge.prior` / `.post` are
  `ActionRef.Bound?` — a filter can no longer be attached to an edge.
- New in 1.2.0 and kept: `MappingOperation`, `Observation`, `TransitionPattern`,
  `ForkedPath`, the `onMappingOperation` and `onObservation` visitor events, and the
  `observe()` helpers on `TransitionMapping`, `ElementaryActions` and the DSL action
  context.

See §4 of the [adaptive actions guide](docs/ADAPTIVE-ACTIONS-GUIDE.md) for the complete
API.

## License

kdaptive is free software: you can redistribute it and/or modify it under the terms of
the GNU General Public License as published by the Free Software Foundation, either
version 3 of the License, or (at your option) any later version (SPDX:
`GPL-3.0-or-later`). The full license text is in the [LICENSE](LICENSE) file.

Every source file carries the `// SPDX-License-Identifier: GPL-3.0-or-later` header;
the README and the guides are marked with the same identifier.
