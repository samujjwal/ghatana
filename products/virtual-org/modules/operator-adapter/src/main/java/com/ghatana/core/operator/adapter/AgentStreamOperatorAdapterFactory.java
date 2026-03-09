package com.ghatana.core.operator.adapter;

import com.ghatana.platform.types.identity.OperatorId;
import com.ghatana.virtualorg.agent.VirtualOrgAgent;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Factory for creating AgentStreamOperatorAdapter instances.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates OperatorId generation logic and provides
 * convenient creation methods for adapters.
 *
 * <p><b>OperatorId Format</b><br>
 * Auto-generated IDs follow pattern: {@code virtualorg:agent:{role}:1.0.0}
 * <ul>
 *   <li><b>namespace</b>: "virtualorg" (identifies virtual-org agents)</li>
 *   <li><b>type</b>: "agent" (operator type)</li>
 *   <li><b>name</b>: agent role in lowercase (e.g., "ceo", "cto", "engineer")</li>
 *   <li><b>version</b>: "1.0.0" (adapter version)</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Create with auto-generated ID
 * VirtualOrgAgent agent = new VirtualOrgAgent(...);
 * AgentStreamOperatorAdapter adapter =
 *     AgentStreamOperatorAdapterFactory.create(agent, meterRegistry);
 * // ID: "virtualorg:agent:ceo:1.0.0"
 *
 * // Create with custom ID
 * OperatorId customId = OperatorId.of("my-org:llm-agent:strategic:2.0.0");
 * AgentStreamOperatorAdapter adapter2 =
 *     AgentStreamOperatorAdapterFactory.create(customId, agent, meterRegistry);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * All methods are stateless and thread-safe.
 *
 * @see AgentStreamOperatorAdapter
 * @see VirtualOrgAgent
 * @see OperatorId
 */
public class AgentStreamOperatorAdapterFactory {

    private static final String NAMESPACE = "virtualorg";
    private static final String TYPE = "agent";
    private static final String VERSION = "1.0.0";

    private AgentStreamOperatorAdapterFactory() {
        throw new UnsupportedOperationException("Factory class cannot be instantiated");
    }

    /**
     * Creates adapter with auto-generated OperatorId.
     *
     * <p>Generated ID format: {@code virtualorg:agent:{role}:1.0.0}
     * where {@code {role}} is the agent's role in lowercase.
     *
     * @param agent         Virtual-org agent instance
     * @param meterRegistry Metrics registry for observability
     * @return AgentStreamOperatorAdapter instance
     * @throws NullPointerException if agent or meterRegistry is null
     */
    public static AgentStreamOperatorAdapter create(
            VirtualOrgAgent agent,
            MeterRegistry meterRegistry
    ) {
        OperatorId id = OperatorId.of(
            NAMESPACE,
            TYPE,
            agent.getRole().name().toLowerCase(),
            VERSION
        );

        return new AgentStreamOperatorAdapter(id, agent, meterRegistry);
    }

    /**
     * Creates adapter with custom OperatorId.
     *
     * @param id            Custom operator identifier
     * @param agent         Virtual-org agent instance
     * @param meterRegistry Metrics registry for observability
     * @return AgentStreamOperatorAdapter instance
     * @throws NullPointerException if id, agent, or meterRegistry is null
     */
    public static AgentStreamOperatorAdapter create(
            OperatorId id,
            VirtualOrgAgent agent,
            MeterRegistry meterRegistry
    ) {
        return new AgentStreamOperatorAdapter(id, agent, meterRegistry);
    }
}
