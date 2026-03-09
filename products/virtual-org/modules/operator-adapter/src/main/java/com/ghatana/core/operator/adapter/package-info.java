/**
 * Core Operator Adapter - Bridges external components to unified operator framework.
 *
 * <p><b>Purpose</b><br>
 * This package provides adapter implementations that wrap non-operator components
 * (agents, services, etc.) in the {@link com.ghatana.platform.workflow.operator.UnifiedOperator}
 * interface, enabling them to participate in stream processing pipelines.
 *
 * <p><b>Key Components</b><br>
 * <ul>
 *   <li>{@link com.ghatana.core.operator.adapter.AgentStreamOperatorAdapter} - 
 *       Wraps {@link com.ghatana.virtualorg.agent.VirtualOrgAgent} as a stream operator</li>
 *   <li>{@link com.ghatana.core.operator.adapter.AgentStreamOperatorAdapterFactory} - 
 *       Factory for creating agent adapters with auto-generated IDs</li>
 * </ul>
 *
 * <p><b>Architecture Pattern</b><br>
 * Implements the Adapter (Bridge) pattern to integrate domain-specific components
 * with the unified operator framework. Adapters extend {@link com.ghatana.platform.workflow.operator.AbstractOperator}
 * to reuse lifecycle management, metrics collection, and health checks.
 *
 * <p><b>Usage Example</b><br>
 * <pre>{@code
 * VirtualOrgAgent agent = new CEOAgent(...);
 * AgentStreamOperatorAdapter adapter = 
 *     AgentStreamOperatorAdapterFactory.create(agent, meterRegistry);
 * 
 * // Register with operator catalog
 * operatorCatalog.register(adapter);
 * 
 * // Use in pipeline
 * Event taskEvent = createTaskEvent();
 * adapter.process(taskEvent).whenResult(result -> {
 *     result.getOutputEvents().forEach(eventCloud::append);
 * });
 * }</pre>
 *
 * @doc.layer core
 * @doc.purpose Adapter implementations for unified operator integration
 * @doc.pattern Adapter (Bridge)
 * @since 2.0.0
 */
package com.ghatana.core.operator.adapter;
