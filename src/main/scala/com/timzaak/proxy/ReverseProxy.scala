package com.timzaak.proxy

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.headers.*
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.*
import sttp.client4.pekkohttp.PekkoHttpBackend
import sttp.model.{HeaderNames, StatusCode}

import scala.concurrent.Future
import scala.util.{Failure, Success}


class ReverseProxy(jksConf: Option[JKSConf], output: HttpRequestFormat)(using actorSystem: ActorSystem) {

  import actorSystem.dispatcher

  // STTP backend for making outgoing requests
  private val backend: GenericBackend[Future, PekkoStreams] = PekkoHttpBackend.usingActorSystem(actorSystem)

  // Base STTP request template
  private val sttpProxyReqTemplate = basicRequest // .disableAutoDecompression if specific handling is needed

  // Pekko HTTP route definition
  val route: Route =
    extractRequest { pekkoRequest =>
      val record = output.beginRecord(pekkoRequest) // Logs request headers and non-streamed body parts

      // Prepare the outgoing STTP request
      // Headers to filter from the incoming Pekko request before forwarding
      val headersToFilterOut = Set(
        HeaderNames.AcceptEncoding.toLowerCase, // STTP usually handles this
        HeaderNames.RemoteAddress.toLowerCase,
        "timeout-access", // Pekko HTTP specific
        "pekko-http-user-agent" // Pekko HTTP specific
      )

      val filteredPekkoHeaders = pekkoRequest.headers.filterNot(h => headersToFilterOut.contains(h.lowercaseName()))

      // Convert Pekko HTTP headers to STTP headers
      val sttpHeaders = filteredPekkoHeaders.map(h => sttp.model.Header(h.name(), h.value()))

      val outgoingSttpRequest = sttpProxyReqTemplate
        .headers(sttpHeaders*)
        .method(
          sttp.model.Method(pekkoRequest.method.value), // Convert Pekko method to STTP method
          sttp.model.Uri(pekkoRequest.uri.toString) // Convert Pekko URI to STTP URI
        )
        .streamBody(PekkoStreams)(record.getProcessedRequestBody(pekkoRequest.entity.dataBytes)) // Use processed body for logging
        .response(asStreamAlwaysUnsafe(PekkoStreams)) // Expect a stream as response

      // Execute the STTP request
      val responseFuture: Future[Response[Source[ByteString, Any]]] = outgoingSttpRequest.send(backend)

      // Handle the STTP response and convert it to a Pekko HTTP response
      onComplete(responseFuture) {
        case Success(sttpResponse) =>
          // Filter headers from STTP response before sending back to client
          val responseHeadersToFilterOut = Set(
            HeaderNames.ContentEncoding.toLowerCase, // Client should handle this based on raw stream
            HeaderNames.TransferEncoding.toLowerCase // Pekko HTTP handles chunking
          )
          val pekkoResponseHeaders = sttpResponse.headers
            .filterNot(h => responseHeadersToFilterOut.contains(h.name.toLowerCase))
            .map(sttpHeader => RawHeader(sttpHeader.name, sttpHeader.value): org.apache.pekko.http.scaladsl.model.HttpHeader)

          val responseBodyStream = record.logResponse(sttpResponse, sttpResponse.body) // Log & get (potentially decoded) stream

          complete(
            HttpResponse(
              status = StatusCode.intToStatusCode(sttpResponse.code.code),
              headers = pekkoResponseHeaders.toVector,
              entity = org.apache.pekko.http.scaladsl.model.HttpEntity(
                sttpResponse.contentType.getOrElse(ContentTypes.`application/octet-stream`).toString, // Use STTP content type or default
                responseBodyStream
              )
            )
          )
        case Failure(exception) =>
          actorSystem.log.error(exception, s"Proxy request failed for ${pekkoRequest.uri}")
          // It's important to call output() on the record if an error happens before response logging
          record.logResponse( // Simulate a basic error response for logging purposes
            Response("Internal Server Error".getBytes(), StatusCode.InternalServerError, "", Nil, Nil, RequestMetadata(sttp.model.Method.GET, sttp.model.Uri("http://localhost/error"), Nil)),
            Source.empty
          )
          complete(StatusCodes.InternalServerError, s"Request failed: ${exception.getMessage}")
      }
    }

  def startServer(): Future[Http.ServerBinding] = {
    val serverBuilder = Http()
      .newServerAt("0.0.0.0", jksConf.fold(80)(_ => 443))

    val serverWithHttpsMaybe = jksConf match {
      case Some(conf) =>
        serverBuilder.enableHttps(org.apache.pekko.http.scaladsl.ConnectionContext.httpsServer(SSLContextProvider.fromJKS(conf)))
      case None => serverBuilder
    }
    serverWithHttpsMaybe.bind(route)
  }
}
