name := "Gamebone POC"

scalaVersion in ThisBuild := "2.11.8"

lazy val root = project.in(file(".")).
  aggregate(gbJS, gbJVM).
  settings(
    publish := {},
    publishLocal := {}
  )

lazy val gb = crossProject.in(file(".")).
  settings(
    name := "gb",
    version := "0.1-SNAPSHOT"
  ).
  jvmSettings(
    resolvers += "Sonatype OSS Releases" at
      "https://oss.sonatype.org/content/repositories/releases/",
  	libraryDependencies ++= Seq(
		"com.typesafe.akka" %% "akka-actor" % "2.4.7",
		"com.typesafe.akka" %% "akka-stream" % "2.4.7",
		"com.typesafe.akka" %% "akka-http-experimental" % "2.4.7",
      "com.livestream" %% "scredis" % "2.0.6"
		)
	  ).
  jsSettings(
    // Add JS-specific settings here
  )

lazy val gbJVM = gb.jvm
lazy val gbJS = gb.js
