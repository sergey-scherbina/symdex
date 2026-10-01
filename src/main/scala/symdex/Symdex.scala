package symdex

/**
 * The entry point. v0 is a specification (specs/symdex.md); this file is
 * the build's proof that okay and tasty-query resolve together.
 */
object Symdex:
  val version: String = "0.0.0"

  def main(args: Array[String]): Unit =
    println(s"symdex $version — see specs/symdex.md")
