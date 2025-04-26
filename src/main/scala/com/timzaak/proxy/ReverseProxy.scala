package com.timzaak.proxy

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.{HttpsConnectionContext, *}
import org.apache.pekko.http.scaladsl.server.Route
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.*
import sttp.client4.*
import sttp.client4.pekkohttp.PekkoHttpBackend
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

import scala.concurrent.Await
import scala.concurrent.duration.*

class ReverseProxy(targetIp:String) {

  val backend = PekkoHttpBackend()
  
  implicit val actorSystem: ActorSystem = ActorSystem()

  import actorSystem.dispatcher

  val _proxy = endpoint
    .in(extractFromRequest(identity))
    .in(streamBinaryBody(PekkoStreams)(CodecFormat.OctetStream()))
    .out(headers)
    .out(sttp.tapir.streamBinaryBody(PekkoStreams)(CodecFormat.OctetStream()))
    .serverLogicSuccess { (request, body) =>
      val newUri = request.uri.host("127.0.0.1")
      val result = basicRequest
        .headers(request.headers*)
        .method(request.method, newUri)
        .streamBody(PekkoStreams)(body)
        .response(asStreamAlwaysUnsafe(PekkoStreams))
        .send(backend)
      result.map(result => result.headers.toList -> result.body)
    }

  val streamingRoute: Route = PekkoHttpServerInterpreter().toRoute(_proxy)

  def startServer(): Unit = {
    
    val bindAndCheck = Http()
      .newServerAt("localhost", 9080)
      .enableHttps(???)
      .bindFlow(streamingRoute).flatMap(_.whenTerminated)

    Await.result(bindAndCheck, Duration.Inf)
    println("over")
  }

}
