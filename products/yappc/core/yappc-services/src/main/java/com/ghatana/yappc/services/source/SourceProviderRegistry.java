package com.ghatana.yappc.services.source;

import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.SourceLocator;
import io.activej.promise.Promise;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Registry for source provider implementations with capability introspection
 * @doc.layer service
 * @doc.pattern Registry
 * 
 * P1-14: Updated to include GitLab provider capability.
 */
public final class SourceProviderRegistry {

    private final Map<String, SourceProvider> providers = new HashMap<>();

    public SourceProviderRegistry() {
    }

    public SourceProviderRegistry register(SourceProvider provider) {
        SourceProvider validated = Objects.requireNonNull(provider, "provider must not be null");
        providers.put(validated.providerId(), validated);
        return this;
    }

    public static SourceProviderRegistry defaultRegistry() {
        return defaultRegistry(SourceCredentialResolver.envBacked());
    }

    public static SourceProviderRegistry defaultRegistry(SourceCredentialRepository credentialRepository) {
        Objects.requireNonNull(credentialRepository, "credentialRepository must not be null");
        return defaultRegistry(SourceCredentialResolver.governed(credentialRepository));
    }

    public static SourceProviderRegistry defaultRegistry(SourceCredentialResolver credentialResolver) {
        return new SourceProviderRegistry()
            .register(new GitHubSourceProvider(java.net.http.HttpClient.newHttpClient(), new com.fasterxml.jackson.databind.ObjectMapper(), credentialResolver))
            .register(new LocalFolderSourceProvider())
            .register(new ArchiveSourceProvider())
            // P1-14: Register GitLab source provider
            .register(new GitLabSourceProvider(java.net.http.HttpClient.newHttpClient(), new com.fasterxml.jackson.databind.ObjectMapper(), credentialResolver));
    }

    public Collection<SourceProvider> providers() {
        return providers.values();
    }

    public Map<String, Map<String, Object>> capabilities() {
        Map<String, Map<String, Object>> response = new LinkedHashMap<>();
        for (SourceProvider provider : providers.values()) {
            response.put(provider.providerId(), provider.capabilities());
        }
        return response;
    }

    /**
     * Find a provider by its provider ID.
     *
     * @param providerId the provider identifier
     * @return Optional containing the provider if found
     */
    public Optional<SourceProvider> resolve(String providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    /**
     * Find a provider that can handle the given locator.
     *
     * @param locator the source locator
     * @return Optional containing the provider if found
     */
    public Optional<SourceProvider> findProvider(SourceLocator locator) {
        if (locator == null) {
            return Optional.empty();
        }
        // Exact provider ID match first; only fall back to canHandle when provider is missing/unknown.
        SourceProvider exact = providers.get(locator.provider());
        if (exact != null) {
            return Optional.of(exact);
        }
        for (SourceProvider provider : providers.values()) {
            if (provider.canHandle(locator)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }

    /**
     * Get a provider by ID or throw if not found.
     *
     * @param providerId the provider identifier
     * @return the provider
     * @throws IllegalArgumentException if provider not found
     */
    public SourceProvider getProvider(String providerId) {
        return resolve(providerId)
            .orElseThrow(() -> new IllegalArgumentException("No provider found for: " + providerId));
    }

    /**
     * Resolve a source locator to a repository snapshot using the appropriate provider.
     *
     * @param locator the source locator
     * @param scope the scope context
     * @return promise of the repository snapshot
     */
    public Promise<RepositorySnapshot> resolve(SourceLocator locator, SourceProvider.ScopeContext scope) {
        if (locator == null) {
            return Promise.ofException(new IllegalArgumentException("Source locator must not be null"));
        }
        SourceProvider exact = providers.get(locator.provider());
        if (exact != null) {
            return exact.resolve(locator, scope);
        }
        for (SourceProvider provider : providers.values()) {
            if (provider.canHandle(locator)) {
                return provider.resolve(locator, scope);
            }
        }
        return Promise.ofException(new IllegalArgumentException(
            "No source provider can resolve locator provider=" + locator.provider()
        ));
    }
}
