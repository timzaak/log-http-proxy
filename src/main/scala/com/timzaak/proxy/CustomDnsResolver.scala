package com.timzaak.proxy

import com.timzaak.proxy.CustomDnsResolver.customMappings
import org.xbill.DNS.{AAAARecord, ARecord, ExtendedResolver, Lookup, ResolverConfig}

import java.net.InetAddress
import java.net.spi.{InetAddressResolver, InetAddressResolverProvider}
import java.util
import java.util.concurrent.ConcurrentHashMap
import java.util.stream
import scala.jdk.CollectionConverters.*

object CustomDnsResolver {
  private val customMappings = new ConcurrentHashMap[String, String]()
  private var resolver:Option[ExtendedResolver] = None

  def addMapping(host: String, ip: String): Unit = {
    customMappings.put(host, ip)
  }

  def removeMapping(host: String): Unit = {
    customMappings.remove(host)
  }
  def getMapping(host: String): Option[String] = {
    Option(customMappings.get(host))
  }

  def setResolver(resolvers: String) = {
    ResolverConfig.refresh()
    val r = ExtendedResolver(resolvers.split(','))
    r.setIgnoreTruncation(true)
    Lookup.setDefaultResolver(r)
    resolver = Some(r)

  }

}

class CustomDnsResolver extends InetAddressResolverProvider {

  private val resolver = new InetAddressResolver {
    override def lookupByName(host: String, lookupPolicy: InetAddressResolver.LookupPolicy): stream.Stream[InetAddress] = {
      Option(customMappings.get(host)) match {
        case Some(ip) =>
          Seq(InetAddress.getByAddress(host, InetAddress.getByName(ip).getAddress)).asJava.stream()
        case None =>
          CustomDnsResolver.resolver match {
            case None => util.Arrays.stream(InetAddress.getAllByName(host))
            case Some(resolver) =>
              val lookup = Lookup(host)
              lookup.setHostsFileParser(null)
              Option(lookup.run()) match {
                case None => stream.Stream.empty()
                case Some(list) => util.Arrays.stream(list.collect {
                  case r: ARecord =>  r.getAddress
                  case r: AAAARecord =>  r.getAddress
                })
              }
          }
      }
    }

    override def lookupByAddress(addr: Array[Byte]): String = {
      InetAddress.getByAddress(addr).getHostName
    }
  }

  override def get(configuration: InetAddressResolverProvider.Configuration): InetAddressResolver = {
    resolver
  }

  override def name(): String = "proxy-dns-resolver"

}
