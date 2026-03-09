package com.ghatana.platform.security.auth;

import com.ghatana.platform.security.auth.impl.CustomCredentials;
import com.ghatana.platform.security.auth.impl.TokenCredentials;
import com.ghatana.platform.security.auth.impl.UsernamePasswordCredentials;

import java.util.Objects;

/**
 * Base class for authentication credentials.
 * Different authentication methods should extend this class.
 * 
 * <p>This class provides a common base for all types of authentication credentials
 * used in the system, such as username/password, tokens, API keys, etc.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // For username/password authentication
 * Credentials credentials = new UsernamePasswordCredentials("user@example.com", "password");
 * 
 * // For token-based authentication
 * Credentials tokenCredentials = new TokenCredentials("jwt.token.here");
 * }</pre>
 * 
 * @see UsernamePasswordCredentials
 * @see TokenCredentials
 * @see CustomCredentials
 
 *
 * @doc.type abstract-class
 * @doc.purpose Credentials
 * @doc.layer core
 * @doc.pattern Component
*/
public abstract class Credentials {
    private final String type;
    
    /**
     * Creates a new instance of Credentials with the specified type.
     * 
     * @param type The type of credentials (e.g., "jwt", "basic", "oauth2")
     * @throws NullPointerException if type is null
     */
    protected Credentials(String type) {
        this.type = Objects.requireNonNull(type, "Type cannot be null");
    }
    
    /**
     * Gets the type of these credentials.
     * 
     * @return The credentials type (e.g., "jwt", "basic", "oauth2")
     */
    public String getType() {
        return type;
    }
    
    /**
     * Creates a new UsernamePasswordCredentials instance.
     * 
     * @param username The username
     * @param password The password
     * @return A new UsernamePasswordCredentials instance
     */
    public static UsernamePasswordCredentials of(String username, String password) {
        return new UsernamePasswordCredentials(username, password);
    }
    
    /**
     * Creates a new TokenCredentials instance.
     * 
     * @param token The authentication token
     * @return A new TokenCredentials instance
     */
    public static TokenCredentials of(String token) {
        return new TokenCredentials(token);
    }
    
    /**
     * Creates a new CustomCredentials instance with the specified type and data.
     * 
     * @param type The type of credentials
     * @param data The custom credential data
     * @return A new CustomCredentials instance
     */
    public static <T> CustomCredentials<T> of(String type, T data) {
        return new CustomCredentials<>(type, data);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Credentials that = (Credentials) o;
        return type.equals(that.type);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(type);
    }
    
    @Override
    public String toString() {
        return "Credentials{" +
                "type='" + type + '\'' +
                '}';
    }
}
