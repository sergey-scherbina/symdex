# symdex

**Structural code intelligence for coding agents** — what a codebase *is*
(definitions, references, callers, implementations, which `given` a call
site resolves, which modules depend on which), precomputed and served over
MCP, so an agent asks instead of grepping.

Scala first, through the compiler's own output: **SemanticDB** — every
occurrence of every symbol, what each symbol is, and which given every
call site resolved. Other languages, later, through **SCIP** indexes their
existing indexers already write (scip-java, scip-typescript, scip-python,
rust-analyzer); symdex does not write a per-language analyzer.

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

## Tools

| tool | answers |
|---|---|
| `definition` | where a symbol is defined, with its doc comment and signature lines |
| `references` | every use, resolved by the compiler; `callers` groups them by enclosing definition |
| `implementations` | subtypes, overriders, anonymous `new T { … }`, given instances of a type class |
| `givens` | what the compiler inserted at a line: which given each call resolved |
| `members` | declared and inherited members |
| `modules` | which modules use which, and what a change reaches |
| `outline` | a file's definitions, one line each: what is in it, without reading it |
| `source` | one definition's text, not its file |
| `more` | a page of an answer that was too long and was archived |
| `status` | the index's generation, age, coverage, and sources edited since compiled |

Every answer has a size budget (8 000 characters by default). Over it, an
answer is compressed to its header, the files it names, and its first lines,
all copied from the answer itself. The whole answer is archived, and `more`
pages through it. Exact reads such as `source` are paged, never summarized.
That is the "context diet" applied to a code index, without a model in the
loop: specs/symdex.md, "Context diet".

A query is a name (`joinSorted`, `Bulk.joinSorted`), a SemanticDB symbol
(`okay/Bulk#joinSorted().`) or a position (`Tables.scala:188:60`). A name
that means several symbols is answered with the candidates.

## Use

1. Compile the project with SemanticDB on — in its sbt, once per session:
   `set every semanticdbEnabled := true` then `Test/compile`, or
   `semanticdbEnabled := true` in its build.
2. Ask from the shell: `bin/symdex references query=Bulk.joinSorted callers=true --root ../project`
3. Or serve it to an agent over MCP. For Claude Code, in the project's `.mcp.json`:

   ```json
   {"mcpServers": {"symdex": {"command": "/path/to/symdex/bin/symdex",
     "args": ["serve", "--root", "."]}}}
   ```

The index rebuilds itself when a compile rewrites the SemanticDB; nothing
else needs to run. Results on okay against grep: [specs/symdex.md](specs/symdex.md#results).

## Build

`git clone --recursive`, then `sbt test`. okay is a submodule.

## License

Apache-2.0.
