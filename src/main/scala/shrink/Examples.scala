package shrink

import akka.actor.Actor._
import akka.actor.Actor
import akka.dispatch.{Dispatchers, MessageDispatcher}
import akka.actor.SupervisorFactory
import akka.config.Supervision._

case class Flood(times:Int)

/**
 * Floods the agent with messages
 */
class FloodClient extends Actor {
  self.dispatcher = FloodClient.dispatcher  
  var client = new ShrinkClient("localhost", 2552)

  def receive = {
    case Flood(times) => {
      for (i <- 1 to times) {
	client.send(new Message(new Host("osaka"), "ticks=" + i))
      }
    }
    case _ =>
  }
}

/**
 * Companion object that also creates a dispatcher and supervisor.
 */
object FloodClient {

  // Count of actors that will balance the load
  val ACTORS_COUNT = 1

  /*
   * Initialization of the smart work stealing dispatcher that polls messages from
   * the mailbox of a busy actor and finds other actor in the pool that can process
   * the message.
   */
  val workStealingDispatcher = Dispatchers.newExecutorBasedEventDrivenWorkStealingDispatcher("pooled-dispatcher")
  var dispatcher = workStealingDispatcher
  .withNewThreadPoolWithLinkedBlockingQueueWithUnboundedCapacity
  .setCorePoolSize(ACTORS_COUNT)
  .build

  /*
   * Creates list of actors the will be supervized
   */
  def createListOfSupervizedActors(poolSize: Int): List[Supervise] = {
    (1 to poolSize toList).foldRight(List[Supervise]()) {
      (i, list) => Supervise(Actor.actorOf( { new FloodClient() } ).start, Permanent) :: list
    }
  }

  val supervisor = SupervisorFactory(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 1000),
      createListOfSupervizedActors(ACTORS_COUNT))).newInstance

  // Starts supervisor and all supervised actors
  supervisor.start

  def apply() = Actor.registry.actorsFor[FloodClient](classOf[FloodClient]).head

  // supervisor.stop
  // supervisor.shutdown

}

/**
 * Quick example.
 */
object FloodExample {
  def main(args: Array[String]): Unit = {

    for (i <- 1 to 500) {
      println("Flooding " + "(" + i + ")")
      FloodClient() ! Flood(1000)
    }
  }
}
