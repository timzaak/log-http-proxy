package com.timzaak.practise

import munit.FunSuite
import sttp.client4.*

import java.net.InetAddress

class HttpsSuite extends FunSuite {
  test("simple") {
    val domain = ""
    val url ="https://"
    val result = InetAddress.getAllByName(domain)
    result.foreach(println)

    
    val backend = DefaultSyncBackend()
    val req = basicRequest.get(uri"$url")
      .response(asString).send(backend)
    println(req.body)

  }

}
