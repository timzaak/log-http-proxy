## https proxy
It's mitm proxy, but you need the domain ssl certificate and private key. It's for test and log for the development of the software which
is hard to capture the https request and response. 

### Requires
1. require JDK 19+
2. https cert and private key


### Usage
If you want to get all requests and responses about `https://www.exmample.com (192.168.3.3)` 
1. change domain ip like:`127.0.0.1 www.example.com`
2. run commands below:
```shell
### run with sbt
sbt "runMain Main --dns=192.168.3.3:www.example.com --public=ssl.cer --private=ssl.key"
```

### Package as command-line tools

```shell
### you can package it with the following command: 
sbt universal:packageBin
cp target/universal/app.zip ./package/app.zip
unzip app.zip
cp target/universal/scripts/bin ./package/app/

cd package/app/bin
./https-proxy --dns=192.168.3.3:www.example.com --public=ssl.cer --private=ssl.key

```

You can download JRE to package/jre lib and set environment `BUNDLED_JVM=../../jre`,  `zip ./package` and distribute it.
