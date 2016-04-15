name := "ermine-parser"

organization in Global := "oncue.ermine"

crossScalaVersions := Seq("2.11.8", "2.10.5")

scalaVersion := crossScalaVersions.value.head

description := "A Trifecta inspired parser in Scala."

licenses += ("BSD Simplified", url("https://github.com/oncue/ermine-parser/blob/master/LICENSE"))

homepage := Some(url("https://github.com/oncue/ermine-parser"))

bintraySettings

bintray.Keys.bintrayOrganization in bintray.Keys.bintray := Some("oncue")

bintray.Keys.repository in bintray.Keys.bintray := "releases"

bintray.Keys.packageLabels in bintray.Keys.bintray := Seq("scala", "functional programming", "parser", "reasonable")

publishMavenStyle := true

scalacOptions ++=
  Seq("-encoding", "UTF-8", "-Yrecursion", "50", "-deprecation",
      "-unchecked", "-Xlint", "-feature",
      "-language:implicitConversions", "-language:higherKinds",
      "-language:existentials", "-language:postfixOps")

resolvers += Resolver.bintrayRepo("non", "maven") // for kind-projector

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.5.2")

parallelExecution := true

javacOptions += "-Xlint"

val scalazVersion = "7.2.2"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % scalazVersion,
  "org.scalaz" %% "scalaz-scalacheck-binding" % scalazVersion % "test"
)

initialCommands in console := "import scalaz._, Scalaz._; import scalaparsers._"
