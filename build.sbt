name := "parser"

organization in Global := "io.verizon.ermine"

crossScalaVersions in Global := Seq("2.12.4", "2.11.12", "2.10.7")

scalaVersion in Global := crossScalaVersions.value.head

description := "A Trifecta inspired parser in Scala."

licenses += ("BSD Simplified", url("https://github.com/verizon/ermine-parser/blob/master/LICENSE"))

homepage := Some(url("https://github.com/verizon/ermine-parser"))

scalacOptions ++=
  Seq("-encoding", "UTF-8", "-Yrecursion", "50", "-deprecation",
      "-unchecked", "-Xlint", "-feature",
      "-language:implicitConversions", "-language:higherKinds",
      "-language:existentials", "-language:postfixOps")

resolvers += Resolver.bintrayRepo("non", "maven") // for kind-projector

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.5")

javacOptions += "-Xlint"

val catsVersion = "1.0.1"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "cats-free" % catsVersion
)

initialCommands in console := "import cats._; import cats.implicits._; import scalaparsers._"
