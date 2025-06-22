package com.timzaak.proxy
import sttp.client4.*
import munit.FunSuite
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.model.MediaType

import java.io.File
class HttpRequestSuite extends FunSuite {

  val backend = HttpClientSyncBackend()

  test("multipart form") {
    val parts = Seq(
      multipart("field1", "value1"),
      multipart("description", "this is a text field"),
      multipartFile("file", new File("abc.txt"))
        .fileName("abc.txt")
        .contentType(MediaType.TextPlain)
    )

    val request = basicRequest
      .post(uri"https://httpbin.org/post") // 用 httpbin.org 测试上传效果
      .multipartBody(parts)
    val response = request.send(backend)
    println(response.body)
  }
}
