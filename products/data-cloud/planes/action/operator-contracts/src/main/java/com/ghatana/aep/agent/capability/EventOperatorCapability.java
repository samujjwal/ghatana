package com.ghatana.aep.agent.capability;

import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.operator.contract.EventOperator;
import com.ghatana.aep.operator.contract.EventOperatorResult;

/**
 * @doc.type interface
 * @doc.purpose Exposes AEP event processing as a capability of an agent
 * @doc.layer product
 * @doc.pattern Capability
 */
public interface EventOperatorCapability<I, O>
        extends AgentCapability<EventContext<I>, EventOperatorResult<O>>, EventOperator<I, O> {
}
