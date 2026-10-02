package symdex

import okay.codec.Json
import java.nio.file.Path

/**
 * The tools against fixtures this build compiles itself
 * (src/test/scala/fixture, SemanticDB on in build.sbt). Every expected
 * answer below was read off the fixture source by hand.
 */
class TestSymdex extends munit.FunSuite:

  private val ws = Workspace(Path.of("."), include = f => f.module == "symdex:test")
  private val tools = Tools(ws)
  private def call(f: Json => String, kv: (String, Json)*): String = f(Json.JObj(kv.toVector))
  private def q(s: String) = "query" -> Json.JStr(s)
  private val yes = Json.JBool(true)

  test("the fixtures are indexed"):
    val g = ws.generation
    assert(g.index.entries.exists(_.file == "src/test/scala/fixture/Shapes.scala"), g.index.entries.map(_.file))
    assert(g.unreadable.isEmpty, g.unreadable)

  test("symbol names parse"):
    assertEquals(Symbols.names("fixture/Shape#area()."), Vector("fixture", "Shape", "area"))
    assertEquals(Symbols.names("fixture/Square#`<init>`().(side)"), Vector("fixture", "Square", "<init>", "side"))
    assertEquals(Symbols.names("fixture/Show#[A]"), Vector("fixture", "Show", "A"))
    assertEquals(Symbols.names("scala/Predef.println(+1)."), Vector("scala", "Predef", "println"))

  test("definition: by qualified name, with its doc comment"):
    val out = call(tools.definition, q("fixture.Shape"))
    assert(out.contains("fixture/Shape#  (trait)"), out)
    assert(out.contains("src/test/scala/fixture/Shapes.scala:4:7"), out)
    assert(out.contains("/** A shape has an area. */"), out)

  test("definition: a name with several meanings lists every candidate"):
    val out = call(tools.definition, q("area"))
    for s <- Seq("fixture/Shape#area().", "fixture/Square#area().", "fixture/Circle#area().", "fixture/Triangle#area().") do
      assert(out.contains(s), out)

  test("definition: by position"):
    // line 40 is `render(1) + render(shapes.head) + total(shapes) + ...`; col 32 is inside `total`
    val out = call(tools.definition, q("src/test/scala/fixture/Shapes.scala:40:42"))
    assert(out.contains("fixture/Use.total()."), out)

  test("references: across files, and grouped by caller"):
    val plain = call(tools.references, q("Use.total"))
    assert(plain.contains("2 references in 2 files"), plain)
    assert(plain.contains("src/test/scala/fixture/Other.scala: 6"), plain)
    val callers = call(tools.references, q("Use.total"), "callers" -> yes)
    assert(callers.contains("fixture.Use.report"), callers)
    assert(callers.contains("fixture.other.Other.big"), callers)

  test("a qualified name may skip owners, in order"):
    // `Use.total` is exact; `fixture.total` skips the object
    val out = call(tools.definition, q("fixture.total"))
    assert(out.contains("fixture/Use.total()."), out)

  test("references: an ambiguous name asks, unless all"):
    val asks = call(tools.references, q("area"))
    assert(asks.contains("symbols match"), asks)
    val all = call(tools.references, q("area"), "all" -> yes)
    assert(all.contains("references in"), all)

  test("implementations: transitive subtypes, overriders, given instances"):
    val shape = call(tools.implementations, q("fixture.Shape"))
    for s <- Seq("fixture/Square#", "fixture/Circle#", "fixture/Polygon#", "fixture/Triangle#") do
      assert(shape.contains(s), shape)
    assert(shape.contains("in fixture.Use.unit  src/test/scala/fixture/Shapes.scala:36"), shape)
    val area = call(tools.implementations, q("fixture/Shape#area()."))
    assert(area.contains("fixture/Triangle#area()."), area)
    assert(area.contains("in fixture.Use.unit"), area)
    val show = call(tools.implementations, q("fixture.Show"))
    assert(show.contains("fixture/Show.given_Show_Int."), show)
    assert(show.contains("fixture/Show.showShape."), show)

  test("givens: which instance a call site resolved"):
    val out = call(tools.givens, "at" -> Json.JStr("fixture/Shapes.scala:40"))
    assert(out.contains("given fixture/Show.given_Show_Int."), out)
    assert(out.contains("given fixture/Show.showShape."), out)

  test("members: declared and inherited"):
    val out = call(tools.members, q("fixture.Triangle"))
    assert(out.contains("sides  (method)"), out)
    assert(out.contains("inherited from fixture/Polygon#"), out)
    assert(out.contains("label  (method)"), out)
    assert(!out.contains("copy"), out)

  test("outline: what a file is, nested, without its text"):
    val out = call(tools.outline, "file" -> Json.JStr("Shapes.scala"))
    assert(out.contains("src/test/scala/fixture/Shapes.scala"), out)
    assert(out.contains("trait Shape"), out)
    assert(out.contains("  def total"), out)
    assert(out.contains("given showShape"), out)
    assert(!out.contains("shapes.map"), out)

  test("source: one body, by indentation, not the file"):
    val tri = call(tools.source, q("fixture.Triangle"))
    assert(tri.contains("final class Triangle"), tri)
    assert(tri.contains("def area: Double = b * h / 2"), tri)
    assert(!tri.contains("trait Show"), tri)
    assert(tri.contains("(3 lines)"), tri)
    val one = call(tools.source, q("Use.total"))
    assert(one.contains("(1 lines)"), one)
    val capped = call(tools.source, q("fixture.Use"), "maxLines" -> Json.JNum(2))
    assert(capped.contains("more lines (raise `maxLines`)"), capped)

  private def wire(tool: String, kv: (String, Json)*): String =
    tools.table(tool)(okay.agent.ToolCall("t", tool, Json.JObj(kv.toVector)))

  test("over budget, an answer is compressed from itself and archived whole"):
    val full = wire("outline", "file" -> Json.JStr("Shapes.scala"))
    val out = wire("outline", "file" -> Json.JStr("Shapes.scala"), "budget" -> Json.JNum(300))
    assert(out.contains("[compressed:"), out)
    assert(out.contains("first lines:"), out)
    val id = """archived as `([0-9a-f]+)`""".r.findFirstMatchIn(out).map(_.group(1)).getOrElse(fail(out))
    // every line `more` gives back is a line of the full answer: nothing invented
    val page = wire("more", "id" -> Json.JStr(id), "from" -> Json.JNum(1), "lines" -> Json.JNum(500))
    val body = page.linesIterator.drop(1).toVector
    assertEquals(body, full.linesIterator.toVector)

  test("an exact read is paged, never summarized"):
    val out = wire("source", q("fixture.Use"), "budget" -> Json.JNum(150))
    assert(out.contains("[paged:"), out)
    assert(!out.contains("[compressed:"), out)

  test("references `in` narrows to a path or module"):
    val out = call(tools.references, q("Use.total"), "in" -> Json.JStr("Other.scala"))
    assert(out.contains("1 references in 1 files"), out)

  test("modules and status"):
    val m = call(tools.modules)
    assert(m.contains("symdex:test"), m)
    val s = call(tools.status)
    assert(s.contains("documents indexed"), s)

  test("an unchanged disk is the same generation"):
    val a = Workspace(Path.of("."), recheckMillis = 0, include = f => f.module == "symdex:test")
    assertEquals(a.generation.number, 1)
    assertEquals(a.generation.number, 1)

class TestScan extends munit.FunSuite:
  private val base = Path.of("/w/okay")
  private def mod(rel: String) = Scan.moduleOf(base, base.resolve(rel))

  test("module names: cross platforms, the build root, tests"):
    assertEquals(mod("okay-stream/.jvm/target/scala-3.9.0/meta/META-INF/semanticdb"), ("okay-stream", false))
    assertEquals(mod("okay-stream/.js/target/scala-3.9.0/meta/META-INF/semanticdb"), ("okay-stream (js)", false))
    assertEquals(mod(".jvm/target/scala-3.9.0/test-meta/META-INF/semanticdb"), ("okay:test", true))
    assertEquals(mod(".native/target/scala-3.9.0/meta/META-INF/semanticdb"), ("okay (native)", false))
    assertEquals(mod("okay-spark/target/scala-3.9.0/meta/META-INF/semanticdb"), ("okay-spark", false))
