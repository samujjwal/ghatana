package com.ghatana.virtualorg.framework.event;

import com.ghatana.platform.types.identity.Identifier;

/**
 * Abstraction for publishing organization events. Framework components should
 * depend on this interface; adapters bridge to platform-specific publishers
 * (AEP).
 *
 * <p>
 * This interface provides high-level event publishing methods for
 * organizational lifecycle events. Implementations should map events to
 * canonical `OrganizationEvent` contracts and perform tenant tagging.
 *
 * @doc.type interface
 * @doc.purpose Framework-level event publishing abstraction
 * @doc.layer product
 * @doc.pattern Port
 */
public interface EventPublisher {

    /**
     * Publish a generic organization event payload.
     *
     * @param eventType the event type name
     * @param payload serialized payload (JSON or proto bytes depending on
     * implementation)
     */
    void publish(String eventType, byte[] payload);

    /**
     * Publish an OrganizationCreated event.
     *
     * @param name organization name
     * @param description organization description
     */
    void publishOrganizationCreated(String name, String description);

    /**
     * Publish a DepartmentRegistered event.
     *
     * @param departmentId department identifier
     * @param name department name
     * @param type department type/role
     */
    void publishDepartmentRegistered(Identifier departmentId, String name, String type);

    /**
     * Publish a TaskDeclared event.
     *
     * @param taskId task identifier
     * @param name task name
     * @param description task description
     */
    void publishTaskDeclared(String taskId, String name, String description);

    /**
     * Publish a TaskAssigned event.
     *
     * @param taskId task identifier
     * @param agentId assigned agent identifier
     */
    void publishTaskAssigned(String taskId, String agentId);

    /**
     * Publish a TaskCompleted event.
     *
     * @param taskId task identifier
     * @param result task result/outcome
     */
    void publishTaskCompleted(String taskId, String result);
}
