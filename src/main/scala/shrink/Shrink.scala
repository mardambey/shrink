package shrink

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Actor._
import java.io.{ByteArrayInputStream,ByteArrayOutputStream,ObjectOutputStream,ObjectInputStream}
import java.lang.reflect.Method
import com.redis.RedisClient
import akka.persistence.redis._

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
class ShrinkClient(host:String, port:Int, var channel:String = "") {
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
class RedisShrinkRelay extends ShrinkRelay {
  // redis
  var r = new RedisClient()
  // publisher
  val p = actorOf(new Publisher(r))
  // start publisher
  p.start

  def receive = {
    case List(channel:String, msg:Message) => {
      log.info("[RedisShrinkRelay] Relaying message from: " + msg.from.address + " \"" + msg.text + "\"")
      p ! Publish("shrink/" + channel, msg.text)
    }

    case ignore => log.error("[RedisShrinkRelay] Error sending unknown message: " + ignore + " from " + self)
  }
} 

/**
 * Factory that creates and starts the Redis
 * shrink relay, mixed into shrink agents that
 * wish to use Redis.
 */
trait RedisShrinkRelayFactory { this: Actor => val relay:ActorRef = actorOf[RedisShrinkRelay].start }

/**
 * The watcher will watch over its list of channels
 * and will alert its processors when new data
 * arrives on a channel.
 */
trait ShrinkWatcher extends Actor {
   // Subscribe a given processor to a channel.
  def sub(channel:String, proc:ActorRef)

  // Unsubscribe a given processor from a channel
  def unsub(channel:String, proc:ActorRef)
}

/**
 * A simple shrink implementation that uses Redis to relay.
 */
class ShrinkRedisTextAgent extends 
  ShrinkAgent with 
  RedisShrinkRelayFactory {
}
