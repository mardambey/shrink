import sbt._

class ShrinkProject(info: ProjectInfo) extends DefaultProject(info) with AkkaBaseProject with sbt_akka_bivy.AkkaKernelDeployment 
{
  val scalaToolsSnapshots = "Scala-Tools Maven2 Snapshots Repository" at "http://scala-tools.org/repo-snapshots"
  val scalaToolsReleases  = "Scala-Tools Maven2 Releases Repository" at "http://scala-tools.org/repo-releases"

  val akkaRepo   = "Akka Repository" at "http://akka.io/repository"
  val akkaActor  = "se.scalablesolutions.akka" % "akka-actor"  % "1.0"
  val akkaKernel = "se.scalablesolutions.akka" % "akka-kernel"  % "1.0"
  lazy val redis = "net.debasishg" % "redisclient_2.8.1" % "2.3" % "compile"

  override def packageSrcJar = defaultJarPath("-sources.jar")
  lazy val sourceArtifact    = Artifact.sources(artifactID)
  override def managedStyle  = ManagedStyle.Maven

	override def akkaKernelBootClass = "akka.kernel.Main"


  override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageSrc)
  //Credentials(Path.userHome / ".ivy2" / ".credentials", log)
  //lazy val publishTo = "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"
  //lazy val publishTo = Resolver.file("Local Test Repository", Path fileProperty "java.io.tmpdir" asFile)
}
