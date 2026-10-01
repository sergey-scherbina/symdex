# symdex v0 task set — okay, hand-checked

Index: in this repository, with okay's slice compiled with SemanticDB:

```sh
B='{file:'"$PWD"'/okay/}'
sbt "set every semanticdbEnabled := true" "${B}okayStreamJVM/Test/compile" \
  "${B}okayClusterJVM/Test/compile" "${B}okaySpark/Test/compile" "${B}okayFlink/Test/compile"
bin/symdex files --root okay > indexed.txt      # grep's scope: the same files
```

Each task: the symdex call, the grep baseline, and the answer as checked
by reading the code (okay at the submodule's pinned commit).

1. **Callers of `Bulk#joinSorted`.**
   `bin/symdex references query=Bulk.joinSorted callers=true` ·
   `xargs grep -nw joinSorted < indexed.txt` (53 lines).
   Answer: `BulkParallel.apply` (BulkParallel.scala:53, `base.joinSorted`)
   and `Tables.Heap#compile` (Tables.scala:188).
2. **Implementations of `Bulk`.** `bin/symdex implementations query=okay.Bulk`.
   Answer: FlowBulk, FlinkBulk, SparkBulk; anonymous in `Bulk.local`,
   `BulkParallel.apply`, `okay.java.Parallel.bulk`; givens `localBulk`,
   `Parallel.bulk`. `Bulk.Format` instances are not Bulks.
3. **Overriders of `Bulk#joinSorted`.** `bin/symdex implementations query=Bulk.joinSorted`.
   Answer: FlowBulk:92, anonymous in BulkParallel.apply:52 and Bulk.local:144.
   FlinkBulk and SparkBulk inherit the default.
4. **Uses of `Tables.Plan.orderedBy`.** `references query=Tables.orderedBy`.
   Answer: Tables.scala:142 (twice) and :157.
5. **Uses of `Fiber.isDone`.** `references query=Fiber.isDone`.
   Answer: five, all in okay-platform's TestFiberIsDone.
6. **Uses of `Fiber#answered`.** `references query=Fiber.answered`.
   Answer: one, the `isDone` extension (Async.scala:151).
7. **The Ordering `sortByKey` resolves at TestJoinStrategy.scala:28.**
   `givens at=TestJoinStrategy.scala:28`. Answer: `scala.math.Ordering.Int`
   (the keys are Ints), and `okay/Row.In.left()` proving the row.
8. **`FlowBulk`'s members.** `members query=FlowBulk`. Answer: its 21
   declarations; inherited and not overridden: `read`, `sortByKey`, `uncache`.
9. **Definitions named `sortByKey`.** `definition query=sortByKey`.
   Answer: `Bulk#sortByKey`, two `Tables` extensions, `SparkBulk#sortByKey`.
10. **What a change to okay-stream reaches.** `modules query=okay-stream`.
    Answer: every module whose code references a symbol defined in
    okay-stream, transitively — 27 here, including tests.
