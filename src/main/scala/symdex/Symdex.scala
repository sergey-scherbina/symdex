package symdex

import okay.given
import okay.codec.Json
import okay.mcp.{Mcp, Server, Stdio}

import java.nio.file.Path

object Symdex:
  val version = "0.1.0"

  private val usage =
    """symdex — structural code intelligence over SemanticDB
      |
      |  symdex serve [--root DIR]               an MCP server on stdin/stdout
      |  symdex <tool> [--root DIR] key=value…   one tool call, answer on stdout
      |  symdex files [--root DIR]               the source files indexed
      |
      |tools: definition, references, implementations, givens, members, modules, status
      |e.g.   symdex references query=Bulk.joinSorted callers=true""".stripMargin

  def main(args: Array[String]): Unit =
    val (root, rest) = rootOf(args.toList)
    rest match
      case "serve" :: Nil =>
        val tools = Tools(Workspace(root))
        System.err.println(s"symdex $version: serving ${root.toAbsolutePath.normalize}")
        Server.run(Stdio.std, Mcp.Info("symdex", version), tools.specs, tools.table).runWith
      case "files" :: Nil =>
        Workspace(root).generation.index.entries.foreach(e => println(e.source))
      case tool :: kvs if tool != "help" && tool != "--help" =>
        val tools = Tools(Workspace(root))
        tools.table.get(tool) match
          case None =>
            System.err.println(s"no tool '$tool'\n\n$usage")
            sys.exit(2)
          case Some(f) =>
            println(f(okay.agent.ToolCall("cli", tool, argsOf(kvs))))
      case _ => println(usage)

  private def rootOf(args: List[String]): (Path, List[String]) =
    val i = args.indexOf("--root")
    if i >= 0 && i + 1 < args.size then (Path.of(args(i + 1)), args.patch(i, Nil, 2))
    else (Path.of(sys.env.getOrElse("SYMDEX_ROOT", ".")), args)

  private def argsOf(kvs: List[String]): Json =
    Json.JObj(kvs.toVector.map { kv =>
      kv.split("=", 2) match
        case Array(k, v) =>
          k -> (v match
            case "true" => Json.JBool(true)
            case "false" => Json.JBool(false)
            case n if n.toIntOption.isDefined => Json.JNum(n.toDouble)
            case s => Json.JStr(s))
        case Array(k) => k -> Json.JBool(true)
        case _ => kv -> Json.JBool(true)
    })
