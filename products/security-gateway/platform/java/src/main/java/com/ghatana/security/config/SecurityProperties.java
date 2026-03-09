package com.ghatana.security.config;

import io.activej.config.Config;

import java.util.Objects;

/**
 * Main configuration class for security-related settings.
 * 
 * <p>This class serves as the root configuration container for all security-related
 * settings in the application. It's typically loaded from the application's
 * configuration system and provides type-safe access to security settings.</p>
 * 
 * <p>Example configuration (in application.yaml or application.properties):</p>
 * <pre>
 * security:
 *   enabled: true
 *   jwt:
 *     secret: your-secret-key
 *     expiration: 86400
 *     issuer: your-issuer
 *   auth:
 *     enabled: true
 *     providers: ["jwt", "basic"]
 *   cors:
 *     allowed-origins: ["*"]
 *     allowed-methods: ["GET", "POST", "PUT", "DELETE"]
 *   tls:
 *     enabled: true
 *     key-store: classpath:keystore.p12
 *     key-store-password: changeit
 *   encryption:
 *     algorithm: AES
 *     key-size: 256
 *   key-management:
 *     provider: "aws-kms"
 *     region: "us-east-1"
 * </pre>
 
 *
 * @doc.type class
 * @doc.purpose Security properties
 * @doc.layer core
 * @doc.pattern Component
*/
public class SecurityProperties {
    private final boolean enabled;
    private final JwtProperties jwt;
    private final AuthProperties auth;
    private final CorsProperties cors;
    private final TlsProperties tls;
    private final EncryptionProperties encryption;
    private final KeyManagementProperties keyManagement;
    
    /**
     * Creates a new SecurityProperties instance from an ActiveJ Config object.
     * 
     * @param config The ActiveJ configuration source
     * @throws NullPointerException if config is null
     */
    public SecurityProperties(Config config) {
        Objects.requireNonNull(config, "Config cannot be null");
        
        this.enabled = Boolean.parseBoolean(config.get("enabled", "true"));
        this.jwt = new JwtProperties(config.getChild("jwt"));
        this.auth = new AuthProperties(config.getChild("auth"));
        this.cors = new CorsProperties(config.getChild("cors"));
        this.tls = new TlsProperties(config.getChild("tls"));
        this.encryption = new EncryptionProperties(config.getChild("encryption"));
        this.keyManagement = new KeyManagementProperties(config.getChild("key-management"));
    }
    
    /**
     * Checks if security is enabled.
     * 
     * @return true if security is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the JWT configuration properties.
     * 
     * @return JWT properties
     */
    public JwtProperties getJwt() {
        return jwt;
    }
    
    /**
     * Gets the authentication configuration properties.
     * 
     * @return Authentication properties
     */
    public AuthProperties getAuth() {
        return auth;
    }
    
    /**
     * Gets the CORS configuration properties.
     * 
     * @return CORS properties
     */
    public CorsProperties getCors() {
        return cors;
    }
    
    /**
     * Gets the TLS/SSL configuration properties.
     * 
     * @return TLS properties
     */
    public TlsProperties getTls() {
        return tls;
    }
    
    /**
     * Gets the encryption configuration properties.
     * 
     * @return Encryption properties
     */
    public EncryptionProperties getEncryption() {
        return encryption;
    }
    
    /**
     * Gets the key management configuration properties.
     * 
     * @return Key management properties
     */
    public KeyManagementProperties getKeyManagement() {
        return keyManagement;
    }
    
    /**
     * Gets the JWT secret.
     * 
     * @return JWT secret
     */
    public String getJwtSecret() {
        return jwt.getSecret();
    }

    /**
     * Gets the JWT expiration time in milliseconds.
     * 
     * @return JWT expiration time in milliseconds
     */
    public long getJwtExpirationMs() {
        return jwt.getExpirationMs();
    }

    /**
     * Gets the JWT issuer.
     * 
     * @return JWT issuer
     */
    public String getJwtIssuer() {
        return jwt.getIssuer();
    }

    /**
     * Creates a new SecurityProperties instance from an ActiveJ Config object.
     * 
     * @param config The ActiveJ configuration source
     * @return SecurityProperties instance
     */
    public static SecurityProperties fromConfig(Config config) {
        return new SecurityProperties(config);
    }
    
    @Override
    public String toString() {
        return "SecurityProperties{" +
                "enabled=" + enabled +
                ", jwt=" + jwt +
                ", auth=" + auth +
                ", cors=" + cors +
                ", tls=" + tls +
                ", encryption=" + encryption +
                ", keyManagement=" + keyManagement +
                '}';
    }
}
