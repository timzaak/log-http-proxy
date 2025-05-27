package com.timzaak.proxy

import javax.net.ssl.*
import java.io.FileInputStream
import java.security.KeyStore

object SSLContextProvider {

  def fromJKS(jksPath: String, password: String): SSLContext = {

    val keyStore = KeyStore.getInstance("JKS")
    val keyStoreStream = new FileInputStream(jksPath)
    keyStore.load(keyStoreStream, password.toCharArray)
    keyStoreStream.close()

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keyStore, password.toCharArray)

    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(keyStore)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(kmf.getKeyManagers, tmf.getTrustManagers, null)

    sslContext

  }
}
