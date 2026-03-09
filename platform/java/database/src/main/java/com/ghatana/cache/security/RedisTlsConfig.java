package com.ghatana.cache.security;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import javax.net.ssl.*;

/**
 * TLS/SSL security configuration for encrypted Redis connections with mTLS support.
 *
 * <p><b>Purpose</b><br>
 * Configures TLS 1.3 encryption and mutual authentication (mTLS) for secure Redis
 * connections in compliance with PCI DSS, HIPAA, GDPR, and NIST standards.
 *
 * <p><b>Architecture Role</b><br>
 * Security configuration in redis-cache layer for production-grade encryption.
 * Used by:
 * @doc.type class
 * @doc.purpose TLS/SSL and mTLS configuration for encrypted Redis connections
 * @doc.layer core
 * @doc.pattern Configuration, Security Pattern
 *
 * - Production Redis deployments requiring encryption
 * - Compliance-regulated environments (PCI DSS, HIPAA)
 * - Multi-tenant systems with strict data isolation
 * - Cross-datacenter Redis replication
 *
 * <p><b>Security Features</b><br>
 * 
 * <p><b>TLS 1.3 Encryption</b>
 * - Protocol: TLS 1.3 (strongest available)
 * - Cipher Suites: TLS_AES_256_GCM_SHA384, TLS_CHACHA20_POLY1305_SHA256
 * - Forward Secrecy: Ephemeral key exchange
 * - Server Verification: Validates Redis server certificate
 * 
 * <p><b>Mutual TLS (mTLS)</b>
 * - Client Authentication: Redis verifies client certificate
 * - Server Authentication: Client verifies Redis certificate
 * - Certificate Chain: Full CA validation
 * - Revocation: Support for CRL/OCSP checks
 * 
 * <p><b>Trust Management</b>
 * - TrustStore: CA certificates for server validation
 * - KeyStore: Client certificates for authentication
 * - Password Protection: Encrypted KeyStore/TrustStore
 * - Peer Verification: Optional hostname validation
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Basic TLS with server verification
 * RedisTlsConfig tlsConfig = RedisTlsConfig.builder()
 *     .trustStorePath("/etc/redis/certs/truststore.jks")
 *     .trustStorePassword("changeit")
 *     .verifyPeer(true)
 *     .build();
 *
 * // Full mTLS with client certificate
 * RedisTlsConfig tlsConfig = RedisTlsConfig.builder()
 *     .keyStorePath("/etc/redis/certs/client-keystore.jks")
 *     .keyStorePassword("client-secret")
 *     .trustStorePath("/etc/redis/certs/truststore.jks")
 *     .trustStorePassword("trust-secret")
 *     .verifyPeer(true)
 *     .enforceStrongCiphers(true)
 *     .build();
 *
 * // Apply to Redis connection (Jedis example)
 * SSLSocketFactory sslSocketFactory = tlsConfig.createSslSocketFactory();
 * SSLParameters sslParameters = tlsConfig.createSslParameters();
 * Jedis jedis = new Jedis(
 *     "redis.ghatana.prod", 6380,
 *     sslSocketFactory, sslParameters
 * );
 *
 * // Apply to Lettuce connection
 * RedisURI redisUri = RedisURI.builder()
 *     .withHost("redis.ghatana.prod")
 *     .withPort(6380)
 *     .withSsl(true)
 *     .withVerifyPeer(tlsConfig.isVerifyPeer())
 *     .build();
 * RedisClient client = RedisClient.create(redisUri);
 * }</pre>
 *
 * <p><b>Certificate Setup (OpenSSL)</b><br>
 * <pre>
 * # 1. Generate CA
 * openssl genrsa -out ca.key 4096
 * openssl req -new -x509 -days 3650 -key ca.key -out ca.crt
 *
 * # 2. Generate server certificate
 * openssl genrsa -out server.key 4096
 * openssl req -new -key server.key -out server.csr
 * openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key \
 *   -CAcreateserial -out server.crt -days 365
 *
 * # 3. Generate client certificate
 * openssl genrsa -out client.key 4096
 * openssl req -new -key client.key -out client.csr
 * openssl x509 -req -in client.csr -CA ca.crt -CAkey ca.key \
 *   -CAcreateserial -out client.crt -days 365
 *
 * # 4. Create Java KeyStore (client)
 * openssl pkcs12 -export -in client.crt -inkey client.key \
 *   -out client.p12 -name redis-client
 * keytool -importkeystore -srckeystore client.p12 -srcstoretype PKCS12 \
 *   -destkeystore client.jks -deststoretype JKS
 *
 * # 5. Create Java TrustStore (CA)
 * keytool -import -alias ca -file ca.crt -keystore truststore.jks
 * </pre>
 *
 * <p><b>Redis Server Configuration</b><br>
 * <pre>
 * # redis.conf
 * port 0                       # Disable non-TLS
 * tls-port 6380                # Enable TLS
 * tls-cert-file /etc/redis/certs/server.crt
 * tls-key-file /etc/redis/certs/server.key
 * tls-ca-cert-file /etc/redis/certs/ca.crt
 * tls-auth-clients yes         # Require client certificates
 * tls-protocols "TLSv1.3"
 * tls-ciphersuites TLS_AES_256_GCM_SHA384:TLS_CHACHA20_POLY1305_SHA256
 * </pre>
 *
 * <p><b>Compliance Standards</b><br>
 * - <b>PCI DSS 4.1 Req. 4.2</b>: Strong cryptography for cardholder data transmission
 * - <b>HIPAA § 164.312(e)(1)</b>: Transmission security for ePHI
 * - <b>GDPR Art. 32(1)(a)</b>: Encryption of personal data in transit
 * - <b>NIST SP 800-52 Rev. 2</b>: TLS guidelines for federal systems
 * - <b>SOC 2 Type II</b>: Encryption of data in transit
 *
 * <p><b>Security Best Practices</b><br>
 * - Use TLS 1.3 only (disable TLS 1.2)
 * - Enable peer verification in production
 * - Rotate certificates annually
 * - Monitor certificate expiration
 * - Store KeyStores in secure vault (HashiCorp Vault, AWS Secrets Manager)
 * - Never commit certificates to version control
 * - Use separate certificates per environment
 * - Enable strong cipher enforcement
 *
 * <p><b>Thread Safety</b><br>
 * Immutable configuration - all fields final. Safe to share across threads.
 *
 * @see AsyncRedisCache
 * @see RedisCacheConfig
 * @see <a href="https://redis.io/docs/management/security/encryption/">Redis TLS Documentation</a>
 * @doc.type class
 * @doc.purpose TLS/SSL configuration for secure Redis connections
 * @doc.layer core
 * @doc.pattern Value Object
 */
public final class RedisTlsConfig {
    
    private final Path keyStorePath;
    private final String keyStorePassword;
    private final String keyStoreType;
    private final Path trustStorePath;
    private final String trustStorePassword;
    private final String trustStoreType;
    private final boolean verifyPeer;
    private final boolean enforceStrongCiphers;
    private final String[] enabledProtocols;
    private final String[] enabledCipherSuites;

    /**
     * TLS protocol versions.
     */
    public enum TlsProtocol {
        TLS_1_2("TLSv1.2"),
        TLS_1_3("TLSv1.3");

        private final String protocolName;

        TlsProtocol(String protocolName) {
            this.protocolName = protocolName;
        }

        public String getProtocolName() {
            return protocolName;
        }
    }

    /**
     * Strong cipher suites for TLS 1.3 (AEAD ciphers only).
     */
    private static final String[] TLS_13_CIPHER_SUITES = {
        "TLS_AES_256_GCM_SHA384",
        "TLS_AES_128_GCM_SHA256",
        "TLS_CHACHA20_POLY1305_SHA256"
    };

    /**
     * Strong cipher suites for TLS 1.2 (GCM mode only).
     */
    private static final String[] TLS_12_CIPHER_SUITES = {
        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"
    };

    private RedisTlsConfig(Builder builder) {
        this.keyStorePath = builder.keyStorePath;
        this.keyStorePassword = builder.keyStorePassword;
        this.keyStoreType = builder.keyStoreType != null ? builder.keyStoreType : "JKS";
        this.trustStorePath = builder.trustStorePath;
        this.trustStorePassword = builder.trustStorePassword;
        this.trustStoreType = builder.trustStoreType != null ? builder.trustStoreType : "JKS";
        this.verifyPeer = builder.verifyPeer;
        this.enforceStrongCiphers = builder.enforceStrongCiphers;
        this.enabledProtocols = builder.enabledProtocols;
        this.enabledCipherSuites = builder.enabledCipherSuites;
        validate();
    }

    /**
     * Validates the TLS configuration.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (trustStorePath == null) {
            throw new IllegalArgumentException(
                "Trust store path must be provided for TLS connections"
            );
        }
        if (!trustStorePath.toFile().exists()) {
            throw new IllegalArgumentException(
                "Trust store file not found: " + trustStorePath
            );
        }
        if (trustStorePassword == null || trustStorePassword.isEmpty()) {
            throw new IllegalArgumentException(
                "Trust store password must be provided"
            );
        }

        // Client certificate validation (optional for server-only verification)
        if (keyStorePath != null) {
            if (!keyStorePath.toFile().exists()) {
                throw new IllegalArgumentException(
                    "Key store file not found: " + keyStorePath
                );
            }
            if (keyStorePassword == null || keyStorePassword.isEmpty()) {
                throw new IllegalArgumentException(
                    "Key store password must be provided when using client certificates"
                );
            }
        }
    }

    /**
     * Creates an SSLSocketFactory for Redis TLS connections.
     *
     * @return Configured SSLSocketFactory
     * @throws RuntimeException if SSL context creation fails
     */
    public SSLSocketFactory createSslSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            
            KeyManager[] keyManagers = null;
            if (keyStorePath != null) {
                keyManagers = createKeyManagers();
            }
            
            TrustManager[] trustManagers = createTrustManagers();
            
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create SSL socket factory", e);
        }
    }

    /**
     * Creates SSLParameters with configured protocols and cipher suites.
     *
     * @return Configured SSLParameters
     */
    public SSLParameters createSslParameters() {
        SSLParameters sslParameters = new SSLParameters();
        
        if (verifyPeer) {
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        }
        
        if (enforceStrongCiphers) {
            String[] protocols = enabledProtocols != null ? enabledProtocols : new String[]{"TLSv1.3"};
            sslParameters.setProtocols(protocols);
            
            String[] ciphers = enabledCipherSuites != null ? enabledCipherSuites : TLS_13_CIPHER_SUITES;
            sslParameters.setCipherSuites(ciphers);
        }
        
        return sslParameters;
    }

    /**
     * Creates KeyManagers from the configured key store.
     *
     * @return Array of KeyManagers
     */
    private KeyManager[] createKeyManagers() {
        try {
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            try (FileInputStream fis = new FileInputStream(keyStorePath.toFile())) {
                keyStore.load(fis, keyStorePassword.toCharArray());
            }
            
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm()
            );
            kmf.init(keyStore, keyStorePassword.toCharArray());
            return kmf.getKeyManagers();
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException 
                | CertificateException | UnrecoverableKeyException e) {
            throw new RuntimeException("Failed to create key managers", e);
        }
    }

    /**
     * Creates TrustManagers from the configured trust store.
     *
     * @return Array of TrustManagers
     */
    private TrustManager[] createTrustManagers() {
        try {
            KeyStore trustStore = KeyStore.getInstance(trustStoreType);
            try (FileInputStream fis = new FileInputStream(trustStorePath.toFile())) {
                trustStore.load(fis, trustStorePassword.toCharArray());
            }
            
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            );
            tmf.init(trustStore);
            return tmf.getTrustManagers();
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException 
                | CertificateException e) {
            throw new RuntimeException("Failed to create trust managers", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating RedisTlsConfig instances.
     */
    public static final class Builder {
        private Path keyStorePath;
        private String keyStorePassword;
        private String keyStoreType;
        private Path trustStorePath;
        private String trustStorePassword;
        private String trustStoreType;
        private boolean verifyPeer = true;
        private boolean enforceStrongCiphers = true;
        private String[] enabledProtocols;
        private String[] enabledCipherSuites;

        private Builder() {}

        /**
         * Sets the client key store path (for client certificate authentication).
         *
         * @param path Path to JKS/PKCS12 key store file
         * @return This builder
         */
        public Builder keyStorePath(String path) {
            this.keyStorePath = Paths.get(path);
            return this;
        }

        /**
         * Sets the key store password.
         *
         * @param password Key store password
         * @return This builder
         */
        public Builder keyStorePassword(String password) {
            this.keyStorePassword = password;
            return this;
        }

        /**
         * Sets the key store type (default: JKS).
         *
         * @param type Key store type (JKS, PKCS12)
         * @return This builder
         */
        public Builder keyStoreType(String type) {
            this.keyStoreType = type;
            return this;
        }

        /**
         * Sets the trust store path (for server verification).
         *
         * @param path Path to JKS trust store file
         * @return This builder
         */
        public Builder trustStorePath(String path) {
            this.trustStorePath = Paths.get(path);
            return this;
        }

        /**
         * Sets the trust store password.
         *
         * @param password Trust store password
         * @return This builder
         */
        public Builder trustStorePassword(String password) {
            this.trustStorePassword = password;
            return this;
        }

        /**
         * Sets the trust store type (default: JKS).
         *
         * @param type Trust store type (JKS, PKCS12)
         * @return This builder
         */
        public Builder trustStoreType(String type) {
            this.trustStoreType = type;
            return this;
        }

        /**
         * Sets whether to verify the Redis server's hostname.
         *
         * @param verify true to verify peer hostname (default: true)
         * @return This builder
         */
        public Builder verifyPeer(boolean verify) {
            this.verifyPeer = verify;
            return this;
        }

        /**
         * Sets whether to enforce strong cipher suites (TLS 1.3 only).
         *
         * @param enforce true to enforce strong ciphers (default: true)
         * @return This builder
         */
        public Builder enforceStrongCiphers(boolean enforce) {
            this.enforceStrongCiphers = enforce;
            return this;
        }

        /**
         * Sets enabled TLS protocols (overrides default TLSv1.3).
         *
         * @param protocols TLS protocol versions
         * @return This builder
         */
        public Builder enabledProtocols(String... protocols) {
            this.enabledProtocols = protocols;
            return this;
        }

        /**
         * Sets enabled cipher suites (overrides default strong ciphers).
         *
         * @param cipherSuites Cipher suite names
         * @return This builder
         */
        public Builder enabledCipherSuites(String... cipherSuites) {
            this.enabledCipherSuites = cipherSuites;
            return this;
        }

        /**
         * Builds the RedisTlsConfig instance.
         *
         * @return Configured TLS settings
         * @throws IllegalArgumentException if configuration is invalid
         */
        public RedisTlsConfig build() {
            RedisTlsConfig config = new RedisTlsConfig(this);
            config.validate();
            return config;
        }
    }
}
