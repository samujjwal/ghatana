package com.ghatana.virtualorg.framework;

import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.virtualorg.framework.agent.Agent;
import com.ghatana.virtualorg.framework.task.Task;
import io.activej.promise.Promise;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a department within a virtual organization.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides structure for organizational units with agents, tasks, and KPIs.
 * Supports hierarchical organization structure via parent/child relationships.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * public class EngineeringDepartment extends Department {
 *     public EngineeringDepartment(AbstractOrganization org) {
 *         super(org.getTenantId(), "Engineering", DepartmentType.ENGINEERING);
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe via CopyOnWriteArrayList for agents. Subclasses must ensure
 * thread-safety for additional state.
 *
 * @see AbstractOrganization
 * @see DepartmentType
 * @doc.type class
 * @doc.purpose Department abstraction for virtual organizations
 * @doc.layer product
 * @doc.pattern Strategy
 */
public abstract class Department {

    private final Identifier id;
    private final String name;
    private final DepartmentType type;
    private final List<Agent> agents;
    private AbstractOrganization organization;

    /**
     * Constructs a department.
     *
     * @param name department name
     * @param type department type
     */
    protected Department(String name, DepartmentType type) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Department name must not be null/blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Department type must not be null");
        }

        this.id = Identifier.random();
        this.name = name;
        this.type = type;
        this.agents = new CopyOnWriteArrayList<>();
    }

    /**
     * Compatibility constructor used by product modules that pass the owning
     * organization and a department code. The code is mapped to DepartmentType
     * by name (case-insensitive using upper-case lookup).
     */
    protected Department(AbstractOrganization organization, String name, String code) {
        this(name, DepartmentType.valueOf(code.toUpperCase()));
        this.organization = organization;
    }

    /**
     * Registers an agent with this department.
     *
     * @param agentId agent identifier
     */
    /**
     * Register an agent instance with this department. This is a compatibility
     * API used by product modules which create Agent instances and register
     * them directly.
     */
    public void registerAgent(Agent agent) {
        if (agent == null) {
            throw new IllegalArgumentException("Agent must not be null");
        }
        agents.add(agent);
    }

    /**
     * Lists all agents in this department.
     *
     * @return unmodifiable list of agent IDs
     */
    public List<Agent> getAgents() {
        return List.copyOf(agents);
    }

    /**
     * Sets owning organization.
     *
     * <p>
     * Called by AbstractOrganization during registration.
     *
     * @param organization owning organization
     */
    void setOrganization(AbstractOrganization organization) {
        this.organization = organization;
    }

    /**
     * Retrieves owning organization.
     *
     * @return organization or null if not registered
     */
    public AbstractOrganization getOrganization() {
        return organization;
    }

    // Accessors
    public Identifier getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DepartmentType getType() {
        return type;
    }

    /**
     * Assigns a task to a suitable agent. Product modules override this to
     * implement department-specific assignment strategies.
     */
    protected abstract Promise<Agent> assignTask(Task task);

    /**
     * Returns department KPI values.
     */
    public abstract java.util.Map<String, Object> getKpis();

    /**
     * Template method for department-specific initialization.
     */
    protected void initialize() {
        // default no-op for backward compatibility; product modules may override
    }

    /**
     * Helper for defining named workflows. Kept as a no-op compatibility method
     * so product modules that call defineWorkflow compile without depending on
     * workflow engine during early repair.
     */
    protected void defineWorkflow(String name, java.util.List<String> stages) {
        // no-op compatibility shim
    }
}
