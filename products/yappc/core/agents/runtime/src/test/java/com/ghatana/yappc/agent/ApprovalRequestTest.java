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
@DisplayName("ApprovalRequest Tests")
class ApprovalRequestTest {

  @Test
  @DisplayName("should create ApprovalRequest with all fields")
  void shouldCreateApprovalRequest() {
    Instant expiresAt = Instant.now().plusSeconds(3600);

    ApprovalRequest request = new ApprovalRequest(
        "req-1",
        "tenant-1",
        "project-1",
        "agent-1",
        ApprovalRequest.TYPE_PHASE_ADVANCE,
        "dev",
        "staging",
        "Need approval for phase advance",
        List.of("criteria-1"),
        List.of("artifact-1"),
        expiresAt
    );

    assertThat(request.requestId()).isEqualTo("req-1");
    assertThat(request.tenantId()).isEqualTo("tenant-1");
    assertThat(request.projectId()).isEqualTo("project-1");
    assertThat(request.requestingAgentId()).isEqualTo("agent-1");
    assertThat(request.approvalType()).isEqualTo(ApprovalRequest.TYPE_PHASE_ADVANCE);
    assertThat(request.fromPhase()).isEqualTo("dev");
    assertThat(request.toPhase()).isEqualTo("staging");
    assertThat(request.blockReason()).isEqualTo("Need approval for phase advance");
    assertThat(request.unmetCriteria()).containsExactly("criteria-1");
    assertThat(request.missingArtifacts()).containsExactly("artifact-1");
    assertThat(request.expiresAt()).isEqualTo(expiresAt);
  }

  @Test
  @DisplayName("should default null lists to empty")
  void shouldDefaultNullListsToEmpty() {
    ApprovalRequest request = new ApprovalRequest(
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

    assertThat(request.unmetCriteria()).isEmpty();
    assertThat(request.missingArtifacts()).isEmpty();
  }

  @Test
  @DisplayName("should have correct type constants")
  void shouldHaveCorrectTypeConstants() {
    assertThat(ApprovalRequest.TYPE_PHASE_ADVANCE).isEqualTo("PHASE_ADVANCE");
    assertThat(ApprovalRequest.TYPE_DEPLOYMENT).isEqualTo("DEPLOYMENT");
    assertThat(ApprovalRequest.TYPE_RISK_ACCEPTANCE).isEqualTo("RISK_ACCEPTANCE");
  }

  @Test
  @DisplayName("should create immutable copies of lists")
  void shouldCreateImmutableCopies() {
    List<String> criteria = new java.util.ArrayList<>();
    criteria.add("criteria-1");

    ApprovalRequest request = new ApprovalRequest(
        "req-1", "tenant-1", "project-1", null,
        ApprovalRequest.TYPE_RISK_ACCEPTANCE, null, null, "Risk",
        criteria, null, null
    );

    assertThat(request.unmetCriteria()).containsExactly("criteria-1");

    // Modify original list
    criteria.add("criteria-2");

    // Request should still have original value
    assertThat(request.unmetCriteria()).containsExactly("criteria-1");
  }
}
