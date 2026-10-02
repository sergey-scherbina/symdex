package symdex

import java.net.URI
import java.nio.file.{FileSystems, Files, Path}

import scala.jdk.CollectionConverters.*

import tastyquery.Contexts.Context
import tastyquery.Symbols.TermSymbol
import tastyquery.Traversers.TreeTraverser
import tastyquery.Trees.{DefTree, Tree}
import tastyquery.jdk.ClasspathLoaders

/** A definition's full extent, 0-based lines, and whether it is an extension method. */
final case class Span(startLine: Int, endLine: Int, extension: Boolean)

/**
 * What SemanticDB does not record, read from TASTy: where each
 * definition's BODY ends (SemanticDB has only the name's range), and
 * which methods are extension methods. Keyed by the source path as
 * TASTy wrote it and the position of the definition's name, which is
 * exactly SemanticDB's definition occurrence — the join between the two.
 *
 * The class directory is read with whatever else it needs for its trees
 * to unpickle: the Scala library this process runs on, and the JDK. A
 * project's other dependencies are absent, and a top-level class whose
 * trees cannot be read without them is skipped and counted, never fatal:
 * its definitions keep SemanticDB's approximations.
 */
object Tasty:

  final case class Read(spans: Map[(String, Int, Int), Span], classes: Int, skipped: Int)

  private lazy val baseClasspath: List[Path] =
    val jrt = FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules", "java.base")
    val scalaJars = sys.props.getOrElse("java.class.path", "").split(java.io.File.pathSeparator).toList
      .filter(p => p.contains("scala3-library") || p.contains("scala-library"))
      .map(Path.of(_))
    jrt :: scalaJars

  def read(classDir: Path): Read =
    if !Files.isDirectory(classDir) then Read(Map.empty, 0, 0)
    else
      val entries = ClasspathLoaders.read(classDir :: baseClasspath)
      given ctx: Context = Context.initialize(entries)
      val out = collection.mutable.HashMap.empty[(String, Int, Int), Span]
      var classes = 0
      var skipped = 0
      // the traversal is tasty-query's, recursive over the tree; its depth
      // is the source's nesting depth, which the compiler recursed over too
      val collect = new TreeTraverser:
        override def traverse(t: Tree): Unit =
          t match
            case d: DefTree =>
              val p = t.pos
              if !p.isUnknown && p.hasLineColumnInformation && !p.isSynthetic then
                out((p.sourceFile.path, p.pointLine, p.pointColumn)) =
                  Span(p.startLine, p.endLine, d.symbol match
                    case t: TermSymbol => t.isExtensionMethod
                    case _ => false)
            case _ => ()
          super.traverse(t)
      for sym <- ctx.findSymbolsByClasspathEntry(entries.head) do
        classes += 1
        try sym.tree.foreach(collect.traverse)
        catch case _: Exception => skipped += 1
      Read(out.toMap, classes, skipped)

  /** the class directory beside a SemanticDB root: `meta` → `classes`, `test-meta` → `test-classes`, or the one it is in */
  def classDirOf(semanticdbRoot: Path): Path =
    val metaDir = semanticdbRoot.getParent.getParent // .../meta (or .../classes) above META-INF/semanticdb
    metaDir.getFileName.toString match
      case "meta" => metaDir.resolveSibling("classes")
      case "test-meta" => metaDir.resolveSibling("test-classes")
      case _ => metaDir

/** One class directory's TASTy, read on first use and kept while its SemanticDB is unchanged. */
final class TastyDir(val dir: Path, val stamp: Long):
  @volatile private var failure: Option[String] = None
  lazy val read: Tasty.Read =
    try Tasty.read(dir)
    catch case e: Exception =>
      failure = Some(Option(e.getMessage).getOrElse(e.toString))
      Tasty.Read(Map.empty, 0, 0)
  def failed: Option[String] = failure
  @volatile private var done = false
  def loaded: Boolean = done
  def force(): Tasty.Read = { val r = read; done = true; r }

/**
 * Spans by SemanticDB root, loaded per class directory when a tool first
 * asks about a file in it — `references` with callers loads only the
 * modules its references are in. TASTy writes a source path relative to
 * its build's root, as SemanticDB writes its uri: usually the same string;
 * when two builds disagree on the root (okay compiled from symdex's build),
 * the longer path ends with the shorter.
 */
final class Spans(dirs: Map[Path, TastyDir]):
  private val byRoot = collection.concurrent.TrieMap.empty[Path, Map[String, Map[(Int, Int), Span]]]

  private def table(root: Path): Map[String, Map[(Int, Int), Span]] =
    byRoot.getOrElseUpdate(root, dirs.get(root) match
      case None => Map.empty
      case Some(d) =>
        d.force().spans.toVector.groupMap(_._1._1)((k, v) => (k._2, k._3) -> v).view.mapValues(_.toMap).toMap)

  /** the span of the definition whose name SemanticDB puts at `r` in `uri`, under SemanticDB root `root` */
  def of(root: Path, uri: String, r: Range): Option[Span] =
    val t = table(root)
    val path = if t.contains(uri) then Some(uri) else t.keys.find(p => p.endsWith("/" + uri) || uri.endsWith("/" + p))
    path.flatMap(p => t(p).get((r.startLine, r.startChar)))

  /** read every class directory now, in parallel: a server does this once, in the background */
  def warm(): Unit =
    val all = java.util.ArrayList[Path](dirs.keys.toVector.sortBy(_.toString).toSeq.asJava)
    all.parallelStream().forEach(r => { val _ = table(r) })

  private def done = dirs.values.filter(_.loaded)
  def directories: Int = dirs.size
  def loaded: Int = done.size
  def classes: Int = done.iterator.map(_.read.classes).sum
  def skipped: Int = done.iterator.map(_.read.skipped).sum
  def size: Int = done.iterator.map(_.read.spans.size).sum
  def failures: Vector[String] = dirs.values.flatMap(d => d.failed.map(m => s"tasty ${d.dir}: $m")).toVector

object Spans:
  val none: Spans = Spans(Map.empty)
