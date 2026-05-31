package com.ghatana.aep.operator.contract;

import com.ghatana.core.operator.OperatorId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for canonical operator lifecycle contracts (P4-01).
 *
 * <p>Verifies that the lifecycle contract types:
 * <ul>
 *   <li>Have proper validation and immutability</li>
 *   <li>Support all required lifecycle operations</li>
 *   <li>Provide sensible defaults and factory methods</li>
 *   <li>Handle edge cases correctly</li>
 * </ul>
 */
class OperatorLifecycleContractTest {

    // ==================== ValidationResult Tests ====================

    @Test
    void validationResultWithNoErrorsIsValid() {
        OperatorLifecycleContract.ValidationResult result = 
            new OperatorLifecycleContract.ValidationResult(true, List.of(), List.of());
        
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void validationResultWithErrorsIsInvalid() {
        OperatorLifecycleContract.ValidationResult result = 
            new OperatorLifecycleContract.ValidationResult(false, List.of("error1", "error2"), List.of());
        
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("error1", "error2");
    }

    @Test
    void validationResultWithWarningsCanBeValid() {
        OperatorLifecycleContract.ValidationResult result = 
            new OperatorLifecycleContract.ValidationResult(true, List.of(), List.of("warning1"));
        
        assertThat(result.valid()).isTrue();
        assertThat(result.warnings()).containsExactly("warning1");
    }

    @Test
    void validationResultCopiesListsForImmutability() {
        List<String> originalErrors = List.of("error1");
        OperatorLifecycleContract.ValidationResult result = 
            new OperatorLifecycleContract.ValidationResult(false, originalErrors, List.of());
        
        // Modifying original list should not affect result
        assertThat(result.errors()).isNotSameAs(originalErrors);
    }

    // ==================== OperatorExplanation Tests ====================

    @Test
    void operatorExplanationWithAllFields() {
        OperatorLifecycleContract.OperatorExplanation explanation = 
            new OperatorLifecycleContract.OperatorExplanation(
                "Summary",
                List.of("step1", "step2"),
                Map.of("input", "output"),
                List.of("warning1"),
                Optional.of("5ms")
            );
        
        assertThat(explanation.summary()).isEqualTo("Summary");
        assertThat(explanation.steps()).containsExactly("step1", "step2");
        assertThat(explanation.dataFlow()).containsEntry("input", "output");
        assertThat(explanation.warnings()).containsExactly("warning1");
        assertThat(explanation.executionEstimate()).hasValue("5ms");
    }

    @Test
    void operatorExplanationEmptyFactory() {
        OperatorLifecycleContract.OperatorExplanation explanation = 
            OperatorLifecycleContract.OperatorExplanation.empty();
        
        assertThat(explanation.summary()).isEqualTo("No explanation available");
        assertThat(explanation.steps()).isEmpty();
        assertThat(explanation.dataFlow()).isEmpty();
        assertThat(explanation.warnings()).isEmpty();
        assertThat(explanation.executionEstimate()).isEmpty();
    }

    @Test
    void operatorExplanationCopiesCollectionsForImmutability() {
        List<String> originalSteps = List.of("step1");
        OperatorLifecycleContract.OperatorExplanation explanation = 
            new OperatorLifecycleContract.OperatorExplanation(
                "Summary",
                originalSteps,
                Map.of(),
                List.of(),
                Optional.empty()
            );
        
        assertThat(explanation.steps()).isNotSameAs(originalSteps);
    }

    // ==================== SideEffectDeclaration Tests ====================

    @Test
    void sideEffectDeclarationWithNoEffects() {
        OperatorLifecycleContract.SideEffectDeclaration declaration = 
            new OperatorLifecycleContract.SideEffectDeclaration(
                Set.of(),
                List.of(),
                false,
                true,
                Optional.empty()
            );
        
        assertThat(declaration.hasSideEffects()).isFalse();
        assertThat(declaration.isSafeToReplay()).isTrue();
    }

    @Test
    void sideEffectDeclarationWithDestructiveEffects() {
        OperatorLifecycleContract.SideEffectDeclaration declaration = 
            new OperatorLifecycleContract.SideEffectDeclaration(
                Set.of(OperatorLifecycleContract.SideEffectType.DATA_DELETION),
                List.of("resource1"),
                true,
                false,
                Optional.of("restore-backup")
            );
        
        assertThat(declaration.hasSideEffects()).isTrue();
        assertThat(declaration.isDestructive()).isTrue();
        assertThat(declaration.isReversible()).isFalse();
        assertThat(declaration.isSafeToReplay()).isFalse();
        assertThat(declaration.rollbackProcedure()).hasValue("restore-backup");
    }

    @Test
    void sideEffectDeclarationNoneFactory() {
        OperatorLifecycleContract.SideEffectDeclaration declaration = 
            OperatorLifecycleContract.SideEffectDeclaration.none();
        
        assertThat(declaration.hasSideEffects()).isFalse();
        assertThat(declaration.isSafeToReplay()).isTrue();
        assertThat(declaration.effectTypes()).isEmpty();
    }

    @Test
    void sideEffectDeclarationCopiesCollectionsForImmutability() {
        Set<OperatorLifecycleContract.SideEffectType> originalEffects = 
            Set.of(OperatorLifecycleContract.SideEffectType.DATA_MUTATION);
        OperatorLifecycleContract.SideEffectDeclaration declaration = 
            new OperatorLifecycleContract.SideEffectDeclaration(
                originalEffects,
                List.of(),
                false,
                true,
                Optional.empty()
            );
        
        assertThat(declaration.effectTypes()).isNotSameAs(originalEffects);
    }

    // ==================== ReplayBehavior Tests ====================

    @Test
    void replayBehaviorIdempotent() {
        OperatorLifecycleContract.ReplayBehavior behavior = 
            OperatorLifecycleContract.ReplayBehavior.idempotent();
        
        assertThat(behavior.isIdempotent()).isTrue();
        assertThat(behavior.supportsExactlyOnce()).isTrue();
        assertThat(behavior.supportsAtLeastOnce()).isTrue();
        assertThat(behavior.requiresDeduplication()).isFalse();
        assertThat(behavior.stateRecoveryMode()).isEqualTo(
            OperatorLifecycleContract.StateRecoveryMode.STATELESS);
        assertThat(behavior.deduplicationStrategy()).isEqualTo(
            OperatorLifecycleContract.DeduplicationStrategy.NONE);
    }

    @Test
    void replayBehaviorNonIdempotent() {
        OperatorLifecycleContract.ReplayBehavior behavior = 
            OperatorLifecycleContract.ReplayBehavior.nonIdempotent(
                OperatorLifecycleContract.StateRecoveryMode.SNAPSHOT);
        
        assertThat(behavior.isIdempotent()).isFalse();
        assertThat(behavior.supportsExactlyOnce()).isFalse();
        assertThat(behavior.supportsAtLeastOnce()).isTrue();
        assertThat(behavior.requiresDeduplication()).isTrue();
        assertThat(behavior.stateRecoveryMode()).isEqualTo(
            OperatorLifecycleContract.StateRecoveryMode.SNAPSHOT);
        assertThat(behavior.deduplicationStrategy()).isEqualTo(
            OperatorLifecycleContract.DeduplicationStrategy.EVENT_ID);
    }

    @Test
    void replayBehaviorWithCustomIdempotencyKey() {
        OperatorLifecycleContract.ReplayBehavior behavior = 
            new OperatorLifecycleContract.ReplayBehavior(
                false,
                true,
                true,
                OperatorLifecycleContract.StateRecoveryMode.EVENT_SOURCING,
                OperatorLifecycleContract.DeduplicationStrategy.CONTENT_HASH,
                Optional.of("custom-key")
            );
        
        assertThat(behavior.replayIdempotencyKey()).hasValue("custom-key");
    }

    // ==================== RequiredPolicies Tests ====================

    @Test
    void requiredPoliciesNoneFactory() {
        OperatorLifecycleContract.RequiredPolicies policies = 
            OperatorLifecycleContract.RequiredPolicies.none();
        
        assertThat(policies.requiresAnyPolicy()).isFalse();
        assertThat(policies.dataGovernance()).isEmpty();
        assertThat(policies.security()).isEmpty();
        assertThat(policies.compliance()).isEmpty();
        assertThat(policies.resource()).isEmpty();
    }

    @Test
    void requiredPoliciesWithRequirements() {
        OperatorLifecycleContract.PolicyRequirement dataPolicy = 
            new OperatorLifecycleContract.PolicyRequirement(
                "retention",
                "policy-1",
                Map.of("days", 30),
                OperatorLifecycleContract.EnforcementLevel.REQUIRED
            );
        
        OperatorLifecycleContract.RequiredPolicies policies = 
            new OperatorLifecycleContract.RequiredPolicies(
                List.of(dataPolicy),
                List.of(),
                List.of(),
                List.of()
            );
        
        assertThat(policies.requiresAnyPolicy()).isTrue();
        assertThat(policies.dataGovernance()).containsExactly(dataPolicy);
    }

    @Test
    void requiredPoliciesCopiesListsForImmutability() {
        List<OperatorLifecycleContract.PolicyRequirement> originalPolicies = List.of(
            new OperatorLifecycleContract.PolicyRequirement(
                "type", "id", Map.of(), OperatorLifecycleContract.EnforcementLevel.REQUIRED)
        );
        OperatorLifecycleContract.RequiredPolicies policies = 
            new OperatorLifecycleContract.RequiredPolicies(
                originalPolicies,
                List.of(),
                List.of(),
                List.of()
            );
        
        assertThat(policies.dataGovernance()).isNotSameAs(originalPolicies);
    }

    // ==================== ObservabilityRequirements Tests ====================

    @Test
    void observabilityRequirementsMinimalFactory() {
        OperatorLifecycleContract.ObservabilityRequirements requirements = 
            OperatorLifecycleContract.ObservabilityRequirements.minimal();
        
        assertThat(requirements.metrics()).hasSize(1);
        assertThat(requirements.metrics().get(0).metricName()).isEqualTo("execution.count");
        assertThat(requirements.traces()).isEmpty();
        assertThat(requirements.logs()).isEmpty();
        assertThat(requirements.requiresDistributedTracing()).isFalse();
        assertThat(requirements.requiresCustomDashboards()).isFalse();
    }

    @Test
    void observabilityRequirementsWithFullSpec() {
        OperatorLifecycleContract.ObservabilityRequirements requirements = 
            new OperatorLifecycleContract.ObservabilityRequirements(
                List.of(
                    new OperatorLifecycleContract.MetricRequirement(
                        "latency",
                        OperatorLifecycleContract.MetricType.HISTOGRAM,
                        OperatorLifecycleContract.AggregationMode.PERCENTILE
                    )
                ),
                List.of(
                    new OperatorLifecycleContract.TraceRequirement(
                        "process-span",
                        List.of("tenant", "operation"),
                        true
                    )
                ),
                List.of(
                    new OperatorLifecycleContract.LogRequirement(
                        "operator-log",
                        OperatorLifecycleContract.LogLevel.INFO,
                        List.of("eventId", "result")
                    )
                ),
                true,
                true
            );
        
        assertThat(requirements.metrics()).hasSize(1);
        assertThat(requirements.traces()).hasSize(1);
        assertThat(requirements.logs()).hasSize(1);
        assertThat(requirements.requiresDistributedTracing()).isTrue();
        assertThat(requirements.requiresCustomDashboards()).isTrue();
    }

    @Test
    void observabilityRequirementsCopiesListsForImmutability() {
        List<OperatorLifecycleContract.MetricRequirement> originalMetrics = List.of(
            new OperatorLifecycleContract.MetricRequirement(
                "count",
                OperatorLifecycleContract.MetricType.COUNTER,
                OperatorLifecycleContract.AggregationMode.SUM
            )
        );
        OperatorLifecycleContract.ObservabilityRequirements requirements = 
            new OperatorLifecycleContract.ObservabilityRequirements(
                originalMetrics,
                List.of(),
                List.of(),
                false,
                false
            );
        
        assertThat(requirements.metrics()).isNotSameAs(originalMetrics);
    }

    // ==================== PolicyRequirement Tests ====================

    @Test
    void policyRequirementCopiesParametersForImmutability() {
        Map<String, Object> originalParams = Map.of("key", "value");
        OperatorLifecycleContract.PolicyRequirement requirement = 
            new OperatorLifecycleContract.PolicyRequirement(
                "type",
                "id",
                originalParams,
                OperatorLifecycleContract.EnforcementLevel.REQUIRED
            );
        
        assertThat(requirement.parameters()).isNotSameAs(originalParams);
    }

    // ==================== TraceRequirement Tests ====================

    @Test
    void traceRequirementCopiesAttributesForImmutability() {
        List<String> originalAttributes = List.of("attr1", "attr2");
        OperatorLifecycleContract.TraceRequirement requirement = 
            new OperatorLifecycleContract.TraceRequirement(
                "span",
                originalAttributes,
                false
            );
        
        assertThat(requirement.attributes()).isNotSameAs(originalAttributes);
    }

    // ==================== LogRequirement Tests ====================

    @Test
    void logRequirementCopiesFieldsForImmutability() {
        List<String> originalFields = List.of("field1", "field2");
        OperatorLifecycleContract.LogRequirement requirement = 
            new OperatorLifecycleContract.LogRequirement(
                "log",
                OperatorLifecycleContract.LogLevel.DEBUG,
                originalFields
            );
        
        assertThat(requirement.fields()).isNotSameAs(originalFields);
    }
}
