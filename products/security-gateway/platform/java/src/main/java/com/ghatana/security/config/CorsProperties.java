package com.ghatana.security.config;

import io.activej.config.Config;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configuration properties for Cross-Origin Resource Sharing (CORS).
 * 
 * <p>This class holds CORS configuration options that control which origins,
 * methods, and headers are allowed in cross-origin requests.</p>
 * 
 * <p>Example configuration (in application.yaml or application.properties):</p>
 * <pre>
 * security:
 *   cors:
 *     allowed-origins: ["https://example.com", "https://api.example.com"]
 *     allowed-methods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
 *     allowed-headers: ["Authorization", "Content-Type", "X-Requested-With"]
 *     exposed-headers: ["X-Auth-Token"]
 *     allow-credentials: true
 *     max-age: 3600  # in seconds
 * </pre>
 
 *
 * @doc.type class
 * @doc.purpose Cors properties
 * @doc.layer core
 * @doc.pattern Component
*/
public class CorsProperties {
    private final boolean enabled;
    private final List<String> allowedOrigins;
    private final List<String> allowedMethods;
    private final List<String> allowedHeaders;
    private final List<String> exposedHeaders;
    private final boolean allowCredentials;
    private final long maxAge; // in seconds
    
    /**
     * Creates a new CorsProperties instance from a Config object.
     * 
     * @param config The configuration source
     * @throws NullPointerException if config is null
     */
    public CorsProperties(Config config) {
        Objects.requireNonNull(config, "Config cannot be null");
        
        this.enabled = Boolean.parseBoolean(config.get("enabled", "true"));
        this.allowedOrigins = parseList(config.get("allowed-origins", "*"));
        this.allowedMethods = parseList(config.get("allowed-methods", 
            "GET,POST,PUT,DELETE,OPTIONS"));
        this.allowedHeaders = parseList(config.get("allowed-headers", 
            "Authorization,Content-Type,X-Requested-With"));
        this.exposedHeaders = parseList(config.get("exposed-headers", 
            "X-Auth-Token"));
        this.allowCredentials = Boolean.parseBoolean(config.get("allow-credentials", "true"));
        this.maxAge = Long.parseLong(config.get("max-age", "3600"));
    }
    
    private List<String> parseList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        return List.of(value.split("\\s*,\\s*"));
    }
    
    /**
     * Checks if CORS is enabled.
     * 
     * @return true if CORS is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the list of allowed origins.
     * 
     * @return List of allowed origins ("*" means all origins are allowed)
     */
    public List<String> getAllowedOrigins() {
        return Collections.unmodifiableList(allowedOrigins);
    }
    
    /**
     * Gets the list of allowed HTTP methods.
     * 
     * @return List of allowed HTTP methods
     */
    public List<String> getAllowedMethods() {
        return Collections.unmodifiableList(allowedMethods);
    }
    
    /**
     * Gets the list of allowed HTTP headers.
     * 
     * @return List of allowed HTTP headers
     */
    public List<String> getAllowedHeaders() {
        return Collections.unmodifiableList(allowedHeaders);
    }
    
    /**
     * Gets the list of exposed HTTP headers.
     * 
     * @return List of exposed HTTP headers
     */
    public List<String> getExposedHeaders() {
        return Collections.unmodifiableList(exposedHeaders);
    }
    
    /**
     * Checks if credentials are allowed in CORS requests.
     * 
     * @return true if credentials are allowed, false otherwise
     */
    public boolean isAllowCredentials() {
        return allowCredentials;
    }
    
    /**
     * Gets the maximum age (in seconds) that the results of a preflight request can be cached.
     * 
     * @return The maximum age in seconds
     */
    public long getMaxAge() {
        return maxAge;
    }
    
    /**
     * Checks if all origins are allowed.
     * 
     * @return true if all origins are allowed, false otherwise
     */
    public boolean isAnyOriginAllowed() {
        return allowedOrigins.contains("*");
    }
    
    @Override
    public String toString() {
        return "CorsProperties{" +
                "enabled=" + enabled +
                ", allowedOrigins=" + allowedOrigins +
                ", allowedMethods=" + allowedMethods +
                ", allowedHeaders=" + allowedHeaders +
                ", exposedHeaders=" + exposedHeaders +
                ", allowCredentials=" + allowCredentials +
                ", maxAge=" + maxAge +
                '}';
    }
}
