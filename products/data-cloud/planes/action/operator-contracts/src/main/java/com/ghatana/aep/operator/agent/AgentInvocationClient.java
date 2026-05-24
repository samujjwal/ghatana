package com.ghatana.aep.operator.agent;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * Runtime boundary used by AgentOperators to invoke a governed agent.
 *
 * @doc.type interface
 * @doc.purpose Defines the minimal runtime bridge from AgentOperator nodes to agent execution
 * @doc.layer product
 * @doc.pattern SPI
 */
@FunctionalInterface
public interface AgentInvocationClient {

    Promise<Map<String, Object>> invoke(AgentInvocationRequest request);
}
