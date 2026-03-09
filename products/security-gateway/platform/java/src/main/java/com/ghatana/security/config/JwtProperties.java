package com.ghatana.security.config;

import io.activej.config.Config;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration properties for JWT (JSON Web Token) authentication.
 * 
 * <p>This class holds all JWT-related configuration options such as secret key,
 * token expiration, issuer, and other JWT-specific settings.</p>
 * 
 * <p>Example configuration (in application.yaml or application.properties):</p>
 * <pre>
 * security:
 *   jwt:
 *     secret: your-secret-key
 *     expiration: 86400  # in seconds
 *     issuer: your-issuer
 *     audience: your-audience
 *     leeway: 30  # in seconds
 *     refresh-expiration: 2592000  # 30 days in seconds
 * </pre>
 
 *
 * @doc.type class
 * @doc.purpose Jwt properties
 * @doc.layer core
 * @doc.pattern Component
*/
public class JwtProperties {
    private final String secret;
    private final Duration expiration;
    private final String issuer;
    private final String audience;
    private final Duration leeway;
    private final Duration refreshExpiration;
    
    /**
     * Creates a new JwtProperties instance from a Config object.
     * 
     * @param config The configuration source
     * @throws NullPointerException if config is null
     */
    public JwtProperties(Config config) {
        Objects.requireNonNull(config, "Config cannot be null");
        
        this.secret = config.get("secret", "");
        this.expiration = Duration.parse(config.get("expiration", "PT24H"));
        this.issuer = config.get("issuer", "");
        this.audience = config.get("audience", "");
        this.leeway = Duration.parse(config.get("leeway", "PT30S"));
        this.refreshExpiration = Duration.parse(config.get("refresh-expiration", "P30D"));
        
        // Validate required properties
        if (this.secret == null || this.secret.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT secret must be configured");
        }
        
        if (this.expiration.isNegative() || this.expiration.isZero()) {
            throw new IllegalArgumentException("JWT expiration must be a positive duration");
        }
    }
    
    /**
     * Gets the secret key used to sign JWT tokens.
     * 
     * @return The secret key
     */
    public String getSecret() {
        return secret;
    }
    
    /**
     * Gets the token expiration duration.
     * 
     * @return The expiration duration
     */
    public Duration getExpiration() {
        return expiration;
    }
    
    /**
     * Gets the token expiration in milliseconds.
     * 
     * @return The expiration in milliseconds
     */
    public long getExpirationMs() {
        return expiration.toMillis();
    }
    
    /**
     * Gets the token issuer.
     * 
     * @return The issuer
     */
    public String getIssuer() {
        return issuer;
    }
    
    /**
     * Gets the token audience.
     * 
     * @return The audience
     */
    public String getAudience() {
        return audience;
    }
    
    /**
     * Gets the clock skew leeway for token validation.
     * 
     * @return The leeway duration
     */
    public Duration getLeeway() {
        return leeway;
    }
    
    /**
     * Gets the refresh token expiration duration.
     * 
     * @return The refresh token expiration duration
     */
    public Duration getRefreshExpiration() {
        return refreshExpiration;
    }
    
    @Override
    public String toString() {
        return "JwtProperties{" +
                "secret=[HIDDEN]" +
                ", expiration=" + expiration +
                ", issuer='" + issuer + '\'' +
                ", audience='" + audience + '\'' +
                ", leeway=" + leeway +
                ", refreshExpiration=" + refreshExpiration +
                '}';
    }
}
