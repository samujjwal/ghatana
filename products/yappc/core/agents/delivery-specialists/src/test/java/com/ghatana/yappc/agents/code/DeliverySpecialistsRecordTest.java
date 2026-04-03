package com.ghatana.yappc.agents.code;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for delivery-specialist input/output record types.
 * Validates construction, validation constraints, and immutability defaults.
 *
 * @doc.type class
 * @doc.purpose Unit tests for delivery-specialist record types and their invariants
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Delivery Specialists Record Types")
class DeliverySpecialistsRecordTest {

    // ─── BudgetGateInput ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("BudgetGateInput")
    class BudgetGateInputTest {

        @Test
        @DisplayName("constructs successfully with valid arguments")
        void shouldConstructSuccessfully() {
            var input = new BudgetGateInput("req-001", 150.0, "ai-compute", Map.of("env", "prod"));
            assertThat(input.requestId()).isEqualTo("req-001");
            assertThat(input.estimatedCost()).isEqualTo(150.0);
            assertThat(input.budgetCategory()).isEqualTo("ai-compute");
            assertThat(input.context()).containsEntry("env", "prod");
        }

        @Test
        @DisplayName("substitutes empty map when context is null")
        void shouldDefaultContextToEmptyMap() {
            var input = new BudgetGateInput("req-002", 0.0, "storage", null);
            assertThat(input.context()).isEmpty();
        }

        @Test
        @DisplayName("throws when requestId is null")
        void shouldRejectNullRequestId() {
            assertThatThrownBy(() -> new BudgetGateInput(null, 0.0, "compute", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requestId");
        }

        @Test
        @DisplayName("throws when requestId is blank")
        void shouldRejectBlankRequestId() {
            assertThatThrownBy(() -> new BudgetGateInput("", 0.0, "compute", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws when budgetCategory is null")
        void shouldRejectNullBudgetCategory() {
            assertThatThrownBy(() -> new BudgetGateInput("req-003", 10.0, null, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("budgetCategory");
        }

        @Test
        @DisplayName("throws when budgetCategory is blank")
        void shouldRejectBlankBudgetCategory() {
            assertThatThrownBy(() -> new BudgetGateInput("req-003", 10.0, "", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("allows zero estimated cost")
        void shouldAllowZeroCost() {
            var input = new BudgetGateInput("req-004", 0.0, "free-tier", Map.of());
            assertThat(input.estimatedCost()).isZero();
        }
    }

    // ─── BudgetGateOutput ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("BudgetGateOutput")
    class BudgetGateOutputTest {

        @Test
        @DisplayName("constructs with approved=true")
        void shouldConstructApproved() {
            var output = new BudgetGateOutput("gate-001", true, 850.0, Map.of("approvedAt", "now"));
            assertThat(output.gateId()).isEqualTo("gate-001");
            assertThat(output.approved()).isTrue();
            assertThat(output.remainingBudget()).isEqualTo(850.0);
        }

        @Test
        @DisplayName("constructs with approved=false (budget exceeded)")
        void shouldConstructRejected() {
            var output = new BudgetGateOutput("gate-002", false, 0.0, Map.of());
            assertThat(output.approved()).isFalse();
            assertThat(output.remainingBudget()).isZero();
        }

        @Test
        @DisplayName("defaults metadata to empty map when null")
        void shouldDefaultMetadataToEmptyMap() {
            var output = new BudgetGateOutput("gate-003", true, 100.0, null);
            assertThat(output.metadata()).isEmpty();
        }

        @Test
        @DisplayName("throws when gateId is null")
        void shouldRejectNullGateId() {
            assertThatThrownBy(() -> new BudgetGateOutput(null, true, 0.0, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("gateId");
        }

        @Test
        @DisplayName("throws when gateId is blank")
        void shouldRejectBlankGateId() {
            assertThatThrownBy(() -> new BudgetGateOutput("", true, 0.0, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─── ReleaseOrchestratorInput ─────────────────────────────────────────────

    @Nested
    @DisplayName("ReleaseOrchestratorInput")
    class ReleaseOrchestratorInputTest {

        @Test
        @DisplayName("constructs with all required fields")
        void shouldConstruct() {
            var input = new ReleaseOrchestratorInput(
                    "rel-001", "1.2.3", "canary",
                    List.of("app-v1.2.3.jar", "config-v1.2.3.yaml"),
                    Map.of("region", "us-east-1"));
            assertThat(input.releaseId()).isEqualTo("rel-001");
            assertThat(input.version()).isEqualTo("1.2.3");
            assertThat(input.releaseType()).isEqualTo("canary");
            assertThat(input.artifacts()).hasSize(2);
        }

        @Test
        @DisplayName("defaults releaseType to 'standard' when blank")
        void shouldDefaultReleaseType() {
            var input = new ReleaseOrchestratorInput("rel-002", "2.0.0", "", List.of(), Map.of());
            assertThat(input.releaseType()).isEqualTo("standard");
        }

        @Test
        @DisplayName("defaults artifacts to empty list when null")
        void shouldDefaultArtifactsToEmptyList() {
            var input = new ReleaseOrchestratorInput("rel-003", "1.0.0", "standard", null, Map.of());
            assertThat(input.artifacts()).isEmpty();
        }

        @Test
        @DisplayName("defaults context to empty map when null")
        void shouldDefaultContextToEmptyMap() {
            var input = new ReleaseOrchestratorInput("rel-004", "1.0.0", "hotfix", List.of(), null);
            assertThat(input.context()).isEmpty();
        }

        @Test
        @DisplayName("throws when releaseId is null")
        void shouldRejectNullReleaseId() {
            assertThatThrownBy(() -> new ReleaseOrchestratorInput(null, "1.0.0", "standard", List.of(), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("releaseId");
        }

        @Test
        @DisplayName("throws when version is blank")
        void shouldRejectBlankVersion() {
            assertThatThrownBy(() -> new ReleaseOrchestratorInput("rel-005", "", "standard", List.of(), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("version");
        }
    }

    // ─── VulnerabilityScoringInput ────────────────────────────────────────────

    @Nested
    @DisplayName("VulnerabilityScoringInput")
    class VulnerabilityScoringInputTest {

        @Test
        @DisplayName("constructs with valid arguments")
        void shouldConstruct() {
            var input = new VulnerabilityScoringInput(
                    "vuln-123",
                    "CVE-2024-12345",
                    Map.of("severity", "HIGH"));
            assertThat(input.vulnerabilityId()).isEqualTo("vuln-123");
            assertThat(input.cveData()).isEqualTo("CVE-2024-12345");
            assertThat(input.context()).containsEntry("severity", "HIGH");
        }

        @Test
        @DisplayName("defaults context to empty map when null")
        void shouldDefaultContextToEmptyMap() {
            var input = new VulnerabilityScoringInput("vuln-124", "CVE-2024-00001", null);
            assertThat(input.context()).isEmpty();
        }

        @Test
        @DisplayName("throws when vulnerabilityId is null")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> new VulnerabilityScoringInput(null, "CVE-2024-99999", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("vulnerabilityId");
        }

        @Test
        @DisplayName("throws when cveData is blank")
        void shouldRejectBlankCveData() {
            assertThatThrownBy(() -> new VulnerabilityScoringInput("vuln-125", "", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cveData");
        }
    }

    // ─── BudgetGateGenerator metadata ────────────────────────────────────────

    @Nested
    @DisplayName("BudgetGateAgent.BudgetGateGenerator")
    class BudgetGateGeneratorTest {

        @Test
        @DisplayName("generator metadata has expected name and type")
        void shouldHaveCorrectMetadata() {
            var generator = new BudgetGateAgent.BudgetGateGenerator();
            var metadata = generator.getMetadata();
            assertThat(metadata.getName()).isEqualTo("BudgetGateGenerator");
            assertThat(metadata.getType()).isEqualTo("rule-based");
            assertThat(metadata.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("estimateCost always returns 0.0 (deterministic rule-based)")
        void shouldEstimateZeroCost() throws Exception {
            var generator = new BudgetGateAgent.BudgetGateGenerator();
            var costPromise = generator.estimateCost(null, null);
            // Promise.of() returns immediately
            assertThat(costPromise.getResult()).isEqualTo(0.0); // y04-ok: Promise.of() is synchronous
        }
    }
}
