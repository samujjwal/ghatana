package com.ghatana.core.operator.agent;

import com.ghatana.aep.operator.contract.EventOperator;
import com.ghatana.core.operator.UnifiedOperator;

import java.util.Map;

/**
 * First-class agent operator contract for AEP.
 *
 * <p>An agent operator is a governed specialization of {@link UnifiedOperator}. It can
 * participate anywhere a normal event operator participates in PatternSpec or PipelineSpec,
 * while declaring the policies required for model use, tool use, memory, retrieval, replay,
 * guardrails, observability, uncertainty, and human review.
 *
 * @doc.type interface
 * @doc.purpose Defines the AEP AgentOperator contract as a specialization of UnifiedOperator
 * @doc.layer product
 * @doc.pattern OperatorContract
 */
public interface AgentOperator extends UnifiedOperator, EventOperator<Map<String, Object>, Map<String, Object>> {

    /**
     * Gets the canonical agent reference used to materialize this operator.
     *
     * @return agent reference, including version, such as {@code agents/sre-risk-assessor@1.0.0}
     */
    String agentRef();

    /**
     * Gets the specific agent operator kind.
     *
     * @return agent operator kind
     */
    AgentOperatorKind agentOperatorKind();

    /**
     * Gets the side-effect profile for governance and replay policy.
     *
     * @return side-effect profile
     */
    AgentSideEffectProfile sideEffectProfile();

    /**
     * Gets the declared input schema reference.
     *
     * @return input schema reference
     */
    String inputSchema();

    /**
     * Gets the declared output schema reference.
     *
     * @return output schema reference
     */
    String outputSchema();

    Map<String, Object> modelPolicy();

    Map<String, Object> toolPolicy();

    Map<String, Object> memoryPolicy();

    Map<String, Object> retrievalPolicy();

    Map<String, Object> guardrailPolicy();

    Map<String, Object> replayPolicy();

    Map<String, Object> uncertaintyPolicy();

    Map<String, Object> humanReviewPolicy();

    Map<String, Object> observabilityPolicy();
}
