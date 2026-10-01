# Working in this repository (agents)

- **okay is a submodule** (`okay/`, `git submodule update --init`). Its
  modules are ProjectRefs in `build.sbt`. Anything loupe needs that is
  general — MCP server features, a store, a codec, a TASTy-independent
  graph — is built IN OKAY, in okay's repository under okay's rules
  (okay/AGENTS.md), and the submodule is moved to it. loupe holds only what
  is loupe's: the indexers, the schema, the tools.
- **Spec first** (`specs/`): a feature's spec is written and committed
  before its code; behaviour boxes are checked off as tests cover them;
  refuted alternatives are recorded in Decisions/Results.
- **No warnings** (`-Werror` in build.sbt), **no casts without necessity**,
  **no unbounded stack recursion** — okay's code rules hold here too.
- **Measure what is claimed.** loupe's worth is measured on real agent
  tasks against grep (specs/loupe.md, "The measure"); a tool that does not
  move that number does not ship.
- Gate: `sbt test`.
