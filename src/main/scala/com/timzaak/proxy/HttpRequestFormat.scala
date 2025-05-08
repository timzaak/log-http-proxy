package com.timzaak.proxy

import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.Response
import sttp.model.{ Header, MediaType, Method }
import sttp.tapir.model.ServerRequest

import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext

private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

class Record(id: Long, requestHeaderFilter: Header => Boolean, responseHeaderFilter: Header => Boolean)(using
  ExecutionContext
) {
  private val startTime: LocalDateTime = LocalDateTime.now()
  private val buf = StringBuffer()

  def requestBody(req: ServerRequest, body: PekkoStreams.BinaryStream): PekkoStreams.BinaryStream = {
    val headerDesc = req.headers
      .collect {
        case header if requestHeaderFilter(header) =>
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

  def responseBody(resp: Response[?], body: PekkoStreams.BinaryStream): PekkoStreams.BinaryStream = {
    val headerDesc = resp.headers
      .collect {
        case header if requestHeaderFilter(header) =>
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
        body.alsoTo(Sink.foreach[ByteString](v => buf.append(v.utf8String)).mapMaterializedValue {
          _.onComplete { _ =>
            output()
          }
        })
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
    // 获取 now 和 start 的差值，并转换为毫秒
    val duration = java.time.Duration.between(startTime, now)
    println(buf.toString.replaceFirst("##TIME##", s"${duration.toMillis}ms"))
  }

}

class HttpRequestFormat(
  requestHeaderFilter: Header => Boolean = _ => true,
  responseHeaderFilter: Header => Boolean = _ => true,
)(using ec: ExecutionContext) {

  var index: AtomicLong = AtomicLong(0)

  def beginRecord(): Record =
    Record(index.incrementAndGet(), requestHeaderFilter, responseHeaderFilter)(using ec)
}
