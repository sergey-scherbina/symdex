# symdex

**Structural code intelligence for coding agents** — what a codebase *is*
(definitions, references, callers, implementations, which `given` a call
site resolves, which modules depend on which), precomputed and served over
MCP, so an agent asks instead of grepping.

Scala first, through the compiler's own output: **TASTy** (typed trees,
read with `tasty-query`, no compiler run) and **SemanticDB** (every
occurrence of every symbol). Other languages through **SCIP** indexes their
existing indexers already write (scip-java for Java/Kotlin, scip-typescript,
scip-python, rust-analyzer) — symdex does not write a per-language analyzer.

Built on [okay](https://github.com/sergey-scherbina/okay): its MCP server,
codecs and storage; what symdex needs that is general goes into okay.

## How it relates to

- **[Quill](https://github.com/treblereel/quill)** — the same idea for Java,
  from bytecode (Jandex) into SQLite, 50+ MCP tools. Bytecode loses what
  matters in Scala (givens and their resolution, extension methods, opaque
  types, inline), so symdex reads TASTy; and it keeps the tool count small,
  because every tool is context an agent pays for.
- **[rozum](https://github.com/sergey-scherbina/rozum)** — its `rag.search`
  is RETRIEVAL: syntactic chunks ranked by BM25 and embeddings, for
  questions grep loses (a concept, a symptom). symdex is STRUCTURE: exact
  answers to "who calls this", "what implements that". The two complement.

## Status

v0 is a specification: [specs/symdex.md](specs/symdex.md).

## License

Apache-2.0.
