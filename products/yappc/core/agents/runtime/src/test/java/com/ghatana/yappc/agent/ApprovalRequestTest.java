package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ApprovalRequest}.
 *
 * @doc.type class
 * @doc.purpose Verify ApprovalRequest record behavior
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("ApprovalRequest Tests [GH-90000]")
class ApprovalRequestTest {

  @Test
  @DisplayName("should create ApprovalRequest with all fields [GH-90000]")
  void shouldCreateApprovalRequest() { // GH-90000
    Instant expiresAt = Instant.now().plusSeconds(3600); // GH-90000

    ApprovalRequest request = new ApprovalRequest( // GH-90000
        "req-1",
        "tenant-1",
        "project-1",
        "agent-1",
        ApprovalRequest.TYPE_PHASE_ADVANCE,
        "dev",
        "staging",
        "Need approval for phase advance",
        List.of("criteria-1 [GH-90000]"),
        List.of("artifact-1 [GH-90000]"),
        expiresAt
    );

    assertThat(request.requestId()).isEqualTo("req-1 [GH-90000]");
    assertThat(request.tenantId()).isEqualTo("tenant-1 [GH-90000]");
    assertThat(request.projectId()).isEqualTo("project-1 [GH-90000]");
    assertThat(request.requestingAgentId()).isEqualTo("agent-1 [GH-90000]");
    assertThat(request.approvalType()).isEqualTo(ApprovalRequest.TYPE_PHASE_ADVANCE); // GH-90000
    assertThat(request.fromPhase()).isEqualTo("dev [GH-90000]");
    assertThat(request.toPhase()).isEqualTo("staging [GH-90000]");
    assertThat(request.blockReason()).isEqualTo("Need approval for phase advance [GH-90000]");
    assertThat(request.unmetCriteria()).containsExactly("criteria-1 [GH-90000]");
    assertThat(request.missingArtifacts()).containsExactly("artifact-1 [GH-90000]");
    assertThat(request.expiresAt()).isEqualTo(expiresAt); // GH-90000
  }

  @Test
  @DisplayName("should default null lists to empty [GH-90000]")
  void shouldDefaultNullListsToEmpty() { // GH-90000
    ApprovalRequest request = new ApprovalRequest( // GH-90000
        "req-1",
        "tenant-1",
        "project-1",
        null,
        ApprovalRequest.TYPE_DEPLOYMENT,
        null,
        null,
        "Need deployment approval",
        null,
        null,
        null
    );

    assertThat(request.unmetCriteria()).isEmpty(); // GH-90000
    assertThat(request.missingArtifacts()).isEmpty(); // GH-90000
  }

  @Test
  @DisplayName("should have correct type constants [GH-90000]")
  void shouldHaveCorrectTypeConstants() { // GH-90000
    assertThat(ApprovalRequest.TYPE_PHASE_ADVANCE).isEqualTo("PHASE_ADVANCE [GH-90000]");
    assertThat(ApprovalRequest.TYPE_DEPLOYMENT).isEqualTo("DEPLOYMENT [GH-90000]");
    assertThat(ApprovalRequest.TYPE_RISK_ACCEPTANCE).isEqualTo("RISK_ACCEPTANCE [GH-90000]");
  }

  @Test
  @DisplayName("should create immutable copies of lists [GH-90000]")
  void shouldCreateImmutableCopies() { // GH-90000
    List<String> criteria = new java.util.ArrayList<>(); // GH-90000
    criteria.add("criteria-1 [GH-90000]");

    ApprovalRequest request = new ApprovalRequest( // GH-90000
        "req-1", "tenant-1", "project-1", null,
        ApprovalRequest.TYPE_RISK_ACCEPTANCE, null, null, "Risk",
        criteria, null, null
    );

    assertThat(request.unmetCriteria()).containsExactly("criteria-1 [GH-90000]");

    // Modify original list
    criteria.add("criteria-2 [GH-90000]");

    // Request should still have original value
    assertThat(request.unmetCriteria()).containsExactly("criteria-1 [GH-90000]");
  }
}
