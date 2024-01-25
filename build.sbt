import play.sbt.PlayImport.caffeine

name := """Work Accident Database"""

organization := "il.org.kavlaoved"

maintainer := "michael@codeworth.io"

version := "1.4-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.12"

// Targeting JDK11, which is the current LTS
javacOptions ++= Seq("-source", "11", "-target", "11")

libraryDependencies ++= Seq(
  caffeine,
  ws,
  guice,
  "com.google.inject"            % "guice"                % "6.0.0", /* Needed for JDK17 */
//  "com.google.inject.extensions" % "guice-assistedinject" % "5.1.0", /* Needed for JDK17 */
  "org.playframework" %% "play-slick" % "6.0.0",
  "org.playframework" %% "play-slick-evolutions" % "6.0.0",
  "org.playframework" %% "play-mailer" % "10.0.0",
  "org.playframework" %% "play-mailer-guice" % "10.0.0",
  "org.playframework" %% "play-pekko-http-server" % "3.0.0",
  "org.playframework" %% "play-pekko-http2-support" % "3.0.0",
//  "io.methvin" % "directory-watcher" % "0.18.0",
  "be.objectify" %% "deadbolt-scala" % "2.9.0",
  "org.mindrot" % "jbcrypt" % "0.4",
  "org.postgresql" % "postgresql" % "42.7.0",
  "com.github.jferard"% "fastods"%"0.8.1",
  "org.webjars" % "jquery" % "3.2.1",
  "org.webjars" % "jquery-ui" % "1.12.1",
  "org.webjars.bower" % "tether" % "1.4.7",
  "org.webjars" % "sweetalert" % "2.1.0",
  "org.webjars.npm" % "bootstrap" % "5.3.2",
  "org.webjars.bower" % "fontawesome" % "4.7.0",
  "org.webjars" % "d3js" % "5.16.0",
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,
  "org.seleniumhq.selenium" % "selenium-java" % "2.35.0" % Test,
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

