/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.operator.contract;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical operator lifecycle contracts (P4-01).
 *
 * <p>Defines the complete lifecycle interface for AEP event operators including:
 * <ul>
 *   <li>Operator spec validation</li>
 *   <li>Operator compilation to runtime plan</li>
 *   <li>Plan explanation for debugging/auditing</li>
 *   <li>Side effect declarations</li>
 *   <li>Replay behavior specification</li>
 *   <li>Required policy declarations</li>
 *   <li>Observability requirements</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Defines canonical operator lifecycle contracts for validation, compilation, and execution
 * @doc.layer product
 * @doc.pattern Contract
 */
public interface OperatorLifecycleContract {

    /**
     * Validate an operator specification.
     *
     * <p>Checks that the operator spec is well-formed, parameters are valid,
     * and the operator can be safely compiled and executed.
     *
     * @param spec the operator specification to validate
     * @param ctx validation context including tenant and validation options
     * @return validation result with errors and warnings
     */
    ValidationResult validate(OperatorSpec spec, ValidationContext ctx);

    /**
     * Compile an operator specification into a runtime plan.
     *
     * <p>Transforms the declarative operator spec into an executable runtime plan
     * with resolved dependencies, optimized execution order, and resource allocation.
     *
     * @param spec the operator specification to compile
     * @param ctx compilation context including tenant and runtime options
     * @return the compiled runtime plan
     */
    RuntimePlan compile(OperatorSpec spec, CompileContext ctx);

    /**
     * Explain the runtime plan for debugging and auditing.
     *
     * <p>P4-01: Provides a human-readable explanation of what the operator will do,
     * including data flow, transformations, and expected outcomes.
     *
     * @param plan the runtime plan to explain
     * @param detailLevel the level of detail requested (summary, detailed, or verbose)
     * @return explanation of the operator plan
     */
    OperatorExplanation explain(RuntimePlan plan, ExplanationDetailLevel detailLevel);

    /**
     * Declare side effects produced by this operator.
     *
     * <p>P4-01: Operators must explicitly declare all side effects they produce
     * including data mutations, external calls, and state changes. This enables:
     * <ul>
     *   <li>Impact analysis before execution</li>
     *   <li>Safety checks for destructive operations</li>
     *   <li>Rollback planning</li>
     *   <li>Audit trail generation</li>
     * </ul>
     *
     * @param spec the operator specification
     * @return declaration of all side effects
     */
    SideEffectDeclaration declareSideEffects(OperatorSpec spec);

    /**
     * Declare replay behavior for this operator.
     *
     * <p>P4-01: Defines how the operator behaves during event replay including:
     * <ul>
     *   <li>Idempotency: Can the operator be safely re-executed?</li>
     *   <li>State recovery: How is operator state reconstructed?</li>
     *   <li>Deduplication: How are duplicate events handled?</li>
     *   <li>At-least-once vs exactly-once semantics</li>
     * </ul>
     *
     * @param spec the operator specification
     * @return replay behavior specification
     */
    ReplayBehavior declareReplayBehavior(OperatorSpec spec);

    /**
     * Declare required policies for this operator.
     *
     * <p>P4-01: Operators declare policy requirements including:
     * <ul>
     *   <li>Data governance policies (retention, classification)</li>
     *   <li>Security policies (encryption, access control)</li>
     *   <li>Compliance policies (audit, consent)</li>
     *   <li>Resource policies (quotas, throttling)</li>
     * </ul>
     *
     * @param spec the operator specification
     * @return required policies for execution
     */
    RequiredPolicies declareRequiredPolicies(OperatorSpec spec);

    /**
     * Declare observability requirements for this operator.
     *
     * <p>P4-01: Defines what telemetry, metrics, and traces the operator produces
     * and how they should be collected and aggregated.
     *
     * @param spec the operator specification
     * @return observability requirements
     */
    ObservabilityRequirements declareObservabilityRequirements(OperatorSpec spec);

    // ==================== Supporting Types ====================

    /**
     * Level of detail for plan explanations.
     */
    enum ExplanationDetailLevel {
        SUMMARY,      // High-level overview
        DETAILED,     // Include data flows and transformations
        VERBOSE       // Include full execution plan and resource allocation
    }

    /**
     * Human-readable explanation of an operator plan.
     */
    record OperatorExplanation(
        String summary,
        List<String> steps,
        Map<String, Object> dataFlow,
        List<String> warnings,
        Optional<String> executionEstimate
    ) {
        public OperatorExplanation {
            steps = List.copyOf(steps != null ? steps : List.of());
            dataFlow = Map.copyOf(dataFlow != null ? dataFlow : Map.of());
            warnings = List.copyOf(warnings != null ? warnings : List.of());
            executionEstimate = executionEstimate != null ? executionEstimate : Optional.empty();
        }

        public static OperatorExplanation empty() {
            return new OperatorExplanation("No explanation available", List.of(), Map.of(), List.of(), Optional.empty());
        }
    }

    /**
     * Declaration of operator side effects.
     */
    record SideEffectDeclaration(
        Set<SideEffectType> effectTypes,
        List<String> affectedResources,
        boolean isDestructive,
        boolean isReversible,
        Optional<String> rollbackProcedure
    ) {
        public SideEffectDeclaration {
            effectTypes = Set.copyOf(effectTypes != null ? effectTypes : Set.of());
            affectedResources = List.copyOf(affectedResources != null ? affectedResources : List.of());
            rollbackProcedure = rollbackProcedure != null ? rollbackProcedure : Optional.empty();
        }

        public boolean hasSideEffects() {
            return !effectTypes.isEmpty();
        }

        public boolean isSafeToReplay() {
            return !isDestructive || isReversible;
        }

        public static SideEffectDeclaration none() {
            return new SideEffectDeclaration(Set.of(), List.of(), false, true, Optional.empty());
        }
    }

    /**
     * Types of side effects operators can produce.
     */
    enum SideEffectType {
        DATA_MUTATION,      // Modifies stored data
        EXTERNAL_CALL,      // Calls external services
        STATE_CHANGE,       // Changes system state
        EVENT_EMISSION,     // Emits new events
        RESOURCE_ALLOCATION, // Allocates resources
        DATA_DELETION       // Deletes data (destructive)
    }

    /**
     * Specification of replay behavior.
     */
    record ReplayBehavior(
        boolean isIdempotent,
        boolean supportsExactlyOnce,
        boolean supportsAtLeastOnce,
        StateRecoveryMode stateRecoveryMode,
        DeduplicationStrategy deduplicationStrategy,
        Optional<String> replayIdempotencyKey
    ) {
        public ReplayBehavior {
            replayIdempotencyKey = replayIdempotencyKey != null ? replayIdempotencyKey : Optional.empty();
        }

        public boolean requiresDeduplication() {
            return !isIdempotent && supportsAtLeastOnce;
        }

        public static ReplayBehavior idempotent() {
            return new ReplayBehavior(true, true, true, StateRecoveryMode.STATELESS, DeduplicationStrategy.NONE, Optional.empty());
        }

        public static ReplayBehavior nonIdempotent(StateRecoveryMode recoveryMode) {
            return new ReplayBehavior(false, false, true, recoveryMode, DeduplicationStrategy.EVENT_ID, Optional.empty());
        }
    }

    /**
     * State recovery modes for replay.
     */
    enum StateRecoveryMode {
        STATELESS,          // No state to recover
        SNAPSHOT,           // Recover from checkpoint snapshot
        EVENT_SOURCING,     // Rebuild state from event log
        EXTERNAL_STORE      // Load state from external store
    }

    /**
     * Deduplication strategies for non-idempotent operators.
     */
    enum DeduplicationStrategy {
        NONE,               // No deduplication needed (idempotent)
        EVENT_ID,           // Deduplicate by event ID
        CONTENT_HASH,       // Deduplicate by content hash
        CUSTOM              // Custom deduplication logic
    }

    /**
     * Required policies for operator execution.
     */
    record RequiredPolicies(
        List<PolicyRequirement> dataGovernance,
        List<PolicyRequirement> security,
        List<PolicyRequirement> compliance,
        List<PolicyRequirement> resource
    ) {
        public RequiredPolicies {
            dataGovernance = List.copyOf(dataGovernance != null ? dataGovernance : List.of());
            security = List.copyOf(security != null ? security : List.of());
            compliance = List.copyOf(compliance != null ? compliance : List.of());
            resource = List.copyOf(resource != null ? resource : List.of());
        }

        public boolean requiresAnyPolicy() {
            return !dataGovernance.isEmpty() || !security.isEmpty() ||
                   !compliance.isEmpty() || !resource.isEmpty();
        }

        public static RequiredPolicies none() {
            return new RequiredPolicies(List.of(), List.of(), List.of(), List.of());
        }
    }

    /**
     * Individual policy requirement.
     */
    record PolicyRequirement(
        String policyType,
        String policyId,
        Map<String, Object> parameters,
        EnforcementLevel enforcementLevel
    ) {
        public PolicyRequirement {
            parameters = Map.copyOf(parameters != null ? parameters : Map.of());
        }
    }

    /**
     * Policy enforcement levels.
     */
    enum EnforcementLevel {
        REQUIRED,    // Must be satisfied or execution fails
        PREFERRED,   // Should be satisfied, warns if not
        OPTIONAL     // Nice to have, no enforcement
    }

    /**
     * Observability requirements for operators.
     */
    record ObservabilityRequirements(
        List<MetricRequirement> metrics,
        List<TraceRequirement> traces,
        List<LogRequirement> logs,
        boolean requiresDistributedTracing,
        boolean requiresCustomDashboards
    ) {
        public ObservabilityRequirements {
            metrics = List.copyOf(metrics != null ? metrics : List.of());
            traces = List.copyOf(traces != null ? traces : List.of());
            logs = List.copyOf(logs != null ? logs : List.of());
        }

        public static ObservabilityRequirements minimal() {
            return new ObservabilityRequirements(
                List.of(new MetricRequirement("execution.count", MetricType.COUNTER, AggregationMode.SUM)),
                List.of(),
                List.of(),
                false,
                false
            );
        }
    }

    /**
     * Metric requirement specification.
     */
    record MetricRequirement(
        String metricName,
        MetricType type,
        AggregationMode aggregation
    ) {}

    enum MetricType {
        COUNTER, GAUGE, HISTOGRAM, TIMER
    }

    enum AggregationMode {
        SUM, AVG, MIN, MAX, COUNT, PERCENTILE
    }

    /**
     * Trace requirement specification.
     */
    record TraceRequirement(
        String spanName,
        List<String> attributes,
        boolean isErrorTracking
    ) {
        public TraceRequirement {
            attributes = List.copyOf(attributes != null ? attributes : List.of());
        }
    }

    /**
     * Log requirement specification.
     */
    record LogRequirement(
        String logName,
        LogLevel level,
        List<String> fields
    ) {
        public LogRequirement {
            fields = List.copyOf(fields != null ? fields : List.of());
        }
    }

    enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
}
