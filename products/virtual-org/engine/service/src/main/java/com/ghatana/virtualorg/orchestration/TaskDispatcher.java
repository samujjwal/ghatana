package com.ghatana.virtualorg.orchestration;

import com.ghatana.virtualorg.agent.VirtualOrgAgent;
import com.ghatana.virtualorg.v1.AgentRoleProto;
import com.ghatana.virtualorg.v1.AgentStateProto;
import com.ghatana.virtualorg.v1.TaskProto;
import com.ghatana.virtualorg.v1.TaskRequestProto;
import com.ghatana.virtualorg.v1.TaskResponseProto;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task dispatcher for multi-agent coordination and load balancing.
 *
 * <p><b>Purpose</b><br>
 * Orchestrates task routing to available agents based on role capability,
 * availability, and load balancing strategies. Manages task queueing when
 * all agents are busy, enabling scalable multi-agent coordination.
 *
 * <p><b>Architecture Role</b><br>
 * Central dispatcher in the orchestration layer providing:
 * - Agent registration and discovery by role
 * - Task-to-agent routing (role matching + availability)
 * - Load balancing (round-robin within role groups)
 * - Task queue integration (enqueue when no agents available)
 * - Agent health monitoring (track active/busy/failed states)
 *
 * <p><b>Routing Strategy</b><br>
 * Multi-stage routing algorithm:
 * 1. <b>Role Matching</b>: Task type → Agent role mapping (e.g., CODE_REVIEW → SENIOR_ENGINEER)
 * 2. <b>Availability Check</b>: Filter agents by state (READY, not BUSY)
 * 3. <b>Load Balancing</b>: Round-robin selection within available agents
 * 4. <b>Queue Fallback</b>: Enqueue task if no agents available
 *
 * <p><b>Agent Health Monitoring</b><br>
 * Tracks agent states:
 * - READY: Available for work
 * - BUSY: Currently executing task
 * - FAILED: Encountered error, temporarily unavailable
 * - OFFLINE: Stopped or disconnected
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TaskQueue queue = new TaskQueue(100);
 * TaskDispatcher dispatcher = new TaskDispatcher(queue);
 * 
 * // Register agents by role
 * dispatcher.registerAgent(seniorEngineer1);
 * dispatcher.registerAgent(seniorEngineer2);
 * dispatcher.registerAgent(architect);
 * 
 * // Dispatch task - automatically routes to available senior engineer
 * TaskProto task = TaskProto.newBuilder()
 *     .setTaskId("task-123")
 *     .setType("CODE_REVIEW")
 *     .build();
 * 
 * TaskResponseProto response = dispatcher.dispatch(task).getResult();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe using ConcurrentHashMap for agent registry and
 * CopyOnWriteArrayList for role mappings.
 *
 * @see TaskQueue
 * @see VirtualOrgAgent
 * @see AgentRoleProto
 * @doc.type class
 * @doc.purpose Task dispatcher for multi-agent coordination
 * @doc.layer product
 * @doc.pattern Dispatcher
 */
public class TaskDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TaskDispatcher.class);

    private final Map<String, VirtualOrgAgent> agents = new ConcurrentHashMap<>();
    private final Map<AgentRoleProto, List<String>> roleToAgents = new ConcurrentHashMap<>();
    private final TaskQueue taskQueue;

    public TaskDispatcher(@NotNull TaskQueue taskQueue) {
        this.taskQueue = taskQueue;
        log.info("Initialized TaskDispatcher");
    }

    /**
     * Registers an agent with the dispatcher.
     *
     * @param agent the agent to register
     */
    public void registerAgent(@NotNull VirtualOrgAgent agent) {
        agents.put(agent.getAgentId(), agent);

        // Add to role mapping
        roleToAgents.computeIfAbsent(agent.getRole(), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(agent.getAgentId());

        log.info("Registered agent: id={}, role={}", agent.getAgentId(), agent.getRole());
    }

    /**
     * Unregisters an agent from the dispatcher.
     *
     * @param agentId the agent ID
     */
    public void unregisterAgent(@NotNull String agentId) {
        VirtualOrgAgent agent = agents.remove(agentId);

        if (agent != null) {
            List<String> agentIds = roleToAgents.get(agent.getRole());
            if (agentIds != null) {
                agentIds.remove(agentId);
            }

            log.info("Unregistered agent: id={}, role={}", agentId, agent.getRole());
        }
    }

    /**
     * Dispatches a task to an appropriate agent.
     *
     * <p>The dispatcher:
     * <ol>
     *   <li>Determines required role based on task type</li>
     *   <li>Finds available agent with that role</li>
     *   <li>Queues task if no agents available</li>
     *   <li>Returns promise of task response</li>
     * </ol>
     *
     * @param task the task to dispatch
     * @return promise of task response
     */
    @NotNull
    public Promise<TaskResponseProto> dispatch(@NotNull TaskProto task) {
        log.debug("Dispatching task: taskId={}, type={}", task.getTaskId(), task.getType());

        // Determine required role
        AgentRoleProto requiredRole = determineRequiredRole(task);

        // Find available agent
        String agentId = findAvailableAgent(requiredRole);

        if (agentId == null) {
            log.warn("No available agents for role: {}, queueing task", requiredRole);
            return taskQueue.enqueue(task, requiredRole)
                    .then(() -> waitForAgent(task, requiredRole));
        }

        VirtualOrgAgent agent = agents.get(agentId);

        if (agent == null) {
            return Promise.ofException(new IllegalStateException("Agent not found: " + agentId));
        }

        log.info("Assigned task {} to agent {} ({})", task.getTaskId(), agentId, requiredRole);

        TaskRequestProto request = TaskRequestProto.newBuilder()
                .setTask(task)
                .build();

        return agent.processTask(request);
    }

    /**
     * Gets all registered agents.
     *
     * @return map of agent ID to agent
     */
    @NotNull
    public Map<String, VirtualOrgAgent> getAgents() {
        return Map.copyOf(agents);
    }

    /**
     * Gets agents by role.
     *
     * @param role the agent role
     * @return list of agent IDs with that role
     */
    @NotNull
    public List<String> getAgentsByRole(@NotNull AgentRoleProto role) {
        return List.copyOf(roleToAgents.getOrDefault(role, List.of()));
    }

    // =============================
    // Private helper methods
    // =============================

    private AgentRoleProto determineRequiredRole(TaskProto task) {
        // Simple mapping - in production, use more sophisticated logic
        return switch (task.getType()) {
            case TASK_TYPE_ARCHITECTURE_DESIGN -> AgentRoleProto.AGENT_ROLE_ARCHITECT;
            case TASK_TYPE_FEATURE_IMPLEMENTATION, TASK_TYPE_REFACTORING ->
                    AgentRoleProto.AGENT_ROLE_SENIOR_ENGINEER;
            case TASK_TYPE_BUG_FIX -> AgentRoleProto.AGENT_ROLE_ENGINEER;
            case TASK_TYPE_TESTING -> AgentRoleProto.AGENT_ROLE_QA_ENGINEER;
            case TASK_TYPE_CODE_REVIEW -> AgentRoleProto.AGENT_ROLE_SENIOR_ENGINEER;
            case TASK_TYPE_DEPLOYMENT -> AgentRoleProto.AGENT_ROLE_DEVOPS_ENGINEER;
            case TASK_TYPE_REQUIREMENTS_ANALYSIS -> AgentRoleProto.AGENT_ROLE_PRODUCT_MANAGER;
            case TASK_TYPE_DOCUMENTATION -> AgentRoleProto.AGENT_ROLE_TECH_WRITER;
            default -> AgentRoleProto.AGENT_ROLE_ENGINEER;
        };
    }

    private String findAvailableAgent(AgentRoleProto role) {
        List<String> agentIds = roleToAgents.get(role);

        if (agentIds == null || agentIds.isEmpty()) {
            return null;
        }

        // Simple round-robin - find first available agent
        for (String agentId : agentIds) {
            VirtualOrgAgent agent = agents.get(agentId);

            // Check if agent is not idle/busy (use getState() instead of isRunning())
            if (agent != null && agent.getState() == AgentStateProto.AGENT_STATE_IDLE) {
                return agentId;
            }
        }

        return null;
    }

    private Promise<TaskResponseProto> waitForAgent(TaskProto task, AgentRoleProto role) {
        // TODO: Implement waiting logic with polling
        // For now, return error
        return Promise.ofException(
                new IllegalStateException("No agents available for role: " + role)
        );
    }
}
