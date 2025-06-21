package com.timzaak.proxy

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers.HttpEncodings
import org.apache.pekko.http.scaladsl.coding.Coders
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.Response
// Import sttp.model.MediaType if it's still needed for response content type parsing,
// otherwise rely on Pekko's ContentType and MediaType
import sttp.model.{Header => SttpHeader, MediaType => SttpMediaType}


import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

class Record(
    id: Long,
    pekkoRequest: HttpRequest,
    log: (HttpRequest, String) => Unit,
)(using
    actorSystem: ActorSystem
) {
  import actorSystem.dispatcher
  given Materializer = Materializer(actorSystem)

  private val startTime: LocalDateTime = LocalDateTime.now()
  private val buf = StringBuffer()

  // Helper to determine if content type is text-based
  private def isTextBased(contentType: ContentType): Boolean = {
    val mediaType = contentType.mediaType
    mediaType.isApplication || mediaType.isText || mediaType.isMessage || mediaType.isMultipart || mediaType.mainType == "json" // common case for application/json
  }

  def logRequest(): Unit = {
    val headerDesc = pekkoRequest.headers
      .map { header =>
        s"  ${header.name()}: ${header.value()}"
      }
      .mkString("\n")
    buf.append(
      s"${startTime.format(dateTimeFormatter)} [$id] ${pekkoRequest.method.value} ${pekkoRequest.uri} ##TIME##\n"
    )
    if (headerDesc.nonEmpty) {
      buf.append(headerDesc)
      buf.append("\nBody:\n")
    } else {
      buf.append("Body:\n")
    }

    // Request body will be logged as it's streamed in getProcessedRequestBody
    // Only indicate here if it's expected to have a body or not for clarity in the log
    pekkoRequest.method match {
      case HttpMethods.POST | HttpMethods.PUT | HttpMethods.PATCH if pekkoRequest.entity.contentLengthOption.forall(_ > 0) =>
        if (!isTextBased(pekkoRequest.entity.contentType)) {
          buf.append("[request body is binary or non-text, will not be logged as string]\n")
        }
        // Actual body content is logged by getProcessedRequestBody by tapping into the stream
      case _ =>
        // For GET, DELETE, etc., or empty bodies, no specific body logging needed here
        buf.append("[no request body or method does not typically have a body]\n")
    }
  }

  // This method is called before sending the request to the backend,
  // so it returns the entity to be used in the outgoing sttp request.
  def getProcessedRequestBody(originalBody: Source[ByteString, Any]): Source[ByteString, Any] = {
    pekkoRequest.method match {
      case HttpMethods.POST | HttpMethods.PUT | HttpMethods.PATCH if pekkoRequest.entity.contentLengthOption.forall(_ > 0) =>
        if (isTextBased(pekkoRequest.entity.contentType)) {
          // Log the body as it passes through
          originalBody.alsoTo(
            Sink
              .foreach[ByteString](bs => buf.append(bs.utf8String))
              .mapMaterializedValue(_.onComplete(_ => buf.append("\n")))
          )
        } else {
          // For non-text, just pass through, already indicated it won't be logged in detail.
          originalBody
        }
      case _ =>
        originalBody // Pass through for GET, etc.
    }
  }


  def logResponse(resp: Response[?], body: PekkoStreams.BinaryStream): PekkoStreams.BinaryStream = {
    val headerDesc = resp.headers
      .map { header => // Assuming resp.headers are sttp.model.Header
        s"  ${header.name}: ${header.value}"
      }
      .mkString("\n")
    buf.append(s"Response: ${resp.code.code}\n")
    if (headerDesc.nonEmpty) {
      buf.append(headerDesc)
      buf.append("\nBody:\n")
    } else {
      buf.append("Body:\n")
    }

    // Try to parse content type from sttp Response
    val contentTypeOpt = resp.contentType.flatMap(SttpMediaType.parse(_).toOption)

    contentTypeOpt match {
      case Some(ct) if ct.isText || ct.isApplication || ct.isMessage || ct.isMultipart || ct.mainType == "json" =>
        // Check for content encoding and decode if necessary
        resp.header(Header.CONTENT_ENCODING) match {
          case Some(encodingValue) =>
            val decoder = encodingValue.toLowerCase match {
              case "gzip" => Coders.Gzip
              case "deflate" => Coders.Deflate
              case _ => Coders.NoCoding // Or handle as an error/unsupported
            }
            if (decoder != Coders.NoCoding) {
               body.via(decoder.decoderFlow).alsoTo(Sink.foreach[ByteString](bs => buf.append(bs.utf8String)).mapMaterializedValue {
                _.onComplete { _ => output() }
              })
            } else { // Unknown encoding or NoCoding
              body.alsoTo(Sink.foreach[ByteString](bs => buf.append(bs.utf8String)).mapMaterializedValue {
                _.onComplete { _ => output() }
              })
            }
          case None => // No content encoding
            body.alsoTo(Sink.foreach[ByteString](bs => buf.append(bs.utf8String)).mapMaterializedValue {
              _.onComplete { _ => output() }
            })
        }
      case _ => // Non-text or unknown content type
        body.alsoTo(Sink.ignore.mapMaterializedValue(_.onComplete { _ =>
          buf.append(s"[response body not logged or binary]\n")
          output()
        }))
    }
  }

  private def output(): Unit = {
    val now = LocalDateTime.now()
    val duration = java.time.Duration.between(startTime, now)
    log(pekkoRequest, buf.toString.replaceFirst("##TIME##", s"${duration.toMillis}ms"))
  }
}

class HttpRequestFormat(
    log: (HttpRequest, String) => Unit = (_, body) => println(body),
)(using ec: ActorSystem) {

  var index: AtomicLong = AtomicLong(0)

  def beginRecord(req: HttpRequest): Record = {
    val record = Record(index.incrementAndGet(), req, log)(using ec)
    record.logRequest() // Initial logging of request headers and potentially body if not streamed
    record
  }
}
