package com.ghatana.virtualorg.framework.config;

import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.Department;
import com.ghatana.virtualorg.framework.DepartmentType;
import com.ghatana.virtualorg.framework.agent.Agent;
import com.ghatana.virtualorg.framework.task.Task;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Configuration-driven department implementation.
 *
 * <p>
 * <b>Purpose</b><br>
 * Creates a department entirely from YAML configuration. Supports dynamic task
 * assignment strategies, KPI definitions, and workflow configurations loaded
 * from configuration files.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * DepartmentConfig config = loader.loadDepartmentSync(
 *     Path.of("config/departments/engineering.yaml")
 * );
 * ConfigurableDepartment dept = new ConfigurableDepartment(
 *     organization,
 *     config.spec().displayName(),
 *     DepartmentType.ENGINEERING,
 *     config
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Configuration-driven department implementation
 * @doc.layer core
 * @doc.pattern Strategy
 */
public class ConfigurableDepartment extends Department {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurableDepartment.class);

    private final DepartmentConfig config;
    private final TaskAssignmentStrategy assignmentStrategy;
    private final Map<String, Object> kpis;
    private final Map<String, TaskTypeDefinition> taskTypes;

    /**
     * Creates a configurable department.
     *
     * @param organization owning organization
     * @param name department display name
     * @param type department type
     * @param config department configuration
     */
    public ConfigurableDepartment(
            AbstractOrganization organization,
            String name,
            DepartmentType type,
            DepartmentConfig config) {
        super(organization, name, type.name());
        this.config = config;
        this.assignmentStrategy = createAssignmentStrategy();
        this.kpis = initializeKpis();
        this.taskTypes = initializeTaskTypes();
    }

    /**
     * Creates task assignment strategy from configuration.
     */
    private TaskAssignmentStrategy createAssignmentStrategy() {
        String strategy = "round-robin";

        if (config.spec().settings() != null
                && config.spec().settings().taskAssignment() != null) {
            strategy = config.spec().settings().taskAssignment().strategy();
        }

        return switch (strategy.toLowerCase()) {
            case "round-robin" ->
                new RoundRobinAssignment();
            case "least-loaded" ->
                new LeastLoadedAssignment();
            case "capability-match" ->
                new CapabilityMatchAssignment();
            default -> {
                LOG.warn("Unknown assignment strategy: {}, using round-robin", strategy);
                yield new RoundRobinAssignment();
            }
        };
    }

    /**
     * Initializes KPIs from configuration.
     */
    private Map<String, Object> initializeKpis() {
        Map<String, Object> kpiMap = new HashMap<>();

        if (config.spec().kpis() != null) {
            for (KpiDefinition kpi : config.spec().kpis()) {
                // Initialize with target as default value
                kpiMap.put(kpi.name(), kpi.target() != null ? kpi.target() : 0.0);
            }
        }

        return kpiMap;
    }

    /**
     * Initializes task types from configuration.
     */
    private Map<String, TaskTypeDefinition> initializeTaskTypes() {
        Map<String, TaskTypeDefinition> types = new HashMap<>();

        if (config.spec().taskTypes() != null) {
            for (TaskTypeDefinition taskType : config.spec().taskTypes()) {
                types.put(taskType.name(), taskType);
            }
        }

        return types;
    }

    @Override
    protected Promise<Agent> assignTask(Task task) {
        List<Agent> availableAgents = getAgents().stream()
                .filter(Agent::isAvailable)
                .toList();

        if (availableAgents.isEmpty()) {
            return Promise.ofException(new NoSuitableAgentException(
                    "No available agents in department: " + getName()
            ));
        }

        return assignmentStrategy.assign(task, availableAgents);
    }

    @Override
    public Map<String, Object> getKpis() {
        return Collections.unmodifiableMap(kpis);
    }

    /**
     * Updates a KPI value.
     *
     * @param kpiName KPI name
     * @param value new value
     */
    public void updateKpi(String kpiName, Object value) {
        if (kpis.containsKey(kpiName)) {
            kpis.put(kpiName, value);
        } else {
            LOG.warn("Unknown KPI: {} in department: {}", kpiName, getName());
        }
    }

    /**
     * Gets the department configuration.
     */
    public DepartmentConfig getConfig() {
        return config;
    }

    /**
     * Gets KPI definition by name.
     */
    public Optional<KpiDefinition> getKpiDefinition(String name) {
        if (config.spec().kpis() == null) {
            return Optional.empty();
        }
        return config.spec().kpis().stream()
                .filter(k -> k.name().equals(name))
                .findFirst();
    }

    /**
     * Gets task type definition by name.
     */
    public Optional<TaskTypeDefinition> getTaskType(String name) {
        return Optional.ofNullable(taskTypes.get(name));
    }

    /**
     * Gets all configured task types.
     */
    public Collection<TaskTypeDefinition> getTaskTypes() {
        return Collections.unmodifiableCollection(taskTypes.values());
    }

    // =========================================================================
    // Task Assignment Strategies
    // =========================================================================
    /**
     * Task assignment strategy interface.
     */
    public interface TaskAssignmentStrategy {

        Promise<Agent> assign(Task task, List<Agent> availableAgents);
    }

    /**
     * Round-robin assignment strategy.
     */
    private static class RoundRobinAssignment implements TaskAssignmentStrategy {

        private int currentIndex = 0;

        @Override
        public Promise<Agent> assign(Task task, List<Agent> availableAgents) {
            if (availableAgents.isEmpty()) {
                return Promise.ofException(new NoSuitableAgentException("No available agents"));
            }

            Agent selected = availableAgents.get(currentIndex % availableAgents.size());
            currentIndex++;
            return Promise.of(selected);
        }
    }

    /**
     * Least-loaded assignment strategy.
     */
    private static class LeastLoadedAssignment implements TaskAssignmentStrategy {

        @Override
        public Promise<Agent> assign(Task task, List<Agent> availableAgents) {
            return Promise.of(
                    availableAgents.stream()
                            .min(Comparator.comparingInt(Agent::getCurrentWorkload))
                            .orElseThrow(() -> new NoSuitableAgentException("No available agents"))
            );
        }
    }

    /**
     * Capability-matching assignment strategy.
     */
    private static class CapabilityMatchAssignment implements TaskAssignmentStrategy {

        @Override
        public Promise<Agent> assign(Task task, List<Agent> availableAgents) {
            // Simple implementation - would be enhanced with actual capability matching
            // For now, just return the first available agent
            if (availableAgents.isEmpty()) {
                return Promise.ofException(new NoSuitableAgentException("No available agents"));
            }
            return Promise.of(availableAgents.get(0));
        }
    }

    /**
     * Exception when no suitable agent is found.
     */
    public static class NoSuitableAgentException extends RuntimeException {

        public NoSuitableAgentException(String message) {
            super(message);
        }
    }
}
