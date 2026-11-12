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

## Toolchain at a glance

| Piece | Choice |
|---|---|
| Language | Kotlin 2.4 (language/api level 2.4.0) |
| Bytecode | Java 11 (major version 55) |
| Build | Gradle wrapper 9.9.1, Kotlin DSL build script |
| Tests | `kotlin("test")` over JUnit 5 (594 tests: 465 library, 129 examples) |
| Static analysis | detekt 1.23.8, **zero-tolerance** config |
| Format / style | Spotless, ktlint rules, 120-col, SPDX license header |
| Docs | Dokka HTML (`./gradlew dokkaGenerateHtml`, fail-on-warning) |
| Coverage | Kover: line ≥ 95%, branch ≥ 78% enforced on `check` |
| API compatibility | binary-compatibility validator, baseline in `api/kdaptive.api` (`./gradlew apiCheck`) |
| Supply chain | Gradle dependency verification (`gradle/verification-metadata.xml`) + wrapper checksum |
| Logging | SLF4J API only (backend chosen by the consumer) |

Coordinates: `br.usp.poli.lta.cereda:kdaptive:6.0.0`.

> **Remark**: the build currently **does not ship a publishing plugin**. The version is
> **derived from [`CHANGELOG.md`](CHANGELOG.md)**: the release build
> (`./gradlew build -Prelease=true`) lands the library jar in
> `./build/libs/kdaptive-6.0.0.jar` (next to `kdaptive-6.0.0-sources.jar`), while a
> plain `./gradlew build` appends `-SNAPSHOT` to the derived version. `:examples`
> consumes `:kdaptive` (the root module) from the same multi-module build. To use the
> library in another project, either include it (composite build / `includeBuild`) or
> install the jar into a local repository of your choice.

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
        symbol(q0, 'a', q1)
        symbol(q1, 'a', q0)   // an even number of 'a's returns to q0
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
    symbol("q0", 'a', "q1")
    symbol("q1", 'a', "q0")
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
val result = ka.recognize(convert("aaaa"))   // convert(): String -> List<Symbol>
println(result.accepted)                     // true
println(result.acceptedPaths)                // the paths that accepted the input
```

`recognize()` answers with a **result object**, not with a bare `Boolean`: the verdict
*and* the paths it was made of. `result.accepted` is the verdict; `result.paths` holds
every path that reached a verdict, and `result.acceptedPaths` / `result.rejectedPaths` /
`result.cutPaths` split them by outcome.

> **Hint**: `convert()` is the examples-module helper that maps `String` to
> `List<Symbol>`; the library itself takes a list of `Symbol`s (so a *lexer-like* input
> can be richer than characters). Without it:
>
> ```kotlin
> ka.recognize(listOf(CharSymbol('a'), CharSymbol('a')))
> ```

## The adaptive-actions engine

Actions are what turn a bare automaton into an *adaptive* one. The engine,
`ElementaryActions` provides wildcard-based queries, removals and insertions over the
transition mapping, driven from inside your action handlers:

```kotlin
actions.add(Action(name = "A") { transitions, transition, parameters ->
    val ea = ElementaryActions(transitions, submachines)
    // Remove the 'a'-edge I am currently traversing (one-shot), then re-insert
    // a fresh copy elsewhere so the automaton can explode on demand.
    val p1: State = parameters[0].asState()
    val p2: State = parameters[1].asState()
    ea.remove(p1, CharSymbol('a'), p2)
    ea.add(p2, CharSymbol('a'), p1)
})
```

`ElementaryActions` takes the live mapping **and** the declared submachines: the second
argument is what lets `addCall` reject a call to a submachine that does not exist, at
the place where the addition is written.

See **[docs/ADAPTIVE-ACTIONS-GUIDE.md](docs/ADAPTIVE-ACTIONS-GUIDE.md)** for the full
guide: firing order (prior/post), the per-path mutation-isolation model, argument
matching rules, the DSL-style `query { }` / `remove { }` blocks, transition predicates,
and a catalogue of pitfalls and warnings.

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
        topology: Topology,
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
| `onRecognizeEnd(result)` | once per `recognize()` that finished, with the whole `RecognitionResult` |
| `onRecognizeFailed(cause)` | instead of `onRecognizeEnd`, when the recognition **died** before deciding anything |
| `onPathStart(pathId, input)` | a path begins; forked children included |
| `onPathEnd(pathId, outcome, cursor)` | a path ends: it accepted, rejected, was cut, or failed |
| `onPathFork(pathId, children)` | a path forks: it reaches no verdict, its children carry on. Each child carries the edge it was created for |
| `onBeforeTraverse(pathId, edge)` / `onAfterTraverse(pathId, edge)` | around each edge traversal |
| `onBeforeAction(pathId, actionRef, isPrior)` / `onAfterAction(pathId, actionRef, isPrior)` | around each adaptive action handler |
| `onEdgeAdded(...)` / `onEdgeRemoved(...)` | after each insertion/deletion in the path's mapping, together with an immutable `Topology` snapshot |
| `onCall(pathId, submachine)` / `onReturn(pathId)` | entering or leaving a submachine |

Three rules are worth remembering:

- **A path terminates exactly once**, either with `onPathEnd` (accept, reject, cut, or
  failure) or with `onPathFork` (the path was replaced by its children). A visitor that
  tracks open paths must handle both.
- **A failure is not a rejection.** A path that died before reaching a verdict reports
  `PathOutcome.Failed`, and the recognition reports `onRecognizeFailed` instead of
  `onRecognizeEnd`: the engine could not decide anything about the input, and saying
  "rejected" would claim a verdict nobody reached.
- **Callbacks are synchronous**, on the recognition thread: a slow visitor slows
  recognition down. With no visitor registered the engine skips every callback — and
  never builds a topology snapshot — so the feature costs nothing when unused.

### Per-path isolation

Forked paths own independent copies of the transition mapping, so a mutation on one path
never shows up on a sibling. The `onEdgeAdded` / `onEdgeRemoved` events carry the
`pathId` of the path that mutated, so the streams of different paths stay apart. What
they carry is a `Topology` — an **immutable snapshot**, not the live mapping: a visitor
can inspect the automaton, but it cannot reach into the running recognition and change
it behind the engine's back.

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
`DotExporter.toDot(topology: Topology)` is public too, so a topology can also be
snapshotted on demand.

A runnable example lives in `br.usp.poli.lta.cereda.kdaptive.examples.GraphVizExample`:

```bash
./gradlew :examples:run -Pmain=br.usp.poli.lta.cereda.kdaptive.examples.GraphVizExampleKt
```

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
    val pattern: TransitionPattern
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
per family: `TransitionPatternKind.Symbol`, `TransitionPatternKind.Epsilon`,
`TransitionPatternKind.Mixed` and `TransitionPatternKind.Submachine` give the same filter
different meanings, which is why the pattern reports a **kind**. The kind is *derived*
from the filters, so a pattern can never contradict itself. Every filter is an explicit
value — a wildcard is `State.Any`, `Symbol.Any`, `SubmachineRef.Any` or
`ActionRef.Free`, never a null.

The pattern also reports **how many values each filter may select**:
`sourceDomainSize`, `symbolDomainSize`, `targetDomainSize`, `submachineDomainSize`,
`priorDomainSize` and `postDomainSize`. Each is a `DomainSize`: a concrete filter
reports `DomainSize.Bounded(1)`, a **domain** reports `DomainSize.Bounded(n)` with the
number of its members, and a wildcard reports `DomainSize.Unbounded` — which names an
unbounded number of values, and is *not* the same as "empty". These are facts, not
costs: what an unbounded filter is worth is the consumer's decision.

### Facts published by an action

The engine cannot see inside an action. When an action computes something only it knows —
the projection of a query result, say — it publishes a fact, and the consumer decides
whether it is worth anything:

```kotlin
data class DomainCount(val count: Int) : Observation

action("Reshape") { _, _ ->
    val matched = ea.query(q0, sym('b'), State.Any)
    observe(DomainCount(matched.map { it.source }.distinct().size))
    ea.remove(k, sym('a'), State.Any)
    ea.add(k, sym('a'), k)
}
```

`Observation` is an open marker interface, so a consumer defines its own fact types; the
library never has to learn about them. The imperative API reaches the same channel through
`transitions.observe(fact)`.

### A consumer, end to end

Nothing here is known to the engine. `br.usp.poli.lta.cereda.kdaptive.examples.CostVisitor`
prices the work: a query costs the scan of the mapping, and a removal or an addition
costs that scan once per edge shape its pattern may touch — the product of the domain
sizes the pattern reports:

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

## Model API tour

| Type | Role |
|---|---|
| `Symbol` / `CharSymbol` | Input alphabet. `CharSymbol('a')` is the bread-and-butter symbol; subclass `Symbol()` for richer tokens (lexer-like recognizers). |
| `State` | Abstract base. Subclass with value-based `equals`/`hashCode` (see `ExampleState`); transitions compare states by equality. |
| `StateDomain` / `SymbolDomain` | The set-valued filters built by `State.anyOf(...)` and `Symbol.anyOf(...)`: a domain names several states or symbols at once, and an addition over domains produces their Cartesian product. |
| `SubmachineRef` | The filter over the called submachine: `Any` (any call), `Named("M")`, `Domain` (`SubmachineRef.anyOf("M", "N")`) or `None` (the filter does not apply). It also names the submachines an addition calls. |
| `Transition` (sealed) | Exactly one of `SymbolTransition`, `Epsilon`, `Call`, `Return`. |
| `TransitionEdge` | An edge of the mapping: source, target, a `Transition`, and the prior/post action slots — an `ActionRef.Bound` or the empty slot `ActionRef.None`. |
| `Submachine` | A named automaton fragment with its own initial/accepting states; called via `call(...)` and returning via a synthetic return transition. |
| `Action` / `ActionRef` / `ActionHandler` | A named action with its Kotlin handler; `ActionRef.Bound(name, args)` references it with arguments on an edge, `ActionRef.None` is the empty slot, while `Free`/`Any`/`Named` filter an action slot in a query or a removal. |
| `Arity` | How many arguments an action is called with: `Arity.Any` or `Arity.Exactly(n)`. |
| `DomainSize` | How many values a filter may select: `DomainSize.Bounded(count)` or `DomainSize.Unbounded`. |
| `Stack` | The call stack backing submachine call/return discipline. |
| `Actions` / `Submachines` | The collections of an automaton's actions and submachines. Both are iterable, and both answer `size` and `contains` directly. |
| `TransitionMapping` | The **live** set of edges: `val transitions`, `val size`, `contains(id)`, and the mutation sinks. |
| `Topology` | The **immutable snapshot** of a topology: its edges, its submachines, and the queries (`edgesFrom`, `edgesConsuming`, `matching`, …). |
| `TransitionPattern` | The single implementation of what a filter means: `matches(edge)` answers it, edge by edge. |
| `RecognitionPath` | One recognizer path: the `trail` of `TransitionEdge`s it walked, its cursor, its state, and the `PathOutcome` verdict (`PENDING`, `ACCEPTED`, `REJECTED`, `CUT` or `FAILED`). |
| `RecognitionResult` | What `recognize()` returns: the verdict (`accepted`) and the paths it was made of. |
| `AdaptiveAutomaton` | The automaton itself, plus the recognition driver, the `stopOnFirstAcceptance` flag and the `limits` backstop. |
| `ExecutionVisitor` | The recognition-event interface: no-op defaults for every event, registered through `AdaptiveAutomaton.visitors`. |
| `DotExporter` / `GraphVizVisitor` | GraphViz DOT rendering of a topology, and the visitor that emits a snapshot on every mutation. |

## The recognition model, precisely

These semantics come straight from the original Java implementation and are preserved:

1. **Input** is a list of `Symbol`; no string parsing is imposed on you.
2. A **prior action** runs *before* its edge is applied and **may mutate the transition
   mapping**; if the action removes the very edge the path was about to traverse, the
   path **re-plans**: it stays at the source state of the removed edge and chooses again
   among the transitions applicable there. The prior action does not fire twice.
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
7. **Acceptance is tested only when the path is stuck.** A path that is accepting but
   still has a move available keeps moving: an accepting state with an outgoing epsilon
   edge does **not** accept, because the path never comes to rest there.
8. **A path that re-enters a configuration it already left is cut.** The engine proves
   that such a branch cannot reach a verdict — acceptance is only ever tested from a
   stuck position — and prunes it, reporting `PathOutcome.Cut`. The cut is neither an
   acceptance nor a rejection.

> **Call-out**: because forked paths get copies, an adaptive action executed on one path
> cannot undo a mutation performed on a sibling path. Effects are per-path, which is
> precisely what gives the device its expressive power (a path can "check" a hypothesis
> without leaving traces on the others).

## Non-determinism and path control

Anything ambiguous forks paths. The default is **exhaustive**: every path runs to
completion, and the result carries them all.

```kotlin
val result = ka.recognize(convert("abab"))
println(result.accepted)                       // the verdict
println(result.paths)                          // every path that reached a verdict
println(result.acceptedPaths)                  // the ones that accepted
println(result.rejectedPaths)                  // the ones that rejected
println(result.cutPaths)                       // the ones the engine pruned
```

Set `stopOnFirstAcceptance = true` to stop as soon as **one path accepts**:

```kotlin
ka.stopOnFirstAcceptance = true
val result = ka.recognize(convert("abab"))
```

Only an acceptance is decisive. Under the existence semantics of the automaton, a
*rejecting* path says nothing about the branches still in flight, so stopping on one
would answer "rejected" for an input the automaton does accept. The old
`stopAtFirstFinishedRecognitionPath` flag did exactly that, and could return a wrong
`false`; it is gone, and its replacement fires on acceptance only.

> **Hint**: the flag is a property, not a method: `stopOnFirstAcceptance = true`. The
> Java-style `setStopAtFirstResult(true)` setter was deleted — it was a second way to
> set the same thing.

### Epsilon cycles: rejected, warned, or cut

An epsilon cycle (a cycle of transitions that consume nothing) makes a path run forever.
The engine answers it in two layers:

- **Statically**, the validator looks for a *reachable* epsilon-only cycle. If the
  automaton registers **no** adaptive actions, nothing can change the topology during the
  run, so the cycle is provably non-terminating: the automaton is **rejected** at
  `recognize()` time. With actions present the cycle is **warned** about (SLF4J), because
  an action may legitimately break it.
- **At runtime**, a path that re-enters a configuration it already left is **cut**: its
  outcome becomes `PathOutcome.Cut`, and the exploration carries on with the other
  branches. A cut discards no verdict, because acceptance is only ever tested from a
  stuck position, and a path that revisits a configuration was never stuck there.

The cut is guarded by an **action epoch**: if an action fired since the ancestor visit,
the future is not provably identical, so no cut happens and the action gets its chance.
That is what keeps the one-shot idiom (a prior action that removes its own edge) working.

For the topologies the engine cannot *prove* to be bounded — those whose configuration
never repeats because an action keeps changing it — a structural backstop applies:
`limits` bounds the live paths, the steps per path and the call depth. When a bound
trips, the engine **throws** `IllegalStateException`: the answer there is "unknown", not
"rejected". There is no wall-clock timeout anywhere in the engine.

```kotlin
aa.limits = RecognitionLimits(maxLivePaths = 10_000, maxStepsPerPath = 10_000, maxCallDepth = 1_000)
```

## Module layout

| Path | Contents |
|---|---|
| `kdaptive/src/main/kotlin/…/model/` | `State`, `Symbol`, `CharSymbol`, `Transition`, `TransitionEdge`, `Submachine`, `Action`, `Stack`, plus `Observation`, `MappingOperation`, `TransitionPattern`, `Topology`, `DomainSize`, `Arity`, and the `Actions`, `Submachines` and `TransitionMapping` collections, with `ElementaryActions` and `Predicates` |
| `kdaptive/src/main/kotlin/…/execution/` | `AdaptiveAutomaton` (the public entry point), `PathExecutor`, `PathState`, `ExecutionVisitor` (the recognition-event interface), `RecognitionPath`, `PathOutcome`, `RecognitionResult`, `RecognitionLimits`, `ForkedPath` |
| `kdaptive/src/main/kotlin/…/dot/` | `DotExporter`, `GraphVizVisitor` (the GraphViz DOT export) |
| `kdaptive/src/main/kotlin/…/dsl/` | `AutomatonDsl.kt`: the `automaton { }` builder and `specToAutomaton` |
| `kdaptive/src/main/kotlin/…/logging/` | the SLF4J facade (`val <T> Any.logger`) |
| `kdaptive/src/test/kotlin/` | mirrors the main source set, with unit, error-path and coverage tests |
| `examples/` | the `:examples` module: 16 executable examples, depending on the root module |
| `benchmarks/` | the `:benchmarks` module: the scaling benchmarks of the engine |

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

## Build & develop

All commands assume the Gradle wrapper:

```bash
./gradlew build                 # compile + test + detekt + Spotless check + Kover verify
./gradlew clean build           # full rebuild
./gradlew test                  # tests of every module (library + examples)
./gradlew :test                 # library tests only
./gradlew :examples:test        # example tests only
./gradlew apiCheck              # binary compatibility against the baseline in api/
./gradlew changelogPrint        # the last release and the version derived from the changelog
./gradlew changelogCheck        # the changelog follows the conventions (part of `check`)
./gradlew dokkaGenerateHtml     # API docs in ./build/dokka/html, warnings fail the build
./gradlew koverHtmlReport       # coverage report → build/reports/kover/html/index.html
./gradlew :benchmarks:run       # the scaling benchmarks of the engine
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
- **Binary compatibility**: `apiCheck` compares the compiled API against the baseline in
  `api/kdaptive.api` and fails on an accidental break.
- **The changelog**: `changelogCheck` verifies that `CHANGELOG.md` follows the conventions
  of the format, and the version of the build is derived from it — the changelog is the
  single source of truth of the version.
- **Supply chain**: Gradle dependency verification (`gradle/verification-metadata.xml`)
  checks every dependency checksum, and the wrapper distribution carries its own
  SHA-256.

## Compatibility

- The bytecode is Java 11; Kotlin language/api level 2.4.0. Kotlin 2.4.21 is the compiler.
- No Android-specific code; it should run on the JVM and in Android modules that ship
  Java 11+ or desugared Kotlin stdlib.
- Choose any SLF4J backend you like (`slf4j-simple` is prewired for tests/examples).

### The null-free doctrine

**No public member of the library has a nullable type.** Not a filter, not a value, not a
marker, not a configuration slot. "Not configured yet" is a real state, but it is a
private one: it surfaces as an exception, never as a `null` the consumer has to guard
against.

```kotlin
aa.mainSubmachine = "M"        // fine
aa.mainSubmachine               // IllegalStateException: Main submachine was not declared.
```

The doctrine is enforced by a test that sweeps the compiled library with reflection and
fails on any public nullable type, so it cannot regress silently. The only nulls left in
the library are private implementation details (the lazy by-source index, the private
"not configured yet" slots) and the standard `equals(other: Any?)` override the language
itself mandates.

### Release notes

The release notes live in [`CHANGELOG.md`](CHANGELOG.md), which is also the **source of
truth of the version**: the build reads the changelog and derives the version of the
release it describes.

- The **migration table** of the last breaking release (6.0.0) is in the
  [changelog](CHANGELOG.md#600---2026-11-12).
- The **notes of every release**, from 1.0.0 to today, are in the same file.
- The **public API** of a release is the set of declarations in `api/kdaptive.api`; a
  release that changes it is breaking, whatever its number says.
- A release is declared by promoting the changelog's `[Unreleased]` section to a version
  heading (`./gradlew changelogBump -Prelease=true`); the build reads the heading, and
  the tag names it.

## License

kdaptive is free software: you can redistribute it and/or modify it under the terms of
the GNU General Public License as published by the Free Software Foundation, either
version 3 of the License, or (at your option) any later version (SPDX:
`GPL-3.0-or-later`). The full license text is in the [LICENSE](LICENSE) file.

Every source file carries the `// SPDX-License-Identifier: GPL-3.0-or-later` header;
the README and the guides are marked with the same identifier.
