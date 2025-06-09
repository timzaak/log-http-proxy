package com.timzaak.proxy

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.coding.Coders
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.Response
import sttp.model.{ Header, MediaType, Method }
import sttp.tapir.model.ServerRequest

import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong

private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

class Record(
  id: Long,
  serverRequest: ServerRequest,
  log: (ServerRequest, String) => Unit,
)(using
  actorSystem: ActorSystem
) {
  import actorSystem.dispatcher

  private val startTime: LocalDateTime = LocalDateTime.now()
  private val buf = StringBuffer()

  def requestBody(req: ServerRequest, body: PekkoStreams.BinaryStream): PekkoStreams.BinaryStream = {
    val headerDesc = req.headers
      .collect { case header =>
        s"  ${header.name}: ${header.value}"
      }
      .mkString("\n")
    buf.append(s"${startTime.format(dateTimeFormatter)} [$id] ${req.method} ${req.uri} ##TIME##\n")
    if (headerDesc.nonEmpty) {
      buf.append(headerDesc)
      buf.append("\nBody:\n")
    } else {
      buf.append("Body:\n")
    }

    req.method match {
      case Method.POST | Method.PUT | Method.PATCH if !req.contentLength.contains(0) =>
        req.contentTypeParsed match {
          case Some(v) if v.isText || v.isApplication || v.isMessage || v.isMultipart =>
            body.alsoTo(
              Sink
                .foreach[ByteString](v => buf.append(v.utf8String))
                .mapMaterializedValue(_.onComplete(_ => buf.append("\n")))
            )
          case _ =>
            body.alsoTo(
              Sink.ignore.mapMaterializedValue(_.onComplete(_ => buf.append("[request body can not parser]\n")))
            )
        }
      case _ =>
        body
    }
  }

  def responseBody(resp: Response[?], body: PekkoStreams.BinaryStream)(using
    org.apache.pekko.stream.Materializer
  ): PekkoStreams.BinaryStream = {
    val headerDesc = resp.headers
      .collect { case header =>
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
    val newBody = resp.contentType.flatMap(MediaType.parse(_).toOption) match {
      case Some(v) if v.isText || v.isApplication || v.isMessage || v.isMultipart =>
        resp
          .header("content-encoding")
          .flatMap(encoding => List(Coders.Gzip, Coders.Deflate).find(_.encoding.value == encoding)) match {
          case Some(decompressor) =>
            body.alsoTo(
              Flow[ByteString]
                .fold(ByteString.empty)(_ ++ _)
                .mapAsync(1)(v =>
                  decompressor
                    .decode(v)
                    .map(v => buf.append(v.utf8String))
                )
                .to(Sink.ignore.mapMaterializedValue(_.onComplete { _ =>
                  output()
                }))
            )
          case None =>
            body.alsoTo(
              Sink
                .foreach[ByteString](v => buf.append(v.utf8String))
                .mapMaterializedValue(_.onComplete(_ => buf.append("\n")))
            )
        }
      case _ =>
        body.alsoTo(Sink.ignore.mapMaterializedValue(_.onComplete { _ =>
          buf.append(s"[response body can not parser]\n")
          output()
        }))
    }
    newBody
  }
  private def output(): Unit = {
    val now = LocalDateTime.now()
    val duration = java.time.Duration.between(startTime, now)
    log(serverRequest, buf.toString.replaceFirst("##TIME##", s"${duration.toMillis}ms"))
  }

}

class HttpRequestFormat(
  log: (ServerRequest, String) => Unit = (_, body) => println(body),
)(using ec: ActorSystem) {

  var index: AtomicLong = AtomicLong(0)

  def beginRecord(req: ServerRequest): Record =
    Record(index.incrementAndGet(), req, log)(using ec)
}
