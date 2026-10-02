package symdex

/** A source range, 0-based lines and characters, as SemanticDB writes it. */
final case class Range(startLine: Int, startChar: Int, endLine: Int, endChar: Int):
  def covers(line: Int, char: Int): Boolean =
    (line > startLine || (line == startLine && char >= startChar)) &&
      (line < endLine || (line == endLine && char <= endChar))

/** One occurrence of a symbol in a document. */
final case class Occurrence(range: Range, symbol: String, definition: Boolean)

/**
 * What SemanticDB says about one symbol, reduced to what the tools ask:
 * its kind and properties, its parents (a class) or the head of its
 * result type (a method or a value — which is how a `given Ordering[K]`
 * is found from `Ordering`), its declarations and what it overrides.
 */
final case class Info(symbol: String, kind: Int, properties: Int, name: String,
                      parents: Vector[String], declarations: Vector[String],
                      result: String, overridden: Vector[String],
                      params: Vector[String] = Vector.empty):
  def is(property: Int): Boolean = (properties & property) != 0
  def kindName: String = Info.kindNames.getOrElse(kind, "symbol")
  def isGiven: Boolean = is(Info.Given) || is(Info.Implicit)

object Info:
  val Abstract = 0x4
  val Final = 0x8
  val Sealed = 0x10
  val Implicit = 0x20
  val Lazy = 0x40
  val Case = 0x80
  val Val = 0x400
  val Var = 0x800
  val Enum = 0x4000
  val Given = 0x10000
  val Inline = 0x20000
  val Opaque = 0x200000

  val Local = 19
  val Field = 20
  val Method = 3
  val Constructor = 21
  val Macro = 6
  val Type = 7
  val Parameter = 8
  val SelfParameter = 17
  val TypeParameter = 9
  val Object = 10
  val Package = 11
  val PackageObject = 12
  val Class = 13
  val Trait = 14
  val Interface = 18

  val kindNames: Map[Int, String] = Map(
    Local -> "local", Field -> "field", Method -> "method", Constructor -> "constructor",
    Macro -> "macro", Type -> "type", Parameter -> "parameter", SelfParameter -> "self",
    TypeParameter -> "type parameter", Object -> "object", Package -> "package",
    PackageObject -> "package object", Class -> "class", Trait -> "trait",
    Interface -> "interface")

  private val propertyNames = Vector(Abstract -> "abstract", Final -> "final", Sealed -> "sealed",
    Implicit -> "implicit", Lazy -> "lazy", Case -> "case", Enum -> "enum", Given -> "given",
    Inline -> "inline", Opaque -> "opaque")

  def describe(i: Info): String =
    (propertyNames.collect { case (p, n) if i.is(p) => n } :+ i.kindName).mkString(" ")

/**
 * Something the compiler inserted at a range — a `using` argument, an
 * implicit conversion, an inferred `apply` — with every symbol the
 * inserted tree names.
 */
final case class Synthetic(range: Range, symbols: Vector[String])

/** One `.semanticdb` text document, as read. */
final case class Document(uri: String, infos: Vector[Info], occurrences: Vector[Occurrence],
                          synthetics: Vector[Synthetic])

/**
 * The SemanticDB schema (scalameta's semanticdb.proto), decoded by hand
 * for the fields symdex uses. Field numbers are the schema's; everything
 * else is skipped, so a newer producer with more fields still reads.
 */
object Semanticdb:

  def read(bytes: Array[Byte]): Vector[Document] =
    val p = Proto(bytes)
    val docs = Vector.newBuilder[Document]
    while p.hasMore do
      p.field() match
        case (1, 2) => docs += document(p.message())
        case (_, w) => p.skip(w)
    docs.result()

  private def document(p: Proto): Document =
    var uri = ""
    val infos = Vector.newBuilder[Info]
    val occs = Vector.newBuilder[Occurrence]
    val synths = Vector.newBuilder[Synthetic]
    while p.hasMore do
      p.field() match
        case (2, 2) => uri = p.string()
        case (5, 2) => infos += info(p.message())
        case (6, 2) => occs += occurrence(p.message())
        case (12, 2) => synths += synthetic(p.message())
        case (_, w) => p.skip(w)
    Document(uri, infos.result(), occs.result(), synths.result())

  private def range(p: Proto): Range =
    var sl, sc, el, ec = 0
    while p.hasMore do
      p.field() match
        case (1, 0) => sl = p.int()
        case (2, 0) => sc = p.int()
        case (3, 0) => el = p.int()
        case (4, 0) => ec = p.int()
        case (_, w) => p.skip(w)
    Range(sl, sc, el, ec)

  private def occurrence(p: Proto): Occurrence =
    var r = Range(0, 0, 0, 0)
    var sym = ""
    var role = 0
    while p.hasMore do
      p.field() match
        case (1, 2) => r = range(p.message())
        case (2, 2) => sym = p.string()
        case (3, 0) => role = p.int()
        case (_, w) => p.skip(w)
    Occurrence(r, sym, role == 2)

  private def info(p: Proto): Info =
    var sym = ""
    var kind = 0
    var props = 0
    var name = ""
    var sig = Sig.empty
    val overridden = Vector.newBuilder[String]
    while p.hasMore do
      p.field() match
        case (1, 2) => sym = p.string()
        case (3, 0) => kind = p.int()
        case (4, 0) => props = p.int()
        case (5, 2) => name = p.string()
        case (17, 2) => sig = signature(p.message())
        case (19, 2) => overridden += p.string()
        case (_, w) => p.skip(w)
    Info(sym, kind, props, name, sig.parents, sig.declarations, sig.result, overridden.result(), sig.params)

  private final case class Sig(parents: Vector[String], declarations: Vector[String], result: String,
                               params: Vector[String] = Vector.empty)
  private object Sig:
    val empty: Sig = Sig(Vector.empty, Vector.empty, "")

  private def signature(p: Proto): Sig =
    var sig = Sig.empty
    while p.hasMore do
      p.field() match
        case (1, 2) => sig = classSignature(p.message())
        case (2, 2) => sig = methodSignature(p.message())
        case (4, 2) => sig = Sig.empty.copy(result = typeAt(p.message(), 1))
        case (_, w) => p.skip(w)
    sig

  /** a method's first parameter list (an extension's receiver is its first) and its result type */
  private def methodSignature(p: Proto): Sig =
    var params = Vector.empty[String]
    var first = true
    var result = ""
    while p.hasMore do
      p.field() match
        case (2, 2) =>
          val list = scope(p.message())
          if first then { params = list; first = false }
        case (3, 2) => result = headSymbol(p.message())
        case (_, w) => p.skip(w)
    Sig(Vector.empty, Vector.empty, result, params)

  private def classSignature(p: Proto): Sig =
    val parents = Vector.newBuilder[String]
    var decls = Vector.empty[String]
    while p.hasMore do
      p.field() match
        case (2, 2) => parents += headSymbol(p.message())
        case (4, 2) => decls = scope(p.message())
        case (_, w) => p.skip(w)
    Sig(parents.result().filter(_.nonEmpty), decls, "")

  /** a Scope's members: symlinks by name, hardlinks as full infos */
  private def scope(p: Proto): Vector[String] =
    val out = Vector.newBuilder[String]
    while p.hasMore do
      p.field() match
        case (1, 2) => out += p.string()
        case (2, 2) => out += info(p.message()).symbol
        case (_, w) => p.skip(w)
    out.result()

  /** the head symbol of the Type in field `number` of this message */
  private def typeAt(p: Proto, number: Int): String =
    var out = ""
    while p.hasMore do
      p.field() match
        case (n, 2) if n == number => out = headSymbol(p.message())
        case (_, w) => p.skip(w)
    out

  /**
   * The symbol a Type is "of": a TypeRef's (`Ordering` in
   * `Ordering[Int]`), a SingleType's, or through an annotation, a
   * by-name or a repeated type. Bounded: every step but the last moves
   * into a strictly smaller window, and at most `MaxWrap` wrappers are
   * unwrapped.
   */
  private val MaxWrap = 8
  private def headSymbol(start: Proto): String =
    var p = start
    var out = ""
    var steps = 0
    var go = true
    while go && steps < MaxWrap do
      steps += 1
      go = false
      var next: Proto | Null = null
      while p.hasMore do
        p.field() match
          case (2, 2) | (20, 2) => out = symbolField(p.message(), 2)
          case (8, 2) | (13, 2) | (14, 2) => next = p.message()
          case (_, w) => p.skip(w)
      next match
        case n: Proto => p = innerType(n); go = true
        case null => ()
    out

  /** AnnotatedType keeps its type in field 3, ByName/Repeated in field 1 */
  private def innerType(p: Proto): Proto =
    var out = Proto(Array.emptyByteArray)
    while p.hasMore do
      p.field() match
        case (1, 2) | (3, 2) => out = p.message()
        case (_, w) => p.skip(w)
    out

  private def symbolField(p: Proto, number: Int): String =
    var out = ""
    while p.hasMore do
      p.field() match
        case (n, 2) if n == number => out = p.string()
        case (_, w) => p.skip(w)
    out

  private def synthetic(p: Proto): Synthetic =
    var r = Range(0, 0, 0, 0)
    var syms = Vector.empty[String]
    while p.hasMore do
      p.field() match
        case (1, 2) => r = range(p.message())
        case (2, 2) => syms = treeSymbols(p.message())
        case (_, w) => p.skip(w)
    Synthetic(r, syms)

  /** what a piece of bytes is, on the tree walk's worklist */
  private enum Node:
    case Tree, Apply, Function, Id, MacroExpansion, Select, TypeApply

  /**
   * Every IdTree symbol under a Synthetic's tree. An explicit worklist,
   * not recursion: a synthetic tree nests as deep as the code it stands
   * for (operator rule, no unbounded stack recursion).
   */
  private def treeSymbols(root: Proto): Vector[String] =
    val out = Vector.newBuilder[String]
    var todo: List[(Node, Proto)] = List(Node.Tree -> root)
    while todo.nonEmpty do
      val (node, p) = todo.head
      todo = todo.tail
      while p.hasMore do
        val (n, w) = p.field()
        (node, n, w) match
          case (Node.Tree, 1, 2) => todo ::= Node.Apply -> p.message()
          case (Node.Tree, 2, 2) => todo ::= Node.Function -> p.message()
          case (Node.Tree, 3, 2) => todo ::= Node.Id -> p.message()
          case (Node.Tree, 5, 2) => todo ::= Node.MacroExpansion -> p.message()
          case (Node.Tree, 7, 2) => todo ::= Node.Select -> p.message()
          case (Node.Tree, 8, 2) => todo ::= Node.TypeApply -> p.message()
          case (Node.Apply, 1 | 2, 2) => todo ::= Node.Tree -> p.message()
          case (Node.Function, 1, 2) => todo ::= Node.Id -> p.message()
          case (Node.Function, 2, 2) => todo ::= Node.Tree -> p.message()
          case (Node.Id, 1, 2) => out += p.string()
          case (Node.MacroExpansion, 1, 2) => todo ::= Node.Tree -> p.message()
          case (Node.Select, 1, 2) => todo ::= Node.Tree -> p.message()
          case (Node.Select, 2, 2) => todo ::= Node.Id -> p.message()
          case (Node.TypeApply, 1, 2) => todo ::= Node.Tree -> p.message()
          case (_, _, w) => p.skip(w)
    out.result().filter(_.nonEmpty)
