package com.ghatana.platform.security.auth.impl;

import com.ghatana.platform.security.auth.Credentials;

import java.util.Objects;

/**
 * Credentials implementation for token-based authentication.
 * 
 * <p>This class represents authentication credentials consisting of a single token,
 * typically a JWT (JSON Web Token) or similar bearer token.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create token credentials
 * TokenCredentials credentials = new TokenCredentials("jwt.token.here");
 * 
 * // Or using the factory method
 * Credentials credentials = Credentials.of("jwt.token.here");
 * }</pre>
 
 *
 * @doc.type class
 * @doc.purpose Token credentials
 * @doc.layer core
 * @doc.pattern Component
*/
public class TokenCredentials extends Credentials {
    private final String token;
    
    /**
     * Creates a new TokenCredentials instance.
     * 
     * @param token The authentication token (e.g., JWT)
     * @throws NullPointerException if token is null
     */
    public TokenCredentials(String token) {
        super("token");
        this.token = Objects.requireNonNull(token, "Token cannot be null");
    }
    
    /**
     * Gets the authentication token.
     * 
     * @return The authentication token
     */
    public String getToken() {
        return token;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        TokenCredentials that = (TokenCredentials) o;
        return token.equals(that.token);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), token);
    }
    
    @Override
    public String toString() {
        return "TokenCredentials{" +
                "type='" + getType() + '\'' +
                ", token=[HIDDEN]" +
                '}';
    }
}
