package shrink

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Actor._
import Actor._

case class Host(address:String)
case class Message(from:Host, content:String)

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
    case msg @ Message(from, content) => {
      log.info("Got message: " + from.address + " => " + content)
      relay ! msg
    }

    case _ =>
  }
}

class ShrinkClient(host:String, port:Int) {
  val service = remote.actorFor("shrink-service", host, port)

  def send(msg:Message) {
    service ! Message(Host("ewp1"), "error loading stuff")
  }
}

class RedisShrinkRelay extends ShrinkRelay {
  def receive = {
    case Message(from, content) => {
      log.info("RedisShrinkRelay: relaying message from: " + from.address + " \"" + content + "\"")
    } 
  }
}

trait RedisShrinkRelayFactory { this: Actor => val relay:ActorRef = actorOf[RedisShrinkRelay].start }

class ShrinkRedisTextAgent extends 
  ShrinkAgent with 
  RedisShrinkRelayFactory {
}
