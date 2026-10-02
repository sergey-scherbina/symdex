package symdex

import okay.*
import okay.given
import okay.agent.ToolCall
import okay.codec.Json
import okay.mcp.{Mcp, Rpc, Server}
import java.nio.file.Path

/** symdex on the wire: okay's MCP server stage, driven with messages, no process. */
class TestServe extends munit.FunSuite:

  private val tools = Tools(Workspace(Path.of("."), include = f => f.module == "symdex:test"))
  private val info = Mcp.Info("symdex", Symdex.version)

  private def talk(msgs: Rpc*): Seq[Rpc] =
    !.run(Writer.run(through(Writer.of(msgs.toList))(Server.serve(info, tools.specs, tools.table))))._1

  private val hello = Rpc.Request(Json.JNum(1), Mcp.Initialize, Mcp.initializeParams(Mcp.Info("client", "1")))

  test("tools/list names the ten tools"):
    val Rpc.Answer(_, result) = talk(hello, Rpc.Request(Json.JNum(2), Mcp.ToolsList, Json.JObj(Vector.empty)))(1): @unchecked
    assertEquals(Mcp.toolsOf(result)._1.map(_.name).toSet,
      Set("definition", "references", "implementations", "givens", "members", "modules", "status", "source", "outline", "more"))

  test("a lean, narrowed server: only the tools asked for, terse schemas, fewer tokens"):
    val full = tools.serving(None, lean = false)
    val lean = tools.serving(Some(Set("definition", "references")), lean = true)
    assertEquals(lean.tools.map(_.name).toSet, Set("definition", "references"))
    assertEquals(lean.call.keySet, Set("definition", "references"))
    assert(!Json.print(lean.tools.head.schema).contains("description"), lean.tools.head.schema)
    val (f, l) = (Tools.schemaTokens(full.tools), Tools.schemaTokens(lean.tools))
    assert(l * 4 < f, s"lean $l vs full $f")
    // what is not served is absent: asking for it answers "no such tool"
    val ask = Rpc.Request(Json.JNum(9), Mcp.ToolsCall, Mcp.callParams(ToolCall("z", "status", Json.JObj(Vector.empty))))
    val out = !.run(Writer.run(through(Writer.of(List[Rpc](hello, ask)))(Server.serve(lean))))._1
    val Rpc.Answer(_, r) = out(1): @unchecked
    assert(Mcp.textOf(r).contains("no such tool"), Mcp.textOf(r))

  test("every answer says what it costs"):
    val r = tools.table("status")(ToolCall("s", "status", Json.JObj(Vector.empty)))
    assert("""~\d+ tokens\]""".r.findFirstIn(r.linesIterator.next()).isDefined, r)
    assert(r.contains("tool schemas: 10 tools"), r)

  test("tools/call answers; a missing argument is an isError answer"):
    val ok = Rpc.Request(Json.JNum(3), Mcp.ToolsCall, Mcp.callParams(
      ToolCall("x", "references", Json.JObj(Vector("query" -> Json.JStr("Use.total"))))))
    val bad = Rpc.Request(Json.JNum(4), Mcp.ToolsCall, Mcp.callParams(
      ToolCall("y", "references", Json.JObj(Vector.empty))))
    val out = talk(hello, ok, bad)
    val Rpc.Answer(_, r1) = out(1): @unchecked
    assert(Mcp.textOf(r1).contains("2 references in 2 files"), Mcp.textOf(r1))
    val Rpc.Answer(_, r2) = out(2): @unchecked
    assertEquals(Rpc.field(r2, "isError"), Some(Json.JBool(true)))
    assert(Mcp.textOf(r2).contains("missing argument 'query'"))
