package com.ghatana.virtualorg.framework.event;

import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.domain.auth.TenantId;

/**
 * Publisher for organization lifecycle events to EventCloud.
 *
 * <p>
 * <b>Purpose</b><br>
 * Emits organization events (OrganizationCreated, DepartmentRegistered, etc.)
 * to EventCloud for consumption by AEP and other downstream systems.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * OrganizationEventPublisher publisher = new OrganizationEventPublisher(tenantId, orgId);
 * publisher.publishOrganizationCreated("Acme Corp", "Software company");
 * publisher.publishDepartmentRegistered(deptId, "Engineering", DepartmentType.ENGINEERING);
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Adapter between virtual-org framework and EventCloud. Translates domain
 * events to protobuf events for event-driven orchestration.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - all operations are atomic and stateless.
 *
 * <p>
 * <b>TODO</b><br>
 * Full protobuf integration deferred - org-events moved to virtual-org
 * (architecture consolidation complete). Next step: Implement actual protobuf
 * event creation using com.ghatana.contracts.org.v1.* classes after resolving
 * field name mismatches (setOrganizationName vs setName, etc.)
 *
 * @doc.type class
 * @doc.purpose Event publisher for organization lifecycle
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class OrganizationEventPublisher {

    private final TenantId tenantId;
    private final Identifier organizationId;

    /**
     * Constructs event publisher.
     *
     * @param tenantId tenant owning organization
     * @param organizationId organization identifier
     */
    public OrganizationEventPublisher(TenantId tenantId, Identifier organizationId) {
        this.tenantId = tenantId;
        this.organizationId = organizationId;
    }

    /**
     * Publishes OrganizationCreated event.
     *
     * @param name organization name
     * @param description organization description
     */
    public void publishOrganizationCreated(String name, String description) {
        System.out.println("[EVENT] OrganizationCreated: " + name + " (tenant=" + tenantId + ", org=" + organizationId + ")");
    }

    /**
     * Publishes DepartmentRegistered event.
     *
     * @param departmentId department identifier
     * @param departmentName department name
     * @param departmentType department type
     */
    public void publishDepartmentRegistered(
            Identifier departmentId,
            String departmentName,
            String departmentType
    ) {
        System.out.println("[EVENT] DepartmentRegistered: " + departmentName + " (" + departmentType + ")");
    }

    /**
     * Publishes AgentRegistered event.
     *
     * @param agentId agent identifier
     * @param agentName agent name
     * @param agentType agent type
     * @param departmentId department agent belongs to
     */
    public void publishAgentRegistered(
            Identifier agentId,
            String agentName,
            String agentType,
            Identifier departmentId
    ) {
        System.out.println("[EVENT] AgentRegistered: " + agentName + " (" + agentType + ") in dept " + departmentId);
    }

    /**
     * Publishes TaskDeclared event.
     *
     * @param taskId task identifier
     * @param taskName task name
     * @param departmentId owning department
     */
    public void publishTaskDeclared(
            Identifier taskId,
            String taskName,
            Identifier departmentId
    ) {
        System.out.println("[EVENT] TaskDeclared: " + taskName + " in dept " + departmentId);
    }

    /**
     * Publishes TaskAssigned event.
     *
     * @param taskId task identifier
     * @param agentId assigned agent
     */
    public void publishTaskAssigned(Identifier taskId, Identifier agentId) {
        System.out.println("[EVENT] TaskAssigned: task " + taskId + " -> agent " + agentId);
    }

    /**
     * Publishes TaskStarted event.
     *
     * @param taskId task identifier
     */
    public void publishTaskStarted(Identifier taskId) {
        System.out.println("[EVENT] TaskStarted: " + taskId);
    }

    /**
     * Publishes TaskCompleted event.
     *
     * @param taskId task identifier
     */
    public void publishTaskCompleted(Identifier taskId) {
        System.out.println("[EVENT] TaskCompleted: " + taskId);
    }

    /**
     * Publishes TaskFailed event.
     *
     * @param taskId task identifier
     * @param reason failure reason
     */
    public void publishTaskFailed(Identifier taskId, String reason) {
        System.out.println("[EVENT] TaskFailed: " + taskId + " - " + reason);
    }

    /**
     * Appends a raw event to the event stream.
     *
     * <p>
     * This method allows direct publication of serialized events to the
     * underlying event stream (EventCloud or equivalent) without requiring full
     * protobuf infrastructure. Used by adapters for bridging framework events
     * to AEP.
     *
     * @param eventType the type/classification of the event
     * @param payload the serialized event payload (typically JSON or protobuf
     * bytes)
     */
    public void appendRawEvent(String eventType, String payload) {
        System.out.println("[EVENT] Raw: " + eventType + " - " + payload);
    }
}
