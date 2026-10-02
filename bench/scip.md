# The SCIP test index

`src/test/resources/scip/index.scip` is scip-java 0.12.3's index of the
three Java files beside it, made with:

```sh
PLUG=$(cs fetch com.sourcegraph:semanticdb-javac:0.12.3 | head -1)
javac -d out/classes -classpath "$PLUG" \
  "-Xplugin:semanticdb -sourceroot:$PWD -targetroot:$PWD/out/meta" src/shop/*.java
cs launch com.sourcegraph:scip-java_2.13:0.12.3 -M com.sourcegraph.scip_java.ScipJava -- \
  index-semanticdb --cwd "$PWD" --output index.scip out/meta
```

The first two lines alone are a Java project symdex already reads: the
javac plugin writes SemanticDB, the same format Scala's compiler writes.
SCIP is for the languages whose indexers write only SCIP
(scip-typescript, scip-python, rust-analyzer, scip-clang): put the
`index.scip` anywhere under the root.
