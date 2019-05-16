lazy val baseName  = "Span"
lazy val baseNameL = baseName.toLowerCase

lazy val projectVersion = "1.4.2"
lazy val mimaVersion    = "1.4.0"

lazy val deps = new {
  val main = new {
    val serial    = "1.1.1"
  }
  val test = new {
    val scalaTest = "3.0.8-RC2"
  }
}

lazy val root = project.withId(baseNameL).in(file("."))
  .settings(
    name               := baseName,
    version            := projectVersion,
    organization       := "de.sciss",
    scalaVersion       := "2.12.6",
    crossScalaVersions := Seq("2.12.6", "2.11.12", "2.13.0-RC2"),
    description        := "A simple data type for describing sample frame intervals",
    homepage           := Some(url(s"https://git.iem.at/sciss/${name.value}")),
    licenses           := Seq("LGPL v2.1+" -> url( "http://www.gnu.org/licenses/lgpl-2.1.txt")),
    mimaPreviousArtifacts := Set("de.sciss" %% baseNameL % mimaVersion),
    initialCommands in console := """import de.sciss.span._""",
    scalacOptions      := Seq("-deprecation", "-unchecked", "-feature", "-Xfuture", "-encoding", "utf8", "-Xlint"),
    libraryDependencies ++= Seq(
      "de.sciss" %% "serial" % deps.main.serial
    ),
    libraryDependencies += {
      if (scalaVersion.value == "2.13.0-RC2") {
        "org.scalatest" % "scalatest_2.13.0-RC1" % deps.test.scalaTest % Test
      } else {
        "org.scalatest" %% "scalatest" % deps.test.scalaTest % Test
      }
    }
  )
  .settings(publishSettings)

// ---- publishing ----
lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := { val n = name.value
<scm>
  <url>git@git.iem.at:sciss/{n}.git</url>
  <connection>scm:git:git@git.iem.at:sciss/{n}.git</connection>
</scm>
<developers>
   <developer>
      <id>sciss</id>
      <name>Hanns Holger Rutz</name>
      <url>http://www.sciss.de</url>
   </developer>
</developers>
  }
)

