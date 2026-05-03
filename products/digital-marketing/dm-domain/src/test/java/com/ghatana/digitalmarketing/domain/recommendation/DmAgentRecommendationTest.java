package com.ghatana.digitalmarketing.domain.recommendation;

import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link DmAgentRecommendation}.
 *
 * @doc.type class
 * @doc.purpose Verifies recommendation domain entity lifecycle (DMOS-F2-005)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmAgentRecommendation domain tests")
class DmAgentRecommendationTest {

    private DmAgentRecommendation.Builder validBuilder() {
        return DmAgentRecommendation.builder()
            .id("rec-1")
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .agentId("agent-1")
            .targetCommandType(DmCommandType.CAMPAIGN_CREATE)
            .payload(Map.of("budget", 500))
            .rationale("High ROI predicted")
            .status(DmRecommendationStatus.PENDING)
            .createdAt(Instant.now());
    }

    @Test
    @DisplayName("accept transitions PENDING to ACCEPTED with commandId")
    void acceptSuccess() {
        DmAgentRecommendation accepted = validBuilder().build().accept("cmd-1");
        assertThat(accepted.getStatus()).isEqualTo(DmRecommendationStatus.ACCEPTED);
        assertThat(accepted.getCommandId()).isEqualTo("cmd-1");
        assertThat(accepted.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("accept rejects non-PENDING recommendation")
    void acceptRejectsNonPending() {
        DmAgentRecommendation accepted = validBuilder().build().accept("cmd-1");
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> accepted.accept("cmd-2"));
    }

    @Test
    @DisplayName("accept rejects null commandId")
    void acceptRejectsNullCommandId() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> validBuilder().build().accept(null));
    }

    @Test
    @DisplayName("reject transitions PENDING to REJECTED with reason")
    void rejectSuccess() {
        DmAgentRecommendation rejected = validBuilder().build().reject("policy violation");
        assertThat(rejected.getStatus()).isEqualTo(DmRecommendationStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("policy violation");
    }

    @Test
    @DisplayName("reject rejects non-PENDING recommendation")
    void rejectNonPending() {
        DmAgentRecommendation rejected = validBuilder().build().reject("reason");
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> rejected.reject("another reason"));
    }

    @Test
    @DisplayName("reject rejects null reason")
    void rejectNullReason() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> validBuilder().build().reject(null));
    }

    @Test
    @DisplayName("expire transitions PENDING to EXPIRED")
    void expireSuccess() {
        DmAgentRecommendation expired = validBuilder().build().expire();
        assertThat(expired.getStatus()).isEqualTo(DmRecommendationStatus.EXPIRED);
        assertThat(expired.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("expire rejects non-PENDING recommendation")
    void expireNonPending() {
        DmAgentRecommendation expired = validBuilder().build().expire();
        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(expired::expire);
    }

    @Test
    @DisplayName("isProcessed returns false for PENDING and true for others")
    void isProcessed() {
        assertThat(validBuilder().build().isProcessed()).isFalse();
        assertThat(validBuilder().build().accept("c").isProcessed()).isTrue();
        assertThat(validBuilder().build().reject("r").isProcessed()).isTrue();
        assertThat(validBuilder().build().expire().isProcessed()).isTrue();
    }

    @Test
    @DisplayName("equals and hashCode are id-based")
    void equalsAndHashCode() {
        DmAgentRecommendation a = validBuilder().id("rec-1").build();
        DmAgentRecommendation b = validBuilder().id("rec-1").rationale("different").build();
        DmAgentRecommendation c = validBuilder().id("rec-2").build();
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    @DisplayName("toString contains id, agentId, commandType, and status")
    void toStringContainsKeyFields() {
        String str = validBuilder().build().toString();
        assertThat(str).contains("rec-1").contains("agent-1")
            .contains("CAMPAIGN_CREATE").contains("PENDING");
    }

    @Test
    @DisplayName("builder rejects blank id")
    void builderRejectsBlankId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().id("").build());
    }

    @Test
    @DisplayName("builder rejects null status")
    void builderRejectsNullStatus() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> validBuilder().status(null).build());
    }

    @Test
    @DisplayName("builder rejects null payload")
    void builderRejectsNullPayload() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> validBuilder().payload(null).build());
    }

    @Test
    @DisplayName("builder rejects blank tenantId")
    void builderRejectsBlankTenantId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> validBuilder().tenantId("").build());
    }
}
