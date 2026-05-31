package com.ghatana.aep.operator.contract;

import com.ghatana.aep.model.EventContext;
import com.ghatana.core.operator.OperatorId;
import io.activej.promise.Promise;

/**
 * @doc.type interface
 * @doc.purpose Defines the formal AEP event operator validation, compilation, and execution contract
 * @doc.layer product
 * @doc.pattern OperatorContract
 */
public interface EventOperator<I, O>
        extends OperatorLifecycleContract {

    OperatorId id();

    OperatorKind kind();

    OperatorVersion version();

    @Override
    ValidationResult validate(OperatorSpec spec, ValidationContext ctx);

    @Override
    RuntimePlan compile(OperatorSpec spec, CompileContext ctx);

    Promise<EventOperatorResult<O>> process(EventContext<I> input, OperatorRuntimeContext ctx);

    // OperatorLifecycleContract methods are inherited:
    // - explain(RuntimePlan, ExplanationDetailLevel)
    // - declareSideEffects(OperatorSpec)
    // - declareReplayBehavior(OperatorSpec)
    // - declareRequiredPolicies(OperatorSpec)
    // - declareObservabilityRequirements(OperatorSpec)
}
