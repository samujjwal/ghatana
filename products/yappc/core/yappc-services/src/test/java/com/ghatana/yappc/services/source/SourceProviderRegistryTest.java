package com.ghatana.yappc.services.source;

import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.domain.source.SourceLocator;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for SourceProviderRegistry - registry of Java source providers
 * @doc.layer test
 * @doc.pattern UnitTest
 */
@DisplayName("SourceProviderRegistry Tests")
@ExtendWith(MockitoExtension.class)
class SourceProviderRegistryTest {

    private SourceProviderRegistry registry;

    @Mock
    private SourceProvider githubProvider;

    @Mock
    private SourceProvider localProvider;

    @Mock
    private SourceLocator locator;

    @BeforeEach
    void setUp() {
        registry = new SourceProviderRegistry();
    }

    @Test
    @DisplayName("Should register providers and return them by ID")
    void shouldRegisterProvidersAndReturnThemById() {
        when(githubProvider.providerId()).thenReturn("github");
        when(localProvider.providerId()).thenReturn("local-folder");

        registry.register(githubProvider);
        registry.register(localProvider);

        Optional<SourceProvider> found = registry.resolve("github");
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(githubProvider);

        Optional<SourceProvider> notFound = registry.resolve("gitlab");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("Should find provider that can handle a locator")
    void shouldFindProviderThatCanHandleLocator() {
        when(githubProvider.providerId()).thenReturn("github");
        when(localProvider.providerId()).thenReturn("local-folder");
        when(githubProvider.canHandle(any())).thenReturn(true);
        // Note: localProvider is never queried because githubProvider returns true first

        registry.register(githubProvider);
        registry.register(localProvider);

        Optional<SourceProvider> found = registry.findProvider(locator);
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(githubProvider);
    }

    @Test
    @DisplayName("Should return empty when no provider can handle locator")
    void shouldReturnEmptyWhenNoProviderCanHandleLocator() {
        when(githubProvider.providerId()).thenReturn("github");
        when(localProvider.providerId()).thenReturn("local-folder");
        when(githubProvider.canHandle(any())).thenReturn(false);
        when(localProvider.canHandle(any())).thenReturn(false);

        registry.register(githubProvider);
        registry.register(localProvider);

        Optional<SourceProvider> found = registry.findProvider(locator);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should throw when provider not found by ID")
    void shouldThrowWhenProviderNotFoundById() {
        assertThatThrownBy(() -> registry.getProvider("nonexistent"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No provider found for: nonexistent");
    }

    @Test
    @DisplayName("Should return provider by ID with getProvider")
    void shouldReturnProviderByIdWithGetProvider() {
        when(githubProvider.providerId()).thenReturn("github");
        registry.register(githubProvider);

        SourceProvider found = registry.getProvider("github");
        assertThat(found).isEqualTo(githubProvider);
    }

    @Test
    @DisplayName("Should aggregate capabilities from all providers")
    void shouldAggregateCapabilitiesFromAllProviders() {
        when(githubProvider.providerId()).thenReturn("github");
        when(localProvider.providerId()).thenReturn("local-folder");
        when(githubProvider.capabilities()).thenReturn(Map.of("git", true, "largeFiles", true));
        when(localProvider.capabilities()).thenReturn(Map.of("git", false, "local", true));

        registry.register(githubProvider);
        registry.register(localProvider);

        Map<String, Map<String, Object>> caps = registry.capabilities();
        assertThat(caps).hasSize(2);
        assertThat(caps.get("github")).containsEntry("git", true);
        assertThat(caps.get("local-folder")).containsEntry("local", true);
    }

    @Test
    @DisplayName("Should resolve locator using first capable provider")
    void shouldResolveLocatorUsingFirstCapableProvider() {
        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext(
            "tenant-1", "workspace-1", "project-1", "principal-1"
        );
        RepositorySnapshot expectedSnapshot = mock(RepositorySnapshot.class);

        when(githubProvider.providerId()).thenReturn("github");
        when(localProvider.providerId()).thenReturn("local-folder");
        when(githubProvider.canHandle(any())).thenReturn(true);
        when(githubProvider.resolve(any(), any())).thenReturn(Promise.of(expectedSnapshot));

        registry.register(githubProvider);
        registry.register(localProvider);

        Promise<RepositorySnapshot> result = registry.resolve(locator, scope);
        RepositorySnapshot snapshot = result.getResult();

        assertThat(snapshot).isEqualTo(expectedSnapshot);
        verify(githubProvider).resolve(locator, scope);
        verify(localProvider, never()).resolve(any(), any());
    }

    @Test
    @DisplayName("Should return exception promise when no provider can resolve")
    void shouldReturnExceptionPromiseWhenNoProviderCanResolve() {
        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext(
            "tenant-1", "workspace-1", "project-1", "principal-1"
        );

        when(githubProvider.providerId()).thenReturn("github");
        when(githubProvider.canHandle(any())).thenReturn(false);

        registry.register(githubProvider);

        Promise<RepositorySnapshot> result = registry.resolve(locator, scope);
        assertThat(result.isException()).isTrue();
        assertThat(result.getException()).isNotNull();
        assertThat(result.getException().getMessage()).contains("No source provider can resolve");
    }

    @Test
    @DisplayName("Should reject null provider registration")
    void shouldRejectNullProviderRegistration() {
        assertThatThrownBy(() -> registry.register(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("provider must not be null");
    }

    @Test
    @DisplayName("Should return all registered providers")
    void shouldReturnAllRegisteredProviders() {
        when(githubProvider.providerId()).thenReturn("github");
        when(localProvider.providerId()).thenReturn("local-folder");
        registry.register(githubProvider);
        registry.register(localProvider);

        var providers = registry.providers();
        assertThat(providers).hasSize(2);
        assertThat(providers).contains(githubProvider, localProvider);
    }

    @Test
    @DisplayName("defaultRegistry with explicit resolver registers governed providers")
    void defaultRegistryWithResolverRegistersGovernedProviders() {
        SourceCredentialResolver resolver = (locator, providerId, tenantId, workspaceId, projectId) -> "token";

        SourceProviderRegistry defaultRegistry = SourceProviderRegistry.defaultRegistry(resolver);

        assertThat(defaultRegistry.resolve("github")).isPresent();
        assertThat(defaultRegistry.resolve("gitlab")).isPresent();
        assertThat(defaultRegistry.resolve("local-folder")).isPresent();
    }

    @Test
    @DisplayName("defaultRegistry rejects null credential resolver")
    void defaultRegistryRejectsNullCredentialResolver() {
        assertThatThrownBy(() -> SourceProviderRegistry.defaultRegistry((SourceCredentialResolver) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("credentialResolver must not be null");
    }

    @Test
    @DisplayName("defaultRegistry rejects null credential repository")
    void defaultRegistryRejectsNullCredentialRepository() {
        assertThatThrownBy(() -> SourceProviderRegistry.defaultRegistry((SourceCredentialRepository) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("credentialRepository must not be null");
    }

    @Test
    @DisplayName("findProvider prefers exact provider ID over canHandle fallback")
    void findProviderPrefersExactProviderIdOverCanHandleFallback() {
        SourceProvider exactProvider = mock(SourceProvider.class);
        SourceProvider fallbackProvider = mock(SourceProvider.class);

        SourceLocator exactLocator = SourceLocator.builder()
            .provider("gitlab")
            .repoId("org/repo")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .projectId("project-1")
            .build();

        when(exactProvider.providerId()).thenReturn("gitlab");
        when(fallbackProvider.providerId()).thenReturn("github");

        registry.register(fallbackProvider);
        registry.register(exactProvider);

        Optional<SourceProvider> found = registry.findProvider(exactLocator);

        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(exactProvider);
        verify(fallbackProvider, never()).canHandle(any());
        verify(exactProvider, never()).canHandle(any());
    }
}
