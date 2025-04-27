## https proxy
print inputs and outputs to console.

require JDK 19+


### Usage
If you want to get all requests and responses about `www.exmample.com`
1. change domain ip like:`127.0.0.1 www.example.com`
2. run commands below:
```shell
### run with sbt
sbt "runMain Main --dns=192.168.3.3:www.example.com --public=ssl.cer --private=ssl.key"

### you can package it with the following command: 
sbt universal:packageBin
cp target/universal/app.zip ./package/app.zip
unzip app.zip
cp target/universal/scripts/bin ./package/app/

cd package/app/bin
./https-proxy --dns=192.168.3.3:www.example.com --public=ssl.cer --private=ssl.key

### PS: BUNDLED_JVM environment would change JRE path, for eaxmple: BUNDLED_JVM=../../jre ./https-proxy ...

```
