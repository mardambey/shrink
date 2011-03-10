package shrink

import akka.actor.{SupervisorFactory, Actor}
import akka.actor.Actor._
import akka.stm._
import akka.stm.TransactionalMap
import akka.config.Supervision._
import scala.xml.NodeSeq
import java.lang.Integer
import javax.ws.rs.{GET, Path, Produces}

class Boot {
  val factory = SupervisorFactory(
    SupervisorConfig(
      OneForOneStrategy(List(classOf[Exception]), 3, 100),
      Supervise(actorOf[SimpleServiceActor],Permanent)
      :: Nil))
  factory.newInstance.start
}

/**
 * Cobbled up together from akkaminimal, Jonas Boner's Akka chat, and the pubsub akka demo.
 */
@Path("/scalacount")
class SimpleService {
  @GET
  @Produces(Array("text/html"))
  def count = {
    val result = for{a <- registry.actorFor[SimpleServiceActor]
                     r <- (a !! "Tick").as[NodeSeq]} yield r
    result getOrElse <error>Error in counter</error>
  }
}

class SimpleServiceActor extends Actor {
  private val KEY = "COUNTER"
  private var hasStartedTicking = false
  private val storage = TransactionalMap[String, Integer]()

  var agent = new ShrinkRedisTextAgent()
  var service = remote.start("localhost", 2552, getClass.getClassLoader)  
  remote.register("shrink-service", actorOf(agent))

  var client = new ShrinkClient("localhost", 2552)

  def receive = {
    case "Tick" => if (hasStartedTicking) {
      val count = atomic {
        val current = storage.get(KEY).get.asInstanceOf[Integer].intValue
        val updated = current + 1
        storage.put(KEY, new Integer(updated))
        updated
      }

      client.send(new StringMessage(new Host("osaka"), "ticks=" + count))
      self.reply(<success>Tick:{count}</success>)
    } else {
      atomic {
        storage.put(KEY, new Integer(0))
      }
      hasStartedTicking = true
      self.reply(<success>Tick: 0</success>)
    }
  }
}

