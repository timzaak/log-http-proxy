import com.timzaak.proxy.{CustomDnsResolver, ReverseProxy}
import mainargs.{ParserForClass, ParserForMethods, arg, main}

import java.net.InetAddress

object Main {




  @main
  def argsRun(
               @arg(doc = "dns, format like 192.168.0.1:www.example.com") dns:List[String],
               @arg(doc = "public ssl path") public: String,
               @arg(doc = "private ssl path") `private`: String
             ) = {
    dns.foreach { d =>
      val Array(ip, domain) = d.split(':')
      CustomDnsResolver.addMapping(domain, ip)
    }
    ReverseProxy(public, `private`).startServer()
  }

  def main(args: Array[String]): Unit = {
    ParserForMethods(this).runOrExit(args)
  }
}
