package part2actors

import akka.actor.{Actor, ActorSystem, Props}

object ActorsIntro extends App {

  // part1 - actor systems

  /*
  The actor system is a heavy weight data structure that controls a number of threads under the hood,
  which then allocates to running actors .
  */

  /*
  It is recommended to have one of these actor systems for application instance ,
  unless we have good reason to create more.
   */
  val actorSystem = ActorSystem("firstActorSystem")
  println(actorSystem.name)


  // part2 - create actors
  // word count actor
  class WordCountActor extends  Actor {
    // internal data
    var totalWords = 0

    // behaviour : Receive is a partial function of type [Any, Unit]
    def receive: Receive = {
      case message: String => totalWords += message.split(" ").length

      case msg => println(s"I can't understand the message: ${msg.toString}")
    }
  }

  // instantiate our actor
  val wordCounter = actorSystem.actorOf(Props[WordCountActor], "wordCounter")

  /*
  Here type of wordCounter is ActorRef which what akka provides to us so that ,
  we can't directly modify the internal data of WordCounterActor rather ,
  we can communicate with this actor via actor reference
   */

  // part4 - communicate!
  wordCounter !  "I am learning Akka and it's awesome"
  // asychrounous

  /*
   ! means tell
   */

  class Person(name: String) extends  Actor {
    override def receive: Receive = {
      case "hi" => println(s"my name is $name")
      case _ =>
    }
  }

  // passing constructor arguements to actor
  val person = actorSystem.actorOf(Props(new Person("Rohit")))
  person ! "hi"

  // Best Practise to create actors with constructor params
  // Create companion object
  object Person {
    def props(name: String) = Props(new Person(name))
  }

  val person2 = actorSystem.actorOf(Person.props("Sunil"))
  person2 ! "hi"
}
