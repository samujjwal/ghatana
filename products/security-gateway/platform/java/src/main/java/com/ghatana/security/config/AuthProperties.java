package com.ghatana.security.config;

import io.activej.config.Config;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configuration properties for authentication settings.
 * 
 * <p>This class holds configuration options related to authentication,
 * including enabled authentication providers, password policies, and
 * account lockout settings.</p>
 * 
 * <p>Example configuration (in application.yaml or application.properties):</p>
 * <pre>
 * security:
 *   auth:
 *     enabled: true
 *     providers: ["jwt", "basic"]
 *     password:
 *       min-length: 8
 *       require-uppercase: true
 *       require-lowercase: true
 *       require-digit: true
 *       require-special-char: true
 *     lockout:
 *       max-attempts: 5
 *       duration: 1800  # in seconds (30 minutes)
 *     session:
 *       timeout: 1800  # in seconds (30 minutes)
 *       max-sessions: 5
 * </pre>
 
 *
 * @doc.type class
 * @doc.purpose Auth properties
 * @doc.layer core
 * @doc.pattern Component
*/
public class AuthProperties {
    private final boolean enabled;
    private final List<String> providers;
    private final PasswordProperties password;
    private final LockoutProperties lockout;
    private final SessionProperties session;
    
    /**
     * Creates a new AuthProperties instance from a Config object.
     * 
     * @param config The configuration source
     * @throws NullPointerException if config is null
     */
    public AuthProperties(Config config) {
        Objects.requireNonNull(config, "Config cannot be null");
        
        this.enabled = Boolean.parseBoolean(config.get("enabled", "true"));
        this.providers = parseCommaSeparatedList(config.get("providers", "jwt"));
        this.password = new PasswordProperties(config.getChild("password"));
        this.lockout = new LockoutProperties(config.getChild("lockout"));
        this.session = new SessionProperties(config.getChild("session"));
    }
    
    private List<String> parseCommaSeparatedList(String value) {
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        return List.of(value.split("\\s*,\\s*"));
    }
    
    /**
     * Checks if authentication is enabled.
     * 
     * @return true if authentication is enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the list of enabled authentication providers.
     * 
     * @return List of provider names
     */
    public List<String> getProviders() {
        return Collections.unmodifiableList(providers);
    }
    
    /**
     * Gets the password policy configuration.
     * 
     * @return Password properties
     */
    public PasswordProperties getPassword() {
        return password;
    }
    
    /**
     * Gets the account lockout configuration.
     * 
     * @return Lockout properties
     */
    public LockoutProperties getLockout() {
        return lockout;
    }
    
    /**
     * Gets the session management configuration.
     * 
     * @return Session properties
     */
    public SessionProperties getSession() {
        return session;
    }
    
    @Override
    public String toString() {
        return "AuthProperties{" +
                "enabled=" + enabled +
                ", providers=" + providers +
                ", password=" + password +
                ", lockout=" + lockout +
                ", session=" + session +
                '}';
    }
    
    /**
     * Password policy configuration.
     */
    public static class PasswordProperties {
        private final int minLength;
        private final boolean requireUppercase;
        private final boolean requireLowercase;
        private final boolean requireDigit;
        private final boolean requireSpecialChar;
        
        public PasswordProperties(Config config) {
            this.minLength = Integer.parseInt(config.get("min-length", "8"));
            this.requireUppercase = Boolean.parseBoolean(config.get("require-uppercase", "true"));
            this.requireLowercase = Boolean.parseBoolean(config.get("require-lowercase", "true"));
            this.requireDigit = Boolean.parseBoolean(config.get("require-digit", "true"));
            this.requireSpecialChar = Boolean.parseBoolean(config.get("require-special-char", "true"));
        }
        
        public int getMinLength() {
            return minLength;
        }
        
        public boolean isRequireUppercase() {
            return requireUppercase;
        }
        
        public boolean isRequireLowercase() {
            return requireLowercase;
        }
        
        public boolean isRequireDigit() {
            return requireDigit;
        }
        
        public boolean isRequireSpecialChar() {
            return requireSpecialChar;
        }
        
        @Override
        public String toString() {
            return "PasswordProperties{" +
                    "minLength=" + minLength +
                    ", requireUppercase=" + requireUppercase +
                    ", requireLowercase=" + requireLowercase +
                    ", requireDigit=" + requireDigit +
                    ", requireSpecialChar=" + requireSpecialChar +
                    '}';
        }
    }
    
    /**
     * Account lockout configuration.
     */
    public static class LockoutProperties {
        private final int maxAttempts;
        private final long duration; // in seconds
        
        public LockoutProperties(Config config) {
            this.maxAttempts = Integer.parseInt(config.get("max-attempts", "5"));
            this.duration = Long.parseLong(config.get("duration", "1800")); // 30 minutes
        }
        
        public int getMaxAttempts() {
            return maxAttempts;
        }
        
        public long getDuration() {
            return duration;
        }
        
        @Override
        public String toString() {
            return "LockoutProperties{" +
                    "maxAttempts=" + maxAttempts +
                    ", duration=" + duration +
                    '}';
        }
    }
    
    /**
     * Session management configuration.
     */
    public static class SessionProperties {
        private final long timeout; // in seconds
        private final int maxSessions;
        
        public SessionProperties(Config config) {
            this.timeout = Long.parseLong(config.get("timeout", "1800")); // 30 minutes
            this.maxSessions = Integer.parseInt(config.get("max-sessions", "5"));
        }
        
        public long getTimeout() {
            return timeout;
        }
        
        public int getMaxSessions() {
            return maxSessions;
        }
        
        @Override
        public String toString() {
            return "SessionProperties{" +
                    "timeout=" + timeout +
                    ", maxSessions=" + maxSessions +
                    '}';
        }
    }
}
