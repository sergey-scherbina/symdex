/*
 * symdex — structural code intelligence for agents, built on okay.
 *
 * okay rides as a git SUBMODULE (`okay/`) and its modules are referenced
 * as ProjectRefs, the arrangement okay-chat uses: one checkout, no
 * published artifact, and a change symdex needs in okay is made in okay
 * (its own repository, its own gate) and the submodule moved.
 */
ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "io.github.sergey-scherbina"
ThisBuild / licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / scalacOptions ++= Seq("-Wunused:all", "-Werror")

lazy val okayMcp = ProjectRef(file("okay"), "okayMcpJVM")
lazy val okayCodec = ProjectRef(file("okay"), "okayCodecJVM")

lazy val symdex = (project in file("."))
  .dependsOn(okayMcp, okayCodec)
  .settings(
    name := "symdex",
    // symdex's own sources are indexed too: the test fixtures under
    // src/test/scala/fixture are read back from the SemanticDB the
    // compiler writes beside the classes
    semanticdbEnabled := true,
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.1.1" % Test,
    ),
  )
