#!/bin/bash
set -e

# Create certs directory if it doesn't exist
mkdir -p certs

# Generate CA certificate
echo "Generating CA certificate..."
openssl req -x509 -new -nodes -newkey rsa:4096 \
  -keyout certs/ca.key -out certs/ca.crt \
  -days 3650 -subj '/CN=DCMAR CA'

# Generate server certificate
echo "Generating server certificate..."
openssl req -new -newkey rsa:4096 -nodes \
  -keyout certs/server.key -out certs/server.csr \
  -subj '/CN=server'

openssl x509 -req -in certs/server.csr \
  -CA certs/ca.crt -CAkey certs/ca.key -CAcreateserial \
  -out certs/server.crt -days 365 -extfile <(printf 'subjectAltName=DNS:server,DNS:localhost')

# Generate agent certificate
echo "Generating agent certificate..."
openssl req -new -newkey rsa:4096 -nodes \
  -keyout certs/agent.key -out certs/agent.csr \
  -subj '/CN=agent'

openssl x509 -req -in certs/agent.csr \
  -CA certs/ca.crt -CAkey certs/ca.key -CAcreateserial \
  -out certs/agent.crt -days 365

# Set permissions
echo "Setting permissions..."
chmod 644 certs/*.crt certs/*.key

# Clean up CSR files
echo "Cleaning up..."
rm -f certs/*.csr certs/*.srl

echo "Certificates generated successfully in certs/ directory"

# Display certificate information
echo -e "\nCertificate Information:"
echo "----------------------"
echo "CA Certificate:"
openssl x509 -in certs/ca.crt -noout -issuer -subject -dates

echo -e "\nServer Certificate:"
openssl x509 -in certs/server.crt -noout -issuer -subject -dates

echo -e "\nAgent Certificate:"
openssl x509 -in certs/agent.crt -noout -issuer -subject -dates
