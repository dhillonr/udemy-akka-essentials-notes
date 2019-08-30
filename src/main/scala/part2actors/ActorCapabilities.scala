package part2actors

import akka.actor.Status.{Failure, Success}
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ActorCapabilities.BankAccount.Deposit
import part2actors.ActorCapabilities.Counter.{Decreament, Increament, Print}
import part2actors.ActorCapabilities.Person.LiveTheLife

object ActorCapabilities extends  App {

  class SimpleActor extends Actor {
    override def receive: Receive = {
      case "Hi !" => context.sender() ! "Hello, there" //replying to a message
      case message: String =>println(s"[$self] I have received: $message")
      case number: Int => println(s"[$self] I have received a NUMBER: $number")
      case SpecialMessage(contents) =>  println(s"[$self] I have received a special message : $contents")
      case SendMessageToYourself(contents) =>
        self ! contents
      case SayHiTo(ref) => ref ! "Hi !"
      case WirelessPhoneMessage(content, ref) => ref forward (content + "s")
    }
  }

  val system = ActorSystem("actorCapabilitiesDemo")
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  simpleActor ! "hello, actor"

  // 1 - messages can be of any type
  // a) messages must be IMMUTABLE
  // b) messages must be SERIALIZABLE (means whether a JVM can transform it into a byte stream and ,
  //    send it some other JVM on same machine or other machine)

  // in practise use case classes or case objects
  simpleActor ! 5

  case class SpecialMessage(content: String)
  simpleActor ! SpecialMessage("some special content")

  // 2 - actors have information about their context and about themselves
  // context (ActorContext) is a member of actor. It has the information about the environment in which the actor run,
  // eg -> context.system is the system which this actor runs on top of and ,
  //       context.self is reference to this actors own reference. It is the equivalent of `this` in OOP
  // context.self is same as self i.e self can be used in place of context.self

  case class SendMessageToYourself(content: String)
  simpleActor ! SendMessageToYourself(" I am an actor and I am proud of it")
  // 3 - actors can REPLY to Messages (by using context)
  val alice = system.actorOf(Props[SimpleActor], "alice")
  val bob = system.actorOf(Props[SimpleActor], "bob")

  // ActorRef are used by akka to know which actor to send messages to
  case class SayHiTo(ref: ActorRef)
  alice ! SayHiTo(bob)

  // 4 - dead letters
  alice ! "Hi !" // reply to "me"

  // 5  -  forwarding messages
  // forwarding = sending the message with ORIGINAL sender

  case class WirelessPhoneMessage(content: String, ref: ActorRef)
  alice ! WirelessPhoneMessage( "Hi", bob)

  /**
    * Exercises
    *
    * 1. a Counter actor
    *   - Increment
    *   - Decrement
    *   - Print
    *
    * 2. a Bank account as an actor
    *   receives
    *   - Deposit an amount
    *   - Withdraw an amount
    *   - Statement
    *   replies with
    *   - Success
    *   - Failure
    *
    *   interact with some other kind of actor
    */

  object  Counter {
    case object Decreament
    case object Increament
    case object Print
  }

  class Counter extends Actor {
    import Counter._

    private var count: Int = 0

    override def receive: Receive = {
      case Increament => count += 1
      case Decreament => count -= 1
      case Print => println(s"[$self] Count is : $count")
    }
  }

  val counter = system.actorOf(Props[Counter], "myCounter")
  (1 to 5).foreach(_ => counter ! Increament)
  (1 to 3).foreach(_ => counter ! Decreament)
  counter ! Print

  // bank account
  object  BankAccount {
    case class Deposit(amount: Float)
    case class Withdraw(amount: Float)
    case object Statement

    case class TransactionSuccess(message: String)
    case class TransactionFailure(message: String)
  }

  class BankAccount extends  Actor{
    import BankAccount._

    var balance: Float = 0

    override def receive: Receive = {
      case Deposit(amount) => {
        if (amount <0) sender() ! TransactionFailure("Invalid deposit amount")
        else {
          balance += amount
          sender() ! TransactionSuccess(s"successfully deposited $amount")
        }
      }
      case Withdraw(amount) => {
        if (amount < 0 ) sender() ! TransactionFailure(" Invalid withdrawal amount")
        else if (amount > balance) sender() ! TransactionFailure(s"Insufficient Funds")
        else {
          balance -= amount
          TransactionSuccess(s"Withdrawn amount $amount")
        }
      }
      case Statement => sender() ! s" Your Balance is : $balance"
    }
  }

  object Person{
    case class LiveTheLife(account: ActorRef)
  }

  class Person extends Actor{
    import Person._
    import BankAccount._

    override def receive: Receive ={
      case LiveTheLife(account) =>
        account ! Deposit(10000)
        account ! Withdraw(999999)
        account ! Withdraw(500)
        account ! Statement

      case message => println(message.toString)
    }
  }

  val bankAccount = system.actorOf(Props[BankAccount], "bankAccount")
  val alex= system.actorOf(Props[Person], "alex")

  alex ! LiveTheLife(bankAccount)
}
