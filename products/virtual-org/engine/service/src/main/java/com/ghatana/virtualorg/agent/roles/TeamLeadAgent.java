package com.ghatana.virtualorg.agent.roles;

import com.ghatana.virtualorg.agent.AbstractVirtualOrgAgent;
import com.ghatana.virtualorg.llm.LLMClient;
import com.ghatana.virtualorg.llm.LLMResponse;
import com.ghatana.virtualorg.memory.AgentMemory;
import com.ghatana.virtualorg.tool.ToolExecutor;
import com.ghatana.virtualorg.tool.ToolRegistry;
import com.ghatana.virtualorg.v1.*;
import com.google.protobuf.Timestamp;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import com.ghatana.virtualorg.util.DecisionExtractor;

/**
 * Team Lead Agent for engineering team management and coordination.
 *
 * <p><b>Purpose</b><br>
 * Mid-level management agent responsible for leading engineering team (4-8 engineers),
 * coordinating sprint execution, removing blockers, and ensuring team productivity.
 * Bridges individual contributors with senior leadership (Architect Lead/CTO).
 *
 * <p><b>Architecture Role</b><br>
 * Management layer in organizational hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with team management capabilities</li>
 *   <li>Role: TEAM_LEAD in organizational hierarchy</li>
 *   <li>Reports to: Architect Lead (technical) or CTO (strategic)</li>
 *   <li>Manages: 4-8 engineers (SeniorEngineerAgent, EngineerAgent, JuniorEngineerAgent)</li>
 *   <li>Collaborates with: Product Manager (requirements), QA Lead (quality)</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * Team leadership and coordination:
 * <ul>
 *   <li>Team Management: Manage and mentor 4-8 engineers, 1-on-1s, career development</li>
 *   <li>Sprint Execution: Coordinate sprint planning, daily standups, retrospectives</li>
 *   <li>Task Assignment: Distribute work based on skills, capacity, and priorities</li>
 *   <li>Code Review: Conduct and coordinate code reviews, enforce quality standards</li>
 *   <li>Blocker Removal: Identify and resolve technical blockers, facilitate collaboration</li>
 *   <li>Metrics Tracking: Track team velocity, throughput, quality metrics</li>
 *   <li>Skill Development: Support engineer growth, recommend training, pair programming</li>
 *   <li>Risk Escalation: Escalate delivery risks, technical debt, resource issues</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Team-level decisions:
 * <ul>
 *   <li>TASK_ASSIGNMENT: Full authority over team task distribution and prioritization</li>
 *   <li>CODE_QUALITY: Can mandate code changes, refactoring, style improvements</li>
 *   <li>TECHNICAL: Can approve technical decisions <2K LOC (escalate to Architect Lead)</li>
 *   <li>SPRINT_SCOPE: Can adjust sprint scope within ±10% capacity</li>
 *   <li>PROCESS: Can implement team-level process improvements (standups, retros)</li>
 * </ul>
 *
 * <p><b>Escalation Framework</b><br>
 * Mid-level escalation paths:
 * <ul>
 *   <li>Escalate to Architect Lead: Architecture decisions, tech debt >2 weeks, >2K LOC changes</li>
 *   <li>Escalate to Product Manager: Scope conflicts, requirement clarifications, timeline risks</li>
 *   <li>Escalate to CTO: Strategic technical decisions, cross-team conflicts, resource shortages</li>
 *   <li>Receive escalations from: Engineers on technical blockers, design questions, tool needs</li>
 * </ul>
 *
 * <p><b>LLM Configuration</b><br>
 * Management-focused reasoning:
 * <ul>
 *   <li>Model: GPT-4 or Claude 3 Sonnet (balanced capability)</li>
 *   <li>Temperature: 0.4 (balanced people management and technical judgment)</li>
 *   <li>Max Tokens: 3000 (team coordination tasks)</li>
 *   <li>System Prompt: Experienced engineering team lead with 5+ years management</li>
 *   <li>Context: Team capacity, current sprint, engineer skills, past velocity</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Team management tools:
 * <ul>
 *   <li>ProjectManagementTool: Sprint planning, task tracking, burndown charts</li>
 *   <li>CodeReviewTool: PR review queue, code quality metrics</li>
 *   <li>MetricsDashboardTool: Team velocity, throughput, cycle time</li>
 *   <li>GitTool: Repository access for code review, branching strategy</li>
 *   <li>CommunicationTool: Slack/email for team coordination</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TeamLeadAgent teamLead = new TeamLeadAgent(
 *     "team-lead-backend",
 *     DecisionAuthorityProto.newBuilder()
 *         .addAllowedDecisionTypes(DecisionTypeProto.TASK_ASSIGNMENT)
 *         .addAllowedDecisionTypes(DecisionTypeProto.CODE_QUALITY)
 *         .setMaxComplexity(2000) // 2K LOC
 *         .build(),
 *     eventloop, llmClient, memory, toolRegistry, toolExecutor,
 *     meterRegistry, tracer, llmConfig, memoryConfig
 * );
 *
 * // Sprint planning task
 * TaskRequestProto task = TaskRequestProto.newBuilder()
 *     .setTaskId("sprint-planning-42")
 *     .setDescription("Plan Sprint 42 with 5 engineers, 40 story points")
 *     .setType(TaskTypeProto.SPRINT_PLANNING)
 *     .putMetadata("team_capacity", "40")
 *     .putMetadata("engineer_count", "5")
 *     .build();
 *
 * teamLead.processTask(task).whenResult(result ->
 *     log.info("Sprint planned: {}", result.getDecision().getRationale())
 * );
 * }</pre>
 *
 * @see AbstractVirtualOrgAgent
 * @see ArchitectLeadAgent
 * @see SeniorEngineerAgent
 * @doc.type class
 * @doc.purpose Team Lead agent for engineering team management
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class TeamLeadAgent extends AbstractVirtualOrgAgent {


    private static final Logger log = LoggerFactory.getLogger(TeamLeadAgent.class);

    private static final String TEAMLEAD_SYSTEM_PROMPT = """
        You are a Team Lead in a software development organization. Your role is to:
        
        1. TEAM MANAGEMENT
           - Manage and mentor engineering team (4-8 engineers)
           - Assign tasks based on skills and capacity
           - Track team progress and velocity
           - Support career development
        
        2. SPRINT EXECUTION
           - Coordinate team's sprint planning and daily standups
           - Remove blockers and resolve dependencies
           - Adjust sprint scope and priorities
           - Ensure team meets sprint commitments
        
        3. CODE QUALITY & REVIEWS
           - Conduct code reviews and ensure standards
           - Promote best practices and patterns
           - Ensure test coverage and quality
           - Drive continuous improvement
        
        4. COLLABORATION & COMMUNICATION
           - Facilitate team collaboration
           - Communicate with PM, Architect, QA on progress
           - Escalate risks and blockers
           - Foster positive team culture
        
        5. DECISION FRAMEWORK
           - Evaluate by: team capacity, skill fit, priority, risk
           - Balance delivery speed with code quality
           - Consider team growth and learning
           - Use data (velocity, burndown) for planning
        
        When processing tasks:
        - Analyze team capacity and skill fit
        - Consider sprint priorities and commitments
        - Evaluate technical complexity and risks
        - Provide clear team coordination rationale
        """;

    public TeamLeadAgent(
            @NotNull String agentId,
            @NotNull DecisionAuthorityProto authority,
            @NotNull Eventloop eventloop,
            @NotNull LLMClient llmClient,
            @NotNull AgentMemory memory,
            @NotNull ToolRegistry toolRegistry,
            @NotNull ToolExecutor toolExecutor,
            @NotNull MeterRegistry meterRegistry,
            @NotNull Tracer tracer,
            @NotNull LLMConfigProto llmConfig,
            @NotNull MemoryConfigProto memoryConfig) {

        super(agentId, AgentRoleProto.AGENT_ROLE_SENIOR_ENGINEER, authority, eventloop,
                llmClient, memory, toolRegistry, toolExecutor, meterRegistry, tracer,
                llmConfig, memoryConfig);

        log.info("Initialized Team Lead Agent: id={}", agentId);
    }

    @Override
    protected void onStart() throws Exception {
        log.info("Team Lead Agent starting: {}", getAgentId());
    }

    @Override
    protected void onStop() throws Exception {
        log.info("Team Lead Agent stopping: {}", getAgentId());
    }

    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        log.info("Team Lead processing task: {}", task.getTitle());

        Instant startTime = Instant.now();

        Promise<TaskResponseProto> resultPromise = memory.retrieveContext(task)
            .then(context -> {
                String prompt = buildTeamLeadPrompt(task, context);
                return llmClient.complete(TEAMLEAD_SYSTEM_PROMPT, prompt, getTools(),
                    llmConfig.getTemperature(), llmConfig.getMaxTokens());
            })
            .map(llmResponse -> {
                DecisionProto decision = extractDecision(llmResponse, task);
                
                TaskResponseProto response = TaskResponseProto.newBuilder()
                    .setTaskId(task.getTaskId())
                    .setAgentId(getAgentId())
                    .setSuccess(true)
                    .setResult("Decision: " + decision.getType().name())
                    .setReasoning(decision.getReasoning())
                    .build();

                recordMetrics(task, startTime, true);
                return response;
            });

        // Handle errors with whenException (side effects only)
        resultPromise.whenException(error -> {
            log.error("Team Lead failed to process task: {}", error.getMessage());
            recordMetrics(task, startTime, false);
        });

        // Return result, will throw on error
        return resultPromise;
    }

    private String buildTeamLeadPrompt(TaskProto task, String context) {
        return "TEAM COORDINATION DECISION\n" +
               "Task: " + task.getTitle() + "\n" +
               "Description: " + task.getDescription() + "\n\n" +
               "Context: " + context + "\n\n" +
               "Provide team coordination analysis and recommendation.";
    }

    private DecisionProto extractDecision(LLMResponse llmResponse, TaskProto task) {
        return DecisionExtractor.extractDecision(
            llmResponse.getContent(),
            task,
            getAgentId()
        );
    }

    private void recordMetrics(TaskProto task, Instant startTime, boolean success) {
        meterRegistry.counter("virtualorg.teamlead.tasks",
            "type", task.getType().name(),
            "status", success ? "success" : "failure"
        ).increment();
    }
}
