package symdex

/**
 * SCIP (sourcegraph/scip, scip.proto): the index format other languages'
 * indexers write — scip-java, scip-typescript, scip-python,
 * rust-analyzer, scip-clang. Read into the same model as SemanticDB, so
 * every tool answers over it unchanged:
 *
 *  - a SCIP symbol is `scheme manager package version descriptors`; the
 *    descriptors use SemanticDB's syntax (`shop/Shape#area().`), and they
 *    are kept as the symbol, so `Symbols.names` reads them as it reads
 *    Scala's. A local is `local N`, kept as `localN`.
 *  - kinds map onto SemanticDB's; when an indexer leaves the kind out,
 *    the descriptor's suffix says it (`#` a type, `().` a method).
 *  - an `is_implementation` relationship is a parent (of a type) or an
 *    overridden member (of a method).
 *  - an occurrence's ENCLOSING range is its definition's body: SCIP has
 *    the spans SemanticDB lacks, so a SCIP index needs no TASTy.
 */
object Scip:

  final case class Read(documents: Vector[Document], spans: Map[(String, Int, Int), Span])

  def read(bytes: Array[Byte]): Read =
    val p = Proto(bytes)
    val docs = Vector.newBuilder[Document]
    val spans = Map.newBuilder[(String, Int, Int), Span]
    while p.hasMore do
      p.field() match
        case (2, 2) =>
          val (d, s) = document(p.message())
          docs += d
          spans ++= s
        case (_, w) => p.skip(w)
    Read(docs.result(), spans.result())

  /** the descriptor part of a SCIP symbol: after four space-separated fields (a space in a field is doubled) */
  def descriptor(symbol: String): String =
    if symbol.startsWith("local ") then "local" + symbol.drop(6)
    else
      var fields = 0
      var i = 0
      while fields < 4 && i < symbol.length do
        if symbol(i) == ' ' then
          if i + 1 < symbol.length && symbol(i + 1) == ' ' then i += 2
          else { fields += 1; i += 1 }
        else i += 1
      if fields < 4 then symbol else symbol.substring(i)

  private def document(p: Proto): (Document, Vector[((String, Int, Int), Span)]) =
    var path = ""
    val occs = Vector.newBuilder[(Occurrence, Option[Range])]
    val infos = Vector.newBuilder[Info]
    while p.hasMore do
      p.field() match
        case (1, 2) => path = p.string()
        case (2, 2) => occs += occurrence(p.message())
        case (3, 2) => infos += info(p.message())
        case (_, w) => p.skip(w)
    val all = occs.result()
    val spans = all.collect { case (o, Some(body)) if o.definition =>
      (path, o.range.startLine, o.range.startChar) -> Span(body.startLine, body.endLine, extension = false)
    }
    // a member's declarations: SCIP names each symbol's owner (`enclosing_symbol`)
    // only sometimes, so a class's members are read off the descriptors instead
    val is = infos.result()
    val byOwner = is.groupBy(i => ownerOf(i.symbol))
    val withDecls = is.map(i =>
      if Set(Info.Class, Info.Trait, Info.Interface, Info.Object)(i.kind)
      then i.copy(declarations = byOwner.getOrElse(i.symbol, Vector.empty).map(_.symbol))
      else i)
    (Document(path, withDecls, all.map(_._1), Vector.empty), spans)

  /** `shop/Shape#area().` is owned by `shop/Shape#` */
  private def ownerOf(s: String): String =
    val names = Symbols.names(s)
    if names.size <= 1 then "" else
      // drop the last descriptor: find where it starts by re-reading
      val last = names.last
      val i = s.lastIndexOf(last, s.length - 1)
      if i <= 0 then "" else s.substring(0, if s.charAt(i - 1) == '`' then i - 1 else i)

  private def occurrence(p: Proto): (Occurrence, Option[Range]) =
    var range = Range(0, 0, 0, 0)
    var enclosing: Option[Range] = None
    var symbol = ""
    var roles = 0
    while p.hasMore do
      p.field() match
        case (1, 2) => range = packed(p.message())
        case (8, 2) => range = single(p.message())
        case (9, 2) => range = multi(p.message())
        case (2, 2) => symbol = descriptor(p.string())
        case (3, 0) => roles = p.int()
        case (7, 2) => enclosing = Some(packed(p.message()))
        case (10, 2) => enclosing = Some(single(p.message()))
        case (11, 2) => enclosing = Some(multi(p.message()))
        case (_, w) => p.skip(w)
    (Occurrence(range, symbol, (roles & 1) != 0), enclosing)

  /** the deprecated packed form: `[line, start, end]` or `[startLine, start, endLine, end]` */
  private def packed(p: Proto): Range =
    val xs = Vector.newBuilder[Int]
    while p.hasMore do xs += p.int()
    xs.result() match
      case Vector(l, s, e) => Range(l, s, l, e)
      case Vector(sl, sc, el, ec) => Range(sl, sc, el, ec)
      case _ => Range(0, 0, 0, 0)

  private def single(p: Proto): Range =
    var l, s, e = 0
    while p.hasMore do
      p.field() match
        case (1, 0) => l = p.int()
        case (2, 0) => s = p.int()
        case (3, 0) => e = p.int()
        case (_, w) => p.skip(w)
    Range(l, s, l, e)

  private def multi(p: Proto): Range =
    var sl, sc, el, ec = 0
    while p.hasMore do
      p.field() match
        case (1, 0) => sl = p.int()
        case (2, 0) => sc = p.int()
        case (3, 0) => el = p.int()
        case (4, 0) => ec = p.int()
        case (_, w) => p.skip(w)
    Range(sl, sc, el, ec)

  private def info(p: Proto): Info =
    var symbol = ""
    var kind = 0
    var name = ""
    val implemented = Vector.newBuilder[String]
    while p.hasMore do
      p.field() match
        case (1, 2) => symbol = descriptor(p.string())
        case (4, 2) => relationship(p.message()).foreach(implemented += _)
        case (5, 0) => kind = p.int()
        case (6, 2) => name = p.string()
        case (_, w) => p.skip(w)
    val k = kindOf(kind, symbol)
    val impl = implemented.result()
    val isType = Set(Info.Class, Info.Trait, Info.Interface, Info.Object, Info.Type)(k)
    Info(symbol, k, if Set(66)(kind) then Info.Abstract else 0,
      if name.nonEmpty then name else Symbols.name(symbol),
      parents = if isType then impl else Vector.empty,
      declarations = Vector.empty, result = "",
      overridden = if isType then Vector.empty else impl)

  /** the symbol an `is_implementation` relationship names, if it is one */
  private def relationship(p: Proto): Option[String] =
    var symbol = ""
    var impl = false
    while p.hasMore do
      p.field() match
        case (1, 2) => symbol = descriptor(p.string())
        case (3, 0) => impl = p.int() != 0
        case (_, w) => p.skip(w)
    if impl && symbol.nonEmpty then Some(symbol) else None

  private def kindOf(scip: Int, symbol: String): Int = scip match
    case 7 | 11 | 49 => Info.Class
    case 21 => Info.Interface
    case 53 => Info.Trait
    case 33 => Info.Object
    case 26 | 66 | 80 | 17 => Info.Method
    case 9 => Info.Constructor
    case 15 | 41 | 61 | 8 => Info.Field
    case 54 | 55 => Info.Type
    case 35 | 30 | 29 => Info.Package
    case _ =>
      if symbol.startsWith("local") then Info.Local
      else if symbol.endsWith("#") then Info.Class
      else if symbol.endsWith(").") then Info.Method
      else if symbol.endsWith("/") then Info.Package
      else if symbol.endsWith(".") then Info.Field
      else 0
