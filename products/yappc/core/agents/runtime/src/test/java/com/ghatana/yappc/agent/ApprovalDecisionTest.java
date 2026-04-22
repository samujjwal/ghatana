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
@DisplayName("ApprovalDecision Tests [GH-90000]")
class ApprovalDecisionTest {

  @Test
  @DisplayName("should create ApprovalDecision with all fields [GH-90000]")
  void shouldCreateApprovalDecision() { // GH-90000
    Instant now = Instant.now(); // GH-90000

    ApprovalDecision decision = new ApprovalDecision( // GH-90000
        "req-1", true, "user-1", now, "Approved"
    );

    assertThat(decision.requestId()).isEqualTo("req-1 [GH-90000]");
    assertThat(decision.approved()).isTrue(); // GH-90000
    assertThat(decision.decidedBy()).isEqualTo("user-1 [GH-90000]");
    assertThat(decision.decidedAt()).isEqualTo(now); // GH-90000
    assertThat(decision.comment()).isEqualTo("Approved [GH-90000]");
  }

  @Test
  @DisplayName("isApproved should return true for approved decision [GH-90000]")
  void isApprovedShouldReturnTrue() { // GH-90000
    ApprovalDecision decision = new ApprovalDecision("req-1", true, "user-1", Instant.now(), null); // GH-90000

    assertThat(decision.isApproved()).isTrue(); // GH-90000
    assertThat(decision.isRejected()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("isRejected should return true for rejected decision [GH-90000]")
  void isRejectedShouldReturnTrue() { // GH-90000
    ApprovalDecision decision = new ApprovalDecision("req-1", false, "user-1", Instant.now(), null); // GH-90000

    assertThat(decision.isRejected()).isTrue(); // GH-90000
    assertThat(decision.isApproved()).isFalse(); // GH-90000
  }

  @Test
  @DisplayName("approved factory should create approved decision with current timestamp [GH-90000]")
  void approvedFactoryShouldCreateApprovedDecision() { // GH-90000
    ApprovalDecision decision = ApprovalDecision.approved("req-1", "user-1", "Looks good"); // GH-90000

    assertThat(decision.requestId()).isEqualTo("req-1 [GH-90000]");
    assertThat(decision.approved()).isTrue(); // GH-90000
    assertThat(decision.decidedBy()).isEqualTo("user-1 [GH-90000]");
    assertThat(decision.comment()).isEqualTo("Looks good [GH-90000]");
    assertThat(decision.decidedAt()).isNotNull(); // GH-90000
  }

  @Test
  @DisplayName("rejected factory should create rejected decision with current timestamp [GH-90000]")
  void rejectedFactoryShouldCreateRejectedDecision() { // GH-90000
    ApprovalDecision decision = ApprovalDecision.rejected("req-2", "user-2", "Needs work"); // GH-90000

    assertThat(decision.requestId()).isEqualTo("req-2 [GH-90000]");
    assertThat(decision.approved()).isFalse(); // GH-90000
    assertThat(decision.decidedBy()).isEqualTo("user-2 [GH-90000]");
    assertThat(decision.comment()).isEqualTo("Needs work [GH-90000]");
    assertThat(decision.decidedAt()).isNotNull(); // GH-90000
  }

  @Test
  @DisplayName("should handle null fields [GH-90000]")
  void shouldHandleNullFields() { // GH-90000
    ApprovalDecision decision = new ApprovalDecision(null, false, null, null, null); // GH-90000

    assertThat(decision.requestId()).isNull(); // GH-90000
    assertThat(decision.decidedBy()).isNull(); // GH-90000
    assertThat(decision.decidedAt()).isNull(); // GH-90000
    assertThat(decision.comment()).isNull(); // GH-90000
  }

  @Test
  @DisplayName("should implement equals correctly [GH-90000]")
  void shouldImplementEquals() { // GH-90000
    Instant now = Instant.now(); // GH-90000
    ApprovalDecision d1 = new ApprovalDecision("req-1", true, "user-1", now, "comment"); // GH-90000
    ApprovalDecision d2 = new ApprovalDecision("req-1", true, "user-1", now, "comment"); // GH-90000
    ApprovalDecision d3 = new ApprovalDecision("req-2", false, "user-2", now, "other"); // GH-90000

    assertThat(d1).isEqualTo(d2); // GH-90000
    assertThat(d1).isNotEqualTo(d3); // GH-90000
  }
}
