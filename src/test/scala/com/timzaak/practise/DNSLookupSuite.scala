package com.timzaak.practise

import com.timzaak.proxy.CustomDnsResolver
import munit.FunSuite
import org.xbill.DNS.*

class DNSLookupSuite extends FunSuite {
  test("DNS lookup") {
    val domain = "www.google.com"
    val resolver = ExtendedResolver(Array("8.8.8.8", "1.1.1.1"))

    val lookup = Lookup(domain)

    lookup.setResolver(resolver)


    val recordsOpt = Option(lookup.run())
    for {
      records <- recordsOpt
      record <- records
    }{
      println(s"= $record")
    }

  }


  test("ip") {
    CustomDnsResolver.setResolver(List("114.114.114.114"))
    val z = Address.getAllByName("www.example.com")
    z.foreach(println)
  }


  
}
