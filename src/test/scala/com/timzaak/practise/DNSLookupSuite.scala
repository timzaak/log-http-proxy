package com.timzaak.practise

import munit.FunSuite
import org.xbill.DNS.*
import org.xbill.DNS.lookup.LookupSession
import org.xbill.DNS.lookup.LookupSession.LookupSessionBuilder

import scala.jdk.CollectionConverters.*

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
  test("lookup session") {
    val domain = "www.google.com"
    val resolver = ExtendedResolver(Array("8.8.8.8", "1.1.1.1"))



//    val lookup = LookupSession.defaultBuilder().resolver(resolver).build()
//    val result = lookup.lookupAsync(Name.fromString(domain), Type.A, DClass.IN)
//    val r = result.toCompletableFuture.thenApply(lookupResult => {
//      val records = lookupResult.getRecords
//      if (records == null) {
//        java.util.stream.Stream.empty()
//      } else {
//        records.asScala.collect {
//          case r: ARecord => r.getAddress
//          case r: AAAARecord => r.getAddress
//        }.asJava.stream()
//      })
//    println(r)
//    println(r)
  }

}
