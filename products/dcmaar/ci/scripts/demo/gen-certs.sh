#!/usr/bin/env bash
# DCMaar Demo Certificate Generator
# Generates self-signed certificates for demo purposes

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
CERT_DIR="${CERT_DIR:-$(pwd)/security/dev-pki/demo}"
CA_KEY="$CERT_DIR/ca.key"
CA_CERT="$CERT_DIR/ca.crt"
SERVER_KEY="$CERT_DIR/server.key"
SERVER_CSR="$CERT_DIR/server.csr"
SERVER_CERT="$CERT_DIR/server.crt"
CLIENT_KEY="$CERT_DIR/client.key"
CLIENT_CSR="$CERT_DIR/client.csr"
CLIENT_CERT="$CERT_DIR/client.crt"
DAYS=365
COUNTRY="US"
STATE="California"
LOCALITY="San Francisco"
ORG="DCMaar Demo"
OU="Engineering"
COMMON_NAME="dcmaar-demo.local"
ALT_NAMES="DNS:localhost,IP:127.0.0.1,DNS:dcmaar-demo.local"

# Ensure the certificate directory exists
mkdir -p "$CERT_DIR"

# Check if OpenSSL is installed
if ! command -v openssl &> /dev/null; then
    echo -e "${RED}✗ OpenSSL is not installed. Please install OpenSSL.${NC}"
    exit 1
fi

# Generate CA private key
generate_ca() {
    echo -e "${GREEN}🔐 Generating CA private key...${NC}"
    openssl genrsa -out "$CA_KEY" 4096
    
    echo -e "${GREEN}📝 Generating CA certificate...${NC}"
    openssl req -x509 -new -nodes -key "$CA_KEY" -sha256 -days "$DAYS" -out "$CA_CERT" \
        -subj "/C=$COUNTRY/ST=$STATE/L=$LOCALITY/O=$ORG/OU=$OU/CN=$COMMON_NAME CA"
    
    echo -e "${GREEN}✅ CA certificate generated at $CA_CERT${NC}"
}

# Generate server certificate
generate_server_cert() {
    echo -e "${GREEN}🔐 Generating server private key...${NC}"
    openssl genrsa -out "$SERVER_KEY" 2048
    
    echo -e "${GREEN}📝 Generating server certificate signing request...${NC}"
    openssl req -new -key "$SERVER_KEY" -out "$SERVER_CSR" \
        -subj "/C=$COUNTRY/ST=$STATE/L=$LOCALITY/O=$ORG/OU=$OU/CN=server.$COMMON_NAME"
    
    echo -e "${GREEN}🔏 Generating server certificate...${NC}"
    cat > "$CERT_DIR/server.ext" <<-EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = $ALT_NAMES
extendedKeyUsage = serverAuth
EOF
    
    openssl x509 -req -in "$SERVER_CSR" -CA "$CA_CERT" -CAkey "$CA_KEY" -CAcreateserial \
        -out "$SERVER_CERT" -days "$DAYS" -sha256 -extfile "$CERT_DIR/server.ext"
    
    rm -f "$CERT_DIR/server.ext" "$CERT_DIR/server.csr"
    echo -e "${GREEN}✅ Server certificate generated at $SERVER_CERT${NC}"
}

# Generate client certificate
generate_client_cert() {
    echo -e "${GREEN}🔐 Generating client private key...${NC}"
    openssl genrsa -out "$CLIENT_KEY" 2048
    
    echo -e "${GREEN}📝 Generating client certificate signing request...${NC}"
    openssl req -new -key "$CLIENT_KEY" -out "$CLIENT_CSR" \
        -subj "/C=$COUNTRY/ST=$STATE/L=$LOCALITY/O=$ORG/OU=$OU/CN=client.$COMMON_NAME"
    
    echo -e "${GREEN}🔏 Generating client certificate...${NC}"
    cat > "$CERT_DIR/client.ext" <<-EOF
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment
subjectAltName = DNS:client.$COMMON_NAME
extendedKeyUsage = clientAuth
EOF
    
    openssl x509 -req -in "$CLIENT_CSR" -CA "$CA_CERT" -CAkey "$CA_KEY" -CAcreateserial \
        -out "$CLIENT_CERT" -days "$DAYS" -sha256 -extfile "$CERT_DIR/client.ext"
    
    rm -f "$CERT_DIR/client.ext" "$CERT_DIR/client.csr"
    echo -e "${GREEN}✅ Client certificate generated at $CLIENT_CERT${NC}"
}

# Generate a PKCS#12 bundle for client authentication
generate_p12_bundle() {
    echo -e "${GREEN}📦 Generating PKCS#12 bundle...${NC}"
    openssl pkcs12 -export -out "$CERT_DIR/client.p12" -inkey "$CLIENT_KEY" \
        -in "$CLIENT_CERT" -certfile "$CA_CERT" -passout pass:
    echo -e "${GREEN}✅ PKCS#12 bundle generated at $CERT_DIR/client.p12${NC}"
}

# Main function
main() {
    echo -e "${GREEN}🚀 Starting certificate generation for DCMaar demo...${NC}"
    
    # Generate CA if it doesn't exist
    if [ ! -f "$CA_KEY" ] || [ ! -f "$CA_CERT" ]; then
        generate_ca
    else
        echo -e "${YELLOW}ℹ️  Using existing CA certificate at $CA_CERT${NC}"
    fi
    
    # Generate server certificate
    if [ ! -f "$SERVER_KEY" ] || [ ! -f "$SERVER_CERT" ]; then
        generate_server_cert
    else
        echo -e "${YELLOW}ℹ️  Using existing server certificate at $SERVER_CERT${NC}"
    fi
    
    # Generate client certificate
    if [ ! -f "$CLIENT_KEY" ] || [ ! -f "$CLIENT_CERT" ]; then
        generate_client_cert
    else
        echo -e "${YELLOW}ℹ️  Using existing client certificate at $CLIENT_CERT${NC}"
    fi
    
    # Generate PKCS#12 bundle
    if [ ! -f "$CERT_DIR/client.p12" ]; then
        generate_p12_bundle
    else
        echo -e "${YELLOW}ℹ️  Using existing PKCS#12 bundle at $CERT_DIR/client.p12${NC}"
    fi
    
    # Set proper permissions
    chmod 600 "$CERT_DIR"/*.key
    chmod 644 "$CERT_DIR"/*.crt "$CERT_DIR"/*.p12
    
    echo -e "\n${GREEN}🎉 Certificate generation complete!${NC}"
    echo -e "\n${YELLOW}📋 Certificate Summary:${NC}"
    echo -e "- CA Certificate: ${GREEN}$CA_CERT${NC}"
    echo -e "- Server Certificate: ${GREEN}$SERVER_CERT${NC}"
    echo -e "- Server Key: ${GREEN}$SERVER_KEY${NC}"
    echo -e "- Client Certificate: ${GREEN}$CLIENT_CERT${NC}"
    echo -e "- Client Key: ${GREEN}$CLIENT_KEY${NC}"
    echo -e "- Client PKCS#12 Bundle: ${GREEN}$CERT_DIR/client.p12${NC}"
    echo -e "\n${YELLOW}🔐 These certificates are for DEMO USE ONLY and should not be used in production.${NC}"
}

# Run the main function
main

exit 0
