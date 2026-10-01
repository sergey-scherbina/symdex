/*
 * loupe — structural code intelligence for agents, built on okay.
 *
 * okay rides as a git SUBMODULE (`okay/`) and its modules are referenced
 * as ProjectRefs, the arrangement okay-chat uses: one checkout, no
 * published artifact, and a change loupe needs in okay is made in okay
 * (its own repository, its own gate) and the submodule moved.
 */
ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "io.github.sergey-scherbina"
ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / scalacOptions ++= Seq("-Wunused:all", "-Werror")

lazy val okayMcp = ProjectRef(file("okay"), "okayMcpJVM")
lazy val okayCodec = ProjectRef(file("okay"), "okayCodecJVM")

lazy val loupe = (project in file("."))
  .dependsOn(okayMcp, okayCodec)
  .settings(
    name := "loupe",
    libraryDependencies ++= Seq(
      // TASTy read without a compiler: the Scala layer's source of truth
      "ch.epfl.scala" %% "tasty-query" % "1.6.1",
      "org.scalameta" %% "munit" % "1.1.1" % Test,
    ),
  )
