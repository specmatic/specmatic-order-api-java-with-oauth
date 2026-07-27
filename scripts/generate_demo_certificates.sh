#!/usr/bin/env bash
set -euo pipefail

cert_dir="${1:-build/certs}"
password="${DEMO_CERT_PASSWORD:-changeit}"

mkdir -p "$cert_dir"

required_files=(
  "$cert_dir/ca.crt"
  "$cert_dir/server-keystore.jks"
  "$cert_dir/server-truststore.jks"
  "$cert_dir/specmatic-client.jks"
  "$cert_dir/specmatic-client.crt"
  "$cert_dir/specmatic-client.key"
)

for required_file in "${required_files[@]}"; do
  if [ ! -f "$required_file" ]; then
    certificates_are_complete=false
    break
  fi
  certificates_are_complete=true
done

if [ "${certificates_are_complete:-false}" = true ]; then
  exit 0
fi

rm -f "$cert_dir"/*

openssl genrsa -out "$cert_dir/ca.key" 2048
openssl req -x509 -new -sha256 \
  -key "$cert_dir/ca.key" \
  -out "$cert_dir/ca.crt" \
  -days 3650 \
  -subj "/CN=Specmatic Demo CA"

openssl genrsa -out "$cert_dir/order-api.key" 2048
openssl req -new \
  -key "$cert_dir/order-api.key" \
  -out "$cert_dir/order-api.csr" \
  -subj "/CN=order-api"

cat > "$cert_dir/order-api.ext" <<'EOF'
subjectAltName=DNS:order-api,DNS:localhost,DNS:host.docker.internal,IP:127.0.0.1
extendedKeyUsage=serverAuth
keyUsage=digitalSignature,keyEncipherment
EOF

openssl x509 -req -sha256 \
  -in "$cert_dir/order-api.csr" \
  -CA "$cert_dir/ca.crt" \
  -CAkey "$cert_dir/ca.key" \
  -CAcreateserial \
  -out "$cert_dir/order-api.crt" \
  -days 3650 \
  -extfile "$cert_dir/order-api.ext"

openssl pkcs12 -export \
  -name order-api \
  -inkey "$cert_dir/order-api.key" \
  -in "$cert_dir/order-api.crt" \
  -certfile "$cert_dir/ca.crt" \
  -out "$cert_dir/server-keystore.p12" \
  -password "pass:$password"

keytool -importkeystore -noprompt \
  -srckeystore "$cert_dir/server-keystore.p12" \
  -srcstoretype PKCS12 \
  -srcstorepass "$password" \
  -destkeystore "$cert_dir/server-keystore.jks" \
  -deststoretype JKS \
  -deststorepass "$password"

keytool -importcert -noprompt \
  -alias specmatic-demo-ca \
  -file "$cert_dir/ca.crt" \
  -keystore "$cert_dir/server-truststore.jks" \
  -storepass "$password"

openssl genrsa -out "$cert_dir/specmatic-client.key" 2048
openssl req -new \
  -key "$cert_dir/specmatic-client.key" \
  -out "$cert_dir/specmatic-client.csr" \
  -subj "/CN=specmatic-client"

cat > "$cert_dir/specmatic-client.ext" <<'EOF'
extendedKeyUsage=clientAuth
keyUsage=digitalSignature,keyEncipherment
EOF

openssl x509 -req -sha256 \
  -in "$cert_dir/specmatic-client.csr" \
  -CA "$cert_dir/ca.crt" \
  -CAkey "$cert_dir/ca.key" \
  -CAcreateserial \
  -out "$cert_dir/specmatic-client.crt" \
  -days 3650 \
  -extfile "$cert_dir/specmatic-client.ext"

openssl pkcs12 -export \
  -name specmatic-client \
  -inkey "$cert_dir/specmatic-client.key" \
  -in "$cert_dir/specmatic-client.crt" \
  -certfile "$cert_dir/ca.crt" \
  -out "$cert_dir/specmatic-client.p12" \
  -password "pass:$password"

keytool -importkeystore -noprompt \
  -srckeystore "$cert_dir/specmatic-client.p12" \
  -srcstoretype PKCS12 \
  -srcstorepass "$password" \
  -destkeystore "$cert_dir/specmatic-client.jks" \
  -deststoretype JKS \
  -deststorepass "$password"

keytool -importcert -noprompt \
  -alias specmatic-demo-ca \
  -file "$cert_dir/ca.crt" \
  -keystore "$cert_dir/specmatic-client.jks" \
  -storepass "$password"
