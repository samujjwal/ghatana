package com.ghatana.platform.security.auth;

import com.ghatana.platform.security.model.User;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A composite authentication provider that delegates to multiple authentication providers.
 * 
 * <p>This provider maintains a list of delegate providers and attempts authentication
 * with each one in order of priority (highest first) until one succeeds or all fail.</p>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create individual providers
 * AuthenticationProvider jwtProvider = new JwtAuthenticationProvider(jwtService);
 * AuthenticationProvider basicAuthProvider = new BasicAuthenticationProvider(userService);
 * 
 * // Create composite provider
 * AuthenticationProvider composite = new CompositeAuthenticationProvider(jwtProvider, basicAuthProvider);
 * 
 * // Or use the factory method
 * AuthenticationProvider composite2 = AuthenticationProvider.composite(jwtProvider, basicAuthProvider);
 * }</pre>
 
 *
 * @doc.type class
 * @doc.purpose Composite authentication provider
 * @doc.layer core
 * @doc.pattern Provider
*/
public class CompositeAuthenticationProvider implements AuthenticationProvider {
    private final List<AuthenticationProvider> providers;
    
    /**
     * Creates a new composite authentication provider with the specified delegates.
     * 
     * @param providers The authentication providers to include in the composite
     * @throws NullPointerException if providers is null or contains null
     */
    public CompositeAuthenticationProvider(AuthenticationProvider... providers) {
        this(Arrays.asList(Objects.requireNonNull(providers, "Providers array cannot be null")));
    }
    
    /**
     * Creates a new composite authentication provider with the specified delegates.
     * 
     * @param providers The authentication providers to include in the composite
     * @throws NullPointerException if providers is null or contains null
     */
    public CompositeAuthenticationProvider(Iterable<AuthenticationProvider> providers) {
        Objects.requireNonNull(providers, "Providers iterable cannot be null");
        
        // Sort providers by priority (highest first)
        this.providers = StreamSupport.stream(providers.spliterator(), false)
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(AuthenticationProvider::getPriority).reversed())
            .collect(Collectors.toList());
        
        if (this.providers.isEmpty()) {
            throw new IllegalArgumentException("At least one provider must be specified");
        }
    }
    
    @Override
    public Promise<Optional<User>> authenticate(Credentials credentials) {
        Objects.requireNonNull(credentials, "Credentials cannot be null");
        
        // Find the first provider that supports the credentials type
        return findSupportingProvider(credentials)
            .map(provider -> provider.authenticate(credentials))
            .orElseGet(() -> Promise.of(Optional.empty()));
    }
    
    @Override
    public boolean supports(String type) {
        return providers.stream().anyMatch(p -> p.supports(type));
    }
    
    @Override
    public int getPriority() {
        // Return the highest priority among all providers
        return providers.stream()
            .mapToInt(AuthenticationProvider::getPriority)
            .max()
            .orElse(0);
    }
    
    /**
     * Gets an unmodifiable list of all providers in this composite.
     * 
     * @return An unmodifiable list of providers
     */
    public List<AuthenticationProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }
    
    private Optional<AuthenticationProvider> findSupportingProvider(Credentials credentials) {
        return providers.stream()
            .filter(p -> p.supports(credentials.getType()))
            .findFirst();
    }
    
    /**
     * Creates a new builder for CompositeAuthenticationProvider.
     * 
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for CompositeAuthenticationProvider.
     */
    public static class Builder {
        private final List<AuthenticationProvider> providers = new ArrayList<>();
        
        /**
         * Adds a provider to the composite.
         * 
         * @param provider The provider to add
         * @return This builder instance
         * @throws NullPointerException if provider is null
         */
        public Builder withProvider(AuthenticationProvider provider) {
            providers.add(Objects.requireNonNull(provider, "Provider cannot be null"));
            return this;
        }
        
        /**
         * Adds multiple providers to the composite.
         * 
         * @param providers The providers to add
         * @return This builder instance
         * @throws NullPointerException if providers is null or contains null
         */
        public Builder withProviders(AuthenticationProvider... providers) {
            for (AuthenticationProvider provider : providers) {
                withProvider(provider);
            }
            return this;
        }
        
        /**
         * Adds multiple providers to the composite.
         * 
         * @param providers The providers to add
         * @return This builder instance
         * @throws NullPointerException if providers is null or contains null
         */
        public Builder withProviders(Iterable<AuthenticationProvider> providers) {
            Objects.requireNonNull(providers, "Providers cannot be null");
            for (AuthenticationProvider provider : providers) {
                withProvider(provider);
            }
            return this;
        }
        
        /**
         * Builds the CompositeAuthenticationProvider.
         * 
         * @return A new CompositeAuthenticationProvider instance
         * @throws IllegalStateException if no providers have been added
         */
        public CompositeAuthenticationProvider build() {
            if (providers.isEmpty()) {
                throw new IllegalStateException("At least one provider must be specified");
            }
            return new CompositeAuthenticationProvider(providers);
        }
    }
}
