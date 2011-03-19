package shrink

import akka.actor.Actor._
import akka.actor.Actor
import akka.dispatch.{Dispatchers, MessageDispatcher}
import akka.actor.SupervisorFactory
import akka.config.Supervision._

import shrink.processors.ShrinkProcessor
import shrink.processors.pipelined._

case class Flood(times:Int)

/**
 * Floods the agent with messages
 */
class FloodClient extends Actor {
  self.dispatcher = FloodClient.dispatcher  

  // create a client and set the channel to send on
  var client = new ShrinkClient("localhost", 2552, "example-channel")

  def receive = {
    case Flood(times) => {
      for (i <- 1 to times) {
	client.send(new Message(new Host("osaka"), "ticks=" + i))
      }
    }

    case ignore => log.error("[FloodClient] Ignored: " + ignore)
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
}

class ExampleProcessor extends Actor {
  // tune into a channel
  FloodRedisWatcher() ! "example-channel"

  def receive = {
    case msg:String => {
      log.info("[ExampleProcessor] Got a new message: " + msg)
    }
  }

}

/**
 * Watches redis for messages and tells processors
 */
object FloodRedisWatcher {
  val watcher = actorOf(new RedisShrinkWatcher()).start

  def apply() = watcher
}

/**
 * Create a pipelined processor that will:
 * - write to standard out
 */
class ExamplePipelinedProcessor extends 
ShrinkProcessor with       // define a shrink processor (actor)
PipelinedProcessing with  // that will behave in a pipelined fashion 
StdoutWriter with            // and will write all messges to stdout
FifoWriter                       // and will write all messages to a fifo
{
  // tune into a channel
  FloodRedisWatcher() ! "example-channel"
}

/**
 * Quick example.
 */
object FloodExample {
  def main(args: Array[String]): Unit = {

    // create a processor to deal with messages
    val proc = actorOf[ExampleProcessor].start

    // create a pipelined processor to deal with messages
    val pipedProc = actorOf[ExamplePipelinedProcessor].start

    for (i <- 1 to 2) {
      println("Flooding " + "(" + i + ")")
      val client = FloodClient()
      client ! Flood(1)
    }
  }
}

