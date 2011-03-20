package shrink

import scala.collection.mutable.{ListBuffer => List}

import com.redis.{RedisClient, PubSubMessage, S, U, M}
import akka.persistence.redis._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Actor._
import java.io.{ByteArrayInputStream,ByteArrayOutputStream,ObjectOutputStream,ObjectInputStream}
import java.lang.reflect.Method
import java.io.{OutputStreamWriter,BufferedWriter}

/**
 * A host in the system, usually a client.
 */
case class Host(address:String)

/**
 * A message sent from a client to be broadcasted.
 */
case class Message(from:Host, text:String)

/**
 * Abstraction of Shrink relay layer for relaying messages
 */
trait ShrinkRelay extends Actor

/**
 * This is a simple Shrink client that gets a shrink
 * service actor (an agent) and sends messages to it.
 */
class ShrinkClient(host:String, port:Int, var channel:String = "shrink") {
  val service = remote.actorFor("shrink-service", host, port)

  def send(msg:Message, ch:String = channel) {
    log.debug("[ShrinkClient] Sending message to shrink-service: " + msg)
    service ! List(ch, msg)
  }
}

/**
 * The agent sits on a client machine and accepts messages from
 * local clients so they can be sent to the relay server.
 */
trait ShrinkAgent extends Actor {

  val relay: ActorRef

  override def preStart {
    log.info("[ShrinkAgent] Shrink agent is starting up...")
  }

  def receive = {
    case m @ List(channel:String, msg:Message) => {
      log.info("[ShrinkAgent] Got message (relaying): " + msg.from.address + " => " + msg.text + " : " + msg)
      relay ! m
    }

    case ignore => log.error("[ShrinkAgent] Error relaying messge: " + ignore)
  }
}

/**
 * This implements a shrink relay that uses Redis to
 * pass messages around.
 */
class RedisShrinkRelay(val host:String = "localhost", val port:Int = 6389)  extends ShrinkRelay {
  // redis
  var r = new RedisClient()
  // publisher
  val p = actorOf(new Publisher(r))
  // start publisher
  p.start

  def receive = {
    case List(channel:String, msg:Message) => {
      log.info("[RedisShrinkRelay] Relaying message from: " + msg.from.address + " \"" + msg.text + "\"")      
      p ! Publish(channel, msg.text)
    }

    case ignore => log.error("[RedisShrinkRelay] Error sending unknown message: " + ignore + " from " + self)
  }
} 

/**
 * Factory that creates and starts the Redis
 * shrink relay, mixed into shrink agents that
 * wish to use Redis.
 */
trait RedisShrinkRelayFactory { this: Actor => val relay:ActorRef = actorOf(new RedisShrinkRelay()).start }

/**
 * The watcher will watch over its list of channels
 * and will alert its processors when new data
 * arrives on a channel.
 */
trait ShrinkWatcher extends Actor {
   // Subscribe a given processor to channels
  def sub(proc:ActorRef, channels:String*)

  // Unsubscribe a given processor from channels
  def unsub(proc:ActorRef, channels:String*)
}

class RedisShrinkWatcher(val host:String = "localhost", val port:Int = 6379) extends ShrinkWatcher {
  
  val r = new RedisClient(host, port)
  val s = actorOf(new Subscriber(r)).start
  s ! Register(callback)

  // subscribers, channel mapped to set of actors
  var subs = Map[String, collection.mutable.Set[ActorRef]]()

  override def sub(proc:ActorRef, channels:String*) {
    // i don't like the use of getOrElse here
    channels.foreach (
      channel => subs += channel -> (subs.getOrElse(channel, collection.mutable.Set[ActorRef]()) += proc)
    )

    log.debug("[RedisShrinkWatcher] Subscribing " + proc + " to " + channels)

    // subscribe with redis
    s ! Subscribe(channels.toArray)
  }

  override def unsub(proc:ActorRef, channels:String*) {}

  def receive = {    
    // subscribe sender
    case channel: String => {
      log.debug("[RedisShrinkWatcher] Subscribing a sender to " + channel)
      self.sender.foreach(sub(_, List(channel): _*)) // cast it with : _*
    }

    case ignore => log.error("[RedisShrinkWatcher] Ignoring message: " + ignore)
  }

  def sub(channels: String*) = {
    s ! Subscribe(channels.toArray)
  }

  def unsub(channels: String*) = {
    s ! Unsubscribe(channels.toArray)
  }

  def callback(pubsub: PubSubMessage) = pubsub match {
    case S(channel, no) => println("subscribed to " + channel + " and count = " + no)
    case U(channel, no) => println("unsubscribed from " + channel + " and count = " + no)
    case M(channel, msg) => {      
      // check who's subscribed on the channel and forward over the
      // message so they can process it
      
      log.debug("[RedisShrinkWatcher] received message on channel " + channel + " as : " + msg)
      // this is annoying, I know, creating and throwing out that map
      // how should this be done?
      subs.getOrElse(channel, collection.mutable.Set[ActorRef]()).foreach(_ ! msg)
    }
  }
}

  /**
   * Processors in shrink can be any actor and do not need to extend
   * anything special. This processor is provided for convenience and as
   * an example that people can use.
   */
package processors {

  trait ShrinkProcessor extends Actor

  package pipelined {

    trait PipelineProcessor {
      def apply(msg:String, args:Array[Any]) : String
    }

    /**
     * Allows a shrink processor to implement sub-processors
     * as a pipeline.
     */
    trait PipelinedProcessing extends Actor {

      var pipeline = List[String => String]()

      // accept a function and register it
      def register(proc:String => String) = {
	log.debug("[PipelinedProcessing] Registering: " + proc)
	pipeline += proc
      }

      def receive = {
	case msg:String => {
	  log.debug("[PipelinedProcessing] Received messgage: " + msg)

	  var m = msg
	  pipeline foreach ( proc => m = proc(m) )	 
	  m
	}

	case ignore => log.warning("[PipelinedProcessing] Ignored message: " + ignore)
      }
    }

    /**
     * Writes the message to a file.
     */
    trait FileWriter {
      this:PipelinedProcessing => {	
	register(fileWriter)
      }

      // the output stream to use
      var writers = List[OutputStreamWriter]()

      /**
       * Add new processes that will receive the message
       */
      def writeToFiles(args:OutputStreamWriter*) = writers ++= args

      def fileWriter(msg:String):String = {
	log.debug("[FileWriter] Writing message: " + msg)
	writers.foreach(w => {
	  w.write(msg)
	  w.flush
	})
	msg
      }
    }
       
    /**
     * Writes the message to stdout
     */
    trait StdoutWriter {
      this:PipelinedProcessing => {
	register(stdoutWriter)
      }    

      def stdoutWriter(msg:String) : String = {
	println("[StdoutWriter] " + msg)
	msg
      }
    }  
  }
}

/**
 * A simple shrink implementation that uses Redis to relay.
 */
class ShrinkRedisTextAgent extends 
  ShrinkAgent with 
  RedisShrinkRelayFactory {
}
