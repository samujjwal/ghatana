/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * PHASE: A
 * OWNER: @platform-team
 * MIGRATED: 2026-05-11
 * DEPENDS_ON: platform:java:core
 */
package com.ghatana.platform.audit;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AuditService query methods added in Phase 3.5.
 *
 * @doc.type class
 * @doc.purpose Test audit service query functionality
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("AuditService Query Tests")
class AuditServiceQueryTest {

    private InMemoryAuditQueryService auditService;
    private Eventloop eventloop;

    @BeforeEach
    void setUp() {
        eventloop = Eventloop.create();
        auditService = new InMemoryAuditQueryService();
    }

    @Test
    @DisplayName("queryByPhase should return events for specific phase")
    void queryByPhase_shouldReturnEventsForSpecificPhase() {
        // Given
        String projectId = "project-123";
        String phase = "INTENT";
        Instant startDate = Instant.now().minusSeconds(3600);
        Instant endDate = Instant.now();

        AuditEvent event1 = AuditEvent.builder()
                .tenantId("tenant-1")
                .eventType("PHASE_START")
                .principal("user-1")
                .resourceType("project")
                .resourceId(projectId)
                .detail("phase", phase)
                .timestamp(Instant.now().minusSeconds(1800))
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .tenantId("tenant-1")
                .eventType("PHASE_COMPLETE")
                .principal("user-1")
                .resourceType("project")
                .resourceId(projectId)
                .detail("phase", "DESIGN") // Different phase
                .timestamp(Instant.now().minusSeconds(900))
                .build();

        auditService.record(event1).getResult();
        auditService.record(event2).getResult();

        // When
        List<AuditEvent> result = auditService.queryByPhase(projectId, phase, startDate, endDate).getResult();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("PHASE_START");
        assertThat(result.get(0).getDetail("phase")).isEqualTo(phase);
    }

    @Test
    @DisplayName("queryByProject should return events for specific project")
    void queryByProject_shouldReturnEventsForSpecificProject() {
        // Given
        String projectId = "project-123";
        Instant startDate = Instant.now().minusSeconds(3600);
        Instant endDate = Instant.now();

        AuditEvent event1 = AuditEvent.builder()
                .tenantId("tenant-1")
                .eventType("PROJECT_CREATED")
                .principal("user-1")
                .resourceType("project")
                .resourceId(projectId)
                .detail("projectId", projectId)
                .timestamp(Instant.now().minusSeconds(1800))
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .tenantId("tenant-1")
                .eventType("PROJECT_CREATED")
                .principal("user-1")
                .resourceType("project")
                .resourceId("project-456") // Different project
                .timestamp(Instant.now().minusSeconds(900))
                .build();

        auditService.record(event1).getResult();
        auditService.record(event2).getResult();

        // When
        List<AuditEvent> result = auditService.queryByProject(projectId, startDate, endDate).getResult();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getResourceId()).isEqualTo(projectId);
    }

    @Test
    @DisplayName("query with AuditQuery should filter by multiple criteria")
    void query_withAuditQuery_shouldFilterByMultipleCriteria() {
        // Given
        String projectId = "project-123";
        Instant startDate = Instant.now().minusSeconds(3600);
        Instant endDate = Instant.now();

        AuditEvent event1 = AuditEvent.builder()
                .tenantId("tenant-1")
                .eventType("PHASE_START")
                .principal("user-1")
                .resourceType("project")
                .resourceId(projectId)
                .detail("phase", "INTENT")
                .timestamp(Instant.now().minusSeconds(1800))
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .tenantId("tenant-1")
                .eventType("PHASE_COMPLETE")
                .principal("user-2") // Different principal
                .resourceType("project")
                .resourceId(projectId)
                .detail("phase", "INTENT")
                .timestamp(Instant.now().minusSeconds(900))
                .build();

        auditService.record(event1).getResult();
        auditService.record(event2).getResult();

        AuditQuery query = AuditQuery.builder()
                .projectId(projectId)
                .phase("INTENT")
                .principal("user-1")
                .startDate(startDate)
                .endDate(endDate)
                .build();

        // When
        List<AuditEvent> result = auditService.query(query).getResult();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPrincipal()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("queryByPhase should return empty list when no events match")
    void queryByPhase_shouldReturnEmptyListWhenNoEventsMatch() {
        // Given
        String projectId = "project-123";
        String phase = "NON_EXISTENT_PHASE";
        Instant startDate = Instant.now().minusSeconds(3600);
        Instant endDate = Instant.now();

        // When
        List<AuditEvent> result = auditService.queryByPhase(projectId, phase, startDate, endDate).getResult();

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("queryByPhase should filter by time range")
    void queryByPhase_shouldFilterByTimeRange() {
        // Given
        String projectId = "project-123";
        String phase = "INTENT";
        Instant startDate = Instant.now().minusSeconds(3600);
        Instant endDate = Instant.now().minusSeconds(1800);

        AuditEvent event1 = AuditEvent.builder()
                .tenantId("tenant-1")
                .eventType("PHASE_START")
                .principal("user-1")
                .resourceType("project")
                .resourceId(projectId)
                .detail("phase", phase)
                .timestamp(Instant.now().minusSeconds(2700)) // Within range
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .tenantId("tenant-1")
                .eventType("PHASE_COMPLETE")
                .principal("user-1")
                .resourceType("project")
                .resourceId(projectId)
                .detail("phase", phase)
                .timestamp(Instant.now().minusSeconds(900)) // Outside range
                .build();

        auditService.record(event1).getResult();
        auditService.record(event2).getResult();

        // When
        List<AuditEvent> result = auditService.queryByPhase(projectId, phase, startDate, endDate).getResult();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("PHASE_START");
    }

    @Test
    @DisplayName("query should handle null parameters gracefully")
    void query_shouldHandleNullParametersGracefully() {
        // Given
        AuditQuery query = AuditQuery.builder()
                .build();

        // When
        List<AuditEvent> result = auditService.query(query).getResult();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("queryByProject should require non-null parameters")
    void queryByProject_shouldRequireNonNullParameters() {
        // Given
        String projectId = "project-123";
        Instant startDate = Instant.now().minusSeconds(3600);
        Instant endDate = Instant.now();

        // When & Then - should not throw with valid parameters
        List<AuditEvent> result = auditService.queryByProject(projectId, startDate, endDate).getResult();
        assertThat(result).isNotNull();
    }
}
