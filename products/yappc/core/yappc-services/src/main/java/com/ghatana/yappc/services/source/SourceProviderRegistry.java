package com.ghatana.yappc.services.source;

import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.SourceLocator;
import io.activej.promise.Promise;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type class
 * @doc.purpose Registry of Java source providers used by import orchestration
 * @doc.layer service
 * @doc.pattern Registry
 */
public final class SourceProviderRegistry {

    private final Map<String, SourceProvider> providers = new LinkedHashMap<>();

    public SourceProviderRegistry register(SourceProvider provider) {
        SourceProvider validated = Objects.requireNonNull(provider, "provider must not be null");
        providers.put(validated.providerId(), validated);
        return this;
    }

    public static SourceProviderRegistry defaultRegistry() {
        return new SourceProviderRegistry()
            .register(new GitHubSourceProvider())
            .register(new LocalFolderSourceProvider())
            .register(new ArchiveSourceProvider());
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
