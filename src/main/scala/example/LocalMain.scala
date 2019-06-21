package example

import java.net.InetAddress
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.pattern.PromiseRef
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import example.LocalActor.{SendTo, props}
import example.RemoteActor.RemoteTerminateRequest
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.FiniteDuration
import scala.io.Source

object LocalMain {
  private val LOG: Logger = LoggerFactory.getLogger( this.getClass )

  def main(args: Array[String]): Unit = {
    val hostname = InetAddress.getLocalHost().getHostName().toUpperCase()
    val parameters = args.mkString( "," )

    LOG.info( s"Starting LOCAL actor system on: $hostname parameters: $parameters" )

    val configFile = getClass.getClassLoader.getResourceAsStream("local_system.conf")
    val configContent = Source.fromInputStream( configFile ).mkString.replaceAll( "%HOSTNAME%", hostname )
    LOG.info( s"LOCAL Config: $configContent")

    val config = ConfigFactory.parseString( configContent )

    val system = ActorSystem( "LocalActorSystem", config )

    // promise - to be notified with any message that the system can shut down
    val promise = PromiseRef( system, Timeout( FiniteDuration( 600, TimeUnit.SECONDS ) ))

    // this actor will send messages to remote actors
    if( args.length > 0 ) {
      val actorLocal = system.actorOf( props(args map ( _.toUpperCase ), promise ), name = "LocalActor" )
      actorLocal ! SendTo( "Hello everyone", "all" )
      // we store hosts as upper case for consistency between Windows and Linux
      actorLocal ! SendTo( "Hello pi2", "pi2" )
      actorLocal ! SendTo( "Hello pi3", "pi3" )
      actorLocal ! SendTo( "Hello random", "random" )
      actorLocal ! SendTo( RemoteTerminateRequest( "We are done" ), host = "all" )
    }
    else
      promise.ref ! "Done!"

    ExampleUtil.shutDown( promise, system )
  }
}
