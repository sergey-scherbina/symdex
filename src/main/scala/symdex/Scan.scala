package symdex

import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.nio.file.attribute.BasicFileAttributes

/** One `.semanticdb` file on disk, and which module and configuration wrote it. */
final case class DbFile(path: Path, root: Path, module: String, test: Boolean,
                        mtime: Long, size: Long)

/**
 * Where a build left its SemanticDB. sbt 1.13 writes it under
 * `target/scala-<version>/meta` (`test-meta` for tests); older setups and
 * other tools put it beside the classes, in `classes/META-INF/semanticdb`.
 * Both are found. A class directory is never walked — only its
 * `META-INF/semanticdb` — so a large build's class files cost nothing.
 */
object Scan:

  private val skip = Set(".git", "node_modules", ".bsp", ".idea", ".metals", ".bloop",
    ".symdex", "streams", "zinc", "task-temp-directory", "global-logging",
    "resolution-cache", "src_managed", "resource_managed", "native", "jmh-classes")

  private val classDirs = Set("classes", "test-classes")

  def files(root: Path): Vector[DbFile] =
    val out = Vector.newBuilder[DbFile]
    val base = root.toAbsolutePath.normalize
    Files.walkFileTree(base, new SimpleFileVisitor[Path]:
      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
        val name = Option(dir.getFileName).map(_.toString).getOrElse("")
        if dir != base && skip(name) then FileVisitResult.SKIP_SUBTREE
        else if name == "semanticdb" && Option(dir.getParent).exists(_.getFileName.toString == "META-INF") then
          collect(base, dir, out)
          FileVisitResult.SKIP_SUBTREE
        else if classDirs(name) then
          val db = dir.resolve("META-INF").resolve("semanticdb")
          if Files.isDirectory(db) then collect(base, db, out)
          FileVisitResult.SKIP_SUBTREE
        else FileVisitResult.CONTINUE
      override def visitFileFailed(file: Path, e: java.io.IOException): FileVisitResult =
        FileVisitResult.CONTINUE
    ): Unit
    out.result()

  private def collect(base: Path, db: Path, out: collection.mutable.Builder[DbFile, Vector[DbFile]]): Unit =
    val (module, test) = moduleOf(base, db)
    val stream = Files.walk(db)
    try
      stream.filter(p => p.toString.endsWith(".semanticdb") && Files.isRegularFile(p))
        .forEach { p =>
          out += DbFile(p, db, module, test, Files.getLastModifiedTime(p).toMillis, Files.size(p))
        }
    finally stream.close()

  /**
   * The module is the directory that owns `target`, relative to the
   * workspace, with a cross-build's `.jvm` folded away (`okay-stream/.jvm`
   * is `okay-stream`) and other platforms named (`okay-stream (js)`).
   */
  def moduleOf(base: Path, db: Path): (String, Boolean) =
    val rel = base.relativize(db).toString.replace('\\', '/')
    val parts = rel.split('/').toVector
    val t = parts.indexOf("target")
    val owner = if t < 0 then Vector.empty else parts.take(t)
    val config = if t < 0 then "" else parts.lift(t + 2).getOrElse("")
    val test = config.startsWith("test")
    // a cross-build's platform folder: `.jvm` is the module itself,
    // `.js`/`.native` are the same module on another platform
    val (folded, platform) = owner.lastOption match
      case Some(".jvm") | Some("jvm") => (owner.init, "")
      case Some(p) if p == ".js" || p == ".native" || p == "js" || p == "native" =>
        (owner.init, s" (${p.stripPrefix(".")})")
      case _ => (owner, "")
    val name =
      (if folded.isEmpty then Option(base.getFileName).map(_.toString).getOrElse(".")
       else folded.mkString("/")) + platform
    (if test then s"$name:test" else name, test)
