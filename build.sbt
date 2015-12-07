name := "ShapelyBackend"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
resolvers += "Underscore Bintray" at "https://dl.bintray.com/underscoreio/libraries"

lazy val shapelyBackend = project.in(file(".")).enablePlugins(PlayScala, SbtWeb)

libraryDependencies ++= Seq(
  "org.spire-math" %% "spire" % "0.11.0",
  "com.typesafe.play" %% "play-slick" % "1.1.1",
  "com.typesafe.play" %% "play-slick-evolutions" % "1.1.1",
  "org.scalaz" %% "scalaz-core" % "7.2.0",
  "org.scalaz" %% "scalaz-concurrent" % "7.2.0",
  "org.webjars" % "bootstrap" % "2.3.1",
  "org.webjars" %% "webjars-play" % "2.4.0-2",
  "com.chuusai" %% "shapeless" % "2.2.5",
  "io.underscore" %% "slickless" % "0.1.1",
  "org.postgresql" % "postgresql" % "9.4-1206-jdbc42"
)

updateOptions := updateOptions.value.withCachedResolution(true)

cancelable in Global := true
