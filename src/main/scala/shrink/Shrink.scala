package shrink

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Actor._
import Actor._
import java.io.{ByteArrayInputStream,ByteArrayOutputStream,ObjectOutputStream,ObjectInputStream}
import java.lang.reflect.Method

/**
 * A host in the system, usually a client.
 */
case class Host(address:String)

/**
 * Trait defining what it means to be serializable.
 */
trait Serialization {  
  type obj // type of object to serialize
  def serialize(o:obj):Array[Byte]
  def deserialize(d:Array[Byte]):obj
}

/**
 * Serializes to a byte array.
 */
trait SimpleSerialization extends Serialization {  

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
trait Message { 
  type dataType // type of the data the message will carry
  val from:Host
  var data:Option[dataType] = None
  var sdata:Option[Array[Byte]] = None
}

/**
 * String message, a message with a string data type.
 */
case class StringMessage(from:Host, d:String) extends Message with SimpleSerialization {
  type dataType = String
  data = Option(d)
}

/**
 * Abstraction of Shrink relay layer for relaying messages
 */
trait ShrinkRelay extends Actor

/**
 * A serializable message.
 */
trait SerializableMessage extends Message with Serialization { 
  type obj = Any
  type dataType = Any 
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
    case msg:Message => {
      log.info("[ShrinkAgent] Got message (relaying): " + msg.from.address + " => " + msg.data + " : " + msg)
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
    msg match {
      case m:Serialization => {     
	msg.sdata = Option(m.serialize(msg.data.asInstanceOf[m.obj]))
	msg.data = None
	service ! msg
      }
    }
  }
}

/**
 * This implements a shrink relay that uses Redis to
 * pass messages around.
 */
class RedisShrinkRelay extends ShrinkRelay {
  def receive = {
    case msg:Message => {
      msg match {
	case m:Serialization => {
	  val d = m.deserialize(msg.sdata.get)
	  log.info("[RedisShrinkRelay] relaying message from: " + msg.from.address + " \"" + d + "\"")
	}
      }
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
