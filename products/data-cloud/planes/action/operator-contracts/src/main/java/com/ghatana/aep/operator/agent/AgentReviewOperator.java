package com.ghatana.aep.operator.agent;

import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.aep.operator.contract.OperatorSpec;
import com.ghatana.core.operator.OperatorId;

import java.util.List;

/**
 * Agent operator that reviews candidate patterns or risky matches.
 *
 * @doc.type class
 * @doc.purpose Implements AGENT_REVIEW without allowing high-risk self-approval
 * @doc.layer product
 * @doc.pattern Operator
 */
public final class AgentReviewOperator extends AbstractAgentInferenceOperator {

    public AgentReviewOperator(
            OperatorId operatorId,
            String agentRef,
            String inputSchema,
            String outputSchema,
            AgentInvocationClient invocationClient) {
        super(operatorId, OperatorKind.AGENT_REVIEW, agentRef, inputSchema, outputSchema, invocationClient);
    }

    @Override
    protected void validatePolicies(OperatorSpec spec, List<String> errors) {
        if (isTrue(spec.policies(), "canSelfApproveHighRisk")) {
            errors.add("AGENT_REVIEW must not self-approve high-risk production changes");
        }
        if ("HIGH".equals(spec.policies().get("riskLevel"))
                && "production".equals(spec.policies().get("mode"))
                && !isTrue(spec.policies(), "selfApprovalAllowed")) {
            errors.add("AGENT_REVIEW cannot self-approve high-risk production changes");
        }
    }
}
