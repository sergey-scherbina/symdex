// sbt-symdex: one line in a project's plugins.sbt makes it symdex-ready.
// An sbt plugin builds on sbt's own Scala (2.12), so it is its own build.
sbtPlugin := true
name := "sbt-symdex"
organization := "io.github.sergey-scherbina"
version := "0.5.0"
licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
scalacOptions ++= Seq("-deprecation", "-feature", "-Xfatal-warnings")
