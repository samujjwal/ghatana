package com.ghatana.aep.agent.capability;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.operator.contract.EventOperator;
import com.ghatana.aep.operator.contract.EventOperatorResult;
import com.ghatana.aep.operator.contract.OperatorLifecycleContract;
import com.ghatana.aep.operator.contract.OperatorSpec;

import java.util.Set;

/**
 * @doc.type interface
 * @doc.purpose Exposes AEP event processing as a capability of an agent with full lifecycle contracts
 * @doc.layer product
 * @doc.pattern Capability
 *
 * <p>P4-01: Extends the canonical operator lifecycle contracts:
 * <ul>
 *   <li>validate operator spec - validate configuration before execution</li>
 *   <li>compile operator spec - transform spec to runtime plan</li>
 *   <li>explain operator plan - human-readable plan explanation</li>
 *   <li>declare side effects - explicit side effect declarations</li>
 *   <li>declare replay behavior - idempotency and recovery semantics</li>
 *   <li>declare required policies - governance and security requirements</li>
 *   <li>declare observability requirements - metrics, traces, and logs</li>
 * </ul>
 *
 * <p>WS2: Adds EventOperator-specific declarations for deterministic inputs, idempotency scope,
 * and approval requirements beyond the general operator lifecycle contracts.
 */
public interface EventOperatorCapability<I, O>
        extends AgentCapability<EventContext<I>, EventOperatorResult<O>>,
                EventOperator<I, O>,
                OperatorLifecycleContract {

    // Inherits from OperatorLifecycleContract:
    // - ValidationResult validate(OperatorSpec, ValidationContext)
    // - RuntimePlan compile(OperatorSpec, CompileContext)
    // - OperatorExplanation explain(RuntimePlan, ExplanationDetailLevel)
    // - SideEffectDeclaration declareSideEffects(OperatorSpec)
    // - ReplayBehavior declareReplayBehavior(OperatorSpec)
    // - RequiredPolicies declareRequiredPolicies(OperatorSpec)
    // - ObservabilityRequirements declareObservabilityRequirements(OperatorSpec)

    // Inherits from EventOperator:
    // - OperatorId id()
    // - OperatorKind kind()
    // - OperatorVersion version()
    // - Promise<EventOperatorResult<O>> process(EventContext<I>, OperatorRuntimeContext)

    // ==================== WS2: EventOperator-Specific Declarations ====================

    /**
     * Declare deterministic input requirements for this event operator.
     *
     * <p>WS2: Event operators must declare which input fields are required for deterministic
     * behavior. This enables:
     * <ul>
     *   <li>Input validation before execution</li>
     *   <li>Dependency tracking for event schemas</li>
     *   <li>Impact analysis for schema changes</li>
     * </ul>
     *
     * @param spec the operator specification
     * @return set of required input field names
     */
    Set<String> declareDeterministicInputs(OperatorSpec spec);

    /**
     * Declare the idempotency scope for this event operator.
     *
     * <p>WS2: Defines the scope at which idempotency is guaranteed:
     * <ul>
     *   <li>EVENT_ID: Idempotent per individual event</li>
     *   <li>CORRELATION_ID: Idempotent per correlation group</li>
     *   <li>TENANT: Idempotent per tenant</li>
     *   <li>GLOBAL: Idempotent across all contexts</li>
     * </ul>
     *
     * @param spec the operator specification
     * @return idempotency scope
     */
    IdempotencyScope declareIdempotencyScope(OperatorSpec spec);

    /**
     * Declare approval requirements for this event operator.
     *
     * <p>WS2: Specifies whether human approval is required before execution and at what level:
     * <ul>
     *   <li>NONE: No approval required</li>
     *   <li>REVIEW: Requires peer review before first deployment</li>
     *   <li>PER_EXECUTION: Requires approval for each execution</li>
     *   <li>PER_TENANT: Requires approval per tenant</li>
     * </ul>
     *
     * @param spec the operator specification
     * @return approval requirement
     */
    ApprovalRequirement declareApprovalRequirement(OperatorSpec spec);

    // ==================== WS2: Supporting Types ====================

    /**
     * Idempotency scope for event operators.
     */
    enum IdempotencyScope {
        EVENT_ID,          // Idempotent per individual event
        CORRELATION_ID,    // Idempotent per correlation group
        TENANT,            // Idempotent per tenant
        GLOBAL             // Idempotent across all contexts
    }

    /**
     * Approval requirement levels.
     */
    enum ApprovalRequirement {
        NONE,              // No approval required
        REVIEW,            // Requires peer review before first deployment
        PER_EXECUTION,     // Requires approval for each execution
        PER_TENANT         // Requires approval per tenant
    }
}
