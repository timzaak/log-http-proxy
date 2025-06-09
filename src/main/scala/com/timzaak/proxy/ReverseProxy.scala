package com.timzaak.proxy

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.*
import org.apache.pekko.http.scaladsl.server.Route
import sttp.capabilities.pekko.PekkoStreams
import sttp.tapir.*
import sttp.client4.*
import sttp.client4.pekkohttp.PekkoHttpBackend
import sttp.model.HeaderNames
import sttp.tapir.server.pekkohttp.PekkoHttpServerInterpreter

class ReverseProxy(jksConf: Option[JKSConf], output: HttpRequestFormat)(using actorSystem: ActorSystem) {

  import actorSystem.dispatcher

  private val backend = PekkoHttpBackend.usingActorSystem(actorSystem)

  private val proxyReq = basicRequest.disableAutoDecompression

  private val proxyEndpoint = endpoint
    .in(extractFromRequest(identity))
    .in(streamBinaryBody(PekkoStreams)(CodecFormat.OctetStream()))
    .out(headers)
    .out(sttp.tapir.streamBinaryBody(PekkoStreams)(CodecFormat.OctetStream()))
    .serverLogicSuccess { (request, body) =>
      val record = output.beginRecord(request)

      val result = proxyReq
        .headers(request.headers.filterNot(_.name.equalsIgnoreCase(HeaderNames.AcceptEncoding))*)
        .method(request.method, request.uri)
        .streamBody(PekkoStreams)(record.requestBody(request, body))
        .response(asStreamAlwaysUnsafe(PekkoStreams))
        .send(backend)
      result.map { result =>
        result.headers.toList /*.filterNot(_.name.equalsIgnoreCase(HeaderNames.ContentEncoding))*/ -> record
          .responseBody(result, result.body)
      }
    }

  private val streamingRoute: Route = PekkoHttpServerInterpreter().toRoute(proxyEndpoint)

  /*
  private def loadPemFiles(certPath: String, keyPath: String): (PrivateKey, Array[X509Certificate]) = {
    // 加载私钥
    val keyReader = new FileReader(keyPath)
    val pemKeyParser = new PEMParser(keyReader)
    val keyPair = pemKeyParser.readObject() match {
      case keyPair: PEMKeyPair => keyPair.getPrivateKeyInfo
      case keyInfo: PrivateKeyInfo => keyInfo
      case _ => throw new IllegalArgumentException("Unsupported private key format")
    }
    pemKeyParser.close()

    val keyConverter = new JcaPEMKeyConverter()
    val privateKey = keyConverter.getPrivateKey(keyPair)

    // 加载证书链
    val certReader = new FileReader(certPath)
    val pemCertParser = new PEMParser(certReader)
    val certHolder = pemCertParser.readObject().asInstanceOf[X509CertificateHolder]
    pemCertParser.close()

    val certConverter = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
      .setProvider("BC")
    val certificate = certConverter.getCertificate(certHolder)

    (privateKey, Array(certificate))
  }

  private def createSSLContext(privateKey: PrivateKey,
                                 certChain: Array[X509Certificate]) = {
    // 1. 创建临时 KeyStore
    val password = "changeit".toCharArray
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(null, password)
    // 2. 将密钥和证书存入 KeyStore
    keyStore.setKeyEntry(
      "server",
      privateKey,
      password,
      certChain.toArray
    )

    // 3. 初始化 KeyManagerFactory
    val keyManagerFactory = javax.net.ssl.KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(keyStore, password)

    // 4. 初始化 TrustManagerFactory (使用相同的 KeyStore)
    val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")
    trustManagerFactory.init(keyStore)

    // 5. 创建 SSLContext
    val sslContext = SSLContext.getInstance("TLS")

    sslContext.init(
      keyManagerFactory.getKeyManagers,
      trustManagerFactory.getTrustManagers,
      new SecureRandom
    )
    ConnectionContext.httpsServer(sslContext)
  }
   */

  def startServer() = {
    // Security.addProvider(new BouncyCastleProvider())
    var bindAndCheck = Http()
      .newServerAt("0.0.0.0", jksConf.fold(80)(_ => 443))
    for (conf <- jksConf) {
      bindAndCheck = bindAndCheck.enableHttps(ConnectionContext.httpsServer(SSLContextProvider.fromJKS(conf)))
    }
    bindAndCheck.bindFlow(streamingRoute)
  }
}
