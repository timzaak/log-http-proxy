安装mkcert：https://github.com/FiloSottile/mkcert， 走 README 方式，不要在 release 下载直接用，macOS 会出问题。

```shell
# 生成10年有效期证书，并安装在本机上，需要root权限，如果想回撤，可以使用 mkcert -uninstall
mkcert -install 
# 在该目录下： 可以拿到 证书 rootCA.pem、rootCA-key.pem 文件。
cd "$(mkcert -CAROOT)"
# 生成证书 
kcert example.com "*.example.com"
# 生成 JKS
openssl pkcs12 -export \
    -in .com.pem \
    -inkey example.com-key.pem \
    -out example.com.p12 \
    -name example.com \
    -CAfile rootCA.pem \
    -caname root
    
# 可执行多次，将多个 p12 都到入到 jks 文件中    
keytool -importkeystore \
    -srckeystore example.com.p12 \
    -srcstoretype PKCS12 \
    -destkeystore example.com.jks \
    -deststoretype JKS \
    -alias example.com

#查看 JKS 内容
keytool -list -v -keystore keystore.jks -storepass ${changeit}

# 移除本机安装
mkcert -uninstall 
```

在别的机器安装
```shell
# 1.拷贝rootCA.pem到指定机器上，并设置 CAROOT 证书 rootCA.pem
export CAROOT=$(pwd)
# 检查环境变量是否ok
echo $CAROOT
#
mkcert -install
```

PS: please make sure JAVA_HOME environment is correctly set. otherwise, mkcert would'n inject CA to java truststore.