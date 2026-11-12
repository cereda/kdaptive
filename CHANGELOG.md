# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Every release
is a `## [x.y.z] - yyyy-mm-dd` heading followed by one list of changes per category, and
the categories are `### Added`, `### Changed`, `### Removed` and `### Fixed`.

The **public API** of a release is the set of declarations in `api/kdaptive.api`. A
release that changes it is breaking, whatever its number says.

The **version of the build** is derived from this file: the spotless-changelog plugin
reads the section below and computes the version of the release it describes. A release
is declared by promoting the `[Unreleased]` section to a version heading; an entry that
says *breaking change* — or carries the `**BREAKING**` marker — bumps the major, an
`### Added` section bumps the minor, and anything else bumps the patch. The section
itself must stay empty until it has changes to carry: an empty `[Unreleased]` collapses
the derived version onto the latest release the file names.

## [Unreleased]

## [6.0.0] - 2026-11-12

**BREAKING:** the public API changes, `api/kdaptive.api` is regenerated on purpose, and
the binary-compatibility validator holds the new baseline from now on.

This release renames what the Kotlin conventions ask to be renamed, removes what the
library never promised to give, and makes the build strict about it.

The renames are **clean breaks**: there are no deprecated aliases, no `@Deprecated`
shims, no old names kept alive for a season. A consumer that upgrades fixes the call
sites the compiler points at, and nothing else.

### Changed

- **The enums are PascalCase.** The two enums of the public API — `PathOutcome` and
  `TransitionPatternKind` — name their constants the way Kotlin names every other type:
  `Accepted`, not `ACCEPTED`. The six sealed hierarchies of the library already did; the
  enums were the odd ones out.
- **A lookup is reached through `get`.** `Actions.fromName("A")` becomes `actions["A"]`,
  and `Submachines.fromName("M")` becomes `submachines["M"]`: the collection is a lookup
  table first and an iterable second, and the operator says so.
- **The owner of a state is asked for with `ownerOf`.** `Submachines.fromState(s)` and
  `Topology.fromState(s)` become `ownerOf(s)`. "Owner" is the word the codebase already
  uses for that relation — the topology validator and the DOT exporter both speak of
  it.
- **The transition builders are named after the kind of move they describe.**
  `consume(from, symbol, to)` becomes `symbol(from, symbol, to)`, next to
  `epsilon(from, to)` and `call(from, submachine, to)`.
- **The mapping is pruned by identifier.** `TransitionMapping.removeFromIdentifier(id)`
  becomes `removeById(id)`, next to `contains(id)`.
- **A failed lookup throws `NoSuchElementException`.** This is the one change of
  *meaning* in the release. A lookup that finds nothing now throws
  `NoSuchElementException` — the exception the collections of the standard library
  throw — instead of `IllegalStateException` (or `IllegalArgumentException`, for the
  removals). A consumer that catches the exception of a missing element now catches the
  right one; a consumer that catches `IllegalStateException` around a lookup must widen
  its catch to `NoSuchElementException`.
- **`AdaptiveAutomaton.visitors` is a `val`.** The list is still mutable — a visitor may
  be registered after the automaton is built — but replacing the whole list would
  silently drop the visitors a subclass registered in its constructor.
- **Explicit API mode is a rule.** Every public declaration names its visibility and its
  return type. The warning of 5.1.0 became an error, and the code complies.
- **`-jvm-default=no-compatibility`.** The synthetic `ExecutionVisitor$DefaultImpls`
  class — the binary-compatibility bridges of the interfaces — is gone. The default
  methods live in the interface itself, where they have lived since Kotlin 2.2 anyway.
- **Thirty-seven detekt rules are re-enabled**: the naming and formatting rules, the
  idiom rules, the potential-bugs rules, the coroutine rules, the exception rules and
  the documentation rules. The code already complied with all of them; the dead imports
  and the stray parentheses they found are gone.
- **The version catalog names every version.** Every entry of `[versions]` carries the
  `-version` suffix, and every plugin and library refers to it by that name. The
  plugin alias of the binary-compatibility validator is
  `binary-compatibility-validator`, which reads as
  `libs.plugins.binary.compatibility.validator`.
- **The configuration cache is off, on purpose.** Spotless keeps the classloaders of
  its formatters in a cache that lives in the JVM of the daemon, and it *closes* them
  when the `clean` task runs. The configuration cache keeps a reference to those
  classloaders across builds. The two together are a trap: a build that reuses a
  configuration cache entry points at a classloader that a previous `clean` closed, and
  ktlint fails with an `InvocationTargetException` that hides the real cause. The
  failure was intermittent — roughly two builds in three — and it hit the formatting
  gate, which is the one gate that must never lie. The cache was worth about three
  seconds on a full rebuild; a gate that fails at random is a much worse property
  than a configuration phase that takes three seconds longer. The decision, the
  measurements and the upstream issue are written down in `gradle.properties`.
- **The version of the build comes from this file.** The spotless-changelog plugin reads
  the changelog and derives the version of the release it describes: an entry that says
  *breaking change* — or carries the `**BREAKING**` marker — bumps the major, an
  `### Added` section bumps the minor, and anything else bumps the patch. A release is
  declared by promoting the `[Unreleased]` section to a version heading; the build reads
  the heading, and the tag names it.
- **A contract test for the conventions of the release** (`ApiConventionsTest`) pins
  every spelling the library promises: the names of the enum constants, the `get`
  operator, `ownerOf`, `removeById`, the `symbol` builder, the exception of a failed
  lookup and the exception of a failed recognition.
- **52 test classes** (594 tests: 465 in the library, 129 in the examples), all of them
  executed.
- **Coverage**: 98.9% of the lines and 93.3% of the branches (floors: 95% and 78%).
- **The migration table.** Every rename of the release, in one table:

  | Before (5.1.0) | After (6.0.0) |
  |---|---|
  | `PathOutcome.PENDING` / `.ACCEPTED` / `.REJECTED` / `.CUT` / `.FAILED` | `PathOutcome.Pending` / `.Accepted` / `.Rejected` / `.Cut` / `.Failed` |
  | `TransitionPatternKind.SYMBOL` / `.EPSILON` / `.MIXED` / `.SUBMACHINE` | `TransitionPatternKind.Symbol` / `.Epsilon` / `.Mixed` / `.Submachine` |
  | `actions.fromName("A")` | `actions["A"]` |
  | `submachines.fromName("M")` | `submachines["M"]` |
  | `submachines.fromState(s)` | `submachines.ownerOf(s)` |
  | `topology.fromState(s)` | `topology.ownerOf(s)` |
  | `mapping.removeFromIdentifier(id)` | `mapping.removeById(id)` |
  | `aa.consume(from, symbol, to)` | `aa.symbol(from, symbol, to)` |
  | `automaton { consume(from, symbol, to) }` | `automaton { symbol(from, symbol, to) }` |
  | `IllegalStateException` from a lookup that finds nothing | `NoSuchElementException` |
  | `IllegalStateException` from a recognition that fails | `RecognitionFailedException` — still an `IllegalStateException` |
  | `PathExecutor`, `PathState` | internal |
  | `br.usp.poli.lta.cereda.kdaptive.logging.logger` | internal |
  | `aa.visitors = listOf(v)` | `aa.visitors.add(v)` |
  | `GraphVizVisitor { pathId, dot -> ... }` | unchanged — the lambda still works (`DotSink` is a `fun interface`) |

### Added

- **`RecognitionFailedException(pathId, reason)`** is the exception the engine throws
  when a path dies before it reaches a verdict, or when a structural bound trips. It
  names the path that died and why it died, and it extends `IllegalStateException`, so a
  consumer that catches the broad exception keeps working and a consumer that cares may
  catch this one instead.
- **`DotSink`** is the type of the callback `GraphVizVisitor` writes its snapshots to.
  It is a `fun interface`, so a lambda still works exactly as before, and a consumer
  that wants to declare the sink as a field — or to implement it in another language —
  now has a type to write down.

### Removed

- **`PathExecutor` and `PathState` are internal.** They are the engine's own machinery:
  a consumer builds an automaton, runs it and observes it. Reaching into the executor
  meant holding the mutable state of a running path, which was never a supported thing
  to do — and now it is not even possible.
- **The logging extension is internal.**
  `br.usp.poli.lta.cereda.kdaptive.logging.logger` was a public extension on *every*
  object. What the library logs is not part of the API it promises; a consumer that
  wants a logger of its own asks SLF4J for one.
- **Nine constants stop leaking.** A `const val` is hoisted to a static field of its
  class, and the ones of `DotExporter` and `TransitionMapping` were public without
  anyone meaning them to be. They are private now.

## [5.1.0] - 2026-11-12

This is a **non-breaking** release. The public API is byte-for-byte the one of 5.0.0 —
`api/kdaptive.api` is unchanged — and the binary-compatibility validator says so on
every build. It makes the engine linear in the cases that were still quadratic, makes
the failure path honest about what it caught, and tidies the build.

### Changed

- **Performance: a topology that grows is linear too.** 5.0.0 made the engine linear in
  the *number of paths* and in the *length of the trail*. It was still quadratic in the
  *size of the topology* whenever an adaptive action changed the topology while the path
  ran: every mutation invalidated the index of the mapping, and the next step rebuilt
  the whole index from scratch. The recognition of a path that adds one edge per step
  therefore paid a scan of the entire topology per step.

  The benchmark module measures exactly that shape — a chain whose adaptive action adds
  one edge on every step. On the reference machine, at 8 000 steps:

  | Scenario | 5.0.0 | 5.1.0 | Speed-up |
  |---|---|---|---|
  | 8 000 steps, topology growing | 15 235 ms | **13 ms** | **1 170×** |
  | Cost of one extra step, topology growing | grows 2.6× per doubling | **flat (≈ 1.0×)** | — |
  | 8 000 steps, topology unchanged | 11 ms | **11 ms** | — |
  | 8 000 forks | 9 ms | **9 ms** | — |

  The benchmarks are in the tree, and they run:

  ```bash
  ./gradlew :benchmarks:run
  ```

  Each scenario prints the cost *per unit of work* and the ratio between two consecutive
  sizes. The ratio is what tells a linear engine from a quadratic one: it stays near one
  when the engine scales, and doubles when it does not. The benchmark is not decoration —
  restoring the old invalidating index makes the ratio jump back to ≈ 2.6 and the
  8 000-step run take four minutes instead of thirteen milliseconds.
- **The index of the mapping is updated in place.** Adding or removing an edge touches
  the index instead of invalidating it, so a mutation costs what it costs and not what
  the topology weighs. The fingerprint of the topology is a rolling value, updated the
  same way.
- **A fork is O(1).** The lineage was a map copied on every fork — a chain of forks
  copied an index that grew with it. It is now a stack of *layers*: a fork freezes the
  layer the path owns and hands it to the child, and the path starts a layer of its own.
  Neither of them ever writes to a layer they share.
- **The trail is kept in chunks.** A fork used to copy the whole history of the path. It
  now copies a bounded chunk — the part the path owns — and the frozen chunks before it
  are shared with the children. The trail is flattened into a list once, at the verdict.
- **The worklist is compacted in one sweep.** Removing a path as it finished shifted the
  tail of the list on every removal, and a pass over k paths cost a quadratic number of
  moves. The finished paths are now dropped in one pass at the end.
- **The applicable transitions are collected into a buffer the path owns**, instead of
  into a fresh list built by a closure on every step.
- **The call stack caches its hash.** The configuration of a path is hashed on every
  step, and the call stack was the one part of it that cost more than a constant to hash.
- **The actions and the submachines are indexed by name**, and the submachines are
  indexed by state. A lookup no longer scans the collection.
- **The owner of a state is computed once.** The topology validator asked the
  collection of submachines for the owner of every endpoint of every edge, and the DOT
  exporter scanned the whole edge list once per submachine. Both now partition in a
  single pass.
- **Explicit API mode.** Every public declaration names its visibility and its return
  type. The mode is a **warning** in 5.1.0 and becomes a **rule** in 6.0.0; the work
  it asks for is already done, so the flip is a one-line change.
- **The configuration cache is on**, and the daemon heap is sized for the Kotlin
  compiler and the Dokka generator instead of left at the default.
- **`.editorconfig`, `.gitattributes` and a corrected `.gitignore`**: the conventions of
  the repository are written down, the line endings are normalised, and the Kotlin
  build directory is ignored.
- **A `benchmarks` module**, so the performance claims of this file are verifiable by
  anyone who clones the repository.
- **The formatting gate is self-healing.** ktlint 1.8.0 crashes with an
  `InvocationTargetException` on a file that ends with an extra blank line — and
  `spotlessApply`, the step that would have fixed the file, ran *after* ktlint, so
  the build could neither pass nor repair it. The whitespace steps now run first, and
  a last step collapses the trailing blank lines to a single newline. A file with a
  stray blank line at its end is now a file the formatter quietly fixes.
- **51 test classes** (579 tests: 450 in the library, 129 in the examples), covering
  the concurrency of the identifier counters, the equality of the configurations across
  forks, the structural bounds, the lifecycle of a path that dies with an `Error`, and
  the DOT export of a non-ASCII topology.
- **Coverage**: 98.9% of the lines and 93.4% of the branches (floors: 95% and 78%).

### Fixed

- **Every throwable closes the lifecycle.** The engine caught `Exception` and let an
  `Error` escape with the path still open: a visitor tracking open paths was left
  holding one that would never end. The engine now catches every throwable, closes the
  path — the path-end event fires, the path reports `FAILED` — and then lets the
  throwable out.
- **A throwable keeps its nature on the way out.** An `Error` is rethrown as it is,
  because the engine did not cause it and hiding it behind a wrapper would only bury
  it. An `InterruptedException` restores the interrupt flag of the thread before it
  leaves, so that whoever owns the thread may notice the interruption. A
  `CancellationException` is rethrown untouched, because a cancelled coroutine must
  stay cancelled. Every other failure is wrapped, so that the caller may tell which
  path died and why.
- **The bound on the live paths is exact.** `RecognitionLimits.maxLivePaths` is "the
  largest number of paths that may be alive at once", and the engine now keeps that
  promise: the fork that would take the number of live paths past the bound is the one
  refused. (The bound on the call depth already was exact.)
- **`castOrThrow` takes an `Any`, not an `Any?`.** The library hands out no nulls, and a
  value that reaches the function from the outside is either a value or a mistake. The
  JVM signature is unchanged, so the change is invisible to a binary consumer.
- **The null-free sweep sees everything.** The reflection-based sweep could not see the
  declarations that carry a *reified* type parameter — Kotlin's reflection does not
  report them at all — and it skipped the receiver of a top-level extension function.
  Both holes are closed, and the sweep proves it: reintroducing a nullable receiver
  makes the test fail.

## [5.0.0] - 2026-11-11

This is a **breaking** release. It fixes the honesty of the failure path, makes the
engine linear instead of quadratic, tidies the package layout, and hardens the build.

### Changed

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
- **Performance: linear, not quadratic.** The engine was rewritten around a
  **copy-on-write topology** and an **indexed lineage**. The measurements on the
  reference benchmarks:

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
- **Edges are indexed by identifier**, so `contains(id)` and `removeById(id)`
  are constant-time instead of linear scans.
- **The topology snapshot is built only when someone wants it.** A recognition with no
  visitor registered never builds a `Topology`.
- **The automaton is copied once per recognition**, not once per path.
- **The call stack caches its snapshot**, because the engine reads it on every step
  and it changes far less often than it is read.
- **Cleanup: the package layout and the names.** The `utils` package is **gone**, and
  two sub-packages of `model` are flattened into `model`. The `*Utils` classes were
  renamed:

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

## [4.0.0] - 2026-11-11

This is a **breaking** release. It answers the epsilon-cycle question, turns the
recognition into a result object, completes the null-demotion sweep, and brings the
API to Kotlin idiom.

### Added

- **A structural backstop replaces the hang.** `AdaptiveAutomaton.limits` carries
  `RecognitionLimits(maxLivePaths, maxStepsPerPath, maxCallDepth)`. When a bound trips,
  the engine **throws** `IllegalStateException` — the answer there is "unknown", not
  "rejected". There is **no wall-clock timeout** anywhere in the engine.
- **`DomainSize` is a sealed type.** The six `*DomainSize` properties of
  `TransitionPattern` are `DomainSize.Bounded(count)` or `DomainSize.Unbounded` instead
  of `Int?`, so "unbounded" is no longer conflated with "empty".
- **`Arity` replaces the nullable arity.** `action("A", arity = n)` becomes
  `action("A", arity = Arity.Exactly(n))`; the wildcard is `Arity.Any`.

### Changed

- **`recognize()` returns a `RecognitionResult`** instead of a `Boolean`. It carries
  `accepted` and `paths`, plus `acceptedPaths`, `rejectedPaths` and `cutPaths`.
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
- **The public API is null-free.** `RecognitionPath.state` is a `State`;
  `AutomatonSpec.main` is a required `String`; `TopologyValidator.validate` takes a
  non-null main; `AdaptiveAutomaton.mainSubmachine` and `AutomatonBuilder.main` are
  non-null properties over private slots, with fail-fast getters.
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

### Removed

- **`getRecognitionMap()` and `getRecognitionPaths()` are deleted.** The result carries
  the paths, and the "call `recognize()` first" exception is gone with them. Two
  recognitions running concurrently on one automaton no longer share any state.
- **`TransitionPattern.matches(edge)`** is the single implementation of what a filter
  means. The 14 `TransitionMapping.withX(...)` filter methods are **deleted**: plain
  topology queries live on `Topology` (`edgesFrom`, `edgesConsuming`, `callsTo`,
  `epsilonEdges`, `edgesWithPrior`, …), and pattern queries live on the pattern.
- **`model.actions.requireNonNull` is deleted**, and `asState()` / `asSymbol()` take
  their receiver as `Any` instead of `Any?`.

### Fixed

- **The epsilon cycle question has an answer.** A reachable epsilon-only cycle is now
  **rejected at validation time** when the automaton registers no adaptive actions
  (nothing can then change the topology, so the cycle is provably non-terminating), and
  **warned about** when actions are present (an action may legitimately break it). At
  runtime, a path that re-enters a configuration it already left is **cut**: its outcome
  becomes the new `PathOutcome.Cut`, and the other branches carry on. A cut discards no
  verdict: acceptance is only ever tested from a stuck position, and a path that
  revisits a configuration was never stuck there. The cut is guarded by an **action
  epoch**, so an action that fires in between keeps its chance to break the cycle.
- **`addCall` validates the names of a `SubmachineRef.Domain`** against the declared
  submachines and throws naming the offender, instead of failing later at runtime.

## [3.0.0] - 2026-11-11

This is a **breaking** release that demotes `null` from the model: **filters and values
are null-free**, while configuration and genuine optionality keep their nulls.

### Added

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

### Changed

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

### Removed

- **`Transition.sourceState` and `Transition.targetState` are gone.** A transition
  carries its payload only; the endpoints belong to the edge. The old copies could
  contradict their own edge, and an edge declared with a bare transition was a
  *different* edge from the same edge declared with its endpoints — now they are one
  edge, and adding it twice is a no-op.

## [2.1.0] - 2026-11-11

This release completes the work begun in 2.0.0: the equality contract is explicit, the
engine is safe under concurrent use, the two APIs validate the same topologies, a prior
action that removes its own edge no longer kills the path, and the elementary actions
learned **set-valued domains**.

### Added

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
- **`TransitionPatternKind.Mixed` was added**, for a symbol domain that names the empty
  word among its members: such a filter selects symbol-consuming *and* epsilon edges.

### Changed

- **`MappingOperation` exposes its `pattern` uniformly**, so a consumer can read the
  filters of any operation without a `when` over the three cases.
- **The equality contract is explicit.** The identifiers the library hands out — the
  edge id, the submachine id, the action id — are *bookkeeping*, not identity: they are
  outside the equality of the object they belong to. `Action.id` moved out of the
  constructor, and `State` and `Symbol` now document that the library compares them by
  value.

### Fixed

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

## [2.0.0] - 2026-11-10

This is a **breaking** release: the elementary actions were reworked, epsilon got an
explicit marker, and six engine defects were fixed.

### Added

- **`ActionRef` is a sealed interface.** `Bound` is the concrete reference an edge
  carries; `Free`, `Any`, `None` and `Named` are the filters a query or a removal uses.
  `Named("A")` matches an action by name whatever its arguments; `Bound("A", x)` matches
  exactly these arguments. The old "a reference with zero arguments matches any arity"
  rule is gone — and with it the ambiguity between the two roles.
- New in 1.2.0 and kept: `MappingOperation`, `Observation`, `TransitionPattern`,
  `ForkedPath`, the `onMappingOperation` and `onObservation` visitor events, and the
  `observe()` helpers on `TransitionMapping`, `ElementaryActions` and the DSL action
  context.

See §4 of the [adaptive actions guide](docs/ADAPTIVE-ACTIONS-GUIDE.md) for the complete
API.

### Changed

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
  since 3.0.0 it is a `SubmachineRef` (see the 3.0.0 entry below).
- **Epsilon is named explicitly.** `Symbol.Epsilon` selects the epsilon transitions and
  `Symbol.Any` selects the symbol-consuming ones; a symbol filter no longer matches
  epsilon edges.
- **The additions gained symmetry.** `add` accepts a prior and a post action,
  `add(source, Symbol.Epsilon, target)` inserts an epsilon edge, and `addCall` inserts
  a submachine call.
- `ExecutionVisitor.onBeforeAction` / `onAfterAction` now receive an
  `ActionRef.Bound` instead of an `ActionRef`, and `TransitionEdge.prior` / `.post` are
  `ActionRef.Bound?` — a filter can no longer be attached to an edge.

### Fixed

- **Six engine defects were fixed**: `setup()` no longer duplicates the topology; every
  edge that leaves a mapping fires its own `onEdgeRemoved`; a copied mapping keeps its
  callbacks; a symbol filter no longer matches epsilon; `getRecognitionPaths()` reports
  only the paths that reached a verdict (a path replaced by its children is not one of
  them); and the recognition lifecycle events always pair, even when a path fails.

## [1.1.0] - 2026-11-10

The first release of the Kotlin port that the visitor pattern made visible: the
recognition became observable, and the topology could be exported as GraphViz.

### Added

- **The visitor pattern.** `ExecutionVisitor` reports the lifecycle of a recognition:
  the start and the end, the paths, the traversals, the actions, the mutations of the
  topology and the calls to the submachines.
- **The GraphViz export.** `DotExporter` renders a `Topology` as DOT text, and
  `GraphVizVisitor` emits a snapshot on every mutation.

## [1.0.0] - 2026-11-09

The first release of the Kotlin port of the adaptive automata library.

### Added

- The library: the model of an adaptive automaton, the engine that recognises a word,
  the imperative API and the builder DSL.

[Unreleased]: https://github.com/cereda/kdaptive/compare/v6.0.0...HEAD
[6.0.0]: https://github.com/cereda/kdaptive/compare/v5.1.0...v6.0.0
[5.1.0]: https://github.com/cereda/kdaptive/compare/v5.0.0...v5.1.0
[5.0.0]: https://github.com/cereda/kdaptive/compare/v4.0.0...v5.0.0
[4.0.0]: https://github.com/cereda/kdaptive/compare/v3.0.0...v4.0.0
[3.0.0]: https://github.com/cereda/kdaptive/compare/v2.1.0...v3.0.0
[2.1.0]: https://github.com/cereda/kdaptive/compare/v2.0.0...v2.1.0
[2.0.0]: https://github.com/cereda/kdaptive/compare/v1.1.0...v2.0.0
[1.1.0]: https://github.com/cereda/kdaptive/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/cereda/kdaptive/releases/tag/v1.0.0
