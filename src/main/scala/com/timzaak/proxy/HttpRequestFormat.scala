package com.timzaak.proxy

import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.Response
import sttp.model.{Header, MediaType, Method}
import sttp.tapir.model.ServerRequest

import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.ExecutionContext

//TODO: multiple thread support
class HttpRequestFormat(
                         requestHeaderFilter: Header => Boolean = _ => true,
                         responseHeaderFilter: Header => Boolean = _ => true,
                       )(using ExecutionContext) {

  var index:AtomicLong = AtomicLong(0)
  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def logRequestHeader(req:ServerRequest): (Long, LocalDateTime) = {
    val now = LocalDateTime.now()
    val id = index.incrementAndGet()
    println(s"[${id}] ${now.format(dateTimeFormatter)} ${req.method} ${req.uri}")
    req.headers.collect {
      case header if requestHeaderFilter(header) =>
        println(s"  ${header.name}: ${header.value}")
    }
    id -> now
  }

  def logRequestBody(req:ServerRequest, body:PekkoStreams.BinaryStream): PekkoStreams.BinaryStream = {
    req.method match {
      case Method.POST | Method.PUT | Method.PATCH if !req.contentLength.contains(0) =>
        println("Body:")
        req.contentTypeParsed match {
          case Some(v) if v.isText || v.isApplication || v.isMessage || v.isMultipart =>
            body.alsoTo(Sink.foreach[ByteString](v => print(v.utf8String)).mapMaterializedValue( _.onComplete(_ => println(""))))
          case _ =>
            body.alsoTo(Sink.ignore.mapMaterializedValue(_.onComplete(_=> println("[request body can not parser]"))))
        }
      case _ =>
        body
    }
  }

  def logResponseHeader(resp:Response[?]): Seq[Header] = {
    println(s"Response: ${resp.code.code}")
    resp.headers.collect {
      case header if responseHeaderFilter(header) =>
        println(s"  ${header.name}: ${header.value}")
    }
    resp.headers
  }

  def logResponseBody(id:Long, start:LocalDateTime, resp:Response[?], body:PekkoStreams.BinaryStream): PekkoStreams.BinaryStream = {
    println("Body:")
    val newBody = resp.contentType.flatMap(MediaType.parse(_).toOption) match {
      case Some(v) if v.isText || v.isApplication || v.isMessage || v.isMultipart =>
        body.alsoTo(Sink.foreach[ByteString](v => print(v.utf8String)).mapMaterializedValue {
          _.onComplete{ _ =>
            val now = LocalDateTime.now()
            // 获取 now 和 start 的差值，并转换为毫秒
            val duration = java.time.Duration.between(start, now)
            println(s"\n[$id] cost time: ${duration.toMillis}ms")
          }
        })
      case _ =>
        body.alsoTo(Sink.ignore.mapMaterializedValue{_ =>
          val now = LocalDateTime.now()
          // 获取 now 和 start 的差值，并转换为毫秒
          val duration = java.time.Duration.between(start, now)
          println("[response body can not parser]")
          println(s"[$id] cost time: ${duration.toMillis}ms")
        })
    }
    newBody
  }

}
