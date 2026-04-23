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
        void shouldConstructSuccessfully() { // GH-90000
            var input = new BudgetGateInput("req-001", 150.0, "ai-compute", Map.of("env", "prod")); // GH-90000
            assertThat(input.requestId()).isEqualTo("req-001");
            assertThat(input.estimatedCost()).isEqualTo(150.0); // GH-90000
            assertThat(input.budgetCategory()).isEqualTo("ai-compute");
            assertThat(input.context()).containsEntry("env", "prod"); // GH-90000
        }

        @Test
        @DisplayName("substitutes empty map when context is null")
        void shouldDefaultContextToEmptyMap() { // GH-90000
            var input = new BudgetGateInput("req-002", 0.0, "storage", null); // GH-90000
            assertThat(input.context()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("throws when requestId is null")
        void shouldRejectNullRequestId() { // GH-90000
            assertThatThrownBy(() -> new BudgetGateInput(null, 0.0, "compute", Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("requestId");
        }

        @Test
        @DisplayName("throws when requestId is blank")
        void shouldRejectBlankRequestId() { // GH-90000
            assertThatThrownBy(() -> new BudgetGateInput("", 0.0, "compute", Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("throws when budgetCategory is null")
        void shouldRejectNullBudgetCategory() { // GH-90000
            assertThatThrownBy(() -> new BudgetGateInput("req-003", 10.0, null, Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("budgetCategory");
        }

        @Test
        @DisplayName("throws when budgetCategory is blank")
        void shouldRejectBlankBudgetCategory() { // GH-90000
            assertThatThrownBy(() -> new BudgetGateInput("req-003", 10.0, "", Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("allows zero estimated cost")
        void shouldAllowZeroCost() { // GH-90000
            var input = new BudgetGateInput("req-004", 0.0, "free-tier", Map.of()); // GH-90000
            assertThat(input.estimatedCost()).isZero(); // GH-90000
        }
    }

    // ─── BudgetGateOutput ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("BudgetGateOutput")
    class BudgetGateOutputTest {

        @Test
        @DisplayName("constructs with approved=true")
        void shouldConstructApproved() { // GH-90000
            var output = new BudgetGateOutput("gate-001", true, 850.0, Map.of("approvedAt", "now")); // GH-90000
            assertThat(output.gateId()).isEqualTo("gate-001");
            assertThat(output.approved()).isTrue(); // GH-90000
            assertThat(output.remainingBudget()).isEqualTo(850.0); // GH-90000
        }

        @Test
        @DisplayName("constructs with approved=false (budget exceeded)")
        void shouldConstructRejected() { // GH-90000
            var output = new BudgetGateOutput("gate-002", false, 0.0, Map.of()); // GH-90000
            assertThat(output.approved()).isFalse(); // GH-90000
            assertThat(output.remainingBudget()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("defaults metadata to empty map when null")
        void shouldDefaultMetadataToEmptyMap() { // GH-90000
            var output = new BudgetGateOutput("gate-003", true, 100.0, null); // GH-90000
            assertThat(output.metadata()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("throws when gateId is null")
        void shouldRejectNullGateId() { // GH-90000
            assertThatThrownBy(() -> new BudgetGateOutput(null, true, 0.0, Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("gateId");
        }

        @Test
        @DisplayName("throws when gateId is blank")
        void shouldRejectBlankGateId() { // GH-90000
            assertThatThrownBy(() -> new BudgetGateOutput("", true, 0.0, Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ─── ReleaseOrchestratorInput ─────────────────────────────────────────────

    @Nested
    @DisplayName("ReleaseOrchestratorInput")
    class ReleaseOrchestratorInputTest {

        @Test
        @DisplayName("constructs with all required fields")
        void shouldConstruct() { // GH-90000
            var input = new ReleaseOrchestratorInput( // GH-90000
                    "rel-001", "1.2.3", "canary",
                    List.of("app-v1.2.3.jar", "config-v1.2.3.yaml"), // GH-90000
                    Map.of("region", "us-east-1")); // GH-90000
            assertThat(input.releaseId()).isEqualTo("rel-001");
            assertThat(input.version()).isEqualTo("1.2.3");
            assertThat(input.releaseType()).isEqualTo("canary");
            assertThat(input.artifacts()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("defaults releaseType to 'standard' when blank")
        void shouldDefaultReleaseType() { // GH-90000
            var input = new ReleaseOrchestratorInput("rel-002", "2.0.0", "", List.of(), Map.of()); // GH-90000
            assertThat(input.releaseType()).isEqualTo("standard");
        }

        @Test
        @DisplayName("defaults artifacts to empty list when null")
        void shouldDefaultArtifactsToEmptyList() { // GH-90000
            var input = new ReleaseOrchestratorInput("rel-003", "1.0.0", "standard", null, Map.of()); // GH-90000
            assertThat(input.artifacts()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("defaults context to empty map when null")
        void shouldDefaultContextToEmptyMap() { // GH-90000
            var input = new ReleaseOrchestratorInput("rel-004", "1.0.0", "hotfix", List.of(), null); // GH-90000
            assertThat(input.context()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("throws when releaseId is null")
        void shouldRejectNullReleaseId() { // GH-90000
            assertThatThrownBy(() -> new ReleaseOrchestratorInput(null, "1.0.0", "standard", List.of(), Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("releaseId");
        }

        @Test
        @DisplayName("throws when version is blank")
        void shouldRejectBlankVersion() { // GH-90000
            assertThatThrownBy(() -> new ReleaseOrchestratorInput("rel-005", "", "standard", List.of(), Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("version");
        }
    }

    // ─── VulnerabilityScoringInput ────────────────────────────────────────────

    @Nested
    @DisplayName("VulnerabilityScoringInput")
    class VulnerabilityScoringInputTest {

        @Test
        @DisplayName("constructs with valid arguments")
        void shouldConstruct() { // GH-90000
            var input = new VulnerabilityScoringInput( // GH-90000
                    "vuln-123",
                    "CVE-2024-12345",
                    Map.of("severity", "HIGH")); // GH-90000
            assertThat(input.vulnerabilityId()).isEqualTo("vuln-123");
            assertThat(input.cveData()).isEqualTo("CVE-2024-12345");
            assertThat(input.context()).containsEntry("severity", "HIGH"); // GH-90000
        }

        @Test
        @DisplayName("defaults context to empty map when null")
        void shouldDefaultContextToEmptyMap() { // GH-90000
            var input = new VulnerabilityScoringInput("vuln-124", "CVE-2024-00001", null); // GH-90000
            assertThat(input.context()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("throws when vulnerabilityId is null")
        void shouldRejectNullId() { // GH-90000
            assertThatThrownBy(() -> new VulnerabilityScoringInput(null, "CVE-2024-99999", Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("vulnerabilityId");
        }

        @Test
        @DisplayName("throws when cveData is blank")
        void shouldRejectBlankCveData() { // GH-90000
            assertThatThrownBy(() -> new VulnerabilityScoringInput("vuln-125", "", Map.of())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("cveData");
        }
    }

    // ─── BudgetGateGenerator metadata ────────────────────────────────────────

    @Nested
    @DisplayName("BudgetGateAgent.BudgetGateGenerator")
    class BudgetGateGeneratorTest {

        @Test
        @DisplayName("generator metadata has expected name and type")
        void shouldHaveCorrectMetadata() { // GH-90000
            var generator = new BudgetGateAgent.BudgetGateGenerator(); // GH-90000
            var metadata = generator.getMetadata(); // GH-90000
            assertThat(metadata.getName()).isEqualTo("BudgetGateGenerator");
            assertThat(metadata.getType()).isEqualTo("rule-based");
            assertThat(metadata.getVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("estimateCost always returns 0.0 (deterministic rule-based)")
        void shouldEstimateZeroCost() throws Exception { // GH-90000
            var generator = new BudgetGateAgent.BudgetGateGenerator(); // GH-90000
            var costPromise = generator.estimateCost(null, null); // GH-90000
            // Promise.of() returns immediately // GH-90000
            assertThat(costPromise.getResult()).isEqualTo(0.0); // y04-ok: Promise.of() is synchronous // GH-90000
        }
    }
}
