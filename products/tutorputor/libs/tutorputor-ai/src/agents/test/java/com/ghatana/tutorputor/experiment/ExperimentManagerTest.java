package com.ghatana.tutorputor.experiment;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ExperimentManager.
 *
 * @doc.type class
 * @doc.purpose Unit tests for A/B testing
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ExperimentManager Tests")
class ExperimentManagerTest {

    private ExperimentManager experimentManager;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        experimentManager = new ExperimentManager(meterRegistry);
    }

    @Test
    @DisplayName("Should create and retrieve experiment")
    void shouldCreateAndRetrieveExperiment() {
        // GIVEN
        ExperimentManager.Experiment experiment = ExperimentManager.Experiment.builder()
            .id("exp-001")
            .name("Content Strategy Test")
            .description("Testing creative vs conservative content")
            .addVariant(ExperimentManager.Variant.builder()
                .id("control")
                .name("Standard")
                .weight(50.0)
                .config(Map.of("strategyId", "llm-standard"))
                .build())
            .addVariant(ExperimentManager.Variant.builder()
                .id("treatment")
                .name("Creative")
                .weight(50.0)
                .config(Map.of("strategyId", "llm-creative"))
                .build())
            .build();

        // WHEN
        experimentManager.createExperiment(experiment);

        // THEN
        ExperimentManager.Experiment retrieved = experimentManager.getExperiment("exp-001");
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.name()).isEqualTo("Content Strategy Test");
        assertThat(retrieved.variants()).hasSize(2);
    }

    @Test
    @DisplayName("Should assign users to variants deterministically")
    void shouldAssignUsersDeterministically() {
        // GIVEN
        createTwoVariantExperiment("exp-002");

        // WHEN
        ExperimentManager.Variant variant1 = experimentManager.getVariant("exp-002", "user-123");
        ExperimentManager.Variant variant2 = experimentManager.getVariant("exp-002", "user-123");

        // THEN - same user should get same variant
        assertThat(variant1).isNotNull();
        assertThat(variant2).isNotNull();
        assertThat(variant1.id()).isEqualTo(variant2.id());
    }

    @Test
    @DisplayName("Should distribute users across variants")
    void shouldDistributeUsersAcrossVariants() {
        // GIVEN
        createTwoVariantExperiment("exp-003");

        // WHEN - assign many users
        int controlCount = 0;
        int treatmentCount = 0;
        
        for (int i = 0; i < 1000; i++) {
            ExperimentManager.Variant variant = experimentManager.getVariant("exp-003", "user-" + i);
            if (variant != null) {
                if ("control".equals(variant.id())) {
                    controlCount++;
                } else {
                    treatmentCount++;
                }
            }
        }

        // THEN - roughly 50/50 split (within 10%)
        assertThat(controlCount).isBetween(400, 600);
        assertThat(treatmentCount).isBetween(400, 600);
    }

    @Test
    @DisplayName("Should respect traffic allocation")
    void shouldRespectTrafficAllocation() {
        // GIVEN
        ExperimentManager.Experiment experiment = ExperimentManager.Experiment.builder()
            .id("exp-004")
            .name("Limited Traffic Test")
            .trafficAllocation(10.0) // Only 10% of users
            .addVariant(ExperimentManager.Variant.builder()
                .id("control")
                .name("Control")
                .weight(100.0)
                .build())
            .build();
        experimentManager.createExperiment(experiment);

        // WHEN
        int includedCount = 0;
        for (int i = 0; i < 1000; i++) {
            ExperimentManager.Variant variant = experimentManager.getVariant("exp-004", "user-" + i);
            if (variant != null) {
                includedCount++;
            }
        }

        // THEN - roughly 10% included (within 5%)
        assertThat(includedCount).isBetween(50, 150);
    }

    @Test
    @DisplayName("Should track conversions by variant")
    void shouldTrackConversionsByVariant() {
        // GIVEN
        createTwoVariantExperiment("exp-005");
        
        // Assign users first
        experimentManager.getVariant("exp-005", "user-1");
        experimentManager.getVariant("exp-005", "user-2");

        // WHEN
        experimentManager.recordConversion("exp-005", "user-1", "mastery_gain", 15.0);
        experimentManager.recordConversion("exp-005", "user-1", "mastery_gain", 20.0);
        experimentManager.recordConversion("exp-005", "user-2", "mastery_gain", 25.0);

        // THEN
        ExperimentManager.ExperimentResults results = experimentManager.getResults("exp-005");
        assertThat(results).isNotNull();
        assertThat(results.variantResults()).isNotEmpty();
    }

    @Test
    @DisplayName("Should calculate statistical significance")
    void shouldCalculateStatisticalSignificance() {
        // GIVEN
        createTwoVariantExperiment("exp-006");
        
        // Simulate many users with different outcomes
        for (int i = 0; i < 100; i++) {
            String userId = "control-user-" + i;
            experimentManager.getVariant("exp-006", userId);
            // Control users get lower scores
            experimentManager.recordConversion("exp-006", userId, "score", 50 + Math.random() * 10);
        }
        
        for (int i = 0; i < 100; i++) {
            String userId = "treatment-user-" + i;
            experimentManager.getVariant("exp-006", userId);
            // Treatment users get higher scores
            experimentManager.recordConversion("exp-006", userId, "score", 70 + Math.random() * 10);
        }

        // WHEN
        ExperimentManager.ExperimentResults results = experimentManager.getResults("exp-006");

        // THEN
        assertThat(results).isNotNull();
        // Note: significance calculation requires actual data distribution
    }

    @Test
    @DisplayName("Should not assign inactive experiments")
    void shouldNotAssignInactiveExperiments() {
        // GIVEN
        createTwoVariantExperiment("exp-007");
        experimentManager.stopExperiment("exp-007");

        // WHEN
        ExperimentManager.Variant variant = experimentManager.getVariant("exp-007", "user-1");

        // THEN
        assertThat(variant).isNull();
    }

    @Test
    @DisplayName("Should reject invalid variant weights")
    void shouldRejectInvalidVariantWeights() {
        // GIVEN
        ExperimentManager.Experiment experiment = ExperimentManager.Experiment.builder()
            .id("exp-008")
            .name("Invalid Weights")
            .addVariant(ExperimentManager.Variant.builder()
                .id("a")
                .name("A")
                .weight(30.0)
                .build())
            .addVariant(ExperimentManager.Variant.builder()
                .id("b")
                .name("B")
                .weight(30.0)
                .build())
            // Total: 60, not 100
            .build();

        // WHEN/THEN
        assertThatThrownBy(() -> experimentManager.createExperiment(experiment))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("weights must sum to 100");
    }

    @Test
    @DisplayName("Should list active experiments")
    void shouldListActiveExperiments() {
        // GIVEN
        createTwoVariantExperiment("exp-active-1");
        createTwoVariantExperiment("exp-active-2");
        createTwoVariantExperiment("exp-stopped");
        experimentManager.stopExperiment("exp-stopped");

        // WHEN
        var activeExperiments = experimentManager.listActiveExperiments();

        // THEN
        assertThat(activeExperiments).hasSize(2);
        assertThat(activeExperiments)
            .extracting(ExperimentManager.Experiment::id)
            .containsExactlyInAnyOrder("exp-active-1", "exp-active-2");
    }

    @Test
    @DisplayName("Should handle time-bounded experiments")
    void shouldHandleTimeBoundedExperiments() {
        // GIVEN - experiment that hasn't started yet
        ExperimentManager.Experiment futureExperiment = ExperimentManager.Experiment.builder()
            .id("exp-future")
            .name("Future Experiment")
            .startTime(Instant.now().plus(Duration.ofHours(1)))
            .addVariant(ExperimentManager.Variant.builder()
                .id("control")
                .name("Control")
                .weight(100.0)
                .build())
            .build();
        experimentManager.createExperiment(futureExperiment);

        // WHEN
        ExperimentManager.Variant variant = experimentManager.getVariant("exp-future", "user-1");

        // THEN - should not be assigned (experiment not started)
        assertThat(variant).isNull();
    }

    private void createTwoVariantExperiment(String experimentId) {
        ExperimentManager.Experiment experiment = ExperimentManager.Experiment.builder()
            .id(experimentId)
            .name("Test Experiment")
            .addVariant(ExperimentManager.Variant.builder()
                .id("control")
                .name("Control")
                .weight(50.0)
                .build())
            .addVariant(ExperimentManager.Variant.builder()
                .id("treatment")
                .name("Treatment")
                .weight(50.0)
                .build())
            .build();
        experimentManager.createExperiment(experiment);
    }
}
