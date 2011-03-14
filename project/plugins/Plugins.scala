import sbt._
 
class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
    val akkaRepo = "Akka Repository" at "http://akka.io/repository"
    val akkaPlugin = "se.scalablesolutions.akka" % "akka-sbt-plugin" % "1.0"
    val bumRepo = "Bum Networks Release Repository" at "http://repo.bumnetworks.com/releases"
    val sbtAkkaBivy = "net.evilmonkeylabs" % "sbt-akka-bivy" % "0.2.0"
}

// vim: set ts=4 sw=4 et:
