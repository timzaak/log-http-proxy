package com.timzaak.proxy

import org.apache.pekko.actor.{ Actor, ActorRef, ActorSystem, PoisonPill, Props }
import org.apache.pekko.http.scaladsl.*
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.*
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.*
import sttp.tapir.model.ServerRequest
import sttp.model.StatusCode
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import scala.concurrent.Future

class LogWebViewer(using system: ActorSystem) {

  import system.dispatcher

  private val manager = system.actorOf(Props[ConnectionManager]())

  def extractClientIP(request:ServerRequest) = request
    .header("X-Forwarded-For")
    .orElse(request.header("Remote-Address").map(v => v.take(v.lastIndexOf(':'))))
    .getOrElse(request.connectionInfo.remote.map(_.getAddress.getHostAddress).getOrElse(""))

  def call(request: ServerRequest, data: String) = {
    val ip = extractClientIP(request)
    manager ! ip -> data
  }

  private val viewerEndpoint = endpoint.get
    .in("viewer")
    .in(extractFromRequest(identity))
    .out(htmlBodyUtf8)
    .serverLogicSuccess { request =>
      val ip = extractClientIP(request)

      val htmlContent = s"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Log Viewer</title>
    <script type="text/javascript">
        // Ensure client_ip is replaced by Scala string interpolation
        const client_ip = "$ip"; // This will be interpolated by Scala
        const ws_protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        const ws_host = window.location.host;
        const socket = new WebSocket(`$${ws_protocol}//$${ws_host}/api_ws?ip=$${client_ip}`);

        socket.onopen = function(event) {
            console.log("WebSocket connection established, please start sending request, make sure you have changed your host config");
        };

        socket.onmessage = function(event) {
            console.log(event.data);
        };

        socket.onerror = function(error) {
            console.error("WebSocket Error: ", error);
        };

        socket.onclose = function(event) {
            console.log("WebSocket connection closed");
        };
    </script>
</head>
<body>
    <h1>Log Viewer</h1>
    <p>Your IP address is: $ip</p>
    <p>Open your browser's Developer Console to see logs.</p>
    <p>Shortcuts to open Developer Console:</p>
    <ul>
        <li>Windows/Linux: Ctrl+Shift+J</li>
        <li>macOS: Cmd+Option+J</li>
    </ul>
</body>
</html>
"""
      Future.successful(htmlContent)
    }

  private val handler = endpoint.get
    .in("api_ws")
    .in(query[String]("ip"))
    .out(webSocketBody[String, CodecFormat.TextPlain, String, CodecFormat.TextPlain](PekkoStreams))
    .serverLogicSuccess { ip =>
      val flow = Flow.fromSinkAndSource(
        Sink.ignore.mapMaterializedValue(_.onComplete(_ => manager ! ip)),
        Source
          .actorRef[String](
            completionMatcher = PartialFunction.empty,
            failureMatcher = PartialFunction.empty,
            bufferSize = 256,
            OverflowStrategy.dropHead,
          )
          .mapMaterializedValue { actorRef =>
            manager ! ip -> actorRef
          }
      )
      Future.successful(flow)
    }

  def startServer(port: Int) = {
    val routes = PekkoHttpServerInterpreter().toRoute(List(handler, viewerEndpoint))
    val bindAndCheck = Http()
      .newServerAt("0.0.0.0", port)
      .bindFlow(routes)
    bindAndCheck
  }
}

class ConnectionManager extends Actor {
  private val connections = scala.collection.mutable.Map[String, ActorRef]()
  override def receive: Receive = {
    case (key: String, data: String)  => connections.get(key).foreach(ref => data.trim.split('\n').foreach(ref ! _))
    case (key: String, ref: ActorRef) => connections.addOne(key -> ref)
    case key: String                  =>
      println(s"close ${key}")
      connections.remove(key).foreach(_ ! PoisonPill)
  }
}
