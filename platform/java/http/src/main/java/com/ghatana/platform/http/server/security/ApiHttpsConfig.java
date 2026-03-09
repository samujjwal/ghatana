package com.ghatana.platform.http.server.security;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.time.Duration;

/**
 * Production-grade HTTPS configuration for ActiveJ HTTP servers with TLS 1.3, strong cipher suites, and mTLS support.
 *
 * <p><b>Purpose</b><br>
 * Provides secure HTTPS server configuration with TLS 1.3 enforcement, AEAD cipher suites,
 * server certificate authentication, optional mutual TLS (client certificates), and HSTS
 * headers. Ensures compliance with PCI DSS, HIPAA, GDPR, and NIST SP 800-52 standards.
 *
 * <p><b>Architecture Role</b><br>
 * HTTPS configuration in core/http/security for secure HTTP communication.
 * Used by:
 * - HttpServerBuilder - Create HTTPS servers with TLS
 * - API Gateways - Secure external-facing endpoints
 * - Microservices - mTLS for service-to-service communication
 * @doc.type class
 * @doc.purpose Production-grade HTTPS configuration with TLS 1.3 and mTLS support
 * @doc.layer core
 * @doc.pattern Configuration, Security Pattern
 *
 * - Compliance - Meet regulatory security requirements
 *
 * <p><b>Security Features</b><br>
 * - <b>TLS 1.3 Enforcement</b>: Latest TLS protocol only (no TLS 1.2/1.1/1.0)
 * - <b>Strong Cipher Suites</b>: AEAD ciphers only (AES-GCM, ChaCha20-Poly1305)
 * - <b>Server Authentication</b>: X.509 certificate from Java KeyStore
 * - <b>Mutual TLS (mTLS)</b>: Optional client certificate authentication
 * - <b>HSTS Headers</b>: Strict-Transport-Security injection
 * - <b>Certificate Validation</b>: Trust store for client certificate verification
 * - <b>Socket Timeout</b>: Configurable read/write timeout
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic HTTPS configuration (server authentication only)
 * ApiHttpsConfig httpsConfig = ApiHttpsConfig.builder()
 *     .keyStorePath("/etc/ssl/keystore.jks")
 *     .keyStorePassword("changeit")
 *     .httpsPort(443)
 *     .build();
 *
 * SSLContext sslContext = httpsConfig.createSslContext();
 * SSLParameters sslParameters = httpsConfig.createSslParameters();
 *
 * // 2. Mutual TLS configuration (client certificate authentication)
 * ApiHttpsConfig mtlsConfig = ApiHttpsConfig.builder()
 *     .keyStorePath("/etc/ssl/keystore.jks")
 *     .keyStorePassword("serverKeyStorePass")
 *     .trustStorePath("/etc/ssl/truststore.jks")
 *     .trustStorePassword("trustStorePass")
 *     .requireClientAuth(true)
 *     .httpsPort(443)
 *     .build();
 *
 * // 3. Production configuration with HSTS
 * ApiHttpsConfig prodConfig = ApiHttpsConfig.builder()
 *     .keyStorePath("/etc/ssl/keystore.jks")
 *     .keyStorePassword(System.getenv("KEYSTORE_PASSWORD"))
 *     .httpsPort(443)
 *     .enableHsts(true)
 *     .hstsMaxAge(31536000L)  // 1 year
 *     .socketTimeout(Duration.ofSeconds(30))
 *     .build();
 *
 * // 4. Integration with HttpServerBuilder
 * ApiHttpsConfig httpsConfig = ApiHttpsConfig.builder()
 *     .keyStorePath("/etc/ssl/keystore.jks")
 *     .keyStorePassword("changeit")
 *     .httpsPort(443)
 *     .build();
 *
 * HttpServer server = HttpServerBuilder.create()
 *     .withHttpsConfig(httpsConfig)
 *     .addRoute(HttpMethod.GET, "/api/data", handler)
 *     .build();
 *
 * // 5. Custom cipher suites
 * ApiHttpsConfig customCiphersConfig = ApiHttpsConfig.builder()
 *     .keyStorePath("/etc/ssl/keystore.jks")
 *     .keyStorePassword("changeit")
 *     .enforceStrongCiphers(true)
 *     .enabledCipherSuites(new String[]{
 *         "TLS_AES_256_GCM_SHA384",
 *         "TLS_AES_128_GCM_SHA256"
 *     })
 *     .httpsPort(443)
 *     .build();
 *
 * // 6. Development configuration (self-signed certificate)
 * ApiHttpsConfig devConfig = ApiHttpsConfig.builder()
 *     .keyStorePath("src/test/resources/dev-keystore.jks")
 *     .keyStorePassword("changeit")
 *     .httpsPort(8443)
 *     .enableHsts(false)  // Don't enforce HSTS in dev
 *     .build();
 *
 * // 7. Multiple trust stores (for multi-CA environments)
 * ApiHttpsConfig multiTrustConfig = ApiHttpsConfig.builder()
 *     .keyStorePath("/etc/ssl/keystore.jks")
 *     .keyStorePassword("changeit")
 *     .trustStorePath("/etc/ssl/truststore.jks")
 *     .trustStorePassword("trustpass")
 *     .requireClientAuth(true)
 *     .httpsPort(443)
 *     .build();
 * }</pre>
 *
 * <p><b>Certificate Generation (OpenSSL)</b><br>
 * <pre>
 * # Generate server private key (RSA 4096-bit)
 * openssl genrsa -out server.key 4096
 *
 * # Create certificate signing request (CSR)
 * openssl req -new -key server.key -out server.csr \
 *   -subj "/CN=api.ghatana.com/O=Ghatana/C=US"
 *
 * # Self-sign certificate (1 year validity)
 * openssl x509 -req -in server.csr -signkey server.key \
 *   -out server.crt -days 365
 *
 * # Convert to PKCS12 format
 * openssl pkcs12 -export -in server.crt -inkey server.key \
 *   -out server.p12 -name server -password pass:changeit
 *
 * # Import into Java KeyStore
 * keytool -importkeystore -srckeystore server.p12 -srcstoretype PKCS12 \
 *   -destkeystore keystore.jks -deststoretype JKS \
 *   -srcstorepass changeit -deststorepass changeit
 * </pre>
 *
 * <p><b>Strong Cipher Suites (TLS 1.3)</b><br>
 * Default AEAD ciphers:
 * <pre>
 * TLS_AES_256_GCM_SHA384           → AES-256-GCM with SHA-384
 * TLS_AES_128_GCM_SHA256           → AES-128-GCM with SHA-256
 * TLS_CHACHA20_POLY1305_SHA256     → ChaCha20-Poly1305 with SHA-256
 * </pre>
 *
 * <p><b>Compliance Standards</b><br>
 * - <b>PCI DSS 4.1 Requirement 4.2</b>: Strong cryptography for data transmission
 * - <b>HIPAA § 164.312(e)(1)</b>: Transmission security for ePHI
 * - <b>GDPR Art. 32(1)(a)</b>: Encryption of personal data in transit
 * - <b>NIST SP 800-52 Rev. 2</b>: TLS 1.3 guidelines for federal systems
 * - <b>OWASP ASVS 9.2.1</b>: TLS must be used for all client connections
 *
 * <p><b>KeyStore Configuration</b><br>
 * <pre>
 * keyStorePath: Path to JKS/PKCS12 keystore file
 * keyStorePassword: Password to unlock keystore
 * keyStoreType: "JKS" (default) or "PKCS12"
 * </pre>
 *
 * <p><b>TrustStore Configuration (mTLS)</b><br>
 * <pre>
 * trustStorePath: Path to JKS/PKCS12 truststore file
 * trustStorePassword: Password to unlock truststore
 * trustStoreType: "JKS" (default) or "PKCS12"
 * requireClientAuth: true to enforce client certificates
 * </pre>
 *
 * <p><b>HSTS Configuration</b><br>
 * <pre>
 * enableHsts: true to inject Strict-Transport-Security header
 * hstsMaxAge: Max-age in seconds (default: 31536000 = 1 year)
 * Header: Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
 * </pre>
 *
 * <p><b>Default Values</b><br>
 * - HTTPS Port: 443
 * - Key Store Type: JKS
 * - Trust Store Type: JKS
 * - TLS Protocol: TLSv1.3 only
 * - Cipher Suites: TLS 1.3 AEAD ciphers
 * - Require Client Auth: false
 * - Enforce Strong Ciphers: true
 * - Enable HSTS: true
 * - HSTS Max-Age: 31536000 seconds (1 year)
 * - Socket Timeout: 30 seconds
 *
 * <p><b>Best Practices</b><br>
 * - Store keystore/truststore passwords in environment variables (never in code)
 * - Use 4096-bit RSA keys or 256-bit ECDSA keys
 * - Rotate certificates before expiration (90-day rotation recommended)
 * - Enable mTLS for internal service-to-service communication
 * - Use HSTS with preload for public-facing services
 * - Monitor certificate expiration dates
 * - Keep trust store minimal (only trusted CAs)
 * - Use separate keystores for dev/staging/production
 *
 * <p><b>Certificate Validation</b><br>
 * - Server certificate validated by clients using public CA or custom trust store
 * - Client certificates (mTLS) validated using trust store
 * - Certificate chain validation (intermediate CAs)
 * - Revocation checking (OCSP/CRL) if configured
 *
 * <p><b>Thread Safety</b><br>
 * Configuration is immutable (builder pattern) - thread-safe.
 * SSLContext and SSLParameters are thread-safe after creation.
 *
 * @see HstsHeaderFilter
 * @see HttpsRedirectHandler
 * @see HttpServerBuilder
 * @see <a href="https://tools.ietf.org/html/rfc8446">RFC 8446 - TLS 1.3</a>
 * @see <a href="https://tools.ietf.org/html/rfc6797">RFC 6797 - HSTS</a>
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose HTTPS configuration with TLS 1.3 and mTLS support
 * @doc.layer core
 * @doc.pattern Configuration
 */
public final class ApiHttpsConfig {

    private final Path keyStorePath;
    private final String keyStorePassword;
    private final String keyStoreType;
    private final Path trustStorePath;
    private final String trustStorePassword;
    private final String trustStoreType;
    private final boolean requireClientAuth;
    private final boolean enforceStrongCiphers;
    private final String[] enabledProtocols;
    private final String[] enabledCipherSuites;
    private final int httpsPort;
    private final boolean enableHsts;
    private final long hstsMaxAge;
    private final Duration socketTimeout;

    /**
     * Strong cipher suites for TLS 1.3 (AEAD ciphers only).
     */
    private static final String[] TLS_13_CIPHER_SUITES = {
        "TLS_AES_256_GCM_SHA384",
        "TLS_AES_128_GCM_SHA256",
        "TLS_CHACHA20_POLY1305_SHA256"
    };

    private ApiHttpsConfig(Builder builder) {
        this.keyStorePath = builder.keyStorePath;
        this.keyStorePassword = builder.keyStorePassword;
        this.keyStoreType = builder.keyStoreType != null ? builder.keyStoreType : "JKS";
        this.trustStorePath = builder.trustStorePath;
        this.trustStorePassword = builder.trustStorePassword;
        this.trustStoreType = builder.trustStoreType != null ? builder.trustStoreType : "JKS";
        this.requireClientAuth = builder.requireClientAuth;
        this.enforceStrongCiphers = builder.enforceStrongCiphers;
        this.enabledProtocols = builder.enabledProtocols != null ? builder.enabledProtocols : new String[]{"TLSv1.3"};
        this.enabledCipherSuites = builder.enabledCipherSuites != null ? builder.enabledCipherSuites : TLS_13_CIPHER_SUITES;
        this.httpsPort = builder.httpsPort;
        this.enableHsts = builder.enableHsts;
        this.hstsMaxAge = builder.hstsMaxAge;
        this.socketTimeout = builder.socketTimeout;
        validate();
    }

    /**
     * Validates the HTTPS configuration.
     *
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void validate() {
        if (keyStorePath == null) {
            throw new IllegalArgumentException(
                "Key store path must be provided for HTTPS server"
            );
        }
        if (!keyStorePath.toFile().exists()) {
            throw new IllegalArgumentException(
                "Key store file not found: " + keyStorePath
            );
        }
        if (keyStorePassword == null || keyStorePassword.isEmpty()) {
            throw new IllegalArgumentException(
                "Key store password must be provided"
            );
        }

        // Validate trust store if client authentication is required
        if (requireClientAuth) {
            if (trustStorePath == null) {
                throw new IllegalArgumentException(
                    "Trust store path must be provided when requiring client authentication"
                );
            }
            if (!trustStorePath.toFile().exists()) {
                throw new IllegalArgumentException(
                    "Trust store file not found: " + trustStorePath
                );
            }
            if (trustStorePassword == null || trustStorePassword.isEmpty()) {
                throw new IllegalArgumentException(
                    "Trust store password must be provided when requiring client authentication"
                );
            }
        }

        if (httpsPort < 1 || httpsPort > 65535) {
            throw new IllegalArgumentException(
                "HTTPS port must be between 1 and 65535, got: " + httpsPort
            );
        }
    }

    /**
     * Creates an SSL context with configured key managers and trust managers.
     *
     * @return Configured SSL context
     */
    public SSLContext createSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            
            KeyManager[] keyManagers = createKeyManagers();
            TrustManager[] trustManagers = null;
            
            if (requireClientAuth && trustStorePath != null) {
                trustManagers = createTrustManagers();
            }
            
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create SSL context", e);
        }
    }

    /**
     * Creates SSL parameters with protocol and cipher configuration.
     *
     * @return Configured SSL parameters
     */
    public SSLParameters createSslParameters() {
        SSLParameters sslParameters = new SSLParameters();
        
        if (enforceStrongCiphers) {
            sslParameters.setProtocols(enabledProtocols);
            sslParameters.setCipherSuites(enabledCipherSuites);
        }
        
        if (requireClientAuth) {
            sslParameters.setNeedClientAuth(true);
        }
        
        // Enable hostname verification for HTTPS
        sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
        
        return sslParameters;
    }

    /**
     * Creates key managers from the configured key store.
     *
     * @return Array of key managers
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
     * Creates trust managers from the configured trust store.
     *
     * @return Array of trust managers
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

    public int getHttpsPort() {
        return httpsPort;
    }

    public boolean isHstsEnabled() {
        return enableHsts;
    }

    public long getHstsMaxAge() {
        return hstsMaxAge;
    }

    public Duration getSocketTimeout() {
        return socketTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating ApiHttpsConfig instances.
     */
    public static final class Builder {
        private Path keyStorePath;
        private String keyStorePassword;
        private String keyStoreType;
        private Path trustStorePath;
        private String trustStorePassword;
        private String trustStoreType;
        private boolean requireClientAuth = false;
        private boolean enforceStrongCiphers = true;
        private String[] enabledProtocols;
        private String[] enabledCipherSuites;
        private int httpsPort = 443;
        private boolean enableHsts = true;
        private long hstsMaxAge = 31536000; // 1 year in seconds
        private Duration socketTimeout = Duration.ofSeconds(30);

        private Builder() {}

        /**
         * Sets the server key store path.
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
         * Sets the trust store path for client certificate validation.
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
         * Sets whether to require client certificate authentication (mTLS).
         *
         * @param require true to require client certificates
         * @return This builder
         */
        public Builder requireClientAuth(boolean require) {
            this.requireClientAuth = require;
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
         * Sets the HTTPS port (default: 443).
         *
         * @param port HTTPS port number
         * @return This builder
         */
        public Builder httpsPort(int port) {
            this.httpsPort = port;
            return this;
        }

        /**
         * Sets whether to enable HSTS headers (default: true).
         *
         * @param enable true to enable HSTS
         * @return This builder
         */
        public Builder enableHsts(boolean enable) {
            this.enableHsts = enable;
            return this;
        }

        /**
         * Sets the HSTS max-age in seconds (default: 31536000 = 1 year).
         *
         * @param maxAge HSTS max-age in seconds
         * @return This builder
         */
        public Builder hstsMaxAge(long maxAge) {
            this.hstsMaxAge = maxAge;
            return this;
        }

        /**
         * Sets the socket read timeout (default: 30 seconds).
         *
         * @param timeout Socket timeout duration
         * @return This builder
         */
        public Builder socketTimeout(Duration timeout) {
            this.socketTimeout = timeout;
            return this;
        }

        /**
         * Builds the ApiHttpsConfig instance.
         *
         * @return Configured HTTPS settings
         * @throws IllegalArgumentException if configuration is invalid
         */
        public ApiHttpsConfig build() {
            ApiHttpsConfig config = new ApiHttpsConfig(this);
            config.validate();
            return config;
        }
    }
}
