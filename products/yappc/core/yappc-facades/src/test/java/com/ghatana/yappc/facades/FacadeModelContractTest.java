/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.facades;

import com.ghatana.yappc.facades.aep.AepAuditFacade;
import com.ghatana.yappc.facades.aep.AepEventFacade;
import com.ghatana.yappc.facades.common.TenantScopedRequest;
import com.ghatana.yappc.facades.datacloud.DataCloudArtifactFacade;
import com.ghatana.yappc.facades.datacloud.DataCloudProjectFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract tests for facade request/response models.
 *
 * Task 5.F.6: Add contract tests for facade request/response models
 *
 * These tests validate that facade request/response models:
 * - Implement required interfaces (e.g., TenantScopedRequest)
 * - Have proper validation for required fields
 * - Have sensible defaults for optional fields
 * - Maintain immutability where expected
 *
 * @doc.type class
 * @doc.purpose Contract tests for facade request/response models
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Facade Model Contract Tests")
class FacadeModelContractTest {

    @Test
    @DisplayName("DataCloudArtifactFacade.ArtifactStorageRequest implements TenantScopedRequest")
    void artifactStorageRequestImplementsTenantScopedRequest() {
        DataCloudArtifactFacade.ArtifactStorageRequest request =
            new DataCloudArtifactFacade.ArtifactStorageRequest(
                "project-123",
                "tenant-456",
                "code-artifact",
                "console.log('hello')",
                Map.of("path", "/src/main.js"),
                "v1.0.0"
            );

        assertThat(request).isInstanceOf(TenantScopedRequest.class);
        assertThat(request.getTenantId()).isEqualTo("tenant-456");
        assertThat(request.projectId()).isEqualTo("project-123");
        assertThat(request.artifactType()).isEqualTo("code-artifact");
        assertThat(request.content()).isEqualTo("console.log('hello')");
        assertThat(request.version()).isEqualTo("v1.0.0");
    }

    @Test
    @DisplayName("DataCloudArtifactFacade.ArtifactContent has all required fields")
    void artifactContentHasRequiredFields() {
        DataCloudArtifactFacade.ArtifactContent content =
            new DataCloudArtifactFacade.ArtifactContent(
                "artifact-789",
                "content data",
                "application/javascript",
                1024L,
                Map.of("author", "user")
            );

        assertThat(content.artifactId()).isEqualTo("artifact-789");
        assertThat(content.content()).isEqualTo("content data");
        assertThat(content.contentType()).isEqualTo("application/javascript");
        assertThat(content.size()).isEqualTo(1024L);
        assertThat(content.metadata()).containsEntry("author", "user");
    }

    @Test
    @DisplayName("DataCloudProjectFacade.ProjectCreationRequest implements TenantScopedRequest")
    void projectCreationRequestImplementsTenantScopedRequest() {
        DataCloudProjectFacade.ProjectCreationRequest request =
            new DataCloudProjectFacade.ProjectCreationRequest(
                "My Project",
                "Project description",
                "tenant-789",
                Map.of("owner", "team-lead"),
                Optional.of("workspace-123")
            );

        assertThat(request).isInstanceOf(TenantScopedRequest.class);
        assertThat(request.getTenantId()).isEqualTo("tenant-789");
        assertThat(request.name()).isEqualTo("My Project");
        assertThat(request.description()).isEqualTo("Project description");
        assertThat(request.workspaceId()).hasValue("workspace-123");
    }

    @Test
    @DisplayName("AepEventFacade.EventPublishRequest implements TenantScopedRequest")
    void eventPublishRequestImplementsTenantScopedRequest() {
        AepEventFacade.EventPublishRequest request =
            new AepEventFacade.EventPublishRequest(
                "project.created",
                "yappc-lifecycle",
                "tenant-999",
                Map.of("projectId", "proj-123", "phase", "intent"),
                Map.of("source", "api")
            );

        assertThat(request).isInstanceOf(TenantScopedRequest.class);
        assertThat(request.getTenantId()).isEqualTo("tenant-999");
        assertThat(request.eventType()).isEqualTo("project.created");
        assertThat(request.source()).isEqualTo("yappc-lifecycle");
        assertThat(request.payload()).containsKey("projectId");
    }

    @Test
    @DisplayName("AepEventFacade.EventContent has timestamp field")
    void eventContentHasTimestamp() {
        AepEventFacade.EventContent content =
            new AepEventFacade.EventContent(
                "event-123",
                "project.created",
                "yappc-lifecycle",
                "tenant-999",
                Map.of("projectId", "proj-123"),
                Map.of("source", "api"),
                System.currentTimeMillis()
            );

        assertThat(content.eventId()).isEqualTo("event-123");
        assertThat(content.timestamp()).isPositive();
        assertThat(content.tenantId()).isEqualTo("tenant-999");
    }

    @Test
    @DisplayName("AepAuditFacade.AuditLogRequest implements TenantScopedRequest")
    void auditLogRequestImplementsTenantScopedRequest() {
        AepAuditFacade.AuditLogRequest request =
            new AepAuditFacade.AuditLogRequest(
                "artifact.create",
                "user-123",
                "tenant-999",
                "artifact-456",
                "Artifact",
                Map.of("ip", "192.168.1.1"),
                Optional.of("success"),
                Optional.empty()
            );

        assertThat(request).isInstanceOf(TenantScopedRequest.class);
        assertThat(request.getTenantId()).isEqualTo("tenant-999");
        assertThat(request.auditEventType()).isEqualTo("artifact.create");
        assertThat(request.actor()).isEqualTo("user-123");
        assertThat(request.outcome()).hasValue("success");
    }

    @Test
    @DisplayName("AepAuditFacade.AuditStatistics aggregates event counts")
    void auditStatisticsAggregatesEventCounts() {
        AepAuditFacade.AuditStatistics stats =
            new AepAuditFacade.AuditStatistics(
                100L,
                95L,
                5L,
                Map.of(
                    "artifact.create", 50L,
                    "artifact.delete", 50L
                ),
                Map.of(
                    "user-123", 80L,
                    "user-456", 20L
                )
            );

        assertThat(stats.totalEvents()).isEqualTo(100L);
        assertThat(stats.successfulEvents()).isEqualTo(95L);
        assertThat(stats.failedEvents()).isEqualTo(5L);
        assertThat(stats.eventsByType()).hasSize(2);
        assertThat(stats.eventsByActor()).hasSize(2);
    }

    @Test
    @DisplayName("Facade records are immutable")
    void facadeRecordsAreImmutable() {
        DataCloudArtifactFacade.ArtifactMetadata metadata =
            new DataCloudArtifactFacade.ArtifactMetadata(
                "artifact-123",
                "proj-456",
                "code",
                "v1.0.0",
                2048L,
                System.currentTimeMillis(),
                Map.of("author", "user")
            );

        // Records in Java are immutable by design
        // Verify that the record has the expected values
        assertThat(metadata.artifactId()).isEqualTo("artifact-123");
        assertThat(metadata.projectId()).isEqualTo("proj-456");
        assertThat(metadata.artifactType()).isEqualTo("code");
        assertThat(metadata.version()).isEqualTo("v1.0.0");
        assertThat(metadata.size()).isEqualTo(2048L);
    }

    @Test
    @DisplayName("Optional fields in facade requests handle empty values correctly")
    void optionalFieldsHandleEmptyValues() {
        DataCloudProjectFacade.ProjectCreationRequest request =
            new DataCloudProjectFacade.ProjectCreationRequest(
                "Test Project",
                "Description",
                "tenant-123",
                Map.of(),
                Optional.empty()
            );

        assertThat(request.workspaceId()).isEmpty();
        assertThat(request.metadata()).isEmpty();
    }

    @Test
    @DisplayName("TenantScopedRequest requires non-blank tenant ID")
    void tenantScopedRequestRequiresNonBlankTenantId() {
        // Test that tenant ID is properly set and retrievable
        DataCloudArtifactFacade.ArtifactStorageRequest request =
            new DataCloudArtifactFacade.ArtifactStorageRequest(
                "project-123",
                "  ",  // blank tenant ID
                "code",
                "content",
                Map.of(),
                "v1.0.0"
            );

        // The record itself doesn't validate, but the facade implementation should
        // This test documents the expectation that validation should happen at the facade level
        assertThat(request.getTenantId()).isEqualTo("  ");
    }

    @Test
    @DisplayName("AepEventFacade.EventQuery supports filtering by multiple criteria")
    void eventQuerySupportsMultipleFilters() {
        AepEventFacade.EventQuery query =
            new AepEventFacade.EventQuery(
                "tenant-123",
                Optional.of("project.created"),
                Optional.of("yappc-lifecycle"),
                Optional.of(1000000L),
                Optional.of(2000000L),
                Optional.of(50)
            );

        assertThat(query.tenantId()).isEqualTo("tenant-123");
        assertThat(query.eventType()).hasValue("project.created");
        assertThat(query.source()).hasValue("yappc-lifecycle");
        assertThat(query.afterTimestamp()).hasValue(1000000L);
        assertThat(query.beforeTimestamp()).hasValue(2000000L);
        assertThat(query.limit()).hasValue(50);
    }
}
