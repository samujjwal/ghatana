package com.ghatana.virtualorg.adapter;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;

/**
 * Event emission port for publishing EventCloud events from virtual organization components.
 *
 * <p><b>Purpose</b><br>
 * Abstraction for emitting EventCloud events from virtual-org agents, tools, and workflows.
 * Enables decoupled event publishing with support for fire-and-forget or guaranteed delivery
 * patterns. Events flow to EventCloud for pattern matching, auditing, and downstream processing.
 *
 * <p><b>Architecture Role</b><br>
 * Port interface in hexagonal architecture:
 * <ul>
 *   <li>Port for event publishing abstraction (decouples producers from EventCloud)</li>
 *   <li>Used by: Agents (lifecycle events), Tools (execution events), Workflows (state changes)</li>
 *   <li>Implemented by: EventEmitterImpl (in-memory queuing), KafkaEventEmitter (external queue)</li>
 *   <li>Integrates with: EventCloud event bus, pattern engine, audit log</li>
 * </ul>
 *
 * <p><b>Event Types</b><br>
 * Common event types emitted:
 * <ul>
 *   <li>com.ghatana.virtualorg.task.started - Agent task execution started</li>
 *   <li>com.ghatana.virtualorg.task.completed - Agent task completed successfully</li>
 *   <li>com.ghatana.virtualorg.task.failed - Agent task failed with error</li>
 *   <li>com.ghatana.virtualorg.decision.made - Decision made by agent</li>
 *   <li>com.ghatana.virtualorg.tool.executed - Tool execution completed</li>
 *   <li>com.ghatana.virtualorg.authorization.checked - Authorization check performed</li>
 * </ul>
 *
 * <p><b>Delivery Guarantees</b><br>
 * Two emission modes:
 * <ul>
 *   <li>Fire-and-forget: {@link #emit(Event)} - Async, best-effort, errors logged</li>
 *   <li>Guaranteed: {@link #emitAsync(Event)} - Returns Promise, delivery tracking</li>
 * </ul>
 *
 * <p><b>Implementation Options</b><br>
 * Implementations can choose:
 * <ul>
 *   <li>Publish to event bus (Kafka, RabbitMQ)</li>
 *   <li>Log events for auditing (file, database)</li>
 *   <li>Store in EventCloud directly</li>
 *   <li>In-memory queue for testing</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EventEmitter emitter = new EventEmitterImpl(eventloop);
 * 
 * // Fire-and-forget (non-blocking)
 * Event taskStarted = VirtualOrgEventFactory.createTaskStartedEvent(
 *     "agent-001", task, correlationId);
 * emitter.emit(taskStarted);
 * 
 * // Guaranteed delivery (with Promise)
 * Event decision = VirtualOrgEventFactory.createDecisionMadeEvent(
 *     "agent-001", decision, correlationId);
 * emitter.emitAsync(decision).whenComplete(() ->
 *     log.info("Decision event delivered"));
 * 
 * // Health check
 * if (!emitter.isHealthy()) {
 *     log.warn("Event emitter unhealthy, events may be dropped");
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Implementations MUST be thread-safe for concurrent event emission from multiple agents.
 *
 * @see EventEmitterImpl
 * @see VirtualOrgEventFactory
 * @doc.type interface
 * @doc.purpose Event emission port for publishing EventCloud events
 * @doc.layer product
 * @doc.pattern Port
 */
public interface EventEmitter {
    
    /**
     * Emits an event asynchronously without blocking.
     * Errors are logged but not propagated to avoid impacting the main task flow.
     * 
     * @param event The event to emit
     */
    void emit(Event event);
    
    /**
     * Emits an event and returns a promise for guaranteed delivery tracking.
     * 
     * @param event The event to emit
     * @return A promise that completes when the event has been delivered
     */
    Promise<Void> emitAsync(Event event);
    
    /**
     * Checks if the emitter is healthy and ready to emit events.
     * 
     * @return true if the emitter can accept events
     */
    boolean isHealthy();
}