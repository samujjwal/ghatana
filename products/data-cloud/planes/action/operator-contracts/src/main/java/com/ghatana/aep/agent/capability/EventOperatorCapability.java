package com.ghatana.aep.agent.capability;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.operator.contract.EventOperator;
import com.ghatana.aep.operator.contract.EventOperatorResult;
import com.ghatana.aep.operator.contract.OperatorLifecycleContract;

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
}
