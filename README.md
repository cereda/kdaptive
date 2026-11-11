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
| Tests | `kotlin("test")` over JUnit 5 (514 tests: 385 library, 129 examples) |
| Static analysis | detekt 1.23.8, **zero-tolerance** config |
| Format / style | Spotless, ktlint rules, 120-col, SPDX license header |
| Docs | Dokka HTML (`./gradlew dokkaGenerateHtml`, fail-on-warning) |
| Coverage | Kover: line ≥ 95%, branch ≥ 78% enforced on `check` |
| API compatibility | binary-compatibility validator, baseline in `api/kdaptive.api` (`./gradlew apiCheck`) |
| Supply chain | Gradle dependency verification (`gradle/verification-metadata.xml`) + wrapper checksum |
| Logging | SLF4J API only (backend chosen by the consumer) |

Coordinates: `br.usp.poli.lta.cereda:kdaptive:5.0.0`.

> **Remark**: the build currently **does not ship a publishing plugin**. The library jar
> lands in `./build/libs/kdaptive-5.0.0.jar` (next to `kdaptive-5.0.0-sources.jar`) after
> `./gradlew build`, and `:examples`
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
  `PathOutcome.FAILED`, and the recognition reports `onRecognizeFailed` instead of
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
per family: `TransitionPatternKind.SYMBOL`, `TransitionPatternKind.EPSILON`,
`TransitionPatternKind.MIXED` and `TransitionPatternKind.SUBMACHINE` give the same filter
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
   stuck position — and prunes it, reporting `PathOutcome.CUT`. The cut is neither an
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
  outcome becomes `PathOutcome.CUT`, and the exploration carries on with the other
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
- **Binary compatibility**: `apiCheck` compares the compiled API against the baseline in
  `api/kdaptive.api` and fails on an accidental break.
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
aa.mainSubmachine               // IllegalStateException: Main submachine cannot be null.
```

The doctrine is enforced by a test that sweeps the compiled library with reflection and
fails on any public nullable type, so it cannot regress silently. The only nulls left in
the library are private implementation details (the lazy by-source index, the private
"not configured yet" slots) and the standard `equals(other: Any?)` override the language
itself mandates.

### Changes in 5.0.0

This is a **breaking** release. It fixes the honesty of the failure path, makes the
engine linear instead of quadratic, tidies the package layout, and hardens the build.

#### Correctness: a failure is not a rejection

- **`PathOutcome` gains `FAILED`.** A path that died before reaching a verdict — an
  action threw, or a structural bound tripped — used to be reported as `REJECTED`.
  That was a lie: the engine had decided *nothing* about the input, and "rejected"
  claims it did. The path now reports `FAILED`, and the failure reaches the caller as
  the `IllegalStateException` it always was.
- **`ExecutionVisitor.onRecognizeEnd` takes a `RecognitionResult`, not a `Boolean`,
  and a new `onRecognizeFailed(cause)` reports a recognition that died.** The two
  events are **exclusive**: a recognition that started always ends with exactly one of
  them, so a visitor can rely on the pairing.
- **The identifier spaces are unified.** The engine used to number kernel identifiers
  and recognition-path identifiers from two counters, which could hand the same number
  to two different kinds of object. There is one counter per kind of object now, and
  the counters are atomic, so two recognitions running concurrently never collide.
- **`Submachines.copy()` shares the submachine instances**, like `Actions.copy()`
  already did. A copy is a copy of the *collection*, not of every element in it.

#### Performance: linear, not quadratic

The engine was rewritten around a **copy-on-write topology** and an **indexed
lineage**. The measurements on the reference benchmarks:

| Benchmark | 4.0.0 | 5.0.0 | Speed-up |
|---|---|---|---|
| 40 000-step chain | 4 236 ms | **43 ms** | **97×** |
| 4 000 forks | 2 403 ms | **41 ms** | **59×** |
| Cost of one extra step | grows 1.6× per doubling | **flat** | — |
| Cost of one extra fork | grows 47 → 601 µs | **flat ≈ 9 µs** | — |

- **The trail is appended to, not rebuilt.** A path used to copy its whole history on
  every traversal; it now appends to a list and flattens it once, at the verdict.
- **The lineage is an index, not a chain.** Detecting a repeated configuration walked
  a linked list of ancestors; it is now a map lookup, and the map is shared
  copy-on-write between forked siblings.
- **The topology is copy-on-write.** A forked path used to duplicate every edge of
  the mapping; now the two mappings hold the same edges until one of them is mutated.
  The edges themselves are immutable and their identifiers are unique, so sharing them
  is safe.
- **Edges are indexed by identifier**, so `contains(id)` and `removeFromIdentifier(id)`
  are constant-time instead of linear scans.
- **The topology snapshot is built only when someone wants it.** A recognition with no
  visitor registered never builds a `Topology`.
- **The automaton is copied once per recognition**, not once per path.
- **The call stack caches its snapshot**, because the engine reads it on every step
  and it changes far less often than it is read.

#### Cleanup: the package layout and the names

The `utils` package is **gone**, and two sub-packages of `model` are flattened into
`model`. The `*Utils` classes were renamed:

| Before (4.0.0) | After (5.0.0) |
|---|---|
| `br.usp.poli.lta.cereda.kdaptive.utils.RecognitionPath` | `br.usp.poli.lta.cereda.kdaptive.execution.RecognitionPath` |
| `br.usp.poli.lta.cereda.kdaptive.utils.PathOutcome` | `br.usp.poli.lta.cereda.kdaptive.execution.PathOutcome` |
| `br.usp.poli.lta.cereda.kdaptive.utils.IdentifierUtils` | `br.usp.poli.lta.cereda.kdaptive.model.Identifiers` (**internal**) |
| `br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions` | `br.usp.poli.lta.cereda.kdaptive.model.ElementaryActions` |
| `br.usp.poli.lta.cereda.kdaptive.model.predicates.*` | `br.usp.poli.lta.cereda.kdaptive.model.*` |

The remaining renames:

| Before (4.0.0) | After (5.0.0) | Why |
|---|---|---|
| `PathExecutorConfig` | `PathState` | It is not a configuration: it is the mutable state of a running path, and it is not a data class any more. |
| `PathExecutor.threads` | `PathExecutor.worklist` | It was never a list of threads — the engine is iterative and single-threaded per path. |
| `AdaptiveAutomaton.stack` | `AdaptiveAutomaton.initialStack` | It is the seed a recognition starts from, not the stack of a running path. |
| `RecognitionPath.path` | `RecognitionPath.trail` | A path has *one* trail; the name said "path" about a property of a path. |
| `IdentifierUtils` (public) | `Identifiers` (internal) | The identifiers it hands out are public API; the generator is not. |
| `ExampleUtils.kt` (examples) | `ExampleSupport.kt` | It is support code for the examples, not a utility class. |

`AdaptiveAutomaton.transitions`, `.submachines` and `.actions` are now `var` with a
`private set`: the engine replaces them wholesale when it clones the automaton, and
the consumer may still read them.

#### Good practices: the build defends itself

- **Gradle 9.9.1**, which carries the fix for the "version catalog values run as code
  in generated accessors" vulnerability (critical) and two high-severity ones. The
  project uses `gradle/libs.versions.toml`, so it applies directly.
- **The wrapper distribution carries a SHA-256 checksum**, so a tampered distribution
  is refused.
- **Gradle dependency verification** (`gradle/verification-metadata.xml`) checks every
  dependency checksum on every build.
- **Binary-compatibility validation**: the compiled API is compared against the
  baseline in `api/kdaptive.api`, and `apiCheck` fails on an accidental break. The
  baseline is regenerated deliberately with `apiDump`.
- **The library ships its sources** next to the jar
  (`kdaptive-5.0.0-sources.jar`).
- **Six detekt rules are re-enabled** (`TooGenericExceptionCaught`,
  `EndOfSentenceFormat`, `UnconditionalJumpStatementInLoop`,
  `StringLiteralDuplication`, `VariableMaxLength`, `VariableMinLength`): the code
  already complied with all of them.

### Changes in 4.0.0

This is a **breaking** release. It answers the epsilon-cycle question, turns the
recognition into a result object, completes the null-demotion sweep, and brings the
API to Kotlin idiom.

- **The epsilon cycle question has an answer.** A reachable epsilon-only cycle is now
  **rejected at validation time** when the automaton registers no adaptive actions
  (nothing can then change the topology, so the cycle is provably non-terminating), and
  **warned about** when actions are present (an action may legitimately break it). At
  runtime, a path that re-enters a configuration it already left is **cut**: its outcome
  becomes the new `PathOutcome.CUT`, and the other branches carry on. A cut discards no
  verdict: acceptance is only ever tested from a stuck position, and a path that
  revisits a configuration was never stuck there. The cut is guarded by an **action
  epoch**, so an action that fires in between keeps its chance to break the cycle.
- **A structural backstop replaces the hang.** `AdaptiveAutomaton.limits` carries
  `RecognitionLimits(maxLivePaths, maxStepsPerPath, maxCallDepth)`. When a bound trips,
  the engine **throws** `IllegalStateException` — the answer there is "unknown", not
  "rejected". There is **no wall-clock timeout** anywhere in the engine.
- **`recognize()` returns a `RecognitionResult`** instead of a `Boolean`. It carries
  `accepted` and `paths`, plus `acceptedPaths`, `rejectedPaths` and `cutPaths`.
- **`getRecognitionMap()` and `getRecognitionPaths()` are deleted.** The result carries
  the paths, and the "call `recognize()` first" exception is gone with them. Two
  recognitions running concurrently on one automaton no longer share any state.
- **`stopAtFirstFinishedRecognitionPath` → `stopOnFirstAcceptance`**, and its semantics
  are fixed: it fires on an **acceptance only**. The old flag stopped on any finished
  path, including a rejection, and could therefore answer a wrong `false`.
- **`RecognitionPath.path` is a `List<TransitionEdge>`** and now records **every**
  traversal, for every path — including the synthesised `Transition.Return` edges. It
  used to be a list of renderings, and it used to stay empty on deterministic runs.
- **`PathOutcome` gains `CUT`**, and `ExecutionVisitor.onPathEnd` takes a `PathOutcome`
  instead of a `Boolean`: a cut is neither an acceptance nor a rejection, and the
  visitor can tell them apart.
- **`ExecutionVisitor.onEdgeAdded` / `onEdgeRemoved` hand out an immutable `Topology`**
  instead of the live mutable mapping. A visitor can no longer reach into the running
  recognition and change it.
- **`DotExporter.toDot` takes a `Topology`.**
- **`TransitionPattern.matches(edge)`** is the single implementation of what a filter
  means. The 14 `TransitionMapping.withX(...)` filter methods are **deleted**: plain
  topology queries live on `Topology` (`edgesFrom`, `edgesConsuming`, `callsTo`,
  `epsilonEdges`, `edgesWithPrior`, …), and pattern queries live on the pattern.
- **`DomainSize` is a sealed type.** The six `*DomainSize` properties of
  `TransitionPattern` are `DomainSize.Bounded(count)` or `DomainSize.Unbounded` instead
  of `Int?`, so "unbounded" is no longer conflated with "empty".
- **`Arity` replaces the nullable arity.** `action("A", arity = n)` becomes
  `action("A", arity = Arity.Exactly(n))`; the wildcard is `Arity.Any`.
- **The public API is null-free.** `RecognitionPath.state` is a `State`;
  `AutomatonSpec.main` is a required `String`; `TopologyValidator.validate` takes a
  non-null main; `AdaptiveAutomaton.mainSubmachine` and `AutomatonBuilder.main` are
  non-null properties over private slots, with fail-fast getters.
- **`model.actions.requireNonNull` is deleted**, and `asState()` / `asSymbol()` take
  their receiver as `Any` instead of `Any?`.
- **`addCall` validates the names of a `SubmachineRef.Domain`** against the declared
  submachines and throws naming the offender, instead of failing later at runtime.
- **The Kotlin-idiom pass renames the accessors:**

  | Before (3.0.0) | After (4.0.0) |
  |---|---|
  | `TransitionMapping.getTransitions()` | `val transitions` |
  | `TransitionMapping.size()` | `val size` |
  | `TransitionMapping.hasIdentifier(id)` | `fun contains(id)` |
  | `ActionsSet.getActions()` / `.setActions(x)` / `.size()` | the `Actions` collection itself: iterate it, `val size`, `fun replaceAll(x)` |
  | `SubmachinesSet.getSubmachines()` / `.setSubmachines(x)` / `.size()` | the `Submachines` collection itself: iterate it, `val size`, `fun replaceAll(x)` |
  | `SubmachinesSet.getFromName(n)` / `.getFromState(s)` | `fun fromName(n)` / `fun fromState(s)` |
  | `ActionsSet` / `SubmachinesSet` (in the `model.sets` package) | `Actions` / `Submachines` (in the `model` package) |
  | `Stack.isEmpty()` / `.elementsList()` / `.top()` | `val isEmpty` / `val elements` / `fun peek()` |
  | `AdaptiveAutomaton.setStopAtFirstResult(flag)` | deleted — the property is the only way |

  `Actions` and `Submachines` are now **iterable collections**: there is no second,
  more official way to reach their contents, so `aa.submachines.submachines` no longer
  exists.

### Changes in 3.0.0

This is a **breaking** release that demotes `null` from the model: **filters and values
are null-free**, while configuration and genuine optionality keep their nulls.

- **`SubmachineRef` replaces the nullable submachine filter.** The call family used to
  spell "any call" as `submachine = null` — and in the other families `null` meant "not
  applicable", so one spelling had two readings. The filter is now a value:
  `SubmachineRef.Any` (any call), `SubmachineRef.Named("M")`, `SubmachineRef.Domain`
  (built with `SubmachineRef.anyOf("M", "N")`) and `SubmachineRef.None` (the filter
  does not apply). `queryCall`, `removeCall` and `CallSpec` take it; `queryCall(None)`
  is rejected as meaningless.
- **The call family is set-valued, like the other two.** `SubmachineRef.anyOf(...)`
  names several submachines at once, and `addCall` over a domain adds **one edge per
  submachine**, crossing the state domains — the Cartesian product, one reported
  operation per edge inserted.
- **`addCall` takes a `SubmachineRef`** and rejects the wildcards `Any` and `None`: an
  addition names what it adds. `Transition.Call(submachine: String)` keeps a plain
  string — an edge calls exactly one submachine, so it holds a value, not a filter.
- **The family of a pattern is derived from its filters.** `TransitionPattern.kind` is
  now a computed property: a pattern naming a submachine belongs to the call family,
  and a pattern naming none belongs to the symbol family, whose kind follows the symbol
  filter. A pattern can no longer contradict itself, and `TransitionPatternKind` no
  longer has to be passed in.
- **The action slots of an edge hold values.** `TransitionEdge.prior` and `.post` are
  non-null `ActionRef`s: `ActionRef.None` is the empty slot, and only an
  `ActionRef.Bound` may be attached to an edge — the filter-only markers (`Free`,
  `Any`, `Named`) are rejected when the edge is built. The equality of an edge is
  unchanged.
- **`Transition.sourceState` and `Transition.targetState` are gone.** A transition
  carries its payload only; the endpoints belong to the edge. The old copies could
  contradict their own edge, and an edge declared with a bare transition was a
  *different* edge from the same edge declared with its endpoints — now they are one
  edge, and adding it twice is a no-op.
- **The sinks of the transition mapping are never absent.** `onEdgeAdded`,
  `onEdgeRemoved`, `onOperation` and `onObservation` are non-null callbacks that
  default to a no-op: "nobody is listening" is a callback that does nothing, not a
  missing one.
- **A path verdict names itself.** `RecognitionPath.result: Boolean?` became
  `RecognitionPath.outcome: PathOutcome`, with `PENDING`, `ACCEPTED` and `REJECTED`.
  `recognize()` still answers `true`/`false`: a path has three outcomes, an automaton
  has one verdict.
- **The builder APIs lose their last null.** `consume`, `epsilon`, `call` and
  `step.to` take `prior`/`post` as `Any = ActionRef.None`; `null` no longer compiles.
  The `mainSubmachine` and `AutomatonSpec.main` configuration slots, and the state of
  a path that has not started, keep their nulls — those are genuine optionality, and
  they say so.

### Changes in 2.1.0

This release completes the work begun in 2.0.0: the equality contract is explicit, the
engine is safe under concurrent use, the two APIs validate the same topologies, a prior
action that removes its own edge no longer kills the path, and the elementary actions
learned **set-valued domains**.

- **Filters may name a set of values.** `State.anyOf(q0, q1)` and
  `Symbol.anyOf(sym('a'), sym('e'))` build a **domain**: a filter marker that selects
  every member it names. A domain may name `Symbol.Epsilon` among its members, and then
  it selects the epsilon edges as well; it may not contain a wildcard, another domain,
  or nothing at all. An addition over domains produces the **Cartesian product** of its
  domains — one edge per combination — and reports **one operation per edge inserted**.
- **The pattern reports the size of every domain.** `TransitionPattern` now carries
  `sourceDomainSize`, `symbolDomainSize`, `targetDomainSize`, `submachineDomainSize`,
  `priorDomainSize` and `postDomainSize`: a concrete filter reports `1`, a domain
  reports the number of its members, and a wildcard reports `null` (unbounded). These
  are facts, not costs — the consumer decides what they are worth. The example
  `CostVisitor` was re-derived on top of them.
- **`TransitionPatternKind.MIXED` was added**, for a symbol domain that names the empty
  word among its members: such a filter selects symbol-consuming *and* epsilon edges.
- **`MappingOperation` exposes its `pattern` uniformly**, so a consumer can read the
  filters of any operation without a `when` over the three cases.
- **The equality contract is explicit.** The identifiers the library hands out — the
  edge id, the submachine id, the action id — are *bookkeeping*, not identity: they are
  outside the equality of the object they belong to. `Action.id` moved out of the
  constructor, and `State` and `Symbol` now document that the library compares them by
  value.
- **The engine is safe under concurrent use.** The identifiers are generated atomically,
  the recognition results are published only once the recognition is over, and the
  visitor list is snapshotted: two recognitions may run at the same time on the same
  automaton.
- **The two APIs validate the same topologies.** The rules live in one place
  (`TopologyValidator`), so the builder DSL and the imperative API reject the same
  automata, with the same messages. A domain is not a concrete state: it is rejected
  wherever a concrete state is required.
- **A prior action that removes its own edge no longer kills the path.** The path
  re-plans from the source state of the removed edge and chooses again; the prior action
  does not fire twice. (This also fixed a latent defect: a forked path referenced the
  parent's edge object, whose identifier is not in the child's own copy of the mapping.)

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
  action slot). The `submachine` filter of the call family was the last one to fall;
  since 3.0.0 it is a `SubmachineRef` (see "Changes in 3.0.0").
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
