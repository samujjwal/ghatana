/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.domain.eventstore.TenantContext;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for workspace extraction in {@link HttpHandlerSupport#extractWorkspaceId(HttpRequest)}.
 *
 * <p>Verifies that the {@code X-Workspace-Id} header is correctly propagated from
 * HTTP requests into {@link TenantContext} for workspace isolation.
 *
 * @doc.type class
 * @doc.purpose Workspace extraction and propagation tests for HttpHandlerSupport
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("HttpHandlerSupport workspace extraction")
class HttpHandlerSupportWorkspaceTest {

    private final HttpHandlerSupport support = new HttpHandlerSupport(
        new ObjectMapper(),
        "http://localhost:3000",
        "GET,POST,PUT,DELETE,OPTIONS",
        "Content-Type,X-Tenant-Id,X-Workspace-Id"
    );

    // -------------------------------------------------------------------------
    // extractWorkspaceId — happy paths
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("returns workspace ID when X-Workspace-Id header is present")
    void returnsWorkspaceIdWhenHeaderIsPresent() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities")
            .withHeader(HttpHeaders.of("X-Workspace-Id"), "workspace-alpha")
            .build();

        String result = support.extractWorkspaceId(request);

        assertThat(result).isEqualTo("workspace-alpha");
    }

    @Test
    @DisplayName("trims leading and trailing whitespace from workspace ID")
    void trimsWhitespaceFromWorkspaceId() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities")
            .withHeader(HttpHeaders.of("X-Workspace-Id"), "  workspace-beta  ")
            .build();

        String result = support.extractWorkspaceId(request);

        assertThat(result).isEqualTo("workspace-beta");
    }

    @Test
    @DisplayName("returns workspace ID at minimum length (1 character)")
    void returnsWorkspaceIdAtMinLength() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities")
            .withHeader(HttpHeaders.of("X-Workspace-Id"), "w")
            .build();

        String result = support.extractWorkspaceId(request);

        assertThat(result).isEqualTo("w");
    }

    @Test
    @DisplayName("returns workspace ID at maximum length (64 characters)")
    void returnsWorkspaceIdAtMaxLength() {
        String maxLengthId = "w".repeat(64);
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities")
            .withHeader(HttpHeaders.of("X-Workspace-Id"), maxLengthId)
            .build();

        String result = support.extractWorkspaceId(request);

        assertThat(result).isEqualTo(maxLengthId);
    }

    // -------------------------------------------------------------------------
    // extractWorkspaceId — absent / invalid cases → null
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("returns null when X-Workspace-Id header is absent")
    void returnsNullWhenHeaderIsAbsent() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities")
            .build();

        String result = support.extractWorkspaceId(request);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("returns null when X-Workspace-Id header is blank")
    void returnsNullWhenHeaderIsBlank() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities")
            .withHeader(HttpHeaders.of("X-Workspace-Id"), "   ")
            .build();

        String result = support.extractWorkspaceId(request);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("returns null when X-Workspace-Id header is empty string")
    void returnsNullWhenHeaderIsEmpty() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities")
            .withHeader(HttpHeaders.of("X-Workspace-Id"), "")
            .build();

        String result = support.extractWorkspaceId(request);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("returns null when X-Workspace-Id exceeds 64 characters")
    void returnsNullWhenHeaderExceedsMaxLength() {
        String tooLongId = "w".repeat(65);
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/entities")
            .withHeader(HttpHeaders.of("X-Workspace-Id"), tooLongId)
            .build();

        String result = support.extractWorkspaceId(request);

        assertThat(result).isNull();
    }

    // -------------------------------------------------------------------------
    // Workspace isolation — TenantContext integration
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("workspace ID propagates correctly into TenantContext")
    void workspaceIdPropagatesIntoTenantContext() {
        String workspaceId = support.extractWorkspaceId(
            HttpRequest.get("http://localhost/api/v1/entities")
                .withHeader(HttpHeaders.of("X-Workspace-Id"), "workspace-gamma")
                .build()
        );

        assertThat(workspaceId).isNotNull();

        TenantContext ctx = TenantContext.of("tenant-abc").withWorkspace(workspaceId);

        assertThat(ctx.workspaceId()).isPresent();
        assertThat(ctx.workspaceId().get()).isEqualTo("workspace-gamma");
    }

    @Test
    @DisplayName("absent workspace produces TenantContext with empty workspaceId")
    void absentWorkspaceProducesTenantContextWithNoWorkspace() {
        String workspaceId = support.extractWorkspaceId(
            HttpRequest.get("http://localhost/api/v1/entities").build()
        );

        assertThat(workspaceId).isNull();

        TenantContext ctx = TenantContext.of("tenant-abc");
        // workspace not set when header is absent

        assertThat(ctx.workspaceId()).isEmpty();
    }

    @Test
    @DisplayName("two requests with different workspaces produce distinct TenantContexts")
    void twoDifferentWorkspacesProduceDistinctContexts() {
        String workspaceA = support.extractWorkspaceId(
            HttpRequest.get("http://localhost/api/v1/entities")
                .withHeader(HttpHeaders.of("X-Workspace-Id"), "workspace-a")
                .build()
        );
        String workspaceB = support.extractWorkspaceId(
            HttpRequest.get("http://localhost/api/v1/entities")
                .withHeader(HttpHeaders.of("X-Workspace-Id"), "workspace-b")
                .build()
        );

        assertThat(workspaceA).isEqualTo("workspace-a");
        assertThat(workspaceB).isEqualTo("workspace-b");

        TenantContext ctxA = TenantContext.of("tenant-xyz").withWorkspace(workspaceA);
        TenantContext ctxB = TenantContext.of("tenant-xyz").withWorkspace(workspaceB);

        assertThat(ctxA.workspaceId()).contains("workspace-a");
        assertThat(ctxB.workspaceId()).contains("workspace-b");
        assertThat(ctxA.workspaceId()).isNotEqualTo(ctxB.workspaceId());
    }

    @Test
    @DisplayName("same tenantId with different workspace IDs are isolated from each other")
    void sameTenantDifferentWorkspaceAreIsolated() {
        TenantContext ctx1 = TenantContext.of("tenant-shared").withWorkspace("ws-sales");
        TenantContext ctx2 = TenantContext.of("tenant-shared").withWorkspace("ws-finance");

        assertThat(ctx1.tenantId()).isEqualTo(ctx2.tenantId());
        assertThat(ctx1.workspaceId()).isNotEqualTo(ctx2.workspaceId());
        assertThat(ctx1.workspaceId()).contains("ws-sales");
        assertThat(ctx2.workspaceId()).contains("ws-finance");
    }
}
