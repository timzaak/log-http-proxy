## https proxy
It's mitm proxy, especially for https. It outputs the https request and response for test.

### Requires
1. require JDK 19+
2. https cert and private key
3. https certificate.

If you want to install self-signed certificate, you could use [mkcert](https://github.com/FiloSottile/mkcert) to do this.
```shell
mkcert -install
mkcert -key-file ssl.key -cert-file ssl.cer www.example.com *.example.com
```
PS: please make sure JAVA_HOME environment is correctly set. otherwise, mkcert would'n inject CA to java truststore.

### Usage
If you want to get all requests and responses about `https://www.exmample.com (192.168.3.3)` 
1. change domain ip like:`127.0.0.1 www.example.com`
2. run commands below:
```shell
# run with sbt
sbt "runMain Main --dns=192.168.3.3:www.example.com --jksPath=jks.jks --jksPassword=123456 --websocketPort=9000"

# run client with websocket
websocat ws://127.0.0.1:9000/api_ws?ip=127.0.0.1
```
[websocat](https://github.com/vi/websocat) is a websocket command line.
### Package as command-line tool
```shell
### you can package it with the following command: 
sbt stage
cd package

version="0.1.0"
jpackage --name https-proxy --input ../target/universal/stage/lib --main-jar https-proxy.https-proxy-${version}.jar --main-class Main --type app-image --win-console

```


### Known Issue
1. Request does not support brotli compression, would drop request header: Accept-Encoding.

### TODO
1. [ ] Generate CA file with command.
2. [ ] Auto create ssl certificates automatically with config.
3. [ ] use as http/https proxy.
