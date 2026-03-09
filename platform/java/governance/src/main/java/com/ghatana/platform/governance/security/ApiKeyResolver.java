package com.ghatana.platform.governance.security;

import java.util.Optional;

/**
 * Resolves an API key to a {@link Principal} with tenant and roles.
 * This is a functional interface that can be implemented to provide custom API key resolution logic.
 */
/**
 * Api key resolver.
 *
 * @doc.type interface
 * @doc.purpose Api key resolver
 * @doc.layer platform
 * @doc.pattern Interface
 */
@FunctionalInterface
public interface ApiKeyResolver {
    /**
     * Resolves an API key to a principal.
     *
     * @param apiKey The API key to resolve
     * @return An Optional containing the principal if the key is valid, or empty otherwise
     */
    Optional<Principal> resolve(String apiKey);
}
