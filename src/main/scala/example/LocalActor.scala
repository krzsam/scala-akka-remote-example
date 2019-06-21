package example

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorRef, ActorSelection, Identify, Props}
import akka.pattern.PromiseRef
import example.LocalActor.{RemoteTerminateResponse, SendTo}

import scala.collection.mutable
import scala.language.postfixOps
import scala.util.Random

object LocalActor {
  def props( remoteHosts: Array[String], promise: PromiseRef[Any] ) : Props = Props( new LocalActor( remoteHosts, promise ) )

  abstract sealed class Messages
  case class RemoteTerminateResponse(msg: String = "" ) extends Messages
  case class SendTo( message: Any, host: String ) extends Messages
}

class LocalActor( remoteHosts: Array[String], promise: PromiseRef[Any] ) extends Actor with ActorLogging {
  val hostToActorMap: mutable.Map[String, ActorSelection] = mutable.Map()
  val actorsCompletionMap: mutable.Map[ActorRef, Boolean] = mutable.Map()

  remoteHosts foreach  {
    host =>
      val remotePath = s"akka.tcp://RemoteActorSystem@$host:${RemoteActor.Port}/user/RemoteActor$host"
      val remoteSelection = context.actorSelection( remotePath )
      log.info( s"Adding remote actor on path: $remotePath ref: $remoteSelection" )
      hostToActorMap += ( ( host, remoteSelection ) )
      remoteSelection ! Identify( "Say your name" )
  }

  override def receive: Receive = {
    case ActorIdentity( correlationId , ref ) =>
      log.info( s"Received actor identity from ${ref.get} for request '$correlationId'")
      actorsCompletionMap += ( ( ref.get , false ) )
    case SendTo( message, host ) => host match {
      case "all" =>
        log.info( s"Sending '$message' to all remote actors" )
        hostToActorMap foreach ( t => t._2 ! message  )
      case "random" =>
        val random = Math.abs( Random.nextInt )
        val size = hostToActorMap.keys.size
        val which = random % size
        val chosenHost = hostToActorMap.keys.toArray.apply( which )
        log.info( s"Sending '$message' to a random remote host which happens to be: $chosenHost")
        val actor = hostToActorMap( chosenHost )
        actor ! message
      case hostName =>
        val upper = hostName.toUpperCase
        if( hostToActorMap.keySet.contains( upper ) ) {
          log.info(s"Sending '$message' to actor on $upper")
          val actor = hostToActorMap(upper)
          actor ! message
        } else {
          log.error(s"Unknown host $upper")
        }
    }
    case s: String => log.info( s"Remote actor $sender() replied with $s" )
    case RemoteTerminateResponse( msg ) =>
      log.info( s"Received terminate reply $msg from $sender()" )
      log.info( s"Completion map before is $actorsCompletionMap" )
      actorsCompletionMap( sender() ) = true
      log.info( s"Completion map is now $actorsCompletionMap" )
      val allComplete = actorsCompletionMap.values.forall( _ == true )
      log.info( s"All remote actors completed: $allComplete")
      if( allComplete )
        promise.ref ! "Done!"
    case other: Any => log.info( s"Received something else: $other of type ${other.getClass.getCanonicalName}")
  }
}

