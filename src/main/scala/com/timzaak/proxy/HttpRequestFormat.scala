package com.timzaak.proxy

import org.apache.pekko.stream.scaladsl.Sink
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.Response
import sttp.model.{Header, MediaType}
import sttp.tapir.model.ServerRequest

import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

class HttpRequestFormat(
                         requestHeaderFilter: Header => Boolean = _ => true,
                         responseHeaderFilter: Header => Boolean = _ => true,
                       ) {

  var  index:Long = 0
  val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def logRequestHeader(req:ServerRequest) = {
    val now = LocalDateTime.now()
    index+=1
    val id = index
    println(s"[${id}] ${now.format(dateTimeFormatter)} ")
    println("Request Headers:")
    req.headers.collect {
      case header if requestHeaderFilter(header) =>
        println(s"  ${header.name}: ${header.value}")
    }

    println("Body:")
    id -> now
  }


  def logRequestBody(req:ServerRequest, body:PekkoStreams.BinaryStream) = {
    req.contentTypeParsed match {
      case Some(v) if v.isText || v.isApplication || v.isMessage || v.isMultipart =>
        body.alsoTo(Sink.foreach(v => print(v.utf8String))).mapMaterializedValue( _ => println(""))
      case _ =>
        body.alsoTo(Sink.ignore.mapMaterializedValue(_ => println("request body can not parser")))
    }
  }

  def logResponseHeader(resp:Response[?]) = {
    println("Response Headers:")
    resp.headers.collect {
      case header if responseHeaderFilter(header) =>
        println(s"  ${header.name}: ${header.value}")
    }
    println("Body:")
    resp.headers
  }

  def logResponseBody(id:Long, start:LocalDateTime, resp:Response[?], body:PekkoStreams.BinaryStream) = {

    val newBody = resp.contentType.flatMap(MediaType.parse(_).toOption) match {
      case Some(v) if v.isText || v.isApplication || v.isMessage || v.isMultipart =>
        body.alsoTo(Sink.foreach(v => print(v.utf8String))).mapMaterializedValue { _ =>
          val now = LocalDateTime.now()
          // 获取 now 和 start 的差值，并转换为毫秒
          val duration = java.time.Duration.between(start, now)
          println(s"\n[$id] cost time: ${duration.toMillis}ms")
        }
      case _ =>
        body.alsoTo(Sink.ignore.mapMaterializedValue{_ =>
          val now = LocalDateTime.now()
          // 获取 now 和 start 的差值，并转换为毫秒
          val duration = java.time.Duration.between(start, now)
          println("response body can not parser")
          println(s"\n[$id] cost time: ${duration.toMillis}ms")
        })
    }
    newBody
  }

}
