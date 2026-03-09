package com.ghatana.platform.security.auth;

import com.ghatana.platform.security.model.User;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Interface for authentication providers that can authenticate users and provide user details.
 * 
 * <p>Implementations of this interface are responsible for validating credentials
 * and returning an authenticated user if successful.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create an authentication provider
 * AuthenticationProvider authProvider = new JwtAuthenticationProvider(jwtService);
 * 
 * // Authenticate with credentials
 * Credentials credentials = new TokenCredentials("jwt.token.here");
 * Promise<Optional<User>> authResult = authProvider.authenticate(credentials);
 * 
 * // Handle authentication result
 * authResult.whenComplete((userOpt, e) -> {
 *     if (e != null) {
 *         // Handle error
 *         logger.error("Authentication failed", e);
 *         return;
 *     }
 *     
 *     userOpt.ifPresentOrElse(
 *         user -> {
 *             // Authentication successful
 *             logger.info("Authenticated user: {}", user.getUsername());
 *         },
 *         () -> {
 *             // Authentication failed
 *             logger.warn("Invalid credentials");
 *         }
 *     );
 * });
 * }</pre>
 * 
 * @see User
 * @see Credentials
 * @see io.activej.promise.Promise
 
 *
 * @doc.type interface
 * @doc.purpose Authentication provider
 * @doc.layer core
 * @doc.pattern Provider
*/
public interface AuthenticationProvider {
    
    /**
     * Authenticates a user with the provided credentials.
     * 
     * @param credentials The authentication credentials (e.g., username/password, JWT token, etc.)
     * @return A promise that resolves to an Optional containing the authenticated user if successful,
     *         or an empty Optional if authentication fails
     * @throws NullPointerException if credentials is null
     */
    Promise<Optional<User>> authenticate(Credentials credentials);
    
    /**
     * Checks if this provider supports the specified credentials type.
     * 
     * @param type The credentials type to check
     * @return true if this provider can authenticate with the specified credentials type, false otherwise
     */
    boolean supports(String type);
    
    /**
     * Gets the priority of this provider. Providers with higher priority values are checked first.
     * 
     * @return The provider priority
     */
    default int getPriority() {
        return 0;
    }
    
    /**
     * Creates a new composite authentication provider that delegates to multiple providers.
     * 
     * @param providers The providers to include in the composite
     * @return A new composite authentication provider
     */
    static AuthenticationProvider composite(AuthenticationProvider... providers) {
        return new CompositeAuthenticationProvider(providers);
    }
    
    /**
     * Creates a new composite authentication provider that delegates to multiple providers.
     * 
     * @param providers The providers to include in the composite
     * @return A new composite authentication provider
     */
    static AuthenticationProvider composite(Iterable<AuthenticationProvider> providers) {
        return new CompositeAuthenticationProvider(providers);
    }
}
