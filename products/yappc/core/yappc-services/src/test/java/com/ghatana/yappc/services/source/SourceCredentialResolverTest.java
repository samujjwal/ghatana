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
        // Set environment variable for test
        String testCred = "test-github-token-123";
        System.setProperty("GITHUB_TOKEN_TEST", testCred);

        try {
            SourceCredentialResolver resolver = new EnvBackedSourceCredentialResolver();
            SourceLocator locator = SourceLocator.builder()
                .provider("github")
                .repoId("test/repo")
                .credentialRef("env:GITHUB_TOKEN_TEST")
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .build();

            String resolved = resolver.resolve(locator, new SourceProvider.ScopeContext(TENANT_ID, WORKSPACE_ID, PROJECT_ID, "user"));

            assertThat(resolved).isEqualTo(testCred);
        } finally {
            System.clearProperty("GITHUB_TOKEN_TEST");
        }
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

        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext(TENANT_ID, WORKSPACE_ID, PROJECT_ID, "user");

        assertThatThrownBy(() -> resolver.resolve(locator, scope))
            .isInstanceOf(IllegalArgumentException.class)
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

        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext(TENANT_ID, WORKSPACE_ID, PROJECT_ID, "user");

        assertThatThrownBy(() -> resolver.resolve(locator, scope))
            .isInstanceOf(IllegalArgumentException.class)
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

        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext(TENANT_ID, WORKSPACE_ID, PROJECT_ID, "user");

        assertThatThrownBy(() -> resolver.resolve(locator, scope))
            .isInstanceOf(IllegalArgumentException.class)
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

        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext(TENANT_ID, WORKSPACE_ID, PROJECT_ID, "user");

        assertThatThrownBy(() -> resolver.resolve(locator, scope))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("credential");
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

        SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext(TENANT_ID, WORKSPACE_ID, PROJECT_ID, "user");

        String resolved = resolver.resolve(locator, scope);
        assertThat(resolved).isNull();
    }
}
