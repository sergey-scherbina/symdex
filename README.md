# symdex

**Structural code intelligence for coding agents** — what a codebase *is*
(definitions, references, callers, implementations, which `given` a call
site resolves, which modules depend on which), precomputed and served over
MCP, so an agent asks instead of grepping.

Scala first, through the compiler's own output: **SemanticDB** — every
occurrence of every symbol, what each symbol is, and which given every
call site resolved — and **TASTy** for where each definition's body ends
and which methods are extensions. Java through the same SemanticDB
(scip-java's javac plugin writes it). Other languages through the **SCIP**
indexes their own indexers write (scip-typescript, scip-python,
rust-analyzer, scip-clang): drop an `index.scip` under the root. symdex
writes no per-language analyzer.

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

Tool schemas cost context on every turn: `serve --tools definition,references,source,outline --lean`
serves four terse tools for about 335 tokens instead of about 1 517 for all ten.
`status` shows both numbers.

A query is a name (`joinSorted`, `Bulk.joinSorted`), a SemanticDB symbol
(`okay/Bulk#joinSorted().`) or a position (`Tables.scala:188:60`). A name
that means several symbols is answered with the candidates.

## Use

1. Make the build symdex-ready — one line in `project/plugins.sbt`:

   ```scala
   addSbtPlugin("io.github.sergey-scherbina" % "sbt-symdex" % "0.4.0")
   ```

   (until it is published: `cd sbt-symdex && sbt publishLocal` here first). It turns
   SemanticDB on for every project. Or, without the plugin, `semanticdbEnabled := true`.
2. Compile (`sbt Test/compile`), then register the server: `SYMDEX_HOME=/path/to/symdex sbt symdexMcp`
   writes it into the project's `.mcp.json`. By hand, for Claude Code:

   ```json
   {"mcpServers": {"symdex": {"command": "/path/to/symdex/bin/symdex",
     "args": ["serve", "--root", "."]}}}
   ```

3. Ask from the shell too: `bin/symdex references query=Bulk.joinSorted callers=true --root ../project`

`bin/symdex` stages itself with sbt once (and again only when symdex's sources
change); every other call is plain java, about 0.7 s. `sbt dist` zips the staged tree.

The index rebuilds itself when a compile rewrites the SemanticDB; nothing
else needs to run. Results on okay against grep: [specs/symdex.md](specs/symdex.md#results).

## The hook

`bin/symdex-hook` is a Claude Code PostToolUse hook: a large search or test
output (3 500+ tokens) is replaced by an exact digest of itself — the files it
names with a count each, its first hits, or a log's head, tail and error lines —
and the whole output is archived in `.symdex/archive` for the agent to read back.
No model writes the digest, so it cannot invent a path. Exact reads and diffs
are never touched. In `.claude/settings.json`:

```json
{"hooks": {"PostToolUse": [{"matcher": "Bash|Grep",
  "hooks": [{"type": "command", "command": "/path/to/symdex/bin/symdex-hook"}]}]}}
```

`rg -n answered` over okay: 167 409 characters in, 4 745 out.

## Build

`git clone --recursive`, then `sbt test`. okay is a submodule.

## License

Apache-2.0.
