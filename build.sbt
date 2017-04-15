import sbt.Keys._

// Multi project build file.  For val xxx = project, xxx is the name of the project and base dir
// logging docs: http://doc.akka.io/docs/akka/2.4.16/scala/logging.html
lazy val commonSettings = Seq(
  organization := "org.sackfix",
  version := "0.1.0",
  scalaVersion := "2.11.7",
  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3" % "runtime", // without %runtime did not work in intellij
  libraryDependencies += "org.sackfix" %% "sackfix-common" % "0.1.0" exclude("org.apache.logging.log4j", "log4j-api") exclude("org.apache.logging.log4j", "log4j-core"),
  libraryDependencies += "org.sackfix" %% "sf-session-commmon" % "0.1.1" exclude("org.apache.logging.log4j", "log4j-api") exclude("org.apache.logging.log4j", "log4j-core"),
  libraryDependencies += "org.sackfix" %% "sackfix-messages-fix44" % "0.1.0" exclude("org.apache.logging.log4j", "log4j-api") exclude("org.apache.logging.log4j", "log4j-core"),
  libraryDependencies += "com.typesafe" % "config" % "1.3.0",
  libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4.16",
  libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.4.16" % "test",
  libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.4.16",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  libraryDependencies += "org.mockito" % "mockito-all" % "1.10.19" % "test"
)


lazy val sfexampleacceptor = (project in file("./sf-example-acceptor")).
  settings(commonSettings: _*).
  settings(
    name := "sf-example-acceptor"
  )

lazy val sfexampleinitiator = (project in file("./sf-example-initiator")).
  settings(commonSettings: _*).
  settings(
    name := "sf-example-initiator"
  )

lazy val sackfixexamples = (project in file(".")).aggregate(sfexampleacceptor, sfexampleinitiator)
