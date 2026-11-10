<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# Adaptive actions guide: the automaton that modifies itself

The DSL guide ([DSL-GUIDE.md](DSL-GUIDE.md)) covered how to describe an automaton.
This guide covers what makes the model *adaptive*: transitions may carry code,
**prior** and/or **post actions**, code that runs alongside the recognition process and can
query, remove and insert edges of the very transition mapping the recognizer is
currently traversing.

Everything below is faithful to the original 2016 Java implementation; the Kotlin port
reorganized the pieces but did not change the semantics. Precision matters here, so this
guide is deliberately thorough and filled with warnings earned the hard way.

---

## 1. The three primitives

An adaptive action can do one of exactly three things to the transition mapping:

1. **query**: find edges matching a pattern (returns the matches; no mutation),
2. **remove**: delete matching edges (one-shot edges, consumed alternatives),
3. **add**: insert fresh edges (re-arm consumed edges, spawn new structures, explode).

The class that does this is `ElementaryActions`:

```kotlin
import br.usp.poli.lta.cereda.kdaptive.model.actions.ElementaryActions

val ea = ElementaryActions(transitions)   // wrap the mapping you were handed
```

> **Remark**: `ElementaryActions` is a *facade*: it adds zero state and zero policy.
> It wraps an existing `TransitionMapping` so every call operates in place. Always
> construct it with the mapping **given to your handler**, not some cached copy;
> see §2 for who owns what.

---

## 2. Actions, references, and who runs what

### Registering an action

```kotlin
import br.usp.poli.lta.cereda.kdaptive.model.Action

override fun setup() {
    actions.add(
        Action(name = "A") { transitions, transition, parameters ->
            // transitions : TransitionMapping (MUTATE THIS, it is the live set)
            // transition  : Transition (the sealed kind of the edge being applied)
            // parameters  : List<Any> (the arguments attached at the reference site)
            ...
        },
    )
}
```

The handler type is `ActionHandler = (transitions: TransitionMapping, transition: Transition, parameters: List<Any>) -> Unit`.
Note the second parameter is the **inner `Transition`** (the sealed kind), not the
`TransitionEdge`: you are told *what kind of move is being made* (`SymbolTransition`,
`Epsilon`, `Call`, `Return`), and you can interrogate it with a `when`:

```kotlin
val symbol = when (transition) {
    is Transition.SymbolTransition -> transition.symbol
    is Transition.Epsilon  -> null
    is Transition.Call     -> null
    is Transition.Return   -> null
}
```

The builder DSL has its own registration form, where the handler receives a
`DslActionContext` receiver instead of the raw mapping:

```kotlin
val spec = automaton {
    submachine("M") {
        initial("q0")
        state("q1")
        state("q2")
    }
    action("A") { transition, parameters ->
        ea.add(state("q1"), CharSymbol('c'), state("q2"))   // ea: ElementaryActions
        // freshState("g") allocates a unique state, ref("A", x) builds a runtime reference
    }
    main("M")
}
```

`ea` is the same `ElementaryActions` facade described in §4, bound to the live
mapping of the running path; `freshState(prefix)` replaces the "counter of state
names" idiom with a per-run, per-automaton factory that never collides with the
states of the specification; `ref(name, args...)` builds an `ActionRef` for arguments
that only exist at runtime; `state(name)` looks a state of the specification up by
name and throws when the name is unknown, so a handler can never invent a state.

The builder DSL adds several build-time checks on top of that: `ref(name, "q2")`
rejects a state name that is not part of the automaton (typos fail loudly instead of
creating a phantom state), `action(name, arity = n)` rejects references that bind a
different number of arguments, and `toSpec()` rejects an edge endpoint owned by no
submachine, a call naming an unknown submachine, a call returning to a state of
another submachine and a missing main submachine. The full rules are in the
[DSL guide](DSL-GUIDE.md#adaptive-actions-in-style-b).

### Referencing an action from an edge

Equivalent ways to hang an action on an edge (`prior` = before the move,
`post` = after the move):

```kotlin
consume(q0, 'a', q1, prior = "A")            // name only, zero arguments
consume(q0, 'a', q1, prior = actionRef("A", p1, p2))   // name + arguments
consume(q0, 'a', q1, prior = "A", post = "B")          // both slots
from(q0) { symbol('a', q1, prior = "A") }    // group-block form
epsilon(q0, q1, post = "A")                  // epsilon edges carry actions too
call(q0, "S", q1, prior = "A")               // and so do submachine calls
```

All of them resolve to `ActionRef(name: String?, args: List<Any>)`.

> **Pitfall**: an `ActionRef` with a null **name** is a runtime error, not a modeling
> choice: name-less references are rejected loudly by the executor ("Prior action on
> transition N has no name; an action reference must name a registered action."). The
> same check exists for post actions. This is deliberate: the original implementation
> silently stopped; the port fails fast instead.

### Firing order, precisely

For each edge the path is about to apply:

1. the **prior** action fires (if the edge has one);
2. the **edge is applied** (state moves, cursor advances / stack pushed or popped);
3. the **post** action fires (if the edge has one);
4. the path looks for the next applicable transition (or terminates).

This holds for every kind of edge, epsilon and submachine calls included. On a call,
the prior fires **before** the return address is pushed and the post **after** the jump
into the called submachine.

Two subtle behavioural consequences worth memorizing:

- **A prior action may consume its own edge.** Prior actions mutate the mapping *before*
  the edge is applied. If the action removes its own edge (`ea.remove(...)` matching the
  edge being traversed, a classic one-shot idiom), the path does **not** crash: the
  engine re-checks, notices the edge is gone, and treats the path as *stuck in the
  current configuration*; normal termination rules apply from there. Depending on the
  configuration, a stuck path either rejects, returns from the submachine, or even
  **accepts** (if the configuration was already terminal). This asymmetry (prior
  removal kills the edge; post removal only affects what comes next) is the original
  device's semantics.
- **Nothing makes a prior once-only.** A prior fires every time its edge is about to be
  applied: so an edge inside a consuming loop fires its prior again on every cycle.
  If you want a fire-once effect, remove the edge as part of the action (§6.1), or make
  the handler detect-and-bail out on its own idempotency.

> **Pitfall**: exceptions inside an action handler are NOT swallowed. They propagate
> and wrap into `IllegalStateException("Recognition path N failed: ...")` with the
> original exception attached. A throwing action kills the whole `recognize()` call.
> Handle your own errors inside the handler.

---

## 3. Mutation isolation: per-path, per-call

This is the single most important runtime-model fact:

1. `recognize()` **clones** the automaton (a fresh `newInstance()` receiving copies of
   the mapping, submachines, actions and stack). Your configured original is never
   touched; you can `recognize()` repeatedly and independently.
2. Each recognition path works on **copies** of those structures. When a path forks
   into multiple alternatives (nondeterminism), every fork receives a fresh `copy()`.
3. Therefore an adaptive action's mutations are visible **only on its own path**,
   not on sibling paths, not on other recognition calls, not on the configured
   automaton object.

> **Call-out**: this is a feature, not an overhead tax. isolated mutation lets one path
> "test a hypothesis" (mutate heavily, then die) without corrupting the others. It also
> means an action cannot simply modify *the* automaton; it modifies *its path's copy*.
> If you need out-of-band data (counters, token buffers), don't rely on the mapping,
> use Kotlin state captured **by the handler closure** (see §6.3).

> **Warning**: `Action` handlers are shared objects across all copies of the actions
> set (`actions.copy()` copies the *references*, not the lambdas). State captured by a
> handler closure is shared by every path running that action. Mutating such captured
> state inside an action creates cross-path coupling you must reason about yourself.

> **Hint**: the per-path copies are observable too. The `ExecutionVisitor` events
> `onEdgeAdded` / `onEdgeRemoved` report the `pathId` of the path that mutated, so a
> debugger can watch one path's topology evolve without confusing it with a sibling's.
> See §7.1.

---

## 4. `ElementaryActions`, the complete API

Signatures are grouped; `null` **always means wildcard** ("match anything") and a
non-null parameter always acts as a filter.

The API is organised in two **families**, one per kind of transition:

| Family | Methods | Works on |
|---|---|---|
| symbol | `query`, `remove`, `add` | symbol-consuming transitions |
| call | `queryCall` | submachine-call transitions |

Each family offers **at most one overload per arity**, and the filters are always laid
out the same way:

```
prior-action filter → source → transition filter → target → post-action filter
```

The prior-action filter comes **first** and the post-action filter comes **last**,
bracketing the transition filters the way the actions themselves fire. Because there is
exactly one overload per arity, **no call is ever ambiguous** — not positionally, and
not with named arguments.

### 4.1 Symbol queries/removals

```kotlin
fun query(source: State?, symbol: Symbol?, target: State?): List<TransitionEdge>
fun query(priorAction: ActionRef?, source: State?, symbol: Symbol?, target: State?): List<TransitionEdge>
fun query(
    priorAction: ActionRef?, source: State?, symbol: Symbol?,
    target: State?, postAction: ActionRef?,
): List<TransitionEdge]

fun remove(source: State?, symbol: Symbol?, target: State?)
fun remove(priorAction: ActionRef?, source: State?, symbol: Symbol?, target: State?)
fun remove(
    priorAction: ActionRef?, source: State?, symbol: Symbol?,
    target: State?, postAction: ActionRef?,
)
```

and, using the builder block (unset fields stay wildcards):

```kotlin
val results = ea.query {
    target = p1            // source omitted → wildcard
    symbol = CharSymbol('b')
}

ea.remove {
    symbol = CharSymbol('b')
    target = p1
}

ea.query {
    symbol = CharSymbol('b')
    prior = actionRef("A")     // the prior filter comes first in the positional form
    post = actionRef("B")      // ... and the post filter last
}
```

> **Non-obvious, faithful to the original**: the "symbol" filters also match **epsilon**
> edges (`anyOf(listOf(symbol(symbol), isEpsilon))`). The original device treated
> `null`-ish symbol patterns as "any edges that could be reached without consuming",
> which includes spontaneous moves. If you want strictly symbol-consuming edges, filter
> the result yourself: `.filter { it.transition is Transition.SymbolTransition }`, or use
> the predicates API (§5).

### 4.2 Call queries (the matrix)

Submachine-call edges have their own family, under the name `queryCall`. A `null`
`submachine` matches any `Call` edge (`isSubmachineCall` predicate); a given name
matches only calls to that submachine. `null` prior/post references are wildcards too.

```kotlin
// source, submachine, target
fun queryCall(source: State?, submachine: String?, target: State?): List<TransitionEdge>

// prior reference, source, submachine, target
fun queryCall(priorAction: ActionRef?, source: State?, submachine: String?, target: State?): List<TransitionEdge>

// prior reference, source, submachine, target, post reference
fun queryCall(
    priorAction: ActionRef?, source: State?, submachine: String?,
    target: State?, postAction: ActionRef?,
): List<TransitionEdge>
```

or, using the builder block:

```kotlin
val results = ea.queryCall {
    submachine = "M"
    target = p2
    prior = actionRef("A")
}
```

> **Note**: `queryCall` is a separate method, not an overload of `query`. The two
> families used to share the name `query`, which made `ea.query(null, null, null)`
> ambiguous — the compiler could not tell a symbol query from a call query when every
> filter was a wildcard. Splitting the families is what makes the wildcard calls usable
> at all.

### 4.3 Action-reference matching rules (the argument algebra)

Matching a **prior/post reference filter** against an edge works like this
(private `actionRefPredicate` semantics, worth knowing precisely):

| Filter | Matches edges where… |
|---|---|
| `null` filter | wildcard (matches anything) |
| `ActionRef(name = "A")` (zero args) | the edge has a prior/post **named "A"**, regardless of how many arguments that reference-site carries (the "wildcard arity" rule) |
| `ActionRef(name = "A", x, y)` | name matches **and** the reference-site's arguments match **position-by-position** (`x` at 0, `y` at 1, using `equals`) **and** have exactly the same arity (2) |
| `ActionRef(name = null)` | the edge has **some** prior/post action at all (any name) |

> **Pitfall**: position matters, **type equality matters, not assignability**:
> `p1` matching `p1` compares with `equals` on `Any`; `CharSymbol('a')` does NOT match
> `Letter('a')` even if conceptually the same letter. Use your own value-equality
> symbols/states (so their `equals` is sane) and be deliberate about argument order.

### 4.4 Removals with action filters

```kotlin
fun remove(priorAction: ActionRef?, source: State?, symbol: Symbol?, target: State?)
fun remove(
    priorAction: ActionRef?, source: State?, symbol: Symbol?,
    target: State?, postAction: ActionRef?,
)
```

Same semantics as §4.1 with extra action filters. The builder block covers every
combination:

```kotlin
ea.remove {
    source = q1
    symbol = CharSymbol('a')
    target = q1
    prior = actionRef("A", p1, p2)     // setting `prior` routes to the 4-part removal
}

ea.remove {
    symbol = CharSymbol('a')
    post = actionRef("B")              // setting `post` routes to the 5-part removal
}
```

> **Note**: the prior-action filter comes **first**. The four-argument removal used to
> accept the prior filter both first and last; the two spellings declared the same
> parameter names in a different order, so even a fully named call was ambiguous. Only
> one survives.

### 4.5 Adding edges

```kotlin
fun add(source: State, symbol: Symbol, target: State)                       // symbol edge
fun add(source: State, symbol: Symbol, target: State, priorAction: ActionRef) // symbol edge with prior action
```

Both insert a `SymbolTransition` edge into the mapping (with a fresh edge id, i.e.
`IdentifierUtils.nextTransitionId()`). If you need to add *epsilon*, *call* or any
other edge kind, construct the `TransitionEdge` directly and `transitions.add(...)`:

```kotlin
transitions.add(
    TransitionEdge(source = p0, target = p1, transition = Transition.Call(submachine = "M")),
)
```

> **Pitfall**: the `add()` helpers create **symbol** edges only. For epsilon/call/return
> edges, build a `TransitionEdge` yourself and `transitions.add(edge)` directly. Also
> be careful when creating epsilon edges at runtime: an epsilon self-loop makes the
> query-for-next-step loop forever (see DSL guide, §4).

### 4.6 Removal is by identity, id-verified

Removals obtain the matching edges' **ids** first, then call
`removeFromIdentifier(id)` per edge; two-phase removal is what makes
`remove { ... }` safe while a loop iterates over queried results.

> **Warning**: `removeFromIdentifier` silently does nothing if the id is no longer
> present. Ids come from the `IdentifierUtils` singleton; do NOT invent them.

---

## 5. Predicates, the raw filter layer

`ElementaryActions` is built on a general predicate layer
(`br.usp.poli.lta.cereda.kdaptive.model.predicates`), public and fully usable:

```kotlin
// combinatorial
allOf(listOf(pred1, pred2))   // AND
anyOf(listOf(pred1, pred2))   // OR
not(pred)                     // logical negation

// kind-of-edge detectors
isEpsilon, isOrdinary, isSymbolConsumption, isSubmachineCall, isSubmachineReturn
hasPriorAction, hasPostAction

// field matchers (value equality)
sourceState(s), targetState(t), symbol(sym), submachineCall(name)
```

And `TransitionMapping` exposes the same idea ready-made:

```kotlin
val edges = transitions.withSourceState(q1)                  // O(1) via lazy index
val callEdges = transitions.withSubmachine("M")
val prior = transitions.withPriorAction("A")
val eps = transitions.withEpsilonFromSourceState(q0)
```

> **Hint**: a lazy by-source index (`withSourceState`) is invalidated automatically on
> add/remove; nothing for you to manage. It is also what forks of `PathExecutor` rely on.

---

## 6. Working idioms

### 6.1 One-shot self-removal

```kotlin
// The edge carries prior = actionRef("A"); after firing once, the edge disappears.
actions.add(Action(name = "A") { transitions, _, _ ->
    val ea = ElementaryActions(transitions)
    ea.remove { prior = actionRef("A") }   // wildcard everything but my own name
})
```

### 6.2 Language growth, the aⁿbⁿcⁿ pattern

The `ContextSensitiveLanguageExampleKt` orchestrates the classic bootstrap. Its action
`A` queries b/c edges landing on its argument states (`p1`, `p2`), removes them, and
inserts the next generation plus fresh recursive calls, driving the automaton's
self-expansion while the cursor advances. Study it as the golden reference for
playful, precise use of the query/remove/add trio. The example is written in the
declarative style: the action is registered with `action("A") { ... }` inside the
same `automaton { }` block that declares the states and edges, and its intermediate
states come from `freshState()` instead of a hand-rolled counter.

### 6.3 Stateful actions (token building, counters)

The mapping-mutation trio is not the whole story: the handler is an ordinary Kotlin
lambda, so it can close over your own data:

```kotlin
val tokens = mutableListOf<ExampleToken>()     // WARNING: shared by ALL paths (§3)!
actions.add(Action(name = "Lexer") { _, transition, _ ->
    when (transition) {
        is Transition.SymbolTransition -> {
            val s = transition.symbol
            if (s is CharSymbol && s.value.isLetter()) {
                // accumulate into builder state ...
            }
        }
        else -> {
            // transitions between lexer modes; end-of-token post-processing ...
        }
    }
})
```

> **Warning**: captured mutable state lives outside the per-path-copy isolation of §3.
> For lexers (single path, deterministic) this is exactly what you want; for
> nondeterministic recognizers it is a data race waiting to happen. Keep per-path
> bookkeeping *in the mapping* (edges you can query), keep global bookkeeping only when
> the process is single-pathed or read-mostly.

---

## 7. Debugging

- `TransitionEdge.toString()` renders arrows: `TransitionEdge(id=2, q1 -[call Sub]-> q2, prior=A, post=B)`,
  `q1 --> q2` for epsilon, `q0 -[a]-> q1` for characters. `TransitionMapping.toString()`
  and `AdaptiveAutomaton.toString()` render labeled, indented dumps, safe to print from
  any point inside actions (they only read, never mutate).
- `RecognitionPath(id=..., result=true) { step-1, step-2 }` logs the transitions a
  terminating path took. After `recognize()`, `getRecognitionPaths()` returns them all;
  before it, calls throw (fail fast).
- SLF4J debug logging in `PathExecutor` reports path starts, prior/post firings, forks
  and accept/reject: use your backend's level knobs, not printlns, inside the engine.

### 7.1 Watching the automaton mutate: `ExecutionVisitor`

When the printed dumps are not enough, register a visitor and let the engine report what
it did. `ExecutionVisitor` (package `br.usp.poli.lta.cereda.kdaptive.execution`) declares
every event with a **no-op default**, so you implement only what you need:

```kotlin
import br.usp.poli.lta.cereda.kdaptive.execution.ExecutionVisitor

val trace = mutableListOf<String>()

val visitor = object : ExecutionVisitor {
    override fun onBeforeAction(pathId: Int, actionRef: ActionRef, isPrior: Boolean) {
        trace.add("path $pathId is about to fire ${actionRef.name}")
    }

    override fun onEdgeAdded(
        pathId: Int,
        edge: TransitionEdge,
        transitions: TransitionMapping,
        submachines: SubmachinesSet,
    ) {
        trace.add("path $pathId gained $edge")
    }
}

automaton.visitors.add(visitor)
```

The events, and when they fire:

| Event | Fires when |
|---|---|
| `onRecognizeStart(automaton)` | once per `recognize()`, on the clone, **after** `setup()` populated its topology |
| `onRecognizeEnd(result)` | once per `recognize()`, with the overall verdict |
| `onPathStart(pathId, input)` | a path begins; forked children included |
| `onPathEnd(pathId, accepted, cursor)` | a path accepts or rejects |
| `onPathFork(pathId, children)` | a path forks: it reaches no verdict, its children carry on. Each child carries the edge it was created for |
| `onBeforeTraverse` / `onAfterTraverse` | around each edge traversal |
| `onBeforeAction` / `onAfterAction` | around each action handler (`isPrior` tells prior from post) |
| `onEdgeAdded` / `onEdgeRemoved` | after each insertion/deletion in the path's mapping, with the full mapping and submachines |
| `onCall` / `onReturn` | entering or leaving a submachine |

> **Pitfall**: a path terminates **exactly once**, either with `onPathEnd` (accept or
> reject) or with `onPathFork` (the path was replaced by its children). A visitor that
> keeps a set of open paths must close the path on both events, otherwise every forked
> path leaks in the bookkeeping.

> **Call-out**: the callbacks are **synchronous**, on the recognition thread. A slow
> visitor slows recognition down. With no visitor registered the engine skips every
> callback, so the feature is free when unused.

### 7.2 GraphViz snapshots of the topology

`br.usp.poli.lta.cereda.kdaptive.dot.GraphVizVisitor` is a ready-made visitor: it renders
the topology as GraphViz DOT text on every mutation and hands the document to a sink.

```kotlin
import br.usp.poli.lta.cereda.kdaptive.dot.GraphVizVisitor

val frames = mutableListOf<Pair<Int, String>>()
automaton.visitors.add(GraphVizVisitor { pathId, dot -> frames.add(pathId to dot) })
automaton.recognize(input)
// frames: one (pathId, DOT document) per topology mutation
```

The output is plain text — **no `dot` binary and no GraphViz library required**. Save a
frame as `frame.dot` and render it with `dot -Tpng frame.dot -o frame.png`, or feed it to
any GraphViz-compatible tool. Each submachine becomes a cluster, accepting states are
drawn as `doublecircle`, and edges are labeled with the symbol (or ε / call / return) plus
the prior and post action references. `DotExporter.toDot(transitions, submachines)` is
public too, so a topology can also be snapshotted on demand.

Runnable example: `examples/GraphVizExampleKt`

```bash
./gradlew :examples:run -Pmain=br.usp.poli.lta.cereda.kdaptive.examples.GraphVizExampleKt
```

### 7.3 Measuring the work: the instrumentation facility

§7.1 shows how to watch the automaton mutate. The instrumentation facility goes one step
further and exposes **what the elementary actions did to the topology** — including the
reads, which change nothing and would otherwise be invisible.

The rule: **the library reports facts; the consumer decides what they are worth.** There
is no cost model in the engine, no metric to configure, and nothing to fork the source
tree for. A consumer that wants a different metric writes a different visitor.

#### Three channels, three questions

| Channel | Question | Event |
|---|---|---|
| **Topology** | *What changed in the mapping?* | `onEdgeAdded` / `onEdgeRemoved` — once per edge |
| **Operation** | *What did the elementary actions do?* | `onMappingOperation` — once per elementary operation |
| **Fact** | *What did the action itself compute?* | `onObservation` — once per fact published by action code |

The topology and operation channels answer different questions, and their counts differ:
a removal of three edges produces **one** `MappingOperation.Remove` and **three**
`onEdgeRemoved` events. Count removals on the operation channel and edges removed on the
topology channel — but do not sum them.

#### Operations

`MappingOperation` is a sealed, symmetric hierarchy of the three things an elementary
action can do to the mapping:

| Operation | Carries | Why it matters |
|---|---|---|
| `Query` | the pattern, the edges it selected, the mapping size | the only way to observe a **read** |
| `Remove` | the pattern, the edges it took out, the sizes before and after | shows a removal that **matched nothing** |
| `Add` | the pattern, the edge inserted, the sizes before and after | shows the cost of an insertion |

A removal that matched nothing is still reported: it scanned the whole mapping even
though it changed nothing. A metric that cannot see it is blind to exactly the case it
exists to catch.

The `pattern` describes what was **asked for**, not what was found. Its nullable filters
are read per family, and the family is part of the pattern because the two disagree:
`TransitionPatternKind.SYMBOL` and `TransitionPatternKind.SUBMACHINE` treat a null
submachine filter differently.

#### Facts published by an action

The engine cannot see inside an action. When an action computes something only it knows —
the projection of a query result, say — it publishes a fact, and the consumer decides
whether it is worth anything:

```kotlin
data class DomainSize(val count: Int) : Observation

action("Reshape") { _, _ ->
    val matched = ea.query(q0, sym('b'), null)
    observe(DomainSize(matched.map { it.source }.distinct().size))
    ea.remove(k, sym('a'), null)
    ea.add(k, sym('a'), k)
}
```

`Observation` is an **open** marker interface, so a consumer defines its own fact types
and the library never has to learn about them. The imperative API reaches the same
channel through `transitions.observe(fact)`.

#### A consumer, end to end

`br.usp.poli.lta.cereda.kdaptive.examples.CostVisitor` is a complete example of a
consumer: it prices a query and a removal as a scan of the mapping, and an addition as
one unit.

```kotlin
val visitor = CostVisitor()
automaton.visitors.add(visitor)
automaton.recognize(input)

println(visitor.cost)          // the metric
println(visitor.costPerPath()) // the metric, per path
println(visitor.report())
```

Runnable example: `examples/CostExampleKt`

```bash
./gradlew :examples:run -Pmain=br.usp.poli.lta.cereda.kdaptive.examples.CostExampleKt
```

Changing the cost model means changing `CostVisitor` and nothing else.

---

## 8. Catalogue of pitfalls and warnings

1. **Unnamed action references** (`ActionRef(name = null)`) throw at runtime, loudly
   (prior and post slots alike). Always name your references to registered actions.
2. **`fromName("Unknown")` inside handlers throws** `IllegalStateException`; action
   names in references must correspond to registered `Action`s.
3. **Handler exceptions terminate `recognize()`**, wrapped in
   `IllegalStateException("Recognition path N failed: ...")`.
4. **Forks and copies**: all mutations are per-path (§3); do not expect cross-path
   effects; do NOT mutate captured shared state in nondeterministic settings (§6.3).
5. **Epsilon self-loops** hang the engine (no epsilon-cycle detection, faithful to the
   original). Applies to adaptively added epsilon edges too (§4.5).
6. **"Symbol" filters also match epsilon edges** (§4.1); filter by kind if you care.
7. **Argument matching compares position-by-position with `equals` and requires equal
   arity** (§4.3); use value-equality types on both sides.
8. **The prior-action filter comes first** (§4.1, §4.2, §4.4): write
   `remove(priorAction = ..., source = ..., symbol = ..., target = ...)`, never
   `remove(source = ..., symbol = ..., target = ..., priorAction = ...)`. The latter
   spelling no longer exists; it used to be ambiguous with the former.
9. **The `remove`-matching pattern uses wildcard `null` fields**: an over-broad pattern
   (e.g. all wildcards) removes MORE than you intended, including edges you didn't mean
   to touch. Compose the narrowest filter that expresses the intent.
10. **Ids are engine-internal counters** (`IdentifierUtils`); never fabricate them, and
    they reset per-process (see `IdentifierUtils.reset()` for tests).
11. **`recognize()` runs `setup()` on a clone**, so setup-side effects repeat per call.
    The top-level `counter` in the context-sensitive example never resets: it keeps
    minting fresh, unique state names across calls, which is harmless. But mutable
    *semantic* state (buffers, counts that mean something) should be reset inside
    `setup()` itself or not captured at all, otherwise the second `recognize()` starts
    from the first one's leftovers.
12. **Visitors run synchronously** (§7.1): a slow, blocking or throwing visitor slows
    down — or breaks — the recognition it is observing.
13. **The topology and operation channels count different things** (§7.3): a removal of
    three edges is one `MappingOperation.Remove` and three `onEdgeRemoved` events. Count
    removals on one channel and edges removed on the other, but do not sum them.
14. **A published fact is the action's own claim** (§7.3). The engine relays it verbatim;
    it does not validate or interpret it. A consumer that prices facts is trusting the
    action that published them.

---

## 9. Where to go next

- Read `examples/ContextSensitiveLanguageExampleKt` as the reference study of the trio
  (query/remove/add) used at full power; its companion test keeps it working.
- Read `examples/GraphVizExampleKt` for the visitor pattern in action: it registers a
  `GraphVizVisitor` and prints the topology after every mutation (§7.2).
- The JUnit suites (`src/test/kotlin/.../model/ElementaryActionsOverloadsTest.kt`,
  `ModelSetsTest.kt`, `execution/ErrorPathTest.kt`) double as executable
  documentation of the matching rules in §4.3; when in doubt, verify against them.
- API reference: `./gradlew dokkaGenerateHtml` → `build/dokka/html/index.html`.
