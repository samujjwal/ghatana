/**
 * Migration adapters for bridging legacy agent abstractions to the unified
 * {@link com.ghatana.agent.TypedAgent TypedAgent} framework.
 *
 * <p>The Ghatana platform has three legacy agent abstractions that pre-date
 * the unified {@code TypedAgent<I,O>} / {@link com.ghatana.agent.registry.AgentFrameworkRegistry}:
 *
 * <ol>
 *   <li>{@link com.ghatana.agent.Agent} — original untyped interface used by
 *       virtual-org, yappc workflow, and the workflow package</li>
 *   <li>{@link com.ghatana.agent.framework.runtime.BaseAgent} — GAA lifecycle
 *       base class (PERCEIVE→REASON→ACT→CAPTURE→REFLECT) used by tutorputor and yappc</li>
 *   <li>{@link com.ghatana.agent.framework.coordination.OrchestrationStrategy.Agent} —
 *       coordination-specific agent interface for Sequential/Parallel/Hierarchical orchestration</li>
 * </ol>
 *
 * <p>This package provides:
 * <ul>
 *   <li>{@link com.ghatana.agent.migration.LegacyAgentAdapter} — wraps
 *       {@code Agent} as {@code TypedAgent<Object, Object>}</li>
 *   <li>{@link com.ghatana.agent.migration.BaseAgentAdapter} — wraps
 *       {@code BaseAgent<I,O>} as {@code TypedAgent<I,O>}</li>
 *   <li>{@link com.ghatana.agent.migration.OrchestrationBridge} — bidirectional bridge
 *       between {@code TypedAgent} and {@code OrchestrationStrategy.Agent}</li>
 * </ul>
 *
 * <h2>Migration Path</h2>
 * <pre>
 *   Phase 1: Wrap legacy agents with adapters (register in AgentFrameworkRegistry)
 *   Phase 2: Migrate product code to implement TypedAgent directly
 *   Phase 3: Remove adapters and legacy interfaces
 * </pre>
 *
 * @since 2.0.0
 */
package com.ghatana.agent.migration;
