## HTTPS Proxy
A MITM (Man-In-The-Middle) HTTP/HTTPS proxy that logs HTTPS requests and responses for debugging and testing purposes.
### Features
- Logs HTTPS request and response details.
- View traffic through a browser UI.
- Docker & CLI supported.
- Supports custom DNS mappings and JKS SSL certificates.
### Requirements
1. JDK 19+
2. HTTPS certificate and private key (in JKS format).
3. If using self-signed certificates, consider using [mkcert](https://github.com/FiloSottile/mkcert).

> 📄 more info: [mkcert.md](mkcert.md)

### Quick Start
Suppose you're intercepting traffic for https://www.example.com (IP: 192.168.3.3).
#### Step 1: Local Host Mapping
Edit your system's host file (e.g., `/etc/hosts` or `C:\Windows\System32\drivers\etc\hosts`):

```
127.0.0.1 www.example.com
```
#### Step 2: Run the Proxy
##### Using SBT
```bash
sbt "runMain Main --dns=192.168.3.3:www.example.com --jks-path=jks.jks --jks-password=123456 --viewPort=9000"
```
##### Using Docker
Create a `private.conf` file with the following content:
```hocon
jks {
  path = "/server/config/jks.jks"
  password = "123456"
}
resolver = [114.114.114.114]
viewerPort = 9000
# selfSignedCert = ""  # Uncomment if using a self-signed cert
```
Then run:
```bash
docker run --rm \
  -v ${PWD}/private.conf:/server/config/application.conf \
  -v ${PWD}/jks.jks:/server/config/jks.jks \
  -p 443:443 -p 9000:9000 \
  ghcr.io/timzaak/log-http-proxy:latest

```
> Open your browser and navigate to `http://127.0.0.1:9000` to view traffic logs. or with ip `http://127.0.0.1:9000?ip=${http client ip}`

<img src="/doc/usage.png" alt="Usage screenshot" width="500" /> 

### Parameters

| Name             | Description                                                     |
|------------------| --------------------------------------------------------------- |
| `--dns`          | A list of `IP:domain` mappings to inject into the DNS resolver. |
| `--jks-path`     | Path to the JKS file containing the SSL certificate and key.    |
| `--jks-password` | Password for the JKS keystore.                                  |
| `--resolver`     | (Optional) DNS resolver to use (e.g., `1.1.1.1`).               |
| `--viewPort`     | (Optional) Port for the web UI or WebSocket logging.            |


### Packaging as a CLI Tool
```bash
# Package with sbt
sbt stage

# Then package with jpackage
cd package
version="0.1.0"

jpackage \
  --name https-proxy \
  --input ../target/universal/stage/lib \
  --main-jar https-proxy.https-proxy-${version}.jar \
  --main-class Main \
  --type app-image \
  --win-console
```


### Known Issue
1. Brotli compression (Accept-Encoding: br) is not supported. This header will be stripped.
2. remote-address headers are not retained.


### Alternative Logging (Raw Packet Capture)
#### On Linux Server
> Note: Windows users may need to use `tshark` instead.

```bash
ssh user@remote 'tcpdump -i any -w - -U port <port>' | wireshark -k -i -
```

#### On Kubernetes
```bash
kubectl debug -it <pod-name> -n <namespace> \
  --image=nicolaka/netshoot \
  --target=<container-name>
```
Use tcpdump inside the debug container to capture traffic.
