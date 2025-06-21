package com.timzaak.proxy

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.util.ByteString

import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

class PekkoReverseProxy(jksConf: Option[JKSConf], output: PekkoHttpRequestFormat)(using actorSystem: ActorSystem) {

  import actorSystem.dispatcher
  
  private val router = extractRequest { request =>
    val proxiedRequest = request
      .withHeaders(
        request.headers.filterNot(_.is(org.apache.pekko.http.scaladsl.model.headers.`Accept-Encoding`.lowercaseName))
      )
      .withEntity(
        request.entity.transformDataBytes(Flow[ByteString].alsoTo(Sink.foreach(v => println(v.utf8String))))
      )

    val responseFuture: Future[HttpResponse] =
      Http().singleRequest(proxiedRequest)

    onComplete(responseFuture) {
      case Success(res) => {
        complete(
          res.withEntity(entity =
            res.entity.transformDataBytes(Flow[ByteString].alsoTo(Sink.foreach(v => println(v.utf8String))))
          )
        )
      }
      case Failure(ex) => complete(HttpResponse(StatusCodes.BadGateway, entity = s"Proxy error: ${ex.getMessage}"))
    }

  }

  def startServer() = {
    // Security.addProvider(new BouncyCastleProvider())
    val server = jksConf match {
      case Some(conf) =>
        Http().newServerAt("0.0.0.0", 443).enableHttps(ConnectionContext.httpsServer(SSLContextProvider.fromJKS(conf)))
      case None =>
        Http().newServerAt("0.0.0.0", 80)
    }
    server.bind(router)
  }
}
