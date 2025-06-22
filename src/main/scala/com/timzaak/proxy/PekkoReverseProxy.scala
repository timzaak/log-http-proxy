package com.timzaak.proxy

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.model.*

import scala.concurrent.Future
import scala.util.{ Failure, Success }

class PekkoReverseProxy(jksConf: Option[JKSConf], output: PekkoHttpRequestFormat)(using actorSystem: ActorSystem) {

  import actorSystem.dispatcher

  private val router =
    extractRequest { request =>
      val record = output.beginRecord(request)
      val proxiedRequest = record
        .requestBody(request)
        .withHeaders(
          request.headers.filterNot(_.is(org.apache.pekko.http.scaladsl.model.headers.`Accept-Encoding`.lowercaseName))
        )

      val responseFuture: Future[HttpResponse] =
        Http().singleRequest(proxiedRequest)

      onComplete(responseFuture.map { response =>
        record.responseBody(response)
      }) {
        case Success(res) =>
          complete(
            res /*.withHeaders(res.headers.filterNot(_.is(org.apache.pekko.http.scaladsl.model.headers.`Content-Encoding`.lowercaseName)))*/
          )
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
