import com.timzaak.proxy.{CustomDnsResolver, ReverseProxy}
import mainargs.{ParserForMethods, arg, main}

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object Main {

  @main
  def argsRun(
               @arg(doc = "dns, format like 192.168.0.1:www.example.com") dns:List[String],
               @arg(doc = "jks file path") jksPath: String,
               @arg(doc = "jks password") jksPassword: String,
               @arg(doc ="example: 1.1.1.1,8.8.8.8") resolver: Option[String],
             ): Unit = {
    val dnsPairs = dns.map { d =>
      val Array(ip, domain) = d.split(':')
      domain -> ip
    }

    resolver.foreach(CustomDnsResolver.setResolver)

    dnsPairs.foreach(CustomDnsResolver.addMapping)

    val proxy = ReverseProxy(jksPath, jksPassword)
    import proxy.actorSystem.dispatcher

    val bindAndCheck = proxy.startServer()
    Await.result(bindAndCheck.flatMap{v=>
      val changedHosts = dnsPairs.map((domain, _) => s"127.0.0.1 $domain").mkString("\n")
      val buf = StringBuffer("proxy server is up, listening 443 port.\n")
      if(changedHosts.nonEmpty) {
        buf.append(s"additional host config:\n$changedHosts\n")
      }
      if(resolver.nonEmpty) {
        buf.append(s"dns resolver:\n${resolver.get}\n")
      } else {
        buf.append(s"dns resolver:\nSystem DNS Resolver\n")
      }
      println(buf.toString)
      v.whenTerminated
    }, Duration.Inf)
  }

  def main(args: Array[String]): Unit = {
    ParserForMethods(this).runOrExit(args)
  }
}
