package loupe

class TestLoupe extends munit.FunSuite:
  test("the build resolves okay and tasty-query") {
    assertEquals(Loupe.version, "0.0.0")
    assert(classOf[tastyquery.Contexts.Context] != null)
    assert(okay.mcp.Server.getClass != null)
  }
