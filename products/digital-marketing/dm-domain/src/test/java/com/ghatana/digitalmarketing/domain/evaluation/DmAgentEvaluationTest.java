package com.ghatana.digitalmarketing.domain.evaluation;

import com.ghatana.digitalmarketing.domain.evaluation.DmAgentEvaluation;
import com.ghatana.digitalmarketing.domain.evaluation.DmAgentEvaluation.DmEvalMetric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmAgentEvaluation domain entity")
class DmAgentEvaluationTest {

    private DmAgentEvaluation valid() {
        return DmAgentEvaluation.builder()
            .id("eval-1").tenantId("t1").workspaceId("ws1")
            .agentId("agent-1").agentType("RECOMMENDATION")
            .metrics(List.of(new DmEvalMetric("precision", 0.9, "good")))
            .overallScore(0.9).verdict("PASS")
            .evaluatedBy("system").evaluatedAt(Instant.now()).createdAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmAgentEvaluation e = valid();
        assertThat(e.getId()).isEqualTo("eval-1");
        assertThat(e.getOverallScore()).isEqualTo(0.9);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAgentEvaluation.builder().id("").tenantId("t").agentId("a").agentType("t")
                .metrics(List.of()).overallScore(0.5).verdict("PASS")
                .evaluatedBy("s").evaluatedAt(Instant.now()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects overallScore below 0")
    void shouldRejectNegativeScore() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAgentEvaluation.builder().id("x").tenantId("t").agentId("a").agentType("t")
                .metrics(List.of()).overallScore(-0.1).verdict("FAIL")
                .evaluatedBy("s").evaluatedAt(Instant.now()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("DmEvalMetric rejects score out of range")
    void shouldRejectMetricScoreOutOfRange() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new DmEvalMetric("recall", 1.5, "bad"));
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() { assertThat(valid()).isNotEqualTo(null); }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() { assertThat(valid()).isNotEqualTo("x"); }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmAgentEvaluation e = valid();
        assertThat(e.getTenantId()).isEqualTo("t1");
        assertThat(e.getWorkspaceId()).isEqualTo("ws1");
        assertThat(e.getAgentId()).isEqualTo("agent-1");
        assertThat(e.getAgentType()).isEqualTo("RECOMMENDATION");
        assertThat(e.getVerdict()).isEqualTo("PASS");
        assertThat(e.getMetrics()).hasSize(1);
        assertThat(e.getEvaluatedBy()).isEqualTo("system");
        assertThat(e.getEvaluatedAt()).isNotNull();
        assertThat(e.getCreatedAt()).isNotNull();
        assertThat(e.toString()).contains("eval-1");
    }

    @Test @DisplayName("builder rejects null createdAt")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmAgentEvaluation.builder().id("x").tenantId("t").agentId("a").agentType("t")
                .metrics(List.of()).overallScore(0.5).verdict("PASS")
                .evaluatedAt(Instant.now()).createdAt(null).build());
    }

    @Test @DisplayName("builder rejects null metrics")
    void shouldRejectNullMetrics() {
        assertThatNullPointerException().isThrownBy(() ->
            DmAgentEvaluation.builder().id("x").tenantId("t").agentId("a").agentType("t")
                .metrics(null).overallScore(0.5).verdict("PASS")
                .evaluatedAt(Instant.now()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null id")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAgentEvaluation.builder().id(null).tenantId("t").agentId("a").agentType("t")
                .metrics(List.of()).overallScore(0.5).verdict("PASS")
                .evaluatedAt(Instant.now()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null tenantId")
    void shouldRejectNullTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAgentEvaluation.builder().id("x").tenantId(null).agentId("a").agentType("t")
                .metrics(List.of()).overallScore(0.5).verdict("PASS")
                .evaluatedAt(Instant.now()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank tenantId")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAgentEvaluation.builder().id("x").tenantId("").agentId("a").agentType("t")
                .metrics(List.of()).overallScore(0.5).verdict("PASS")
                .evaluatedAt(Instant.now()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null agentId")
    void shouldRejectNullAgentId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAgentEvaluation.builder().id("x").tenantId("t").agentId(null).agentType("t")
                .metrics(List.of()).overallScore(0.5).verdict("PASS")
                .evaluatedAt(Instant.now()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank agentId")
    void shouldRejectBlankAgentId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAgentEvaluation.builder().id("x").tenantId("t").agentId("").agentType("t")
                .metrics(List.of()).overallScore(0.5).verdict("PASS")
                .evaluatedAt(Instant.now()).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects overallScore above 1")
    void shouldRejectScoreAboveOne() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAgentEvaluation.builder().id("x").tenantId("t").agentId("a").agentType("t")
                .metrics(List.of()).overallScore(1.1).verdict("PASS")
                .evaluatedAt(Instant.now()).createdAt(Instant.now()).build());
    }
}
