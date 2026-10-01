# loupe v0 — structural code intelligence over MCP

Status: DRAFT (2026-10-01). Nothing below is built yet; boxes are checked
as tests cover them.

## Why

An agent working in a Scala codebase spends most of its tool calls
finding things: where a symbol is defined, who calls it, which `given` a
call site actually gets, which modules a change reaches. grep answers by
text, so it misses renamed imports, extension methods, givens, overloads
and inherited members, and it floods on common names. The compiler
already knows all of it, exactly, and writes it down. loupe reads what
the compiler wrote and answers those questions over MCP.

Not retrieval: rozum's `rag.search` ranks chunks for a concept or a
symptom. loupe answers "who calls this" with a list that is complete or
says it is not.

## Sources

- **TASTy** (`.tasty` beside every `.class` of a Scala 3 build), read with
  `tasty-query` — typed trees without running the compiler: definitions,
  signatures, parents, givens, extension methods, opaque types, inline.
- **SemanticDB** (`-Xsemanticdb`) — every occurrence of every symbol with
  its range: what references and call sites need. TASTy has the trees,
  SemanticDB has the positions as written; both are needed.
- **sbt's own model** — projects, their `dependsOn`, source dirs, class
  dirs. Exported by a small sbt plugin (`loupeExport`), no parsing of
  build.sbt.
- v1: **SCIP** indexes for other languages (scip-java, scip-typescript,
  scip-python, rust-analyzer), ingested into the same symbol/occurrence
  model. loupe writes no per-language analyzer.

## Tools (v0)

Few and exact; every tool is context an agent pays for (Quill's 50+ is
the counter-example). Each answer names the generation it came from and
its age.

- [ ] `loupe.definition(symbol | file:line:col)` — where it is defined,
      its signature and doc comment.
- [ ] `loupe.references(symbol)` — every occurrence, grouped by file;
      `callers` is the same filtered to call sites.
- [ ] `loupe.implementations(symbol)` — subclasses, overriding members,
      `given` instances of a typeclass.
- [ ] `loupe.givens(file:line:col)` — which givens the compiler resolved
      at that call site (from SemanticDB synthetics).
- [ ] `loupe.members(type)` — declared and inherited members, extensions
      in scope included.
- [ ] `loupe.modules(path | project)` — which project owns a path, what it
      depends on, what depends on it (the "affected" question).
- [ ] `loupe.status` — generations, their age against the sources, what is
      stale.

Symbol names are SemanticDB's (`okay/Free#flatMap().`), with a fuzzy
lookup from a plain name that returns candidates rather than guessing.

## Freshness

- [ ] An index is a GENERATION: built whole, immutable, swapped in
      atomically under `.loupe/`. A query never sees a half-built index.
- [ ] Building is incremental per class directory (hash of its
      `.tasty`/`.semanticdb` files); an unchanged module is reused.
- [ ] The sbt plugin rebuilds after `compile`; nothing else is a trigger.
- [ ] Every answer carries the generation's age and whether any source
      file is newer than it — a stale answer says so, never silently.

## Storage

Open question, decided by measurement, not taste: an okay store (the
durable log + an in-memory index rebuilt on open) against SQLite (Quill's
choice). The okay road is preferred if open+query on the okay repo (≈5k
files) is within 2x of SQLite; whatever is missing goes into okay.

## The measure

loupe ships only what moves this number. Take real tasks from okay's own
history (e.g. "every caller of `Bulk.joinSorted`", "which modules does a
change to `Tables.scala` reach", "which `given Ordering` does this
`sortByKey` get"), run each with grep/Read only and with loupe, and
record tool calls, tokens and correctness (missed or false hits against
a hand-checked answer).

- [ ] A task set of ≥10 with hand-checked answers, under `bench/`.
- [ ] A results table per release in this spec's Results.

## What goes into okay

- MCP server features the tools need (structured results, pagination).
- The store, if okay's wins the storage measurement.
- Nothing about TASTy or SemanticDB — that is loupe's.

## Decisions

- TASTy over bytecode: bytecode erases what Scala users ask about
  (givens, extensions, opaque types, inline).
- SCIP for other languages instead of writing analyzers.
- Separate repository, okay as a submodule: loupe is a product on okay,
  like okay-chat; it must not grow okay's build.

## Results

(none yet)
