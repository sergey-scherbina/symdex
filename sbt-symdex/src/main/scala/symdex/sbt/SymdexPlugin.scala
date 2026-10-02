package symdex.sbt

import sbt._
import sbt.Keys._

/**
 * Makes a build symdex-ready: SemanticDB on for every project (what
 * symdex reads), and `symdexMcp` to register the server with an MCP
 * client. Nothing else — symdex runs outside sbt and notices each
 * compile by itself.
 *
 *   // project/plugins.sbt
 *   addSbtPlugin("io.github.sergey-scherbina" % "sbt-symdex" % "0.4.0")
 *   // then: sbt Test/compile symdexMcp
 */
object SymdexPlugin extends AutoPlugin {
  override def trigger = allRequirements

  object autoImport {
    val symdexHome = settingKey[Option[File]](
      "the symdex checkout or staged tree holding bin/symdex (default: $SYMDEX_HOME)")
    val symdexTools = settingKey[Seq[String]](
      "the tools to serve; empty serves all (each tool schema costs context every turn)")
    val symdexLean = settingKey[Boolean]("serve terse tool schemas")
    val symdexMcp = taskKey[File](
      "write symdex into .mcp.json (created if absent; an existing file is left alone and the entry printed)")
  }
  import autoImport._

  override def buildSettings: Seq[Setting[_]] = Seq(
    semanticdbEnabled := true,
    symdexHome := sys.env.get("SYMDEX_HOME").map(file),
    symdexTools := Nil,
    symdexLean := false,
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    symdexMcp := {
      val log = streams.value.log
      val root = (ThisBuild / baseDirectory).value
      val home = symdexHome.value.getOrElse(sys.error(
        "symdexHome is not set: set SYMDEX_HOME to the symdex checkout (or its staged tree)"))
      val launcher = home / "bin" / "symdex"
      if (!launcher.exists) sys.error(s"no launcher at $launcher")
      val args = Seq("serve", "--root", ".") ++
        (if (symdexTools.value.isEmpty) Nil else Seq("--tools", symdexTools.value.mkString(","))) ++
        (if (symdexLean.value) Seq("--lean") else Nil)
      val entry = SymdexPlugin.entry(launcher.getAbsolutePath, args)
      val file = root / ".mcp.json"
      if (!file.exists) {
        IO.write(file, s"""{"mcpServers": {$entry}}""" + "\n")
        log.info(s"symdex: wrote $file")
      } else if (IO.read(file).contains("\"symdex\"")) {
        log.info(s"symdex: $file already names symdex; left alone")
      } else {
        log.warn(s"symdex: $file exists; add this to its mcpServers:\n  $entry")
      }
      file
    }
  )

  private def quote(s: String): String =
    "\"" + s.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case c => c.toString
    } + "\""

  /** the server entry, as JSON: `"symdex": {"command": …, "args": […]}` */
  def entry(command: String, args: Seq[String]): String =
    s""""symdex": {"command": ${quote(command)}, "args": [${args.map(quote).mkString(", ")}]}"""
}
