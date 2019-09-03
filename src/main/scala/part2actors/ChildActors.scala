package part2actors

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ChildActors.Parent
import part2actors.ChildActors.Parent.{CreateChild, TellChild}

object ChildActors extends  App {

  object Parent {
    case class CreateChild(name: String)
    case class TellChild(message: String)
  }
  class Parent extends  Actor {
    import Parent._

    override def receive: Receive = {
      case CreateChild(name) =>
        println(s"${self.path} creating child")
        // create an actor inside an actor
        val childRef = context.actorOf(Props[Child], name)
        context.become(withChild(childRef))
    }

    def withChild(childRef: ActorRef): Receive ={
      case TellChild(message) =>
        childRef forward message
    }
  }

  class Child extends Actor {
    override def receive: Receive = {
      case message => println(s"${self.path} I got : $message")
    }
  }

  val system = ActorSystem("ParentChildDemo")
  val parent = system.actorOf(Props[Parent], "parent")
  parent ! CreateChild("child")
  parent ! TellChild("hey Kid!")

  // actor hierarchies
  // parent -> child  -> grandChild
  //        -> child2 ->

  /*
    Guardian actors (top-level)
    - /system = system guardian   (manages logging and other stuff)
    - /user = user-level guardian
    - / = the root guardian       (manages system and user actors)
   */

  /*
   * Actor selection (find an actor by path)
   */

  // It is a wrapper over potential actorRef that we can use to send a message
  val childSelection = system.actorSelection("/user/parent/child") // locate an actor
  childSelection ! "I found you"  // send a message to located actor

  // if the path is not valid then the message will be sent to dead letters

  /*
   *  DANGER !
   *
   *  NEVER PASS MUTABLE STATE OR `this` REFERENCE, TO CHILD ACTORS.
   *
   *  NEVER IN YOUR LIFE
   * (Because it breaks actor encapsulation as it allows child actors direct access to parents
   * internal state i.e child actors can modify the internal state without sending messages ,
   * which breaks our sacred actor principle
   */

  object NaiveBankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object InitializeAccount
  }
  class NaiveBankAccount extends Actor {
    import NaiveBankAccount._
    import CreditCard._

    var amount = 0

    override def receive: Receive = {
      case InitializeAccount =>
        val creditCardRef = context.actorOf(Props[CreditCard], "card")
        creditCardRef ! AttachToAccount(this) // !!
      case Deposit(funds) => deposit(funds)
      case Withdraw(funds) => withdraw(funds)

    }

    def deposit(funds: Int) = {
      println(s"${self.path} depositing $funds on top of $amount")
      amount += funds
    }
    def withdraw(funds: Int) = {
      println(s"${self.path} withdrawing $funds from $amount")
      amount -= funds
    }
  }

  object CreditCard {
    case class AttachToAccount(bankAccount: NaiveBankAccount) // !!
    case object CheckStatus
  }
  class CreditCard extends Actor {
    import CreditCard._
    override def receive: Receive = {
      case AttachToAccount(account) => context.become(attachedTo(account))
    }

    def attachedTo(account: NaiveBankAccount): Receive = {
      case CheckStatus =>
        println(s"${self.path} your messasge has been processed.")
        // benign
        account.withdraw(1) // because I can
    }
  }

  import NaiveBankAccount._
  import CreditCard._

  val bankAccountRef = system.actorOf(Props[NaiveBankAccount], "account")
  bankAccountRef ! InitializeAccount
  bankAccountRef ! Deposit(100)

  Thread.sleep(500)
  val ccSelection = system.actorSelection("/user/account/card")
  ccSelection ! CheckStatus

  // WRONG!!!!!!
}
