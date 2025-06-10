package com.timzaak.proxy

import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import org.apache.pekko.http.scaladsl.*
import org.apache.pekko.http.scaladsl.model.{AttributeKeys, RemoteAddress}
import org.apache.pekko.io.Tcp.SO.KeepAlive
import org.apache.pekko.stream.{CompletionStrategy, OverflowStrategy}
import org.apache.pekko.stream.scaladsl.*
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.*
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import java.net.{InetAddress, InetSocketAddress}
import scala.concurrent.Future

class LogWebViewer(using system: ActorSystem) {

  import system.dispatcher

  val manager = system.actorOf(Props[ConnectionManager]())

  def call(request: ServerRequest, data: String) = {
    val ip = request
      .header("X-Forwarded-For")
      .orElse(request.header("Remote-Address").map(v =>v.take(v.lastIndexOf(':'))))
      .getOrElse(request.connectionInfo.remote.map(_.getAddress.getHostAddress).getOrElse(""))

    manager ! ip -> data
  }

  private val handler = endpoint.get
    .in("api_ws")
    .in(query[String]("ip"))
    .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](PekkoStreams))
    .serverLogicSuccess { ip =>
      val flow = Flow.fromSinkAndSource(
        Sink.ignore.mapMaterializedValue(_.onComplete(_ => manager ! ip)),
        Source.actorRef[String](
          completionMatcher = PartialFunction.empty,
          failureMatcher = PartialFunction.empty,
          bufferSize = 256,
          OverflowStrategy.dropHead,
        ).mapMaterializedValue{actorRef =>
          manager ! ip -> actorRef
        }
      )
      Future.successful(flow)
    }

  def startServer(port:Int) = {
    val bindAndCheck = Http()
      .newServerAt("0.0.0.0", port)
      .bindFlow(PekkoHttpServerInterpreter().toRoute(handler))
    bindAndCheck
  }
}

class ConnectionManager extends Actor {
  // todo: add filter
  private val connections = scala.collection.mutable.Map[String, ActorRef]()
  override def receive: Receive = {
    case (key: String, data: String)  => connections.get(key).foreach(ref => data.trim.split('\n').foreach(ref ! _))
    case (key: String, ref: ActorRef) => connections.addOne(key -> ref)
    case key: String                  =>
      println(s"close ${key}")
      connections.remove(key).foreach(_ ! PoisonPill)
  }
}