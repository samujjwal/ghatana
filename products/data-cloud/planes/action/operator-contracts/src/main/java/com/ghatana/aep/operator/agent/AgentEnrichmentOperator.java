package com.ghatana.aep.operator.agent;

import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.core.operator.OperatorId;

/**
 * Agent operator that enriches event or match context with typed derived fields.
 *
 * @doc.type class
 * @doc.purpose Implements AGENT_ENRICH as a first-class EventOperator
 * @doc.layer product
 * @doc.pattern Operator
 */
public final class AgentEnrichmentOperator extends AbstractAgentInferenceOperator {

    public AgentEnrichmentOperator(
            OperatorId operatorId,
            String agentRef,
            String inputSchema,
            String outputSchema,
            AgentInvocationClient invocationClient) {
        super(operatorId, OperatorKind.AGENT_ENRICH, agentRef, inputSchema, outputSchema, invocationClient);
    }
}
