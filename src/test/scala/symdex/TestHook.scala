package symdex

import okay.codec.Json
import okay.codec.Json.*
import java.nio.file.Files

class TestHook extends munit.FunSuite:
  private val dir = Files.createTempDirectory("symdex-hook")

  private def event(tool: String, input: (String, String), output: String): String =
    Json.print(JObj(Vector(
      "hook_event_name" -> JStr("PostToolUse"), "tool_name" -> JStr(tool),
      "tool_input" -> JObj(Vector(input._1 -> JStr(input._2))),
      "tool_response" -> JObj(Vector("type" -> JStr("text"), "text" -> JStr(output))))))

  private val searchOut =
    (1 to 600).map(i => s"okay-stream/src/main/scala/F${i % 30}.scala:$i:  val x = Source.of($i) // some text").mkString("\n")

  private def replaced(out: Option[String]): String =
    val j = Json.parse(out.getOrElse(fail("expected a replacement")))
    val Some(JObj(h)) = (j match { case JObj(fs) => fs.collectFirst { case ("hookSpecificOutput", v) => v }; case _ => None }): @unchecked
    h.collectFirst { case ("updatedToolOutput", JStr(s)) => s }.getOrElse(fail(out.toString))

  test("a large grep becomes a digest of itself, archived whole, with the symdex query"):
    val d = replaced(Hook.run(event("Bash", "command" -> "rg -n Source okay-stream", searchOut), dir))
    assert(d.contains("600 hits in 30 files"), d)
    assert(d.contains("okay-stream/src/main/scala/F0.scala  (20)"), d)
    assert(d.contains("references query=Source"), d)
    assert(d.length < searchOut.length * 3 / 10, d.length)
    val archived = """the whole output is (\S+\.txt)""".r.findFirstMatchIn(d).map(_.group(1)).get
    assertEquals(Files.readString(java.nio.file.Path.of(archived)), searchOut)

  test("the Grep tool too, by its pattern"):
    val d = replaced(Hook.run(event("Grep", "pattern" -> "\\bjoinSorted\\b", searchOut), dir))
    assert(d.contains("references query=joinSorted"), d)

  test("a test log keeps its head, tail and failures"):
    val log = ((1 to 900).map(i => s"[info] test $i passed") :+ "==> X TestFoo.bar failed: expected 1" :+ "[error] Failed: 1").mkString("\n")
    val d = replaced(Hook.run(event("Bash", "command" -> "sbt test", log), dir))
    assert(d.contains("==> X TestFoo.bar failed"), d)
    assert(d.contains("[error] Failed: 1"), d)
    assert(d.contains("[info] test 1 passed"), d)

  test("left alone: small, exact reads, diffs, credentials, other tools, garbage"):
    assertEquals(Hook.run(event("Bash", "command" -> "rg x", "a:1: b"), dir), None)
    assertEquals(Hook.run(event("Bash", "command" -> "cat big.scala", searchOut), dir), None)
    assertEquals(Hook.run(event("Bash", "command" -> "git diff HEAD~3", searchOut), dir), None)
    assertEquals(Hook.run(event("Bash", "command" -> "rg -n x | head -50", searchOut), dir), None)
    assertEquals(Hook.run(event("Bash", "command" -> "rg key", searchOut + "\nAKIAABCDEFGHIJKLMNOP"), dir), None)
    assertEquals(Hook.run(event("Read", "file_path" -> "x", searchOut), dir), None)
    assertEquals(Hook.run("not json at all", dir), None)

  test("which commands qualify"):
    assert(Hook.allowed("grep -rn foo src"))
    assert(Hook.allowed("cd x && rg -n foo"))
    assert(Hook.allowed("sbt -batch test"))
    assert(!Hook.allowed("cat file"))
    assert(!Hook.allowed("rg foo | sed -n 1,5p"))
    assert(!Hook.allowed("echo hi"))
