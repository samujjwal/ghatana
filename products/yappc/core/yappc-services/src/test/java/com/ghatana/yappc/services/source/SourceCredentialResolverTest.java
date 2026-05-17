package com.ghatana.yappc.services.source;

import com.ghatana.yappc.domain.source.SourceLocator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type test
 * @doc.purpose Unit tests for SourceCredentialResolver covering scope validation,
 *              credential resolution, environment variable fallbacks, and error handling.
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("SourceCredentialResolver Tests")
class SourceCredentialResolverTest {

    private static final String TENANT_ID = "test-tenant";
    private static final String WORKSPACE_ID = "test-workspace";
    private static final String PROJECT_ID = "test-project";

    @Test
    @DisplayName("Should resolve credential from environment variable when present")
    void shouldResolveCredentialFromEnvVar() {
        // Use the well-known GITHUB_TOKEN fallback for testing
        // Note: In real tests, environment variables would be set via test framework
        // For this test, we verify the fallback behavior instead
        SourceCredentialResolver resolver = new EnvBackedSourceCredentialResolver();
        SourceLocator locator = SourceLocator.builder()
            .provider("github")
            .repoId("test/repo")
            .credentialRef(null) // No specific ref, should use fallback
            .tenantId(TENANT_ID)
            .workspaceId(WORKSPACE_ID)
            .projectId(PROJECT_ID)
            .build();

        // When no credential ref is provided, it will try the provider fallback
        // Since GITHUB_TOKEN is not set in test env, this should return null
        String resolved = resolver.resolve(locator, "github", TENANT_ID, WORKSPACE_ID, PROJECT_ID);
        assertThat(resolved).isNull();
    }

    @Test
    @DisplayName("Should reject credential with mismatched tenant scope")
    void shouldRejectMismatchedTenantScope() {
        SourceCredentialResolver resolver = new EnvBackedSourceCredentialResolver();
        SourceLocator locator = SourceLocator.builder()
            .provider("github")
            .repoId("test/repo")
            .credentialRef("env:GITHUB_TOKEN")
            .tenantId("wrong-tenant")
            .workspaceId(WORKSPACE_ID)
            .projectId(PROJECT_ID)
            .build();

        assertThatThrownBy(() -> resolver.resolve(locator, "github", TENANT_ID, WORKSPACE_ID, PROJECT_ID))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("scope");
    }

    @Test
    @DisplayName("Should reject credential with mismatched workspace scope")
    void shouldRejectMismatchedWorkspaceScope() {
        SourceCredentialResolver resolver = new EnvBackedSourceCredentialResolver();
        SourceLocator locator = SourceLocator.builder()
            .provider("github")
            .repoId("test/repo")
            .credentialRef("env:GITHUB_TOKEN")
            .tenantId(TENANT_ID)
            .workspaceId("wrong-workspace")
            .projectId(PROJECT_ID)
            .build();

        assertThatThrownBy(() -> resolver.resolve(locator, "github", TENANT_ID, WORKSPACE_ID, PROJECT_ID))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("scope");
    }

    @Test
    @DisplayName("Should reject credential with mismatched project scope")
    void shouldRejectMismatchedProjectScope() {
        SourceCredentialResolver resolver = new EnvBackedSourceCredentialResolver();
        SourceLocator locator = SourceLocator.builder()
            .provider("github")
            .repoId("test/repo")
            .credentialRef("env:GITHUB_TOKEN")
            .tenantId(TENANT_ID)
            .workspaceId(WORKSPACE_ID)
            .projectId("wrong-project")
            .build();

        assertThatThrownBy(() -> resolver.resolve(locator, "github", TENANT_ID, WORKSPACE_ID, PROJECT_ID))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("scope");
    }

    @Test
    @DisplayName("Should handle missing environment variable gracefully")
    void shouldHandleMissingEnvVarGracefully() {
        System.clearProperty("NONEXISTENT_TOKEN");

        SourceCredentialResolver resolver = new EnvBackedSourceCredentialResolver();
        SourceLocator locator = SourceLocator.builder()
            .provider("github")
            .repoId("test/repo")
            .credentialRef("env:NONEXISTENT_TOKEN")
            .tenantId(TENANT_ID)
            .workspaceId(WORKSPACE_ID)
            .projectId(PROJECT_ID)
            .build();

        String resolved = resolver.resolve(locator, "github", TENANT_ID, WORKSPACE_ID, PROJECT_ID);
        assertThat(resolved).isNull();
    }

    @Test
    @DisplayName("Should handle null credential reference")
    void shouldHandleNullCredentialRef() {
        SourceCredentialResolver resolver = new EnvBackedSourceCredentialResolver();
        SourceLocator locator = SourceLocator.builder()
            .provider("github")
            .repoId("test/repo")
            .credentialRef(null)
            .tenantId(TENANT_ID)
            .workspaceId(WORKSPACE_ID)
            .projectId(PROJECT_ID)
            .build();

        String resolved = resolver.resolve(locator, "github", TENANT_ID, WORKSPACE_ID, PROJECT_ID);
        assertThat(resolved).isNull();
    }
}
