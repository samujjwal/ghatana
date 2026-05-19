package com.ghatana.yappc.api;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.services.artifact.ArtifactRequestScope;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArtifactScopeResolverTest {

    @Test
    void rejectsUnauthorizedWorkspaceProjectScope() {
        ArtifactScopeResolver resolver = new ArtifactScopeResolver(
                (principal, workspaceId, projectId) -> "workspace-allowed".equals(workspaceId));
        Principal principal = new Principal("user-1", List.of("USER"), "tenant-1");
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeader(HttpHeaders.of("X-Workspace-ID"))).thenReturn("workspace-spoofed");
        when(request.getHeader(HttpHeaders.of("X-Project-ID"))).thenReturn("project-1");

        ArtifactScopeResolver.ScopeResolutionException error = assertThrows(
                ArtifactScopeResolver.ScopeResolutionException.class,
                () -> resolver.resolve(request, principal, "tenant-1", "project-1"));

        assertEquals(403, error.statusCode());
    }

    @Test
    void returnsServerScopeForAuthorizedHeaders() {
        ArtifactScopeResolver resolver = new ArtifactScopeResolver(
                (principal, workspaceId, projectId) -> true);
        Principal principal = new Principal("user-1", List.of("USER"), "tenant-1");
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeader(HttpHeaders.of("X-Workspace-ID"))).thenReturn("workspace-1");
        when(request.getHeader(HttpHeaders.of("X-Project-ID"))).thenReturn("project-1");

        ArtifactRequestScope scope = resolver.resolve(request, principal, "tenant-1", "project-1");

        assertEquals("tenant-1", scope.tenantId());
        assertEquals("workspace-1", scope.workspaceId());
        assertEquals("project-1", scope.projectId());
    }
}
