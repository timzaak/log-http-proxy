## https proxy
It's mitm proxy, but you need the domain ssl certificate and private key. It's for test and log for the development of the software which
is hard to capture the https request and response. 

### Requires
1. require JDK 19+
2. https cert and private key


If you want to install self-signed certificate, you could use [mkcert](https://github.com/FiloSottile/mkcert) to do this.
```shell
mkcert -install
mkcert -key-file ssl.key -cert-file ssl.cer www.example.com *.example.com
```

### Usage
If you want to get all requests and responses about `https://www.exmample.com (192.168.3.3)` 
1. change domain ip like:`127.0.0.1 www.example.com`
2. run commands below:
```shell
# run with sbt
sbt "runMain Main --dns=192.168.3.3:www.example.com --public=ssl.cer --private=ssl.key"
```

### Package as command-line tool
```shell
### you can package it with the following command: 
sbt universal:packageBin
cp target/universal/app.zip ./package/app.zip
unzip app.zip
cd package/app

version="0.1.0"
jpackage --name https-proxy --input lib --main-jar https-proxy.https-proxy-${version}.jar --main-class Main --type app-image --win-console

```