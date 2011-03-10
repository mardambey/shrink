package shrink

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Actor._
import Actor._
import java.io.{ByteArrayInputStream,ByteArrayOutputStream,ObjectOutputStream,ObjectInputStream}

/**
 * A host in the system, usually a client.
 */
case class Host(address:String)

/**
 * Final format of the messages going across the network
 */
case class TransportMessage(from:Host, data:Array[Byte], clazz:String)

/**
 * Trait defining what it means to be serializable.
 */
trait Serializable {
  type data
  type obj
  def serialize(o:obj):data
  def deserialize(d:data):obj
}

/**
 * Serializes to a byte array.
 */
trait ByteArraySerialization extends Serializable {
  type data = Array[Byte]

  override def serialize(o:obj) : Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(bos)
    oos.writeObject(o)
    oos.flush
    oos.close
    bos.close
    bos.toByteArray
  }

  override def deserialize(d:Array[Byte]) : obj = {
    val bis = new ByteArrayInputStream(d)
    val ois = new ObjectInputStream(bis)
    val r = ois.readObject.asInstanceOf[obj]
    ois.close
    bis.close    
    r
  }
}

/**
 * A message sent from a client to be broadcasted.
 */
trait Message extends ByteArraySerialization { 
  type dat = obj
  val from:Host
  val data:dat
}

/**
 * String message, a message with a string data type.
 */
case class StringMessage(from:Host, data:String) extends Message {  
  type obj = String
}

object StringMessage extends ByteArraySerialization

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
    log.info("Shrink agent is starting up...")
  }

  def receive = {
    case msg:Message => {
      log.info("Got message: " + msg.from.address + " => " + msg.data)
      relay ! TransportMessage(msg.from, msg.serialize(msg.data), msg.getClass.getName)
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
    service ! msg
  }
}

/**
 * This implements a shrink relay that uses Redis to
 * pass messages around.
 */
class RedisShrinkRelay extends ShrinkRelay {
  def receive = {
    case msg:TransportMessage => {
      val d = msg     
      val m = Class.forName(msg.clazz).asInstanceOf[{ def deserialize(d:Array[Byte]) : Any }]
      val data = m.deserialize(msg.data)

      println("DESERIALIZE: " + msg.data + " -> " + data)

      log.info("RedisShrinkRelay: relaying message from: " + msg.from.address + " \"" + data + "\"")
    } 
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
