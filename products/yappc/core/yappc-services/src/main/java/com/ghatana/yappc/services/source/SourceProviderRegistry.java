package com.ghatana.yappc.services.source;

import io.activej.promise.Promise;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
