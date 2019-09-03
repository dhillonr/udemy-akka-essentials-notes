package part3testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class TestProbeSpec extends TestKit(ActorSystem("TestProbeSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TestProbeSpec._

  "A master actor" should {
    "register a slave "in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave") // a TestProbe is a special actor with some assertion capabilities

      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
    }

    "send the work to slave actor" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")
      // a TestProbe is a special actor with some assertion capabilities
      // TestProbes can send reply messages and can do assertions
      // Can watch other actors i.e when they start or stop

      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workloadString = "I love Akka"
      master ! Work(workloadString)

      // interaction between the master actor and the slave actor
      slave.expectMsg(SlaveWork(workloadString, testActor))
      slave.reply(WorkCompleted(3, testActor))

      expectMsg(Report(3)) // test actor receives the Report(3) message
    }

    "aggregate the data correctly" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave") // a TestProbe is a special actor with some assertion capabilities

      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workloadString = "I love Akka"
      master ! Work(workloadString)
      master ! Work(workloadString)

      // in the mean time I don't have a slave actor
      slave.receiveWhile(){
        case SlaveWork(`workloadString`, `testActor`) => slave.reply(WorkCompleted(3, testActor))
      }

      expectMsg(Report(3))
      expectMsg(Report(6))

      // This is how we test multiple actor interactions with fictitious actors called TestProbes
    }
  }
}

object TestProbeSpec {
  //scenario
  /*
    word counting actor hierarchy master-slave

    send some work to master
      - master sends the slave the piece of work
      - slave processes the work and replies to master
      - master aggregates the results
    master sends total count to the original requester
   */
  case class Work(text: String)
  case class SlaveWork(text: String, originalRequester: ActorRef)
  case class WorkCompleted(count: Int, originalRequester: ActorRef)
  case class Register(slaveRef: ActorRef)
  case class Report(totalWordCount: Int)
  case object RegistrationAck

  class Master extends Actor {
    override def receive: Receive = {
      case Register(slaveRef) =>
        sender() ! RegistrationAck
        context.become(online(slaveRef, 0))
      case _ => //ignore
    }
    def online(slaveRef: ActorRef, totalWordCount: Int): Receive ={
       case Work(text) => slaveRef ! SlaveWork(text, sender())
       case WorkCompleted(count, originalRequester) => {
         val newTotalWordCount = totalWordCount + count
         originalRequester ! Report(newTotalWordCount)
         context.become(online(slaveRef, newTotalWordCount))
     }
    }
  }

  // class Slave extends Actor .....
}
