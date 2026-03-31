package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ApprovalDecision}.
 *
 * @doc.type class
 * @doc.purpose Verify ApprovalDecision record behavior and factory methods
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("ApprovalDecision Tests")
class ApprovalDecisionTest {

  @Test
  @DisplayName("should create ApprovalDecision with all fields")
  void shouldCreateApprovalDecision() {
    Instant now = Instant.now();

    ApprovalDecision decision = new ApprovalDecision(
        "req-1", true, "user-1", now, "Approved"
    );

    assertThat(decision.requestId()).isEqualTo("req-1");
    assertThat(decision.approved()).isTrue();
    assertThat(decision.decidedBy()).isEqualTo("user-1");
    assertThat(decision.decidedAt()).isEqualTo(now);
    assertThat(decision.comment()).isEqualTo("Approved");
  }

  @Test
  @DisplayName("isApproved should return true for approved decision")
  void isApprovedShouldReturnTrue() {
    ApprovalDecision decision = new ApprovalDecision("req-1", true, "user-1", Instant.now(), null);

    assertThat(decision.isApproved()).isTrue();
    assertThat(decision.isRejected()).isFalse();
  }

  @Test
  @DisplayName("isRejected should return true for rejected decision")
  void isRejectedShouldReturnTrue() {
    ApprovalDecision decision = new ApprovalDecision("req-1", false, "user-1", Instant.now(), null);

    assertThat(decision.isRejected()).isTrue();
    assertThat(decision.isApproved()).isFalse();
  }

  @Test
  @DisplayName("approved factory should create approved decision with current timestamp")
  void approvedFactoryShouldCreateApprovedDecision() {
    ApprovalDecision decision = ApprovalDecision.approved("req-1", "user-1", "Looks good");

    assertThat(decision.requestId()).isEqualTo("req-1");
    assertThat(decision.approved()).isTrue();
    assertThat(decision.decidedBy()).isEqualTo("user-1");
    assertThat(decision.comment()).isEqualTo("Looks good");
    assertThat(decision.decidedAt()).isNotNull();
  }

  @Test
  @DisplayName("rejected factory should create rejected decision with current timestamp")
  void rejectedFactoryShouldCreateRejectedDecision() {
    ApprovalDecision decision = ApprovalDecision.rejected("req-2", "user-2", "Needs work");

    assertThat(decision.requestId()).isEqualTo("req-2");
    assertThat(decision.approved()).isFalse();
    assertThat(decision.decidedBy()).isEqualTo("user-2");
    assertThat(decision.comment()).isEqualTo("Needs work");
    assertThat(decision.decidedAt()).isNotNull();
  }

  @Test
  @DisplayName("should handle null fields")
  void shouldHandleNullFields() {
    ApprovalDecision decision = new ApprovalDecision(null, false, null, null, null);

    assertThat(decision.requestId()).isNull();
    assertThat(decision.decidedBy()).isNull();
    assertThat(decision.decidedAt()).isNull();
    assertThat(decision.comment()).isNull();
  }

  @Test
  @DisplayName("should implement equals correctly")
  void shouldImplementEquals() {
    Instant now = Instant.now();
    ApprovalDecision d1 = new ApprovalDecision("req-1", true, "user-1", now, "comment");
    ApprovalDecision d2 = new ApprovalDecision("req-1", true, "user-1", now, "comment");
    ApprovalDecision d3 = new ApprovalDecision("req-2", false, "user-2", now, "other");

    assertThat(d1).isEqualTo(d2);
    assertThat(d1).isNotEqualTo(d3);
  }
}
