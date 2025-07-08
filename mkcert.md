# 📥 Installing and Using `mkcert` for Local HTTPS and Java Keystore

This guide walks you through the process of installing `mkcert`, generating local certificates, and importing them into a Java KeyStore (JKS) for use in HTTPS services.

---

## 🛠️ Prerequisites

- `mkcert` installed (see below)
- `openssl` installed
- JDK (includes `keytool`)
- Environment variable `JAVA_HOME` properly configured (for Java truststore support)

---

## 📦 Install `mkcert`

> ⚠️ **Important:** Do **not** download the binary directly from the GitHub Releases page, especially on **macOS**.  
> Instead, follow the official instructions from the [`mkcert` README](https://github.com/FiloSottile/mkcert).

Once installed, set up the local root Certificate Authority (CA):

```bash
# Install the root CA into the system trust store
mkcert -install
```

This will generate a root certificate valid for **10 years**, and install it into:

```bash
cd "$(mkcert -CAROOT)"
# You'll find:
# - rootCA.pem        (the root certificate)
# - rootCA-key.pem    (the private key)
```

To uninstall the root CA later:

```bash
mkcert -uninstall
```

---

## 📄 Generate TLS Certificates

Generate a certificate and private key for your domain (e.g., `example.com` and its subdomains):

```bash
mkcert example.com "*.example.com"
```

This will output two files:

- `example.com.pem` — the certificate
- `example.com-key.pem` — the private key

---

## 🔐 Convert to Java KeyStore (JKS)

### Step 1: Convert to PKCS#12 (.p12)

```bash
openssl pkcs12 -export \
  -in example.com.pem \
  -inkey example.com-key.pem \
  -out example.com.p12 \
  -name example.com \
  -CAfile rootCA.pem \
  -caname root
```

> This creates `example.com.p12`, which bundles the certificate and private key.

### Step 2: Import `.p12` into JKS

```bash
keytool -importkeystore \
  -srckeystore example.com.p12 \
  -srcstoretype PKCS12 \
  -destkeystore example.com.jks \
  -deststoretype JKS \
  -alias example.com
```

### 🔍 View the JKS Contents

```bash
keytool -list -v -keystore example.com.jks -storepass changeit
```

> 💡 You can repeat the `keytool -importkeystore` step to import additional `.p12` certificates into the same JKS file.

---

## 📤 Installing the Root CA on Another Machine

To use the generated certificates on another machine:

1. Copy the `rootCA.pem` file to the target machine.
2. Set the `CAROOT` environment variable:

   ```bash
   export CAROOT=$(pwd)  # Set to the directory containing rootCA.pem
   ```

3. Confirm:

   ```bash
   echo $CAROOT
   ```

4. Install the root CA:

   ```bash
   mkcert -install
   ```

> ⚠️ Ensure that the `JAVA_HOME` environment variable is correctly set on the target machine.  
> If it's not, `mkcert` may fail to inject the CA into the Java truststore.

---

✅ Done! You now have local TLS certificates trusted by your OS and Java, ready for use in development or testing environments.