/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.rbac.AccessDeniedException;
import com.ghatana.platform.security.rbac.Permission;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Scope permutation test covering all scope transport patterns and authorization scenarios.
 *
 * <p>This test ensures that scope is correctly transported and validated across all operations:
 * - Workspace read
 * - Project read/write
 * - Artifact read/write
 * - Preview session create/validate
 * - Generation review/apply/reject/rollback
 * - Dashboard action execute
 *
 * @doc.type test
 * @doc.purpose Comprehensive scope permutation test for all authorization scenarios
 * @doc.layer api
 * @doc.pattern Scope Authorization Test
 */
@DisplayName("Scope Permutation Tests")
class ScopePermutationTest {

    private static final String TENANT_ID = "tenant-123";
    private static final String WORKSPACE_ID = "workspace-456";
    private static final String PROJECT_ID = "project-789";
    private static final String ARTIFACT_ID = "artifact-101";
    private static final String GENERATION_RUN_ID = "generation-202";
    private static final String PREVIEW_SESSION_ID = "preview-303";

    @Test
    @DisplayName("Workspace read with valid tenant and workspace scope should succeed")
    void workspaceReadWithValidScopeSucceeds() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .build();

        assertDoesNotThrow(() -> service.authorizeWorkspaceAccess(
                principal,
                scopeContext.workspaceId(),
                Permission.WORKSPACE_READ
        ));
    }

    @Test
    @DisplayName("Workspace read with missing workspaceId should fail")
    void workspaceReadWithMissingWorkspaceIdFails() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(null)
                .build();

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> service.authorizeWorkspaceAccess(
                        principal,
                        scopeContext.workspaceId(),
                        Permission.WORKSPACE_READ
                )
        );
        assertTrue(exception.getMessage().contains("workspaceId") || 
                   exception.getMessage().contains("scope"));
    }

    @Test
    @DisplayName("Project read with valid tenant, workspace, and project scope should succeed")
    void projectReadWithValidScopeSucceeds() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .build();

        assertDoesNotThrow(() -> service.authorizeProjectAccess(
                principal,
                scopeContext.workspaceId(),
                scopeContext.projectId(),
                Permission.PROJECT_READ
        ));
    }

    @Test
    @DisplayName("Project write with valid scope and DEVELOPER role should succeed")
    void projectWriteWithValidScopeAndDeveloperRoleSucceeds() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .build();

        assertDoesNotThrow(() -> service.authorizeProjectAccess(
                principal,
                scopeContext.workspaceId(),
                scopeContext.projectId(),
                Permission.PROJECT_UPDATE
        ));
    }

    @Test
    @DisplayName("Project write with VIEWER role should fail")
    void projectWriteWithViewerRoleFails() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("VIEWER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .build();

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> service.authorizeProjectAccess(
                        principal,
                        scopeContext.workspaceId(),
                        scopeContext.projectId(),
                        Permission.PROJECT_UPDATE
                )
        );
        assertTrue(exception.getMessage().contains("permission"));
    }

    @Test
    @DisplayName("Artifact read with valid full scope should succeed")
    void artifactReadWithValidFullScopeSucceeds() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .artifactId(ARTIFACT_ID)
                .build();

        assertDoesNotThrow(() -> service.authorizeArtifactAccess(
                principal,
                scopeContext.workspaceId(),
                scopeContext.projectId(),
                scopeContext.artifactId(),
                Permission.ARTIFACT_READ
        ));
    }

    @Test
    @DisplayName("Artifact write with valid scope and DEVELOPER role should succeed")
    void artifactWriteWithValidScopeAndDeveloperRoleSucceeds() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .artifactId(ARTIFACT_ID)
                .build();

        assertDoesNotThrow(() -> service.authorizeArtifactAccess(
                principal,
                scopeContext.workspaceId(),
                scopeContext.projectId(),
                scopeContext.artifactId(),
                Permission.ARTIFACT_UPDATE
        ));
    }

    @Test
    @DisplayName("Artifact write with VIEWER role should fail")
    void artifactWriteWithViewerRoleFails() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("VIEWER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .artifactId(ARTIFACT_ID)
                .build();

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> service.authorizeArtifactAccess(
                        principal,
                        scopeContext.workspaceId(),
                        scopeContext.projectId(),
                        scopeContext.artifactId(),
                        Permission.ARTIFACT_UPDATE
                )
        );
        assertTrue(exception.getMessage().contains("permission"));
    }

    @Test
    @DisplayName("Preview session create with valid scope should succeed")
    void previewSessionCreateWithValidScopeSucceeds() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .artifactId(ARTIFACT_ID)
                .build();

        assertDoesNotThrow(() -> service.authorizePreviewSessionAccess(
                principal,
                scopeContext.workspaceId(),
                scopeContext.projectId(),
                scopeContext.artifactId(),
                Permission.PREVIEW_CREATE
        ));
    }

    @Test
    @DisplayName("Preview session validate with valid scope should succeed")
    void previewSessionValidateWithValidScopeSucceeds() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .artifactId(ARTIFACT_ID)
                .previewSessionId(PREVIEW_SESSION_ID)
                .build();

        assertDoesNotThrow(() -> service.authorizePreviewSessionAccess(
                principal,
                scopeContext.workspaceId(),
                scopeContext.projectId(),
                scopeContext.artifactId(),
                Permission.PREVIEW_VALIDATE
        ));
    }

    @Test
    @DisplayName("Generation review with valid scope and required fields should succeed")
    void generationReviewWithValidScopeSucceeds() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .artifactId(ARTIFACT_ID)
                .generationRunId(GENERATION_RUN_ID)
                .build();

        assertDoesNotThrow(() -> service.authorizeGenerationAccess(
                principal,
                scopeContext.workspaceId(),
                scopeContext.projectId(),
                scopeContext.generationRunId(),
                Permission.GENERATION_REVIEW
        ));
    }

    @Test
    @DisplayName("Generation apply with valid scope should succeed")
    void generationApplyWithValidScopeSucceeds() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .generationRunId(GENERATION_RUN_ID)
                .build();

        assertDoesNotThrow(() -> service.authorizeGenerationAccess(
                principal,
                scopeContext.workspaceId(),
                scopeContext.projectId(),
                scopeContext.generationRunId(),
                Permission.GENERATION_APPLY
        ));
    }

    @Test
    @DisplayName("Generation reject with valid scope and reason should succeed")
    void generationRejectWithValidScopeAndReasonSucceeds() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .generationRunId(GENERATION_RUN_ID)
                .build();

        assertDoesNotThrow(() -> service.authorizeGenerationAccess(
                principal,
                scopeContext.workspaceId(),
                scopeContext.projectId(),
                scopeContext.generationRunId(),
                Permission.GENERATION_REJECT
        ));
    }

    @Test
    @DisplayName("Generation rollback with valid scope should succeed")
    void generationRollbackWithValidScopeSucceeds() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("ADMIN"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .generationRunId(GENERATION_RUN_ID)
                .build();

        assertDoesNotThrow(() -> service.authorizeGenerationAccess(
                principal,
                scopeContext.workspaceId(),
                scopeContext.projectId(),
                scopeContext.generationRunId(),
                Permission.GENERATION_ROLLBACK
        ));
    }

    @Test
    @DisplayName("Generation rollback with DEVELOPER role should fail")
    void generationRollbackWithDeveloperRoleFails() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .generationRunId(GENERATION_RUN_ID)
                .build();

        AccessDeniedException exception = assertThrows(
                AccessDeniedException.class,
                () -> service.authorizeGenerationAccess(
                        principal,
                        scopeContext.workspaceId(),
                        scopeContext.projectId(),
                        scopeContext.generationRunId(),
                        Permission.GENERATION_ROLLBACK
                )
        );
        assertTrue(exception.getMessage().contains("permission"));
    }

    @Test
    @DisplayName("Dashboard action execute with valid scope should succeed")
    void dashboardActionExecuteWithValidScopeSucceeds() {
        YappcAuthorizationService service = createAuthorizationService();
        Principal principal = createPrincipal("user-1", Set.of("DEVELOPER"));

        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .build();

        assertDoesNotThrow(() -> service.authorizeDashboardAction(
                principal,
                scopeContext.workspaceId(),
                scopeContext.projectId(),
                Permission.DASHBOARD_EXECUTE
        ));
    }

    @Test
    @DisplayName("Scope context with missing tenantId should fail validation")
    void scopeContextWithMissingTenantIdFailsValidation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RequestScopeContext.builder()
                        .tenantId(null)
                        .workspaceId(WORKSPACE_ID)
                        .build()
        );
    }

    @Test
    @DisplayName("Scope context with empty tenantId should fail validation")
    void scopeContextWithEmptyTenantIdFailsValidation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RequestScopeContext.builder()
                        .tenantId("")
                        .workspaceId(WORKSPACE_ID)
                        .build()
        );
    }

    @Test
    @DisplayName("Scope context with all fields should build successfully")
    void scopeContextWithAllFieldsBuildsSuccessfully() {
        RequestScopeContext scopeContext = RequestScopeContext.builder()
                .tenantId(TENANT_ID)
                .workspaceId(WORKSPACE_ID)
                .projectId(PROJECT_ID)
                .artifactId(ARTIFACT_ID)
                .generationRunId(GENERATION_RUN_ID)
                .previewSessionId(PREVIEW_SESSION_ID)
                .actorId("actor-123")
                .phase("GENERATE")
                .build();

        assertEquals(TENANT_ID, scopeContext.tenantId());
        assertEquals(WORKSPACE_ID, scopeContext.workspaceId());
        assertEquals(PROJECT_ID, scopeContext.projectId());
        assertEquals(ARTIFACT_ID, scopeContext.artifactId());
        assertEquals(GENERATION_RUN_ID, scopeContext.generationRunId());
        assertEquals(PREVIEW_SESSION_ID, scopeContext.previewSessionId());
        assertEquals("actor-123", scopeContext.actorId());
        assertEquals("GENERATE", scopeContext.phase());
    }

    // Helper methods

    private YappcAuthorizationService createAuthorizationService() {
        return new YappcAuthorizationService(
                mock(com.ghatana.platform.security.rbac.SyncAuthorizationService.class),
                mock(com.ghatana.audit.AuditLogger.class)
        );
    }

    private Principal createPrincipal(String userId, Set<String> roles) {
        return new Principal() {
            @Override
            public String getUserId() {
                return userId;
            }

            @Override
            public String getTenantId() {
                return TENANT_ID;
            }

            @Override
            public String getName() {
                return userId;
            }

            @Override
            public Set<String> getRoles() {
                return roles;
            }

            @Override
            public Map<String, String> getAttributes() {
                return java.util.Map.of();
            }
        };
    }
}
