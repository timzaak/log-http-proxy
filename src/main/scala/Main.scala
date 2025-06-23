import com.timzaak.proxy.*
import mainargs.{ParserForMethods, arg, main}
import org.apache.pekko.actor.ActorSystem
import sttp.tapir.model.ServerRequest

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

object Main {

  @main
  def argsRun(
    @arg(doc = "dns, format like 192.168.0.1:www.example.com") dns: List[String],
    @arg(doc = "jks file path") jksPath: Option[String],
    @arg(doc = "jks password") jksPassword: Option[String],
    @arg(doc = "example: 1.1.1.1,8.8.8.8") resolver: Option[String],
    @arg(doc = "websocket port, output log via websocket, if not set, output log to cmd") websocketPort: Option[Int],
  ): Unit = {
    var config = AppConfig.load()

    val dnsPairs = dns.map { d =>
      val Array(ip, domain) = d.split(':')
      domain -> ip
    }
    val jksConf = (jksPath, jksPassword) match {
      case (Some(path), Some(password)) => Some(JKSConf(path, password))
      case _ => None
    }
    config = config
      .copy(
        dns = dnsPairs.map((domain,ip) => DNS(ip= ip ,domain = domain)),
        resolver = resolver.map(_.split(',').toList).getOrElse(config.resolver),
        jks = jksConf.orElse(config.jks),
        viewerPort = websocketPort.orElse(config.viewerPort),
      )

    if(config.resolver.nonEmpty) {
      CustomDnsResolver.setResolver(config.resolver)
    }

    config.dns.foreach(d => CustomDnsResolver.addMapping(d.domain,d.ip))

    given actorSystem: ActorSystem = ActorSystem()
    import actorSystem.dispatcher

    val func = config.viewerPort match {
      case Some(port) =>
        val logWebViewer = LogWebViewer(config.selfSignedCert)
        logWebViewer.startServer(port) onComplete {
          case Failure(exception) =>
            println(s"websocket server open error: $exception")
            sys.exit(-1)
          case Success(v) =>
            println(s"websocket server(${v.localAddress.getPort}) open")
            v.whenTerminated onComplete {
              case Failure(exception) =>
                println(s"websocket server close error: $exception")
                sys.exit(-1)
              case Success(v) =>
                println(s"websocket server close")
            }
        }
        logWebViewer.call
      case _ => (_: Any, data: String) => println(data)
    }

    val proxy = PekkoReverseProxy(config.jks, PekkoHttpRequestFormat(func))
    import actorSystem.dispatcher

    val bindAndCheck = proxy.startServer()
    Await.result(
      bindAndCheck.flatMap { v =>
        val changedHosts = dnsPairs.map((domain, _) => s"127.0.0.1 $domain").mkString("\n")
        val buf = StringBuffer("proxy server is up, listening 443 port.\n")
        if (changedHosts.nonEmpty) {
          buf.append(s"additional host config:\n$changedHosts\n")
        }
        if (resolver.nonEmpty) {
          buf.append(s"dns resolver:\n${resolver.get}\n")
        } else {
          buf.append(s"dns resolver:\nSystem DNS Resolver\n")
        }
        buf.append(
          s"Attention: request header Accept-Encoding would override to gzip, deflate (pekko does not support brotli)\n"
        )
        println(buf.toString)
        v.whenTerminated
      },
      Duration.Inf
    )
  }

  def main(args: Array[String]): Unit = {
    ParserForMethods(this).runOrExit(args)
  }
}
