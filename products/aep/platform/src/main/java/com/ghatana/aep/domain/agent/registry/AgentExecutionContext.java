package com.ghatana.aep.domain.agent.registry;

/**
 * {@code AgentExecutionContext} defines the minimal execution context required
 * for agent operation, providing tenant scoping for multi-tenant isolation.
 *
 * <h2>Purpose</h2>
 * Provides execution context for agent operations with:
 * <ul>
 *   <li>Tenant identification for multi-tenant isolation</li>
 *   <li>Lightweight contract for agent invocation</li>
 *   <li>Extensible design for future context additions</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Passed to</b>: Agent operations during invocation</li>
 *   <li><b>Provided by</b>: Orchestrator, agent runtime</li>
 *   <li><b>Used by</b>: Agents for tenant-scoped operations</li>
 *   <li><b>Related to</b>: {@link AgentCapabilities}, {@link AgentInfo}</li>
 * </ul>
 *
 * <h2>Multi-Tenancy</h2>
 * {@code tenantId()} enables:
 * <ul>
 *   <li>Tenant isolation in event processing</li>
 *   <li>Access control enforcement</li>
 *   <li>Quota tracking per tenant</li>
 *   <li>Audit logging with tenant context</li>
 * </ul>
 *
 * <h2>Typical Implementation</h2>
 * {@code
 * public class DefaultAgentExecutionContext implements AgentExecutionContext {
 *     private final String tenantId;
 *
 *     public DefaultAgentExecutionContext(String tenantId) {
 *         this.tenantId = Objects.requireNonNull(tenantId);
 *     }
 *
 *     public String tenantId() {
 *         return tenantId;
 *     }
 * }
 * }
 *
 * <h2>Usage in Agent Operations</h2>
 * {@code
 * public class PlannerAgent implements Agent {
 *     public Promise&lt;List&lt;Task&gt;&gt; plan(
 *         List&lt;Event&gt; events,
 *         AgentExecutionContext context) {
 *         // Use context.tenantId() for tenant-scoped operations
 *         String tenantId = context.tenantId();
 *         return planTasks(events, tenantId);
 *     }
 * }
 * }
 *
 * <h2>Migration Note</h2>
 * This is a lightweight consolidation of execution context types previously
 * scattered across multi-agent-system modules. The minimal contract ensures
 * compatibility while allowing future expansion.
 *
 * @see AgentCapabilities
 * @see AgentInfo
 *
 * @doc.type interface
 * @doc.layer domain
 * @doc.purpose agent execution context contract
 * @doc.pattern contract, context-object, multi-tenancy
 * @doc.test-hints tenant-scoping, context-passing, tenant-isolation
 */
public interface AgentExecutionContext {
    String tenantId();
}
