package com.timzaak.proxy

import org.apache.pekko.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}
import org.apache.pekko.http.javadsl.model.headers.XForwardedFor
import org.apache.pekko.http.scaladsl.*
import org.apache.pekko.http.scaladsl.model.{AttributeKeys, HttpHeader, HttpRequest, RemoteAddress}
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.stream.scaladsl.*
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.HeaderNames
import sttp.tapir.*
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import java.io.File
import scala.concurrent.Future

class LogWebViewer(certPath: Option[String])(using system: ActorSystem) {

  import system.dispatcher

  private val manager = system.actorOf(Props[ConnectionManager]())

  def extractClientIP(request: ServerRequest) = request
    .header("X-Forwarded-For")
    .orElse(request.header("Remote-Address").map(v => v.take(v.lastIndexOf(':'))))
    .getOrElse(request.connectionInfo.remote.map(_.getAddress.getHostAddress).getOrElse(""))

  def call(request:HttpRequest, data:String) = {
    val ip = request.header[XForwardedFor].map(_.value)
      .orElse(request.header[org.apache.pekko.http.javadsl.model.headers.RemoteAddress].map(v => v.value.take(v.value.lastIndexOf(':'))))
      .orElse(request.attribute[RemoteAddress](AttributeKeys.remoteAddress).flatMap(_.toIP.map(_.ip.getHostAddress)))
      .getOrElse("")

    manager ! ip -> data
  }

  private val viewerEndpoint = endpoint.get
    .in("")
    .in(extractFromRequest(identity))
    .out(htmlBodyUtf8)
    .serverLogicSuccess { request =>
      val ip = extractClientIP(request)
      val certDesc = certPath match {
        case Some(_) =>
          s"""<p>the server use self signed cert, you may need to register the self signed rootCA.pem to your system. </p>
             |<p>1. download <a target="_blank" href="./rootCA">rootCA.pem</a> and install <a target="_blank" href="https://github.com/FiloSottile/mkcert?tab=readme-ov-file#installation">mkcert</a></p>
             |<p>2. set environment $$CAROOT to rootCA.pem directory</p>
             |<p>3. run the command:     mkcert -install</p>
             |""".stripMargin
        case _ => ""
      }
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
    $certDesc
</body>
</html>
"""
      Future.successful(htmlContent)
    }

  private val wsHandler = endpoint.get
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

  private val downloadCA = endpoint.get
    .in("rootCA")
    .errorOut(stringBody)
    .out(fileBody)
    .out(header[String]("Content-Disposition"))
    .serverLogic { _ =>
      val result = certPath match {
        case Some(certPath) =>
          val file = File(certPath)
          if (file.exists()) {
            Right((file, "attachment; filename=\"rootCA.pem\""))
          } else {
            Left(s"path:$certPath not found")
          }
        case None =>
          Left("rootCA.pem not found")
      }
      Future.successful(result)
    }

  def startServer(port: Int) = {
    val routes = PekkoHttpServerInterpreter().toRoute(List(wsHandler, viewerEndpoint, downloadCA))
    val bindAndCheck = Http()
      .newServerAt("0.0.0.0", port)
      .bindFlow(routes)
    bindAndCheck
  }
}

class ConnectionManager extends Actor {
  private val connections = scala.collection.mutable.Map[String, ActorRef]()
  override def receive: Receive = {
    case (key: String, data: String) =>
      // connections.get(key).foreach(ref => data.trim.split('\n').foreach(ref ! _))
      connections.get(key).foreach(_ ! data)
    case (key: String, ref: ActorRef) => connections.addOne(key -> ref)
    case key: String                  =>
      println(s"websocket close $key")
      connections.remove(key).foreach(_ ! PoisonPill)
  }
}
