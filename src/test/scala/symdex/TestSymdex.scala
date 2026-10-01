package symdex

class TestSymdex extends munit.FunSuite:
  test("the build resolves okay and tasty-query") {
    assertEquals(Symdex.version, "0.0.0")
    assert(classOf[tastyquery.Contexts.Context] != null)
    assert(okay.mcp.Server.getClass != null)
  }
