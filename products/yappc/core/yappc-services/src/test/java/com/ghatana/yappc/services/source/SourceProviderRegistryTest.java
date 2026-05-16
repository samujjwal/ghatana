package com.ghatana.yappc.services.source;

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

        when(githubProvider.providerId()).thenReturn("github");
        when(localProvider.providerId()).thenReturn("local-folder");
    }

    @Test
    @DisplayName("Should register providers and return them by ID")
    void shouldRegisterProvidersAndReturnThemById() {
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
        when(githubProvider.canHandle(any())).thenReturn(true);
        when(localProvider.canHandle(any())).thenReturn(false);

        registry.register(githubProvider);
        registry.register(localProvider);

        Optional<SourceProvider> found = registry.findProvider(locator);
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(githubProvider);
    }

    @Test
    @DisplayName("Should return empty when no provider can handle locator")
    void shouldReturnEmptyWhenNoProviderCanHandleLocator() {
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
        registry.register(githubProvider);

        SourceProvider found = registry.getProvider("github");
        assertThat(found).isEqualTo(githubProvider);
    }

    @Test
    @DisplayName("Should aggregate capabilities from all providers")
    void shouldAggregateCapabilitiesFromAllProviders() {
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

        when(githubProvider.canHandle(any())).thenReturn(false);

        registry.register(githubProvider);

        Promise<RepositorySnapshot> result = registry.resolve(locator, scope);

        assertThatThrownBy(() -> result.getResult())
            .isInstanceOf(Exception.class)
            .hasMessageContaining("No source provider can resolve");
    }

    @Test
    @DisplayName("Default registry should have standard providers")
    void defaultRegistryShouldHaveStandardProviders() {
        SourceProviderRegistry defaultRegistry = SourceProviderRegistry.defaultRegistry();

        assertThat(defaultRegistry.resolve("github")).isPresent();
        assertThat(defaultRegistry.resolve("local-folder")).isPresent();
        assertThat(defaultRegistry.resolve("archive")).isPresent();
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
        registry.register(githubProvider);
        registry.register(localProvider);

        var providers = registry.providers();
        assertThat(providers).hasSize(2);
        assertThat(providers).contains(githubProvider, localProvider);
    }
}
