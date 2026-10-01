package symdex

/**
 * SemanticDB symbol syntax (scalameta's semanticdb spec, "Symbol"):
 * a global symbol is a sequence of descriptors, `okay/stream/Bulk#joinSorted().`
 * — package `name/`, term `name.`, type `name#`, method
 * `name(disambiguator).`, parameter `(name)`, type parameter `[name]`;
 * a name may be backquoted. Locals are `localN` and mean nothing
 * outside their document.
 */
object Symbols:

  def isLocal(s: String): Boolean = s.startsWith("local")

  /** the descriptor names, outermost first: `okay/Bulk#join().` is `okay, Bulk, join` */
  def names(s: String): Vector[String] =
    val out = Vector.newBuilder[String]
    var i = 0
    val n = s.length
    def upTo(close: Char, from: Int): Int =
      var j = from
      while j < n && s(j) != close do j += 1
      j
    while i < n do
      s(i) match
        case '(' =>
          val j = upTo(')', i + 1)
          out += s.substring(i + 1, j)
          i = j + 1
        case '[' =>
          val j = upTo(']', i + 1)
          out += s.substring(i + 1, j)
          i = j + 1
        case _ =>
          var name = ""
          if s(i) == '`' then
            val j = upTo('`', i + 1)
            name = s.substring(i + 1, j)
            i = j + 1
          else
            var j = i
            while j < n && "/.#(".indexOf(s(j)) < 0 do j += 1
            name = s.substring(i, j)
            i = j
          if i < n && s(i) == '(' then
            // a method's disambiguator, then its '.'
            i = upTo(')', i + 1) + 2
          else i += 1
          out += name
    out.result()

  /** the plain name: the last descriptor's */
  def name(s: String): String = names(s).lastOption.getOrElse(s)

  /** readable form: `okay.stream.Bulk#joinSorted` stays close to the symbol but drops `().` */
  def show(s: String): String =
    if isLocal(s) then s
    else s.replace("().", "").replace("/", ".").stripSuffix(".")
