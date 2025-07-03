## https proxy
It's mitm http/https proxy. It outputs the https request and response for test.

### Requires
1. require JDK 19+
2. https cert and private key
3. https certificate.

If you want to install self-signed certificate, you could use [mkcert](https://github.com/FiloSottile/mkcert) to do this.
more info can be found here [mkcert.md](mkcert.md)

### Usage
If you want to get all requests and responses about `https://www.exmample.com (192.168.3.3)` 
1. change domain ip like:`127.0.0.1 www.example.com`
2. run commands below:
```shell
# run with sbt
sbt "runMain Main --dns=192.168.3.3:www.example.com --jks-path=jks.jks --jks-password=123456 --viewPort=9000"


# run with docker
# config file would be:

# jks {
#   path=/server/config/jks.jks
#   password=123456
# }
# resolver=[114.114.114.114]
# viewerPort=9000
# ##selfSignedCert = "" # if you use self signed cert.

docker run --rm  -v ${pwd}/private.conf:/server/config/application.conf -v${pwd}/jks.jks:/server/config/jks.jks -p 443:443 -p 9000:9000 ghcr.io/timzaak/log-http-proxy:latest


# open your browser to access http://127.0.0.1:9000

```
<img src="/doc/usage.png" alt="usage" width="500" />

The params are:

* dns: A list of domain-to-IP mappings in the format ip:domain. These mappings will be added to the DNS resolver.
* jksPath: An optional path to a JKS file for SSL/TLS configuration.
* jksPassword: An optional password for the JKS file specified by jksPath.
* resolver: An optional custom DNS resolver address, e.g., 1.1.1.1, 8.8.8.8.
* websocketPort: An optional port to start a WebSocket server for logging. If not provided, logs would output to the command line.

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
2. Request would drop header remote-address


### Another way to log

```shell
### Linux Server
#### Windows may need use tshark to replace it.
ssh user@remote 'tcpdump -i any -w - -U port <port>' | wireshark -k -i -


### K8S use debug to inject and run tcpdump, then k8s exec to get tcpdump output
kubectl debug -it <pod-name> -n <namespace> --image=nicolaka/netshoot --target=<container-name>

```
