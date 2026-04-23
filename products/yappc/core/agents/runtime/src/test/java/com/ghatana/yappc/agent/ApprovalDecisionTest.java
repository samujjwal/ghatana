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
  void shouldCreateApprovalDecision() { // GH-90000
    Instant now = Instant.now(); // GH-90000

    ApprovalDecision decision = new ApprovalDecision( // GH-90000
        "req-1", true, "user-1", now, "Approved"
    );

    assertThat(decision.requestId()).isEqualTo("req-1");
    assertThat(decision.approved()).isTrue(); // GH-90000
    assertThat(decision.decidedBy()).isEqualTo("user-1");
    assertThat(decision.decidedAt()).isEqualTo(now); // GH-90000
    assertThat(decision.comment()).isEqualTo("Approved");
  }

  @Test
  @DisplayName("isApproved should return true for approved decision")
  void isApprovedShouldReturnTrue() { // GH-90000
    ApprovalDecision decision = new ApprovalDecision("req-1", true, "user-1", Instant.now(), null); // GH-90000

    assertThat(decision.isApproved()).isTrue(); // GH-90000
    assertThat(decision.isRejected()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("isRejected should return true for rejected decision")
  void isRejectedShouldReturnTrue() { // GH-90000
    ApprovalDecision decision = new ApprovalDecision("req-1", false, "user-1", Instant.now(), null); // GH-90000

    assertThat(decision.isRejected()).isTrue(); // GH-90000
    assertThat(decision.isApproved()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("approved factory should create approved decision with current timestamp")
  void approvedFactoryShouldCreateApprovedDecision() { // GH-90000
    ApprovalDecision decision = ApprovalDecision.approved("req-1", "user-1", "Looks good"); // GH-90000

    assertThat(decision.requestId()).isEqualTo("req-1");
    assertThat(decision.approved()).isTrue(); // GH-90000
    assertThat(decision.decidedBy()).isEqualTo("user-1");
    assertThat(decision.comment()).isEqualTo("Looks good");
    assertThat(decision.decidedAt()).isNotNull(); // GH-90000
  }

  @Test
  @DisplayName("rejected factory should create rejected decision with current timestamp")
  void rejectedFactoryShouldCreateRejectedDecision() { // GH-90000
    ApprovalDecision decision = ApprovalDecision.rejected("req-2", "user-2", "Needs work"); // GH-90000

    assertThat(decision.requestId()).isEqualTo("req-2");
    assertThat(decision.approved()).isFalse(); // GH-90000
    assertThat(decision.decidedBy()).isEqualTo("user-2");
    assertThat(decision.comment()).isEqualTo("Needs work");
    assertThat(decision.decidedAt()).isNotNull(); // GH-90000
  }

  @Test
  @DisplayName("should handle null fields")
  void shouldHandleNullFields() { // GH-90000
    ApprovalDecision decision = new ApprovalDecision(null, false, null, null, null); // GH-90000

    assertThat(decision.requestId()).isNull(); // GH-90000
    assertThat(decision.decidedBy()).isNull(); // GH-90000
    assertThat(decision.decidedAt()).isNull(); // GH-90000
    assertThat(decision.comment()).isNull(); // GH-90000
  }

  @Test
  @DisplayName("should implement equals correctly")
  void shouldImplementEquals() { // GH-90000
    Instant now = Instant.now(); // GH-90000
    ApprovalDecision d1 = new ApprovalDecision("req-1", true, "user-1", now, "comment"); // GH-90000
    ApprovalDecision d2 = new ApprovalDecision("req-1", true, "user-1", now, "comment"); // GH-90000
    ApprovalDecision d3 = new ApprovalDecision("req-2", false, "user-2", now, "other"); // GH-90000

    assertThat(d1).isEqualTo(d2); // GH-90000
    assertThat(d1).isNotEqualTo(d3); // GH-90000
  }
}
