/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
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
@DisplayName("ApprovalRequestMapper [GH-90000]")
class ApprovalRequestMapperTest {

    private static final Instant NOW = Instant.parse("2026-04-06T10:00:00Z [GH-90000]");
    private static final Instant EXPIRES = Instant.parse("2026-04-08T10:00:00Z [GH-90000]");

    private com.ghatana.yappc.agent.ApprovalRequest agentRequest(String type) { // GH-90000
        return new com.ghatana.yappc.agent.ApprovalRequest( // GH-90000
                "",                          // blank requestId — ID assigned by gateway
                "tenant-mapper-test",
                "proj-mapper-001",
                "agent-build-007",
                type,
                "INTENT",
                "SHAPE",
                "criteria not met: code coverage < 80%",
                List.of("coverage-criterion [GH-90000]"),
                List.of("coverage-report.xml [GH-90000]"),
                EXPIRES
        );
    }

    // ─── toLifecycleRequest ───────────────────────────────────────────────────

    @Test
    @DisplayName("maps all fields from agent record to lifecycle record [GH-90000]")
    void mapsAgentToLifecycleAllFields() { // GH-90000
        com.ghatana.yappc.agent.ApprovalRequest src = agentRequest("PHASE_ADVANCE [GH-90000]");
        ApprovalRequest result = ApprovalRequestMapper.toLifecycleRequest(src, "id-001", NOW); // GH-90000

        assertThat(result.id()).isEqualTo("id-001 [GH-90000]");
        assertThat(result.projectId()).isEqualTo("proj-mapper-001 [GH-90000]");
        assertThat(result.requestingAgentId()).isEqualTo("agent-build-007 [GH-90000]");
        assertThat(result.tenantId()).isEqualTo("tenant-mapper-test [GH-90000]");
        assertThat(result.approvalType()).isEqualTo(ApprovalRequest.ApprovalType.PHASE_ADVANCE); // GH-90000
        assertThat(result.status()).isEqualTo(ApprovalRequest.ApprovalStatus.PENDING); // GH-90000
        assertThat(result.createdAt()).isEqualTo(NOW); // GH-90000
        assertThat(result.decidedAt()).isNull(); // GH-90000
        assertThat(result.decidedBy()).isNull(); // GH-90000
        assertThat(result.expiresAt()).isEqualTo(EXPIRES); // GH-90000
    }

    @Test
    @DisplayName("maps nested context block correctly [GH-90000]")
    void mapsContextBlock() { // GH-90000
        ApprovalRequest result = ApprovalRequestMapper.toLifecycleRequest(agentRequest("DEPLOYMENT [GH-90000]"), "id-002", NOW);

        assertThat(result.context()).isNotNull(); // GH-90000
        assertThat(result.context().fromPhase()).isEqualTo("INTENT [GH-90000]");
        assertThat(result.context().toPhase()).isEqualTo("SHAPE [GH-90000]");
        assertThat(result.context().blockReason()).contains("code coverage [GH-90000]");
        assertThat(result.context().unmetCriteria()).containsExactly("coverage-criterion [GH-90000]");
        assertThat(result.context().missingArtifacts()).containsExactly("coverage-report.xml [GH-90000]");
    }

    @Test
    @DisplayName("maps RISK_ACCEPTANCE approvalType correctly [GH-90000]")
    void mapsRiskAcceptanceType() { // GH-90000
        ApprovalRequest result = ApprovalRequestMapper.toLifecycleRequest(agentRequest("RISK_ACCEPTANCE [GH-90000]"), "id-003", NOW);
        assertThat(result.approvalType()).isEqualTo(ApprovalRequest.ApprovalType.RISK_ACCEPTANCE); // GH-90000
    }

    @Test
    @DisplayName("approvalType mapping is case-insensitive [GH-90000]")
    void typeIsCaseInsensitive() { // GH-90000
        com.ghatana.yappc.agent.ApprovalRequest src = agentRequest("phase_advance [GH-90000]");
        ApprovalRequest result = ApprovalRequestMapper.toLifecycleRequest(src, "id-004", NOW); // GH-90000
        assertThat(result.approvalType()).isEqualTo(ApprovalRequest.ApprovalType.PHASE_ADVANCE); // GH-90000
    }

    @Test
    @DisplayName("null unmetCriteria becomes empty list in context [GH-90000]")
    void nullUnmetCriteriaBecomesEmpty() { // GH-90000
        com.ghatana.yappc.agent.ApprovalRequest src = new com.ghatana.yappc.agent.ApprovalRequest( // GH-90000
                "", "tenant", "proj", "agent", "PHASE_ADVANCE",
                "A", "B", "reason", null, null, null);
        ApprovalRequest result = ApprovalRequestMapper.toLifecycleRequest(src, "id-005", NOW); // GH-90000
        assertThat(result.context().unmetCriteria()).isEmpty(); // GH-90000
        assertThat(result.context().missingArtifacts()).isEmpty(); // GH-90000
        assertThat(result.expiresAt()).isNull(); // GH-90000
    }

    @Test
    @DisplayName("unknown approvalType throws IllegalArgumentException [GH-90000]")
    void unknownTypeThrows() { // GH-90000
        com.ghatana.yappc.agent.ApprovalRequest src = agentRequest("UNSUPPORTED_TYPE [GH-90000]");
        assertThatThrownBy(() -> ApprovalRequestMapper.toLifecycleRequest(src, "id-006", NOW)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Unknown approvalType [GH-90000]");
    }

    @Test
    @DisplayName("blank approvalType throws IllegalArgumentException [GH-90000]")
    void blankTypeThrows() { // GH-90000
        com.ghatana.yappc.agent.ApprovalRequest src = agentRequest("   [GH-90000]");
        assertThatThrownBy(() -> ApprovalRequestMapper.toLifecycleRequest(src, "id-007", NOW)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("must not be blank [GH-90000]");
    }

    // ─── toAgentRequest ───────────────────────────────────────────────────────

    @Test
    @DisplayName("round-trips lifecycle → agent preserving key fields [GH-90000]")
    void roundTripLifecycleToAgent() { // GH-90000
        ApprovalRequest lifecycle = ApprovalRequestMapper.toLifecycleRequest( // GH-90000
                agentRequest("PHASE_ADVANCE [GH-90000]"), "rt-id-001", NOW);

        com.ghatana.yappc.agent.ApprovalRequest agent = ApprovalRequestMapper.toAgentRequest(lifecycle); // GH-90000

        assertThat(agent.requestId()).isEqualTo("rt-id-001 [GH-90000]");
        assertThat(agent.tenantId()).isEqualTo("tenant-mapper-test [GH-90000]");
        assertThat(agent.projectId()).isEqualTo("proj-mapper-001 [GH-90000]");
        assertThat(agent.requestingAgentId()).isEqualTo("agent-build-007 [GH-90000]");
        assertThat(agent.approvalType()).isEqualTo("PHASE_ADVANCE [GH-90000]");
        assertThat(agent.fromPhase()).isEqualTo("INTENT [GH-90000]");
        assertThat(agent.toPhase()).isEqualTo("SHAPE [GH-90000]");
        assertThat(agent.blockReason()).contains("code coverage [GH-90000]");
        assertThat(agent.expiresAt()).isEqualTo(EXPIRES); // GH-90000
    }

    @Test
    @DisplayName("toAgentRequest handles null context gracefully [GH-90000]")
    void toAgentRequestNullContext() { // GH-90000
        ApprovalRequest lifecycle = new ApprovalRequest( // GH-90000
                "id-null-ctx", "proj", null,
                ApprovalRequest.ApprovalType.DEPLOYMENT,
                null,  // context = null
                ApprovalRequest.ApprovalStatus.PENDING,
                "tenant", NOW, null, null, null);

        com.ghatana.yappc.agent.ApprovalRequest agent = ApprovalRequestMapper.toAgentRequest(lifecycle); // GH-90000

        assertThat(agent.requestId()).isEqualTo("id-null-ctx [GH-90000]");
        assertThat(agent.fromPhase()).isNull(); // GH-90000
        assertThat(agent.toPhase()).isNull(); // GH-90000
        assertThat(agent.blockReason()).isNull(); // GH-90000
        assertThat(agent.unmetCriteria()).isEmpty(); // GH-90000
        assertThat(agent.missingArtifacts()).isEmpty(); // GH-90000
    }
}
