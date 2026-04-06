/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — ApprovalRequestMapper Tests
 */
package com.ghatana.yappc.services.lifecycle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Verifies bidirectional mapping between agent-layer and lifecycle-layer ApprovalRequest models
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ApprovalRequestMapper")
class ApprovalRequestMapperTest {

    private static final Instant NOW = Instant.parse("2026-04-06T10:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-04-08T10:00:00Z");

    private com.ghatana.yappc.agent.ApprovalRequest agentRequest(String type) {
        return new com.ghatana.yappc.agent.ApprovalRequest(
                "",                          // blank requestId — ID assigned by gateway
                "tenant-mapper-test",
                "proj-mapper-001",
                "agent-build-007",
                type,
                "INTENT",
                "SHAPE",
                "criteria not met: code coverage < 80%",
                List.of("coverage-criterion"),
                List.of("coverage-report.xml"),
                EXPIRES
        );
    }

    // ─── toLifecycleRequest ───────────────────────────────────────────────────

    @Test
    @DisplayName("maps all fields from agent record to lifecycle record")
    void mapsAgentToLifecycleAllFields() {
        com.ghatana.yappc.agent.ApprovalRequest src = agentRequest("PHASE_ADVANCE");
        ApprovalRequest result = ApprovalRequestMapper.toLifecycleRequest(src, "id-001", NOW);

        assertThat(result.id()).isEqualTo("id-001");
        assertThat(result.projectId()).isEqualTo("proj-mapper-001");
        assertThat(result.requestingAgentId()).isEqualTo("agent-build-007");
        assertThat(result.tenantId()).isEqualTo("tenant-mapper-test");
        assertThat(result.approvalType()).isEqualTo(ApprovalRequest.ApprovalType.PHASE_ADVANCE);
        assertThat(result.status()).isEqualTo(ApprovalRequest.ApprovalStatus.PENDING);
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.decidedAt()).isNull();
        assertThat(result.decidedBy()).isNull();
        assertThat(result.expiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    @DisplayName("maps nested context block correctly")
    void mapsContextBlock() {
        ApprovalRequest result = ApprovalRequestMapper.toLifecycleRequest(agentRequest("DEPLOYMENT"), "id-002", NOW);

        assertThat(result.context()).isNotNull();
        assertThat(result.context().fromPhase()).isEqualTo("INTENT");
        assertThat(result.context().toPhase()).isEqualTo("SHAPE");
        assertThat(result.context().blockReason()).contains("code coverage");
        assertThat(result.context().unmetCriteria()).containsExactly("coverage-criterion");
        assertThat(result.context().missingArtifacts()).containsExactly("coverage-report.xml");
    }

    @Test
    @DisplayName("maps RISK_ACCEPTANCE approvalType correctly")
    void mapsRiskAcceptanceType() {
        ApprovalRequest result = ApprovalRequestMapper.toLifecycleRequest(agentRequest("RISK_ACCEPTANCE"), "id-003", NOW);
        assertThat(result.approvalType()).isEqualTo(ApprovalRequest.ApprovalType.RISK_ACCEPTANCE);
    }

    @Test
    @DisplayName("approvalType mapping is case-insensitive")
    void typeIsCaseInsensitive() {
        com.ghatana.yappc.agent.ApprovalRequest src = agentRequest("phase_advance");
        ApprovalRequest result = ApprovalRequestMapper.toLifecycleRequest(src, "id-004", NOW);
        assertThat(result.approvalType()).isEqualTo(ApprovalRequest.ApprovalType.PHASE_ADVANCE);
    }

    @Test
    @DisplayName("null unmetCriteria becomes empty list in context")
    void nullUnmetCriteriaBecomesEmpty() {
        com.ghatana.yappc.agent.ApprovalRequest src = new com.ghatana.yappc.agent.ApprovalRequest(
                "", "tenant", "proj", "agent", "PHASE_ADVANCE",
                "A", "B", "reason", null, null, null);
        ApprovalRequest result = ApprovalRequestMapper.toLifecycleRequest(src, "id-005", NOW);
        assertThat(result.context().unmetCriteria()).isEmpty();
        assertThat(result.context().missingArtifacts()).isEmpty();
        assertThat(result.expiresAt()).isNull();
    }

    @Test
    @DisplayName("unknown approvalType throws IllegalArgumentException")
    void unknownTypeThrows() {
        com.ghatana.yappc.agent.ApprovalRequest src = agentRequest("UNSUPPORTED_TYPE");
        assertThatThrownBy(() -> ApprovalRequestMapper.toLifecycleRequest(src, "id-006", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown approvalType");
    }

    @Test
    @DisplayName("blank approvalType throws IllegalArgumentException")
    void blankTypeThrows() {
        com.ghatana.yappc.agent.ApprovalRequest src = agentRequest("  ");
        assertThatThrownBy(() -> ApprovalRequestMapper.toLifecycleRequest(src, "id-007", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    // ─── toAgentRequest ───────────────────────────────────────────────────────

    @Test
    @DisplayName("round-trips lifecycle → agent preserving key fields")
    void roundTripLifecycleToAgent() {
        ApprovalRequest lifecycle = ApprovalRequestMapper.toLifecycleRequest(
                agentRequest("PHASE_ADVANCE"), "rt-id-001", NOW);

        com.ghatana.yappc.agent.ApprovalRequest agent = ApprovalRequestMapper.toAgentRequest(lifecycle);

        assertThat(agent.requestId()).isEqualTo("rt-id-001");
        assertThat(agent.tenantId()).isEqualTo("tenant-mapper-test");
        assertThat(agent.projectId()).isEqualTo("proj-mapper-001");
        assertThat(agent.requestingAgentId()).isEqualTo("agent-build-007");
        assertThat(agent.approvalType()).isEqualTo("PHASE_ADVANCE");
        assertThat(agent.fromPhase()).isEqualTo("INTENT");
        assertThat(agent.toPhase()).isEqualTo("SHAPE");
        assertThat(agent.blockReason()).contains("code coverage");
        assertThat(agent.expiresAt()).isEqualTo(EXPIRES);
    }

    @Test
    @DisplayName("toAgentRequest handles null context gracefully")
    void toAgentRequestNullContext() {
        ApprovalRequest lifecycle = new ApprovalRequest(
                "id-null-ctx", "proj", null,
                ApprovalRequest.ApprovalType.DEPLOYMENT,
                null,  // context = null
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant", NOW, null, null, null);

        com.ghatana.yappc.agent.ApprovalRequest agent = ApprovalRequestMapper.toAgentRequest(lifecycle);

        assertThat(agent.requestId()).isEqualTo("id-null-ctx");
        assertThat(agent.fromPhase()).isNull();
        assertThat(agent.toPhase()).isNull();
        assertThat(agent.blockReason()).isNull();
        assertThat(agent.unmetCriteria()).isEmpty();
        assertThat(agent.missingArtifacts()).isEmpty();
    }
}
