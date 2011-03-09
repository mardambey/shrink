package shrink

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Actor._
import Actor._
import java.io.Serializable

/**
 * A host in the system, usually a client.
 */
case class Host(address:String)

/**
 * A message sent from a client to be broadcasted.
 */
trait Message[T <: Serializable] { 
  val from:Host
  val data:T
}

/**
 * String message, a message with a string data type.
 */
case class StringMessage(from:Host, data:String) extends Message[String]

/**
 * Abstraction of Shrink relay layer for relaying messages
 */
trait ShrinkRelay extends Actor

trait ShrinkAgent extends Actor {

  val relay: ActorRef
  
  override def preStart {
    log.info("Shrink agent is starting up...")   
  }

  def receive = {
    case msg:Message[Serializable] => {
      log.info("Got message: " + msg.from.address + " => " + msg.data)
      relay ! msg
    }

    case _ =>
  }
}

class ShrinkClient(host:String, port:Int) {
  val service = remote.actorFor("shrink-service", host, port)

  def send[T <: Serializable](msg:Message[T]) {
    service ! msg
  }
}

class RedisShrinkRelay extends ShrinkRelay {
  def receive = {
    case msg:Message[Serializable] => {
      log.info("RedisShrinkRelay: relaying message from: " + msg.from.address + " \"" + msg.data + "\"")
    } 
  }
}

trait RedisShrinkRelayFactory { this: Actor => val relay:ActorRef = actorOf[RedisShrinkRelay].start }

class ShrinkRedisTextAgent extends 
  ShrinkAgent with 
  RedisShrinkRelayFactory {
}
