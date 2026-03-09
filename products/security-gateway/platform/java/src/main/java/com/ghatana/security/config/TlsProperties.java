package com.ghatana.security.config;

import io.activej.config.Config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Configuration properties for TLS/SSL settings.
 * 
 * <p>This class holds configuration options for enabling and configuring
 * Transport Layer Security (TLS) for secure communication.</p>
 * 
 * <p>Example configuration (in application.yaml or application.properties):</p>
 * <pre>
 * security:
 *   tls:
 *     enabled: true
 *     key-store: classpath:keystore.p12
 *     key-store-password: changeit
 *     key-store-type: PKCS12
 *     key-alias: mykey
 *     key-password: keypassword
 *     trust-store: classpath:truststore.jks
 *     trust-store-password: changeit
 *     trust-store-type: JKS
 *     protocol: TLSv1.3
 *     ciphers: TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256
 *     client-auth: want
 * </pre>
 */
public class TlsProperties {
    private final boolean enabled;
    private final String keyStore;
    private final String keyStorePassword;
    private final String keyStoreType;
    private final String keyAlias;
    private final String keyPassword;
    private final String trustStore;
    private final String trustStorePassword;
    private final String trustStoreType;
    private final String protocol;
    private final String[] ciphers;
    private final ClientAuth clientAuth;
    
    /**
     * Client authentication mode.
     
 *
 * @doc.type enum
 * @doc.purpose Client auth
 * @doc.layer core
 * @doc.pattern Enumeration
*/
    public enum ClientAuth {
        /** Client authentication is not wanted. */
        NONE,
        /** Client authentication is wanted but not mandatory. */
        WANT,
        /** Client authentication is needed and mandatory. */
        NEED
    }
    
    /**
     * Creates a new TlsProperties instance from a Config object.
     * 
     * @param config The configuration source
     * @throws NullPointerException if config is null
     */
    public TlsProperties(Config config) {
        Objects.requireNonNull(config, "Config cannot be null");
        
        this.enabled = Boolean.parseBoolean(config.get("enabled", "false"));
        this.keyStore = config.get("key-store", "");
        this.keyStorePassword = config.get("key-store-password", "");
        this.keyStoreType = config.get("key-store-type", "PKCS12");
        this.keyAlias = config.get("key-alias", "");
        this.keyPassword = config.get("key-password", keyStorePassword);
        this.trustStore = config.get("trust-store", "");
        this.trustStorePassword = config.get("trust-store-password", "");
        this.trustStoreType = config.get("trust-store-type", "JKS");
        this.protocol = config.get("protocol", "TLSv1.3");
        
        String ciphersValue = config.get("ciphers", 
            "TLS_AES_256_GCM_SHA384,TLS_AES_128_GCM_SHA256," +
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384," +
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384");
        this.ciphers = ciphersValue.split(",");
        
        this.clientAuth = ClientAuth.valueOf(
            config.get("client-auth", "NONE").toUpperCase());
    }
    
    /**
     * Checks if TLS is enabled.
     * 
     * @return true if TLS is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the path to the key store file.
     * 
     * @return The key store path
     */
    public String getKeyStore() {
        return keyStore;
    }
    
    /**
     * Gets the key store password.
     * 
     * @return The key store password
     */
    public String getKeyStorePassword() {
        return keyStorePassword;
    }
    
    /**
     * Gets the key store type.
     * 
     * @return The key store type (e.g., PKCS12, JKS)
     */
    public String getKeyStoreType() {
        return keyStoreType;
    }
    
    /**
     * Gets the alias of the key to use from the key store.
     * 
     * @return The key alias
     */
    public String getKeyAlias() {
        return keyAlias;
    }
    
    /**
     * Gets the password for the key in the key store.
     * 
     * @return The key password
     */
    public String getKeyPassword() {
        return keyPassword;
    }
    
    /**
     * Gets the path to the trust store file.
     * 
     * @return The trust store path
     */
    public String getTrustStore() {
        return trustStore;
    }
    
    /**
     * Gets the trust store password.
     * 
     * @return The trust store password
     */
    public String getTrustStorePassword() {
        return trustStorePassword;
    }
    
    /**
     * Gets the trust store type.
     * 
     * @return The trust store type (e.g., JKS, PKCS12)
     */
    public String getTrustStoreType() {
        return trustStoreType;
    }
    
    /**
     * Gets the SSL/TLS protocol to use.
     * 
     * @return The protocol (e.g., TLSv1.3, TLSv1.2)
     */
    public String getProtocol() {
        return protocol;
    }
    
    /**
     * Gets the enabled cipher suites.
     * 
     * @return Array of cipher suite names
     */
    public String[] getCiphers() {
        return ciphers.clone();
    }
    
    /**
     * Gets the client authentication mode.
     * 
     * @return The client authentication mode
     */
    public ClientAuth getClientAuth() {
        return clientAuth;
    }
    
    /**
     * Checks if client authentication is required.
     * 
     * @return true if client authentication is required, false otherwise
     */
    public boolean isClientAuthRequired() {
        return clientAuth == ClientAuth.NEED;
    }
    
    /**
     * Checks if client authentication is wanted (but not required).
     * 
     * @return true if client authentication is wanted, false otherwise
     */
    public boolean isClientAuthWanted() {
        return clientAuth == ClientAuth.WANT;
    }
    
    /**
     * Gets the path to the key store file.
     * 
     * @return The key store path
     */
    public Path getKeyStorePath() {
        return Paths.get(keyStore);
    }
    
    /**
     * Gets the path to the trust store file.
     * 
     * @return The trust store path
     */
    public Path getTrustStorePath() {
        return Paths.get(trustStore);
    }
    
    /**
     * Checks if the socket option SO_REUSEADDR is enabled.
     * 
     * @return true if SO_REUSEADDR is enabled, false otherwise
     */
    public boolean isReuseAddress() {
        return true; // Default value
    }
    
    /**
     * Gets the backlog for the server socket.
     * 
     * @return The backlog value
     */
    public int getBacklog() {
        return 1000; // Default value
    }
    
    /**
     * Checks if the socket option TCP_NODELAY is enabled.
     * 
     * @return true if TCP_NODELAY is enabled, false otherwise
     */
    public boolean isTcpNoDelay() {
        return true; // Default value
    }
    
    /**
     * Checks if the socket option SO_KEEPALIVE is enabled.
     * 
     * @return true if SO_KEEPALIVE is enabled, false otherwise
     */
    public boolean isTcpKeepAlive() {
        return true; // Default value
    }
    
    /**
     * Gets the receive buffer size for the socket.
     * 
     * @return The receive buffer size
     */
    public int getReceiveBufferSize() {
        return 8192; // Default value
    }
    
    /**
     * Gets the send buffer size for the socket.
     * 
     * @return The send buffer size
     */
    public int getSendBufferSize() {
        return 8192; // Default value
    }
    
    @Override
    public String toString() {
        return "TlsProperties{" +
                "enabled=" + enabled +
                ", keyStore='" + keyStore + '\'' +
                ", keyStoreType='" + keyStoreType + '\'' +
                ", keyAlias='" + keyAlias + '\'' +
                ", trustStore='" + trustStore + '\'' +
                ", trustStoreType='" + trustStoreType + '\'' +
                ", protocol='" + protocol + '\'' +
                ", clientAuth=" + clientAuth +
                '}';
    }
}
