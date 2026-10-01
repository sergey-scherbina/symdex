# symdex v0 — structural code intelligence over MCP

Status: v0 BUILT (2026-10-01). Boxes are checked as tests cover them;
what changed from the draft is in Decisions.

## Why

An agent working in a Scala codebase spends most of its tool calls
finding things: where a symbol is defined, who calls it, which `given` a
call site actually gets, which modules a change reaches. grep answers by
text, so it misses renamed imports, extension methods, givens, overloads
and inherited members, and it floods on common names. The compiler
already knows all of it, exactly, and writes it down. symdex reads what
the compiler wrote and answers those questions over MCP.

Not retrieval: rozum's `rag.search` ranks chunks for a concept or a
symptom. symdex answers "who calls this" with a list that is complete or
says it is not.

## Sources

- **SemanticDB** (`-Xsemanticdb`, sbt's `semanticdbEnabled`) — every
  occurrence of every symbol with its range, every symbol's kind,
  properties, parents, declarations and overridden symbols, and the
  synthetics: which given each call site resolved. v0 reads only this
  (Decisions).
- later: **TASTy** (`.tasty` beside every `.class`), read with
  `tasty-query`, for definition SPANS — which SemanticDB lacks and an
  exact `callers` needs.
- not needed: an sbt export of the project graph. Modules come from
  where the SemanticDB was written, and their dependencies from
  references (Decisions).
- v1: **SCIP** indexes for other languages (scip-java, scip-typescript,
  scip-python, rust-analyzer), ingested into the same symbol/occurrence
  model. symdex writes no per-language analyzer.

## Tools (v0)

Few and exact; every tool is context an agent pays for (Quill's 50+ is
the counter-example). Each answer names the generation it came from and
its age.

- [x] `symdex.definition(symbol | file:line:col)` — where it is defined,
      its signature and doc comment.
- [x] `symdex.references(symbol)` — every occurrence, grouped by file;
      `callers` is the same filtered to call sites.
- [x] `symdex.implementations(symbol)` — subclasses, overriding members,
      `given` instances of a typeclass.
- [x] `symdex.givens(file:line:col)` — which givens the compiler resolved
      at that call site (from SemanticDB synthetics).
- [x] `symdex.members(type)` — declared and inherited members, extensions
      in scope included.
- [x] `symdex.modules(path | project)` — which module owns a path, what it
      uses, what uses it, and what a change there reaches (the
      "affected" question). A dependency is a USE — a reference from
      one module's code into a symbol defined in another — not a
      declared `dependsOn` (Decisions).
- [x] `symdex.status` — generations, their age against the sources, what is
      stale.

Symbol names are SemanticDB's (`okay/Free#flatMap().`), with a fuzzy
lookup from a plain name that returns candidates rather than guessing.

## Freshness

- [x] An index is a GENERATION: built whole, immutable, swapped in
      atomically under `.symdex/`. A query never sees a half-built index.
- [x] Building is incremental per `.semanticdb` file (its mtime and
      size); an unchanged file is not re-read.
- [x] Compiling is the trigger: before answering, the workspace looks
      at the SemanticDB on disk (at most every 2 s) and builds a new
      generation when it changed. No plugin, no hook (Decisions).
- [x] Every answer carries the generation's age and whether any source
      file is newer than it — a stale answer says so, never silently.

## Storage

v0 keeps the index in memory and persists nothing: on the okay slice
measured below, building it from SemanticDB takes about 0.4 s, which is
less than a JVM start. The okay-store-against-SQLite question stays
open and is decided by measurement when a repository's build makes a
restart cost something; until then a store is a cache with nothing to
save.

## The measure

symdex ships only what moves this number. Take real tasks from okay's own
history (e.g. "every caller of `Bulk.joinSorted`", "which modules does a
change to `Tables.scala` reach", "which `given Ordering` does this
`sortByKey` get"), run each with grep/Read only and with symdex, and
record tool calls, tokens and correctness (missed or false hits against
a hand-checked answer).

- [x] A task set of 10 with hand-checked answers: `bench/tasks.md`.
- [x] A results table per release in this spec's Results (v0 below).
- [ ] The same tasks run by an AGENT, grep-only against symdex-only,
      counting tool calls and tokens: v0 compares answers, not agent runs.

## Not yet

- [ ] Exact enclosing definitions: `callers` credits a reference to the
      nearest definition that starts before it (SemanticDB records a
      definition's name, not its body). TASTy's spans make it exact.
- [ ] Extension methods among a type's `members`.
- [ ] Other languages through SCIP (v1).
- [ ] Generations persisted under `.symdex/`, if a repository's build
      time ever makes a restart cost something (see Storage).

## What goes into okay

- MCP server features the tools need (structured results, pagination).
- The store, if okay's wins the storage measurement.
- Nothing about TASTy or SemanticDB — that is symdex's.

## Decisions

- TASTy over bytecode: bytecode erases what Scala users ask about
  (givens, extensions, opaque types, inline).
- SCIP for other languages instead of writing analyzers.
- Separate repository, okay as a submodule: symdex is a product on okay,
  like okay-chat; it must not grow okay's build.
- v0 reads SemanticDB ONLY. The draft paired it with TASTy; on the
  fixtures and on okay, SemanticDB alone answered all seven tools:
  occurrences give definitions and references, SymbolInformation gives
  kinds, properties (`given`), parents, declarations and overridden
  symbols, and Scala 3's Synthetics record which given each call site
  resolved (`render(1)` → `given_Show_Int`). TASTy is kept for what
  SemanticDB lacks — definition spans — and tasty-query was removed
  until that lane, rather than carried unused.
- The SemanticDB decoder is ~250 lines of our own protobuf reading
  rather than scalameta's semanticdb + scalapb: the schema is stable,
  only a dozen fields are read, unknown fields are skipped, and the
  fixtures pin it.
- Freshness without a hook: a compile rewrites the `.semanticdb` files,
  so their mtimes are the signal. The draft's sbt plugin is not needed
  for freshness; enabling SemanticDB is one setting
  (`semanticdbEnabled := true`, or `set every semanticdbEnabled := true`
  for one session).
- sbt 1.13 writes SemanticDB under `target/scala-<v>/meta` and
  `test-meta`, not beside the classes; both layouts are scanned. A
  cross-built source (JVM/JS/Native) is ONE document — the JVM copy is
  kept — and the build root's own module is named after the root
  (okay's core is `okay`, its JS twin `okay (js)`).
- Module dependencies are USES, read from references: what a change
  reaches is decided by code that actually names the changed symbols,
  not by `dependsOn`, which over-approximates (and is not in SemanticDB).
- Anonymous classes (`new Bulk[Chunks] { ... }`) are document-local
  symbols; they are kept per document, located by their members'
  definitions and named by the definition they sit in. Three of okay's
  six Bulk implementations are anonymous.
- A qualified name matches owners as a suffix first, then in order with
  gaps (`Tables.orderedBy` finds `okay/Tables.Plan.orderedBy().`).

## Results

### v0 (2026-10-01): answers, against grep, on okay

The index: okay's core, okay-stream, okay-cluster, okay-spark,
okay-flink and what they depend on, main and test, compiled with
SemanticDB in symdex's own okay submodule — 660 `.semanticdb` files,
512 documents after folding cross-builds, 47 modules, 54 549 symbols,
287 908 occurrences, built in 0.41 s. grep is `grep -nw <name>` over
exactly those 512 files. The tasks and their hand-checked answers are
`bench/tasks.md`.

| task | grep | symdex | |
|---|---|---|---|
| callers of `Bulk#joinSorted` | 53 lines; six different `joinSorted` methods | 2 refs, callers named | exact; grep leaves 51 lines to rule out |
| implementations of `Bulk` | hand regex: 17 lines, misses `SparkBulk` (`extends okay.Bulk`), 7 are `Bulk.Format` | 3 classes, 3 anonymous, 2 givens | all six; grep missed one |
| overriders of `Bulk#joinSorted` | among the 53 lines above | 1 named, 2 anonymous | exact |
| uses of `Tables.Plan.orderedBy` | 3 lines | 3 refs | equal: a unique name |
| uses of `Fiber.isDone` | 10 lines (ForkJoinTask's, prose) | 5 refs | grep 2x |
| uses of `Fiber#answered` | 236 lines (a common word) | 1 ref | grep unusable |
| which `Ordering` `sortByKey` gets, TestJoinStrategy:28 | not answerable | `Ordering.Int`, and `Row.In.left` for the row | only symdex |
| `FlowBulk`'s members, inherited too | two files to read and diff | 21 declared, 3 inherited defaults | |
| definitions named `sortByKey` | 4 `def` lines | 4, with doc comments | equal |
| what a change to okay-stream reaches | build.sbt by hand (declared) | 27 modules (used) | different questions; see Decisions |

What this does and does not show: on names that are unique grep is as
good; on names that are common (`answered`, `isDone`, `joinSorted`) or
on questions about types (implementations, givens) it is not, and its
misses are silent. It does NOT yet show that an agent finishes tasks
in fewer calls or tokens — that is the open box above.
