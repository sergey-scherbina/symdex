package symdex

import java.nio.file.Path

class TestTasty extends munit.FunSuite:
  test("TASTy spans join SemanticDB's definition positions"):
    val r = Tasty.read(Path.of("target/scala-3.9.0/test-classes"))
    assertEquals(r.skipped, 0)
    // trait Shape: name at line 4 col 7 (1-based), body to line 5
    assertEquals(r.spans.get(("src/test/scala/fixture/Shapes.scala", 3, 6)), Some(Span(3, 4, false)))
    // `extension (s: Shape) def doubled`
    assert(r.spans.get(("src/test/scala/fixture/Shapes.scala", 28, 25)).exists(_.extension), r.spans)

  test("the class directory beside each SemanticDB layout"):
    assertEquals(Tasty.classDirOf(Path.of("/p/target/scala-3.9.0/meta/META-INF/semanticdb")),
      Path.of("/p/target/scala-3.9.0/classes"))
    assertEquals(Tasty.classDirOf(Path.of("/p/target/scala-3.9.0/test-meta/META-INF/semanticdb")),
      Path.of("/p/target/scala-3.9.0/test-classes"))
    assertEquals(Tasty.classDirOf(Path.of("/p/target/scala-2.13/classes/META-INF/semanticdb")),
      Path.of("/p/target/scala-2.13/classes"))
