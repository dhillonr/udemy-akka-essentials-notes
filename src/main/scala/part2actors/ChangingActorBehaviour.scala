package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ActorCapabilities.Counter
import part2actors.ChangingActorBehaviour.Mom.{Food, MomStart}
import part2actors.ChangingActorBehaviour.StatelessCounter.{Increment, Decrement, Print}

object ChangingActorBehaviour extends  App {

  object FussyKid{
    case object KidAccept
    case object KidRject
    val HAPPY = "happy"
    val SAD = "sad"
  }
  class FussyKid extends  Actor {
    import FussyKid._
    import Mom._

    /* Here th internal state of the actor is just a variable,
     * but in practise it could be pretty complex ,
     * so the logic to handle the receive messages might blow up to 100's of lines
     *
     */

    // Internal state of the Kid
    var state = HAPPY  // We should use something which is less mutable.
    override def receive: Receive = {
      case Food(VEGETABLES) => state = SAD
      case Food(CHOCOLATE) => state = HAPPY
      case Ask(message) => {
        if (state == HAPPY) sender() ! KidAccept
        else sender() ! KidRject
      }
    }
  }

  class StatelessFussyKid extends Actor{
    import FussyKid._
    import Mom._

    override def receive: Receive = happyReceive

    def happyReceive: Receive = {
      case Food(VEGETABLES) => context.become(sadReceive, false)// change my receive handler to sadReceive
      case Food(CHOCOLATE) =>  // stay happy
      case Ask(_) => sender() ! KidAccept
    }
    def sadReceive: Receive = {
      case Food(VEGETABLES) => context.become(sadReceive, false)// stay sad
      case Food(CHOCOLATE) => context.unbecome()// change my receive handler to happyReceive
      case Ask(_) => sender() ! KidRject
    }
  }
  object Mom{
    case class MomStart(kidRef: ActorRef)
    case class Food(food: String)
    case class Ask(message: String)
    val VEGETABLES = "veggies"
    val CHOCOLATE = "chocolate"
  }
  class Mom extends  Actor{
    import Mom._
    import  FussyKid._

    override def receive: Receive ={
      case MomStart(ref) => {
        ref ! Food(VEGETABLES)
        ref ! Ask("You wanna sleep ?")
        ref ! Food(CHOCOLATE)
        ref ! Ask("Now you wanna sleep ?")
      }
      case KidAccept => println(" Yay! my kid is happy")
      case  KidRject => println(" What happen ?")
    }
  }

  val system = ActorSystem("home")

  val fussyKid = system.actorOf(Props[FussyKid], "fussyKid")
  val mom = system.actorOf(Props[Mom], "mom")
  val statelessFussyKid = system.actorOf((Props[StatelessFussyKid]), "statelessFussyKid")

  mom ! MomStart(statelessFussyKid)
  /*
   * mom receives MomStart
   *  kid receives Food(veggies) ->  kid will change the handler to to sadReceive
   *  kid receives Ask(play>) -> kid replies with sadReceive handler
   * mom receives KidReject
   */

  /* Messages are handle by the handler which is at the top of th Message Handling Stack
   * To add handler : context.become(handler, discardOld = false)
   * Replace handler : context.become(handler, discardOld = true)
   * Pop handler : context.unbecome()
   *
   * Case 1. context.become without extra parameter i.e default True
   *
   * kid receives Food(veggies) ->  message handler turns to sadReceive
   */

  /*
   * Case 2. context.become with extra parameter False
   *
   * kid receives Food(veggies) ->  stack.push(sadReceive)
   * kid receives Food(chocolate) -> stack.push(happyReceive)
   *
   * Message handling Stack:
   * 1. happyReceive // handler added on receiving message : Food(chocolate)
   * 2. sadReceive   // handler added on receiving message : Food(veggies)
   * 3. happyReceive //initial handler (default added at the time of construction)
   *
   * In this implementation of statelessFussyKid it will become more and more sad ,
   * as you keep feeding him with veggies and will become less sad as you feed it chocolate.
   */

  /*
   * Exercise 1 : Recreate the counter actor with context.become and NO MUTABLE STATES
   */

  object  StatelessCounter  {
    case object Decrement
    case object Increment
    case object Print
  }

  class StatelessCounter extends Actor {
    import StatelessCounter ._

    override def receive: Receive = countReceive(0)

    def countReceive(count: Int): Receive ={
      case Increment =>
        println(s" [$count] incrementing ")
        context.become((countReceive(count +1)))
      case Decrement =>
        println(s" [$count] decrementing ")
        context.become(countReceive(count -1))
      case Print => println(s"[$self] Count is : $count")
    }
  }

  val statelessCounter = system.actorOf(Props[StatelessCounter], "statelessCounter")

  (1 to 5 ).foreach(_ => statelessCounter ! Increment)
  (1 to 3).foreach(_ => statelessCounter ! Decrement)
  statelessCounter ! Print

  // To convert a stateful actor to stateless actor by calling the next state ,
  // instead of holding the state as a member of the actor

  /*
   * Exercise 2: a simplified voting system
   */
  case class Vote(candidate: String)
  case object VoteStatusRequest
  case class VoteStatusReply(candidate: Option[String])
  class Citizen extends Actor {
    override def receive: Receive = {
      case Vote(candidate) => context.become(voted(candidate))
      case VoteStatusRequest => sender() ! VoteStatusReply(None)
    }

    def voted(votedcandidate: String): Receive = {
      case VoteStatusRequest => sender() ! VoteStatusReply(Some(votedcandidate))
    }
  }

  case class AggregateVotes(candidates: Set[ActorRef]){}
  class VoteAggregator extends Actor{
    override def receive: Receive = awaitingCommand

    def awaitingCommand : Receive = {
      case AggregateVotes(citizens) =>{
        citizens.foreach(citizenRef => citizenRef ! VoteStatusRequest)
        context.become(awaitingStatus(citizens, Map()))
      }

    def awaitingStatus(stillWaiting: Set[ActorRef], currentStats: Map[String, Int]): Receive = {
      case VoteStatusReply(None) => sender() ! VoteStatusRequest
      case VoteStatusReply(Some(candidate)) =>
        val newstillWaiting = stillWaiting - sender()
        val currentVotesOfCandidate = currentStats.getOrElse(candidate, 0)
        val newStats = currentStats + (candidate -> (currentVotesOfCandidate + 1))
        if (newstillWaiting.isEmpty) {
          println(s"[aggregator] poll  status $newStats")
        } else {
          context.become(awaitingStatus(newstillWaiting, newStats))
        }
    }
    }
  }

  val alice = system.actorOf(Props[Citizen], "alice")
  val bob = system.actorOf(Props[Citizen], "bob")
  val charlie = system.actorOf(Props[Citizen], "charlie")
  val daniel = system.actorOf(Props[Citizen], "daniel")

  val voteAggregator = system.actorOf(Props[VoteAggregator], "voteAggregator")

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  charlie ! Vote("Roland")
  daniel ! Vote("Roland")

  voteAggregator ! AggregateVotes(Set(alice, bob, charlie, daniel))
}
