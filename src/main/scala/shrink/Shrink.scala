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
 * The agent sits on a client machine and accepts messages from
 * local clients so they can be sent to the relay server.
 */
trait ShrinkAgent extends Actor {

  val relay: ActorRef
  
  override def preStart {
    log.info("[ShrinkAgent] Shrink agent is starting up...")
  }

  def receive = {
    case msg:Message => {
      log.info("[ShrinkAgent] Got message (relaying): " + msg.from.address + " => " + msg.text + " : " + msg)
      relay ! msg
    }

    case _ =>
  }
}

/**
 * This is a simple Shrink client that gets a shrink
 * service actor and sends messages to it.
 */
class ShrinkClient(host:String, port:Int) {
  val service = remote.actorFor("shrink-service", host, port)

  def send(msg:Message) {
    log.debug("[ShrinkClient] Sending message to shrink-service: " + msg)
    service ! msg
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
  // channel to publish to
  val channel = "shrink-channel"
  // start publisher
  p.start

  def receive = {
    case msg:Message => {
      log.info("[RedisShrinkRelay] Relaying message from: " + msg.from.address + " \"" + msg.text + "\"")
      p ! Publish(channel, msg.text)
    }

    case _ =>
  }
} 

/**
 * Factory that creates and starts the Redis
 * shrink relay, mixed into shrink agents that
 * wish to use Redis.
 */
trait RedisShrinkRelayFactory { this: Actor => val relay:ActorRef = actorOf[RedisShrinkRelay].start }

/**
 * A simple shrink implementation that uses Redis to relay.
 */
class ShrinkRedisTextAgent extends 
  ShrinkAgent with 
  RedisShrinkRelayFactory {
}
