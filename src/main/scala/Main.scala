import com.timzaak.proxy.{CustomDnsResolver, ReverseProxy}
import mainargs.{ParserForMethods, arg, main}

import scala.concurrent.Await
import scala.concurrent.duration.Duration


object Main {

  @main
  def argsRun(
               @arg(doc = "dns, format like 192.168.0.1:www.example.com") dns:List[String],
               @arg(doc = "public ssl path") public: String,
               @arg(doc = "private ssl path") `private`: String
             ): Unit = {
    val dnsPairs = dns.map { d =>
      val Array(ip, domain) = d.split(':')
      domain -> ip
    }

    if(dnsPairs.isEmpty) {
      println("dns is empty")
      sys.exit(-1)
    }

    dnsPairs.foreach(CustomDnsResolver.addMapping)

    val proxy = ReverseProxy(public, `private`)
    import proxy.actorSystem.dispatcher

    val bindAndCheck = proxy.startServer()
    Await.result(bindAndCheck.flatMap{v=>
      val changedHosts = dnsPairs.map((domain, _) => s"127.0.0.1 $domain").mkString("\n")

      println(
        s"""proxy server is up, listening 443 port.
          |please change hosts config:
          |$changedHosts
          |""".stripMargin)

      v.whenTerminated
    }, Duration.Inf)
  }

  def main(args: Array[String]): Unit = {
    ParserForMethods(this).runOrExit(args)
  }
}
