# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The **public API** of a release is the set of declarations in
`api/kdaptive.api`. A release that changes it is breaking, whatever its number says.

## [Unreleased]

Nothing yet.

## [5.1.0] — 2026-11-12

A **non-breaking** release: the public API is byte-for-byte the one of 5.0.0, and
`api/kdaptive.api` is unchanged.

### Performance

- **The index of the transition mapping is updated in place.** Adding or removing an
  edge used to invalidate the index, and the next step rebuilt it from scratch: the
  recognition of a path that changed the topology on every step was quadratic in the
  size of the topology. The by-source index, the by-identifier index and the
  structural fingerprint are now maintained incrementally. The benchmark of a chain
  whose action adds one edge per step went from **15 235 ms to 13 ms** at 8 000 steps
  (**1 170×**), and the cost per step is now flat instead of growing 2.6× per doubling.
- **A fork is O(1).** The lineage was a map copied on every fork. It is now a stack of
  layers: a fork freezes the layer the path owns and hands it to the child.
- **The trail is kept in chunks.** A fork copies a bounded chunk instead of the whole
  history of the path; the frozen chunks before it are shared with the children.
- **The worklist is compacted in one sweep** at the end of a pass, instead of removing
  the finished paths one by one and shifting the tail of the list every time.
- **The applicable transitions are collected into a buffer the path owns**, with no
  closure and no fresh list on every step.
- **The call stack caches its hash**, which the configuration of a path needs on every
  step.
- **The actions and the submachines are indexed by name**, and the submachines by
  state. A lookup no longer scans the collection.
- **The owner of a state is computed once** in the topology validator, and the DOT
  exporter partitions the edges in a single pass instead of scanning them once per
  submachine.
- **The logger is shared by the class**, and every log call is parameterised.

### Correctness

- **Every throwable closes the lifecycle.** The engine caught `Exception` and let an
  `Error` escape with the path still open. It now catches every throwable, closes the
  path — the path-end event fires and the path reports `FAILED` — and lets the
  throwable out.
- **A throwable keeps its nature on the way out.** An `Error` is rethrown as it is; an
  `InterruptedException` restores the interrupt flag of the thread; a
  `CancellationException` is rethrown untouched; every other failure is wrapped in an
  `IllegalStateException` that names the path and keeps the cause.
- **The bound on the live paths is exact.** `RecognitionLimits.maxLivePaths` is now the
  largest number of paths that may be alive at once, as documented: the fork that would
  take the number of live paths past the bound is the one refused.
- **`castOrThrow` takes an `Any`, not an `Any?`.** The JVM signature is unchanged.
- **The null-free sweep sees everything.** The reflection-based sweep could not see
  the declarations carrying a *reified* type parameter, and it skipped the receiver of
  a top-level extension function. Both holes are closed.
- **The DOT export escapes UTF-16 code units**, as the documentation says, and the
  wording of several messages was corrected.

### Build and repository

- **Explicit API mode**: every public declaration names its visibility and its return
  type. A warning in 5.1.0, a rule in 6.0.0.
- **The configuration cache is on**, and the daemon heap is sized for the Kotlin
  compiler and the Dokka generator.
- **`.editorconfig`, `.gitattributes`** and a corrected `.gitignore`.
- **A `benchmarks` module**, so the performance claims are verifiable.
- **The formatting gate is self-healing.** ktlint 1.8.0 crashes with an
  `InvocationTargetException` on a file that ends with an extra blank line, and the
  `spotlessApply` step that would have fixed the file ran *after* ktlint: the build
  could neither pass nor repair such a file. The whitespace steps now run first, and
  a last step collapses the trailing blank lines to a single newline.
- **51 test classes** (579 tests: 450 in the library, 129 in the examples), covering
  the concurrency of the identifier counters, the equality of the configurations across
  forks, the structural bounds, the lifecycle of a path that dies with an `Error`, and
  the DOT export of a non-ASCII topology.
- **Coverage**: 98.9% of the lines and 93.4% of the branches (floors: 95% and 78%).

[Unreleased]: https://github.com/cereda/kdaptive/compare/v5.1.0...HEAD
[5.1.0]: https://github.com/cereda/kdaptive/compare/v5.0.0...v5.1.0
