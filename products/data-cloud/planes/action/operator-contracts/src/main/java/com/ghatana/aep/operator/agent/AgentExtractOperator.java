package com.ghatana.aep.operator.agent;

import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.core.operator.OperatorId;

/**
 * Agent operator that extracts typed facts or entities from event context.
 *
 * @doc.type class
 * @doc.purpose Implements AGENT_EXTRACT as an event-operator capability role
 * @doc.layer product
 * @doc.pattern Operator
 */
public final class AgentExtractOperator extends AbstractAgentInferenceOperator {

    public AgentExtractOperator(
            OperatorId operatorId,
            String agentRef,
            String inputSchema,
            String outputSchema,
            AgentInvocationClient invocationClient) {
        super(operatorId, OperatorKind.AGENT_EXTRACT, agentRef, inputSchema, outputSchema, invocationClient);
    }
}
