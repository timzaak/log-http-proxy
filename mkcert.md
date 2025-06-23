## 📥 Install mkcert
> Do NOT download the binary directly from the Releases page, especially on macOS, as it can lead to issues.
> Instead, follow the installation steps provided in the official [README](https://github.com/FiloSottile/mkcert).

```shell
# Install root CA in the system trust store (requires root privileges).
# The root CA is valid for 10 years.
# To undo this later, you can run: mkcert -uninstall
mkcert -install

# Get the directory where the root CA is stored:
cd "$(mkcert -CAROOT)"

# In this directory, you'll find rootCA.pem and rootCA-key.pem
```
## 📄 Generate Certificates
```shell
# Generate certificate and private key for the domain example.com and its subdomains:
mkcert example.com "*.example.com"
```
## 🔐 Create a Java KeyStore (JKS)
```shell
# Step 1: Convert to PKCS12 format (example.com.p12)
openssl pkcs12 -export \
    -in example.com.pem \
    -inkey example.com-key.pem \
    -out example.com.p12 \
    -name example.com \
    -CAfile rootCA.pem \
    -caname root

# Step 2: Import the PKCS12 file into a Java KeyStore (JKS)
keytool -importkeystore \
    -srckeystore example.com.p12 \
    -srcstoretype PKCS12 \
    -destkeystore example.com.jks \
    -deststoretype JKS \
    -alias example.com

# 🔍 View the JKS Contents
keytool -list -v -keystore example.com.jks -storepass changeit
```
> You can repeat the keytool -importkeystore step multiple times to import multiple .p12 files into the same JKS.

## 📦 Install the Root CA on Another Machine
```shell
# 1. Copy the rootCA.pem to the target machine.
# 2. Set the CAROOT environment variable to the directory containing rootCA.pem
export CAROOT=$(pwd)

# 3. Verify the environment variable
echo $CAROOT

# 4. Install the root CA into the local trust store on the target machine
mkcert -install
```
> ⚠️ Note: Ensure the JAVA_HOME environment variable is correctly set on the machine.
If not, mkcert will not be able to inject the CA into the Java truststore.

