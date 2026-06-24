import play.sbt.PlayImport.caffeine

name := """Work Accident Database"""

organization := "il.org.kavlaoved"

maintainer := "michael@codeworth.io"

version := "1.5-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "3.7.3"

// Targeting JDK11, which is the current LTS
javacOptions ++= Seq("-source", "21", "-target", "21")

libraryDependencies ++= Seq(
  caffeine,
  ws,
  guice,
  "org.playframework" %% "play-slick" % "6.2.0",
  "org.playframework" %% "play-slick-evolutions" % "6.2.0",
  "org.playframework" %% "play-mailer" % "10.1.0",
  "org.playframework" %% "play-mailer-guice" % "10.1.0",
  "org.playframework" %% "play-pekko-http-server" % "3.0.11",
  "org.playframework" %% "play-pekko-http2-support" % "3.0.11",
//  "io.methvin" % "directory-watcher" % "0.18.0",
  "be.objectify" %% "deadbolt-scala" % "3.0.0",
  "org.mindrot" % "jbcrypt" % "0.4",
  "org.postgresql" % "postgresql" % "42.7.11",
  "com.github.jferard"% "fastods"%"0.8.1",
  "com.opencsv" % "opencsv" % "5.12.0",
  "org.webjars" % "jquery" % "3.2.1",
  "org.webjars" % "jquery-ui" % "1.12.1",
  "org.webjars.bower" % "tether" % "1.4.7",
  "org.webjars" % "sweetalert" % "2.1.0",
  "org.webjars.npm" % "bootstrap" % "5.3.8",
  "org.webjars.bower" % "fontawesome" % "4.7.0",
  "org.webjars" % "d3js" % "5.16.0",
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test,
  "org.seleniumhq.selenium" % "selenium-java" % "4.21.0" % Test,
//  "org.scalamock" %% "scalamock" % "4.0.0" % Test,
)


import org.irundaia.sbt.sass._

SassKeys.cssStyle := Minified

SassKeys.generateSourceMaps := true

// Needed for M1/Apple Silicon
PlayKeys.fileWatchService := play.dev.filewatch.FileWatchService.jdk7(play.sbt.run.toLoggerProxy(sLog.value))

// TODO add sections and table helpers
// TwirlKeys.templateImports ++= Seq( "views.Sections", "views.TableHelper")
TwirlKeys.templateImports ++= Seq("views.Helpers")

pipelineStages := Seq(digest, gzip)

// Disable documentation creation
Compile / doc / sources  := Seq.empty
Compile / packageDoc / publishArtifact := false

