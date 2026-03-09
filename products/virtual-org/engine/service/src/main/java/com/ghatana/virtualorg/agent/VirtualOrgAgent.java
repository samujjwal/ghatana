package com.ghatana.virtualorg.agent;

import com.ghatana.agent.Agent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.virtualorg.v1.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Core interface for Virtual Organization agents.
 *
 * <p><b>Purpose</b><br>
 * Port interface defining virtual organization agent capabilities including
 * LLM reasoning, autonomous task execution, and decision-making with escalation.
 *
 * <p><b>Architecture Role</b><br>
 * Port interface extending core {@link Agent}. Adds virtual organization capabilities:
 * - LLM-powered reasoning (GPT-4, Claude)
 * - Tool execution (Git, file ops, HTTP)
 * - Decision-making with confidence scoring
 * - Escalation to senior roles
 * - Memory-based learning
 *
 * <p><b>Agent Types</b><br>
 * - **SeniorEngineerAgent**: Complex tasks, code reviews, architecture
 * - **EngineerAgent**: Standard implementation tasks
 * - **JuniorEngineerAgent**: Simple tasks, learning mode
 * - **ArchitectAgent**: System design, architectural decisions
 * - **QAAgent**: Testing, quality assurance
 * - **DevOpsAgent**: Deployment, infrastructure
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * VirtualOrgAgent agent = new SeniorEngineerAgent(
 *     "agent-123",
 *     llmClient,
 *     toolExecutor,
 *     memory,
 *     eventloop
 * );
 * 
 * // Execute task
 * TaskResponseProto response = agent.executeTask(task).getResult();
 * 
 * // Make decision
 * DecisionProto decision = agent.makeDecision(task).getResult();
 * }</pre>
 *
 * @see Agent
 * @see AgentRoleProto
 * @see TaskProto
 * @see DecisionProto
 * @doc.type interface
 * @doc.purpose Virtual organization agent port with LLM reasoning and autonomy
 * @doc.layer product
 * @doc.pattern Port
 */
public interface VirtualOrgAgent extends Agent {

    /**
     * Gets the unique identifier of this agent.
     * 
     * @return the agent ID, never null
     */
    @NotNull
    default String getAgentId() {
        return getId();
    }

    /**
     * Gets the role of this agent.
     *
     * @return the agent role, never null
     */
    @NotNull
    AgentRoleProto getRole();

    /**
     * Gets the current state of this agent.
     *
     * @return the agent state, never null
     */
    @NotNull
    AgentStateProto getState();

    /**
     * Gets the decision authority of this agent.
     *
     * @return the decision authority, never null
     */
    @NotNull
    DecisionAuthorityProto getAuthority();

    /**
     * Starts the agent and initializes its runtime environment.
     * This should be called before the agent can process tasks.
     *
     * @return a promise that completes when the agent has started
     */
    @NotNull
    Promise<Void> start();

    /**
     * Stops the agent and cleanly shuts down its runtime environment.
     *
     * @return a promise that completes when the agent has stopped
     */
    @NotNull
    Promise<Void> stop();

    /**
     * Processes a task assigned to this agent.
     *
     * <p>The agent will:
     * <ol>
     *   <li>Retrieve relevant context from memory</li>
     *   <li>Use LLM to reason about the task</li>
     *   <li>Execute necessary tools</li>
     *   <li>Make decisions within its authority</li>
     *   <li>Escalate if needed</li>
     *   <li>Store results in memory</li>
     * </ol>
     *
     * @param request the task request
     * @return a promise of the task response
     */
    @NotNull
    Promise<TaskResponseProto> processTask(@NotNull TaskRequestProto request);

    /**
     * Makes a decision about a given context.
     *
     * <p>The agent will evaluate options and select the best one based on
     * its reasoning and constraints. If the decision exceeds the agent's
     * authority, it will be escalated.</p>
     *
     * @param decisionType the type of decision
     * @param context      the decision context
     * @param options      the available options
     * @return a promise of the decision
     */
    @NotNull
    Promise<DecisionProto> makeDecision(
            @NotNull DecisionTypeProto decisionType,
            @NotNull Map<String, String> context,
            @NotNull List<OptionProto> options
    );

    /**
     * Checks if this agent has authority to make a specific type of decision.
     *
     * @param decisionType the type of decision
     * @return true if the agent can make this decision, false otherwise
     */
    boolean canDecide(@NotNull DecisionTypeProto decisionType);

    /**
     * Escalates a task or decision to a higher authority.
     *
     * @param taskId       the task ID
     * @param decisionType the decision type
     * @param reason       the reason for escalation
     * @return a promise of the escalation request
     */
    @NotNull
    Promise<EscalationRequestProto> escalate(
            @NotNull String taskId,
            @NotNull DecisionTypeProto decisionType,
            @NotNull String reason
    );

    /**
     * Retrieves the agent's performance metrics.
     *
     * @return the performance metrics
     */
    @NotNull
    AgentPerformanceProto getPerformance();

    /**
     * Updates the agent's configuration.
     *
     * @param config the new configuration
     * @return a promise that completes when configuration is updated
     */
    @NotNull
    Promise<Void> updateConfig(@NotNull VirtualOrgAgentProto config);

    /**
     * Sends a message to another agent.
     *
     * @param targetAgentId the target agent ID
     * @param message       the message content
     * @return a promise that completes when the message is sent
     */
    @NotNull
    Promise<Void> sendMessage(@NotNull String targetAgentId, @NotNull String message);

    /**
     * Gets the list of tools available to this agent.
     *
     * @return the list of tools
     */
    @NotNull
    List<ToolProto> getTools();

    /**
     * Executes a tool with the given arguments.
     *
     * @param toolName  the tool name
     * @param arguments the tool arguments
     * @return a promise of the tool call result
     */
    @NotNull
    Promise<ToolCallProto> executeTool(
            @NotNull String toolName,
            @NotNull Map<String, String> arguments
    );
}
