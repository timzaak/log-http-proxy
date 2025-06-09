package com.timzaak.proxy

import org.apache.pekko.actor.{ Actor, ActorRef, ActorSystem, Props }
import org.apache.pekko.http.scaladsl.*
import org.apache.pekko.io.Tcp.SO.KeepAlive
import org.apache.pekko.stream.scaladsl.*
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.*
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import scala.concurrent.Future
import scala.concurrent.duration.*

class LogWebViewer(using system: ActorSystem) {

  import system.dispatcher

  val manager = system.actorOf(Props[ConnectionManager]())

  def call(request: ServerRequest, data: String) = {
    val ip = request
      .header("X-Forwarded-For")
      .orElse(request.header("Remote-Address"))
      .getOrElse(request.connectionInfo.remote.map(_.getAddress.getHostAddress).getOrElse(""))

    manager ! ip -> data
  }

  val handler = endpoint.get
    .in("api_ws")
    .in(query[String]("ip"))
    .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](PekkoStreams))
    .serverLogicSuccess { ip =>
      val flow = Flow.fromSinkAndSource(
        Sink.actorRef[String](
          system.actorOf(Props(WebSocketConnection(ip, manager))),
          onCompleteMessage = "complete",
          onFailureMessage = v => ""
        ),
        Source
          .tick(1.second, 30.seconds, KeepAlive)
          .map(_ => "")
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
    case (key: String, data: String)  => connections.get(key).foreach(_ ! data)
    case (key: String, ref: ActorRef) => connections += key -> ref
    case key: String                  => connections -= key
  }
}

class WebSocketConnection(key: String, manager: ActorRef) extends Actor {
  manager ! key -> self

  override def receive: Receive = { case v: Any => println(s"received: ${v}") }

  override def postStop(): Unit = {
    println("WebSocket connection closed")
    super.postStop()
    manager ! key
  }
}
