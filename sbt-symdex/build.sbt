// sbt-symdex: one line in a project's plugins.sbt makes it symdex-ready.
// An sbt plugin builds on sbt's own Scala (2.12), so it is its own build.
sbtPlugin := true
name := "sbt-symdex"
organization := "io.github.sergey-scherbina"
version := "0.5.0"
licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
scalacOptions ++= Seq("-deprecation", "-feature", "-Xfatal-warnings")

// Published as an Ivy repository on GitHub Pages (the symdex repo's
// gh-pages branch), so a build needs only a resolver line — no Sonatype:
//   resolvers += Resolver.url("symdex", url("https://sergey-scherbina.github.io/symdex"))(Resolver.ivyStylePatterns)
// `sbt publish` writes into SYMDEX_PAGES (a checkout of gh-pages).
publishMavenStyle := false
publishTo := Some(Resolver.file("symdex-pages",
  file(sys.env.getOrElse("SYMDEX_PAGES", "../target/pages")))(Resolver.ivyStylePatterns))
