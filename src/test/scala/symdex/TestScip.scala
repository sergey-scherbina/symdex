package symdex

import okay.codec.Json
import java.nio.file.{Files, Path}

/**
 * A real SCIP index: scip-java 0.12.3 over the three Java files beside it
 * (src/test/resources/scip; how it was made is in bench/scip.md). Every
 * expectation was read off those files.
 */
class TestScip extends munit.FunSuite:
  private val dir = Path.of("src/test/resources/scip")
  private val tools = Tools(Workspace(dir))
  private def call(f: Json => String, kv: (String, Json)*): String = f(Json.JObj(kv.toVector))
  private def q(s: String) = "query" -> Json.JStr(s)

  test("the symbol's descriptor is what is kept"):
    assertEquals(Scip.descriptor("semanticdb maven . . shop/Shape#area()."), "shop/Shape#area().")
    assertEquals(Scip.descriptor("scip-typescript npm my  pkg 1.0 src/`a.ts`/f()."), "src/`a.ts`/f().")
    assertEquals(Scip.descriptor("local 7"), "local7")

  test("documents, definitions, references"):
    val r = Scip.read(Files.readAllBytes(dir.resolve("index.scip")))
    assertEquals(r.documents.map(_.uri).sorted, Vector("src/shop/Shape.java", "src/shop/Square.java", "src/shop/Use.java"))
    val defs = r.documents.flatMap(_.occurrences).filter(_.definition).map(_.symbol).toSet
    assert(defs("shop/Shape#area()."), defs)

  test("the tools answer over SCIP: implementations, overriders, callers, source"):
    val impl = call(tools.implementations, q("shop.Shape"))
    assert(impl.contains("shop/Square#"), impl)
    val area = call(tools.implementations, q("shop/Shape#area()."))
    assert(area.contains("shop/Square#area()."), area)
    val callers = call(tools.references, q("Shape.area"), "callers" -> Json.JBool(true))
    assert(callers.contains("shop.Use#total"), callers)
    val src = call(tools.source, q("shop/Use#total()."))
    assert(src.contains("return sum;"), src)
    assert(!src.contains("main"), src)
