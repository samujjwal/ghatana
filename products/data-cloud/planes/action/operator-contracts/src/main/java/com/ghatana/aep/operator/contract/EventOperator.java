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
public interface EventOperator<I, O> {

    OperatorId id();

    OperatorKind kind();

    OperatorVersion version();

    ValidationResult validate(OperatorSpec spec, ValidationContext ctx);

    RuntimePlan compile(OperatorSpec spec, CompileContext ctx);

    Promise<EventOperatorResult<O>> process(EventContext<I> input, OperatorRuntimeContext ctx);
}
