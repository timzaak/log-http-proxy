package com.timzaak.proxy

import com.timzaak.proxy.CustomDnsResolver.customMappings

import java.net.InetAddress
import java.net.spi.{InetAddressResolver, InetAddressResolverProvider}
import java.util
import java.util.concurrent.ConcurrentHashMap
import java.util.stream
import scala.jdk.CollectionConverters.*

object CustomDnsResolver {
  private val customMappings = new ConcurrentHashMap[String, String]()

  def addMapping(host: String, ip: String): Unit = {
    customMappings.put(host, ip)
  }

  // 移除自定义映射
  def removeMapping(host: String): Unit = {
    customMappings.remove(host)
  }
  def getMapping(host: String): Option[String] = {
    Option(customMappings.get(host))
  }
}

class CustomDnsResolver extends InetAddressResolverProvider {

  private val resolver = new InetAddressResolver {
    override def lookupByName(host: String, lookupPolicy: InetAddressResolver.LookupPolicy): stream.Stream[InetAddress] = {
      Option(customMappings.get(host)) match {
        case Some(ip) =>
          Seq(InetAddress.getByAddress(host, InetAddress.getByName(ip).getAddress)).asJava.stream()
        case None =>
          util.Arrays.stream(InetAddress.getAllByName(host))
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
  // 添加自定义映射

  // 清除JVM DNS缓存
  private def clearDnsCache(): Unit = {
    System.setProperty("networkaddress.cache.ttl", "0")
    System.setProperty("networkaddress.cache.negative.ttl", "0")
  }
}
