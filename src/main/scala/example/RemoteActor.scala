package example

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.PromiseRef
import example.LocalActor.RemoteTerminateResponse
import example.RemoteActor.RemoteTerminateRequest

object RemoteActor {
  def props( promise: PromiseRef[Any] ) : Props = Props( new RemoteActor( promise ) )

  abstract sealed class Messages
  case class RemoteTerminateRequest( msg: String = "" ) extends Messages

  val Port = "5150"
}

class RemoteActor(promise: PromiseRef[Any] ) extends Actor with ActorLogging {
  override def receive: Receive = {
    case msg: String =>
      log.info( s"Received message: $msg from sender: $sender()")
      sender() ! s"All right buddy, I got it: $msg"
    case RemoteTerminateRequest( msg ) =>
      log.info( s"Received terminate request $msg from $sender()")
      sender() ! RemoteTerminateResponse( "ok" )
      promise.ref ! "Done!"
    case other: Any => log.info( s"Received something else: $other of type ${other.getClass.getCanonicalName}")
  }
}
