package com.timzaak.proxy

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse}
import org.apache.pekko.stream.scaladsl.{Flow, Sink}
import org.apache.pekko.util.ByteString
/*
import org.apache.pekko.http.scaladsl.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.stream.scaladsl.*
import org.apache.pekko.util.ByteString
 */
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong

case class Record2(
  id: Long,
  serverRequest: HttpRequest,
  log: (HttpRequest, String) => Unit,
)(using
  actorSystem: ActorSystem
) {
  import actorSystem.dispatcher

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
  private val startTime: LocalDateTime = LocalDateTime.now()
  private val buf = StringBuffer()

  def requestBody(req: HttpRequest): HttpRequest = {
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
      case HttpMethods.POST | HttpMethods.PUT | HttpMethods.PATCH if req.entity.contentLengthOption.getOrElse(0L) > 0 =>
        req.entity.contentType.mediaType match {
          case mediaType
            if mediaType.isText || mediaType.isApplication || mediaType.isMessage || mediaType.isMultipart =>

            req.withEntity(
              req.entity.transformDataBytes(
                Flow[ByteString].alsoTo(
                  Sink
                    .foreach[ByteString](v => buf.append(v.utf8String))
                    .mapMaterializedValue(_.onComplete(_ => buf.append("\n")))
                )
              )
            )

          case _ =>
            req.withEntity(
              req.entity.transformDataBytes(
                Flow[ByteString].alsoTo(
                  Sink.ignore.mapMaterializedValue(_.onComplete(_ => buf.append("[request body can not parser]\n")))
                )
              )
            )
        }
      case _ =>
        req
    }
  }

  def responseBody(resp: HttpResponse): HttpResponse = {
    val headerDesc = resp.headers
      .collect { case header =>
        s"  ${header.name}: ${header.value}"
      }
      .mkString("\n")
    buf.append(s"Response: ${resp.status}\n")
    if (headerDesc.nonEmpty) {
      buf.append(headerDesc)
      buf.append("\nBody:\n")
    } else {
      buf.append("Body:\n")
    }

    resp.entity.contentType.mediaType match {
      case mediaType if mediaType.isText || mediaType.isApplication || mediaType.isMessage || mediaType.isMultipart =>
        resp.withEntity(
          resp.entity.transformDataBytes(Flow[ByteString].alsoTo(Sink.foreach[ByteString](v => buf.append(v.utf8String))))

        )
      case _ =>
        resp.withEntity(
          resp.entity.transformDataBytes(Flow[ByteString].alsoTo(
            Sink.ignore.mapMaterializedValue(_.onComplete(_ => {
              buf.append(s"[response body can not parser]\n")
              output()
            }))
          ))
        )
    }
  }

  private def output(): Unit = {
    val now = LocalDateTime.now()
    val duration = java.time.Duration.between(startTime, now)
    log(serverRequest, buf.toString.replaceFirst("##TIME##", s"${duration.toMillis}ms"))
  }

}

class PekkoHttpRequestFormat(
  log: (HttpRequest, String) => Unit = (_, body) => println(body),
)(using ec: ActorSystem) {

  var index: AtomicLong = AtomicLong(0)

  def beginRecord(req: HttpRequest): Record2 =
    Record2(index.incrementAndGet(), req, log)(using ec)
}
