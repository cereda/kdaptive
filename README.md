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
| Tests | `kotlin("test")` over JUnit 5 (259 tests: 151 library, 108 examples) |
| Static analysis | detekt 1.23.8, **zero-tolerance** config |
| Format / style | Spotless, ktlint rules, 120-col, SPDX license header |
| Docs | Dokka HTML (`./gradlew dokkaGenerateHtml`, fail-on-warning) |
| Coverage | Kover: line ≥ 95%, branch ≥ 78% enforced on `check` |
| Logging | SLF4J API only (backend chosen by the consumer) |

Coordinates: `br.usp.poli.lta.cereda:kdaptive:1.0.0`.

> **Remark**: the build currently **does not ship a publishing plugin**. The library jar
> lands in `./build/libs/kdaptive-1.0.0.jar` after `./gradlew build`, and `:examples`
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

## Model API tour

| Type | Role |
|---|---|
| `Symbol` / `CharSymbol` | Input alphabet. `CharSymbol('a')` is the bread-and-butter symbol; subclass `Symbol()` for richer tokens (lexer-like recognizers). |
| `State` | Abstract base. Subclass with value-based `equals`/`hashCode` (see `ExampleState`); transitions compare states by equality. |
| `Transition` (sealed) | Exactly one of `SymbolTransition`, `Epsilon`, `Call`, `Return`. |
| `TransitionEdge` | An edge of the mapping: source, target, a `Transition`, and optional prior/post `ActionRef`s. |
| `Submachine` | A named automaton fragment with its own initial/accepting states; called via `call(...)` and returning via a synthetic return transition. |
| `Action` / `ActionRef` / `ActionHandler` | A named action with its Kotlin handler; `ActionRef(name, args)` references it with arguments on an edge. |
| `Stack` | The call stack backing submachine call/return discipline. |
| `TransitionMapping` | The mutable set of edges; O(1) filtered lookups via `withSourceState` and friends. |
| `RecognitionPath` | The trail of one recognizer path: transition `toString()`s, termination cursor/state, accepted/rejected result. |
| `AdaptiveAutomaton` | The automaton itself, plus the recognition driver. |

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
                             Submachine, Action, Stack + predicates/ + sets/ + actions/
    execution/               AdaptiveAutomaton (the public entry), PathExecutor, PathExecutorConfig
    dsl/                     AutomatonDsl.kt: automaton { } / specToAutomaton
    logging/                 SLF4J facade (`val <T> Any.logger`)
  src/test/kotlin/           mirrors main, with unit, error-path and coverage tests
examples/                    :examples, 14 executable examples, depends on the root module
```

The **14 examples** double as the living documentation: each is small, prints its result,
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
| `NondeterminismExampleKt` | `a(b|c)` via forks |
| `LexerExampleKt` | simplified tokenizer (identifiers, numbers, spaces) |
| `ArithmeticExampleKt` | simple binary expressions `digit op digit` (`0`/`1`, `+`/`*`) |
| `ConfigParserExampleKt` | a config line like `a=[01]+;` (multi-phase recognizer) |
| `ModuloExampleKt` | `{aⁿ | n mod 3 = 0}` via a three-state ring counter |
| `SubstringExampleKt` | `{ w | w contains "ab" }` over `{a, b}` |
| `DslShowcaseExampleKt` | dynamic edge insertion (`ElementaryActions.add`) |

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

## License

kdaptive is free software: you can redistribute it and/or modify it under the terms of
the GNU General Public License as published by the Free Software Foundation, either
version 3 of the License, or (at your option) any later version (SPDX:
`GPL-3.0-or-later`). The full license text is in the [LICENSE](LICENSE) file.

Every source file carries the `// SPDX-License-Identifier: GPL-3.0-or-later` header;
the README and the guides are marked with the same identifier.
