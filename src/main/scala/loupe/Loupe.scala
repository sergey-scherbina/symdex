package loupe

/**
 * The entry point. v0 is a specification (specs/loupe.md); this file is
 * the build's proof that okay and tasty-query resolve together.
 */
object Loupe:
  val version: String = "0.0.0"

  def main(args: Array[String]): Unit =
    println(s"loupe $version — see specs/loupe.md")
