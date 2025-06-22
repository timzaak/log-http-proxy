package com.timzaak.proxy

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.{HttpEntity, HttpMethods, HttpRequest, HttpResponse}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Flow, Framing, Sink}
import org.apache.pekko.util.ByteString

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{ExecutionContext, Future}



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
          resp.entity.transformDataBytes(
            Flow[ByteString].alsoTo(
              Sink
                .foreach[ByteString](v => buf.append(v.utf8String))
                .mapMaterializedValue(_.onComplete(_ => {
                  buf.append(s"[response body can not parser]\n")
                  output()
                }))
            )
          )
        )
      case _ =>
        resp.withEntity(
          resp.entity.transformDataBytes(
            Flow[ByteString].alsoTo(
              Sink.ignore.mapMaterializedValue(_.onComplete(_ => {
                buf.append(s"[response body can not parser]\n")
                output()
              }))
            )
          )
        )
    }
  }

  private def output(): Unit = {
    val now = LocalDateTime.now()
    val duration = java.time.Duration.between(startTime, now)
    log(serverRequest, buf.toString.replaceFirst("##TIME##", s"${duration.toMillis}ms"))
  }


  private def parseMultipartStreamed(boundary:String, buf: StringBuffer)(using ec: ExecutionContext, mat: Materializer) = {
    val delimiter = s"--$boundary"
    val closeDelimiter = s"--$boundary--"

    def splitHeaderAndBody(part: ByteString): (ByteString, ByteString, Int) = {
      val sep = ByteString("\r\n\r\n")
      val idx = part.indexOfSlice(sep)
      if (idx >= 0) {
        val (headers, rest) = part.splitAt(idx)
        (headers, rest.drop(sep.length), idx)
      } else {
        (ByteString.empty, part, idx + sep.length)
      }
    }
    Flow[ByteString]
      .via(Framing.delimiter(ByteString("\r\n"), maximumFrameLength = 8192, allowTruncation = true))
      .drop(1)
      .mapAsync(1) { part =>
        val partCleaned = if (part.startsWith(ByteString("\r\n"))) {
          buf.append("\r\n")
          part.drop(2)
        } else {
          part
        }

        // 拆解 header 和 body
        val (headerBytes, bodyBytes, idx) = splitHeaderAndBody(partCleaned)

        val headersStr = headerBytes.utf8String
        val isFilePart = headersStr.contains("filename=")

        if (!isFilePart) {
          buf.append(partCleaned.utf8String)
        } else {
          buf.append(partCleaned.take(idx).utf8String)
          buf.append("[file body, skip log]\n")
        }
        Future.successful(())
      }


  }


}

class PekkoHttpRequestFormat(
  log: (HttpRequest, String) => Unit = (_, body) => println(body),
)(using ec: ActorSystem) {

  var index: AtomicLong = AtomicLong(0)

  def beginRecord(req: HttpRequest): Record2 =
    Record2(index.incrementAndGet(), req, log)(using ec)
}
