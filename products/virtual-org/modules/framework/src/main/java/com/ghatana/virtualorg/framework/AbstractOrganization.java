package com.ghatana.virtualorg.framework;

import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.virtualorg.framework.event.AepEventPublisherAdapter;
import com.ghatana.virtualorg.framework.event.OrganizationEventPublisher;
import com.ghatana.virtualorg.framework.kpi.DepartmentKpiTracker;
import io.activej.eventloop.Eventloop;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for all virtual organizations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides foundational structure for modeling organizations as collections of
 * departments, agents, tasks, and KPIs. Integrates with event runtime for
 * lifecycle tracking and AEP for workflow orchestration.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * public class SoftwareOrganization extends AbstractOrganization {
 *     public SoftwareOrganization(TenantId tenantId, String name) {
 *         super(tenantId, name);
 *         registerDepartment(new EngineeringDepartment(this));
 *         registerDepartment(new QADepartment(this));
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Core abstraction in virtual-org framework layer. Used by: - Software-Org
 * product for organization simulation - YAPPC for org-level planning and
 * routing - AEP for event-driven task orchestration
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe via ConcurrentHashMap for departments. Subclasses should ensure
 * thread-safety for additional state.
 *
 * <p>
 * <b>Event Integration</b><br>
 * Emits OrganizationCreated on construction, DepartmentRegistered on department
 * add.
 *
 * @see Department
 * @see OrganizationContext
 * @see OrganizationEventPublisher
 * @doc.type class
 * @doc.purpose Abstract base for virtual organizations
 * @doc.layer product
 * @doc.pattern Template Method
 */
public abstract class AbstractOrganization {

    private final Identifier id;
    private final TenantId tenantId;
    private final String name;
    private final String description;
    private final Instant createdAt;
    private final Map<String, Department> departments;
    private final OrganizationContext context;
    private final DepartmentKpiTracker kpiTracker;
    private final EventPublisher eventPublisher;
    private final OrganizationEventPublisher aepPublisher;
    private final Eventloop eventloop;

    /**
     * Constructs a new organization.
     *
     * <p>
     * Emits OrganizationCreated event to EventCloud.
     *
     * @param eventloop the eventloop for this organization
     * @param tenantId tenant owning this organization
     * @param name human-readable organization name
     * @throws IllegalArgumentException if tenantId or name is null/blank
     */
    protected AbstractOrganization(Eventloop eventloop, TenantId tenantId, String name) {
        this(eventloop, tenantId, name, "", null);
    }

    /**
     * Constructs a new organization with description.
     *
     * @param eventloop the eventloop for this organization
     * @param tenantId tenant owning this organization
     * @param name human-readable organization name
     * @param description optional organization description
     * @throws IllegalArgumentException if tenantId or name is null/blank
     */
    protected AbstractOrganization(Eventloop eventloop, TenantId tenantId, String name, String description) {
        this(eventloop, tenantId, name, description, null);
    }

    /**
     * Constructs a new organization with custom EventPublisher.
     *
     * <p>
     * This constructor enables dependency injection of EventPublisher
     * implementations (e.g., EventPublisher interface with
     * AepEventPublisherAdapter) for easier testing and runtime flexibility.
     *
     * @param eventloop the eventloop for this organization
     * @param tenantId tenant owning this organization
     * @param name human-readable organization name
     * @param description optional organization description
     * @param injectedPublisher optional EventPublisher implementation; if null,
     * creates default OrganizationEventPublisher
     * @throws IllegalArgumentException if tenantId or name is null/blank
     */
    protected AbstractOrganization(Eventloop eventloop, TenantId tenantId, String name, String description, EventPublisher injectedPublisher) {
        if (tenantId == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("TenantId and name must not be null/blank");
        }
        if (eventloop == null) {
            throw new IllegalArgumentException("Eventloop must not be null");
        }

        this.eventloop = eventloop;
        this.id = Identifier.random();
        this.tenantId = tenantId;
        this.name = name;
        this.description = description != null ? description : "";
        this.createdAt = Instant.now();
        this.departments = new ConcurrentHashMap<>();
        this.context = new OrganizationContext(this);
        this.kpiTracker = new DepartmentKpiTracker(tenantId, id);
        this.aepPublisher = new OrganizationEventPublisher(tenantId, id);

        // Use injected EventPublisher or create adapter wrapping the default AEP publisher
        if (injectedPublisher != null) {
            this.eventPublisher = injectedPublisher;
        } else {
            this.eventPublisher = new AepEventPublisherAdapter(aepPublisher);
        }

        // Emit organization created event
        this.eventPublisher.publishOrganizationCreated(name, description);
    }

    /**
     * Registers a department with this organization.
     *
     * <p>
     * Emits DepartmentRegistered event. Idempotent - re-registering same
     * department ID is allowed and emits duplicate event.
     *
     * GIVEN: A department implementation WHEN: registerDepartment() is called
     * THEN: Department is added and event is emitted
     *
     * @param department department to register
     * @throws IllegalArgumentException if department is null
     */
    public void registerDepartment(Department department) {
        if (department == null) {
            throw new IllegalArgumentException("Department must not be null");
        }

        departments.put(department.getId().raw(), department);
        department.setOrganization(this);

        // Emit department registered event
        eventPublisher.publishDepartmentRegistered(
                department.getId(),
                department.getName(),
                department.getType().name()
        );

        // Initialize KPI tracking for department
        kpiTracker.registerDepartment(department.getId(), department.getName());
    }

    /**
     * Retrieves department by ID.
     *
     * @param departmentId department identifier
     * @return Optional containing department if found
     */
    public Optional<Department> getDepartment(Identifier departmentId) {
        return Optional.ofNullable(departments.get(departmentId.raw()));
    }

    /**
     * Compatibility helper: get department by its code (e.g. "ENG", "QA").
     * Product modules historically used String codes when querying departments.
     */
    public Department getDepartment(String code) {
        return departments.values().stream()
                .filter(d -> d.getType().name().equalsIgnoreCase(code) || d.getName().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + code));
    }

    /**
     * Lists all registered departments.
     *
     * @return unmodifiable collection of departments
     */
    public Collection<Department> getDepartments() {
        return Collections.unmodifiableCollection(departments.values());
    }

    /**
     * Retrieves organization context for cross-department operations.
     *
     * @return organization context
     */
    public OrganizationContext getContext() {
        return context;
    }

    /**
     * Retrieves KPI tracker for all departments.
     *
     * @return KPI tracker
     */
    public DepartmentKpiTracker getKpiTracker() {
        return kpiTracker;
    }

    /**
     * Retrieves the eventloop for this organization.
     *
     * @return eventloop
     */
    public Eventloop getEventloop() {
        return eventloop;
    }

    // Accessors
    public Identifier getId() {
        return id;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Starts the organization.
     */
    public void start() {
        initialize();
    }

    /**
     * Stops the organization.
     */
    public void stop() {
        shutdown();
    }

    /**
     * Template method for custom initialization logic.
     *
     * <p>
     * Called after organization is constructed but before first use. Subclasses
     * should override to set up departments, agents, workflows.
     */
    protected void initialize() {
        // default no-op - product modules may override
    }

    /**
     * Template method for custom shutdown logic.
     *
     * <p>
     * Called before organization is terminated. Subclasses should override to
     * clean up resources, stop agents, complete workflows.
     */
    protected void shutdown() {
        // default no-op
    }
}
