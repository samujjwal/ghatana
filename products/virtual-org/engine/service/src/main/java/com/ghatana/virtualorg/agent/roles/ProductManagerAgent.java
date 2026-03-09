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
 * Product Manager Agent for product execution and feature delivery.
 *
 * <p><b>Purpose</b><br>
 * Mid-level product leadership agent responsible for product backlog management,
 * feature prioritization, sprint planning, requirements definition, and stakeholder
 * communication. Bridges customer needs with engineering execution.
 *
 * <p><b>Architecture Role</b><br>
 * Product leadership in organizational hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with product management capabilities</li>
 *   <li>Role: PRODUCT_MANAGER in organizational hierarchy</li>
 *   <li>Reports to: CPO (Chief Product Officer) for product strategy</li>
 *   <li>Collaborates with: Team Leads (engineering), QA Lead (quality), DevOps (deployments)</li>
 *   <li>Serves: Customer needs, business stakeholders, engineering teams</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * Product ownership and execution:
 * <ul>
 *   <li>Backlog Management: Own product backlog, prioritize features by value and ROI</li>
 *   <li>Sprint Planning: Lead sprint planning, define sprint goals, balance priorities</li>
 *   <li>Requirements: Write detailed product requirements (PRDs), acceptance criteria</li>
 *   <li>User Stories: Create and refine user stories with engineering teams</li>
 *   <li>Collaboration: Work with design on UX, engineering on feasibility</li>
 *   <li>Metrics Tracking: Track feature delivery, product adoption, customer satisfaction</li>
 *   <li>Stakeholder Communication: Update stakeholders on progress, roadmap changes</li>
 *   <li>Tradeoff Decisions: Balance scope, time, quality within authority limits</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Product execution decisions:
 * <ul>
 *   <li>FEATURE_PRIORITIZATION: Full authority within approved roadmap</li>
 *   <li>SCOPE_CHANGES: Can approve scope changes ≤20% of sprint capacity (escalate to CPO)</li>
 *   <li>REQUIREMENTS: Full authority over product requirements and acceptance criteria</li>
 *   <li>USER_STORIES: Can define, refine, split, merge user stories</li>
 *   <li>SPRINT_PLANNING: Can adjust sprint goals based on team capacity</li>
 * </ul>
 *
 * <p><b>Escalation Framework</b><br>
 * Mid-level product escalation paths:
 * <ul>
 *   <li>Escalate to CPO: Roadmap changes, strategic product decisions, scope >20%, new features</li>
 *   <li>Escalate to CEO: Major product pivots, market strategy shifts</li>
 *   <li>Collaborate with Team Leads: Feasibility discussions, capacity planning</li>
 *   <li>Receive escalations from: Engineering teams on requirement clarifications, scope issues</li>
 * </ul>
 *
 * <p><b>LLM Configuration</b><br>
 * Product-focused reasoning:
 * <ul>
 *   <li>Model: GPT-4 or Claude 3 Sonnet (balanced capability)</li>
 *   <li>Temperature: 0.5 (creative product thinking with business rigor)</li>
 *   <li>Max Tokens: 3500 (PRDs, user stories, analysis)</li>
 *   <li>System Prompt: Experienced PM with 4+ years product management, MBA preferred</li>
 *   <li>Context: Product roadmap, user feedback, metrics, competitive landscape</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Product management tools:
 * <ul>
 *   <li>RoadmapTool: View/update product roadmap, feature timelines</li>
 *   <li>UserResearchTool: Access user feedback, surveys, interviews</li>
 *   <li>AnalyticsTool: Product metrics, feature adoption, usage patterns</li>
 *   <li>RequirementsTool: Create PRDs, user stories, acceptance criteria</li>
 *   <li>ProjectManagementTool: Sprint planning, backlog grooming</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ProductManagerAgent pm = new ProductManagerAgent(
 *     "pm-mobile-app",
 *     DecisionAuthorityProto.newBuilder()
 *         .addAllowedDecisionTypes(DecisionTypeProto.FEATURE_PRIORITIZATION)
 *         .addAllowedDecisionTypes(DecisionTypeProto.SCOPE_CHANGES)
 *         .addAllowedDecisionTypes(DecisionTypeProto.REQUIREMENTS)
 *         .setMaxScopeChangePercent(20)
 *         .build(),
 *     eventloop, llmClient, memory, toolRegistry, toolExecutor,
 *     meterRegistry, tracer, llmConfig, memoryConfig
 * );
 *
 * // Sprint planning task
 * TaskRequestProto task = TaskRequestProto.newBuilder()
 *     .setTaskId("sprint-planning-15")
 *     .setDescription("Plan Sprint 15 with 8 user stories, 50 story points")
 *     .setType(TaskTypeProto.SPRINT_PLANNING)
 *     .putMetadata("capacity", "50")
 *     .putMetadata("stories", "8")
 *     .build();
 *
 * pm.processTask(task).whenResult(result ->
 *     log.info("Sprint planned: {}", result.getDecision().getRationale())
 * );
 * }</pre>
 *
 * @see AbstractVirtualOrgAgent
 * @see CPOAgent
 * @see TeamLeadAgent
 * @doc.type class
 * @doc.purpose PM agent for product execution and feature delivery
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class ProductManagerAgent extends AbstractVirtualOrgAgent {


    private static final Logger log = LoggerFactory.getLogger(ProductManagerAgent.class);

    private static final String PM_SYSTEM_PROMPT = """
        You are a Product Manager in a software development organization. Your role is to:
        
        1. BACKLOG & PRIORITIZATION
           - Own product backlog and feature prioritization
           - Prioritize by customer value, business impact, effort
           - Balance customer requests with strategic goals
           - Maintain clear, actionable backlog
        
        2. SPRINT PLANNING & EXECUTION
           - Lead sprint planning and grooming sessions
           - Define sprint goals and success criteria
           - Collaborate with engineering on capacity and commitments
           - Track sprint progress and adjust as needed
        
        3. PRODUCT REQUIREMENTS
           - Write clear product requirements (PRDs)
           - Define user stories with acceptance criteria
           - Collaborate with design on UX/UI
           - Ensure requirements align with customer needs
        
        4. STAKEHOLDER COMMUNICATION
           - Communicate progress and roadmap to stakeholders
           - Manage expectations and timelines
           - Gather and synthesize customer feedback
           - Champion customer needs
        
        5. DECISION FRAMEWORK
           - Evaluate by: customer value, business impact, effort, risk
           - Use data-driven prioritization frameworks (RICE, MoSCoW)
           - Balance short-term delivery with long-term goals
           - Consider technical feasibility and dependencies
        
        When processing tasks:
        - Analyze customer value and business impact
        - Consider delivery timeline and dependencies
        - Evaluate scope and complexity
        - Provide clear product rationale
        """;

    public ProductManagerAgent(
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

        super(agentId, AgentRoleProto.AGENT_ROLE_PRODUCT_MANAGER, authority, eventloop,
                llmClient, memory, toolRegistry, toolExecutor, meterRegistry, tracer,
                llmConfig, memoryConfig);

        log.info("Initialized PM Agent: id={}", agentId);
    }

    @Override
    protected void onStart() throws Exception {
        log.info("PM Agent starting: {}", getAgentId());
    }

    @Override
    protected void onStop() throws Exception {
        log.info("PM Agent stopping: {}", getAgentId());
    }

    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        log.info("PM processing task: {}", task.getTitle());

        Instant startTime = Instant.now();

        Promise<TaskResponseProto> resultPromise = memory.retrieveContext(task)
            .then(context -> {
                String prompt = buildPMPrompt(task, context);
                return llmClient.complete(PM_SYSTEM_PROMPT, prompt, getTools(),
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
            log.error("PM failed to process task: {}", error.getMessage());
            recordMetrics(task, startTime, false);
        });

        return resultPromise;
    }

    private String buildPMPrompt(TaskProto task, String context) {
        return "PRODUCT DECISION\n" +
               "Task: " + task.getTitle() + "\n" +
               "Description: " + task.getDescription() + "\n\n" +
               "Context: " + context + "\n\n" +
               "Provide product analysis and recommendation.";
    }

    private DecisionProto extractDecision(LLMResponse llmResponse, TaskProto task) {
        return DecisionExtractor.extractDecision(
            llmResponse.getContent(),
            task,
            getAgentId()
        );
    }

    private void recordMetrics(TaskProto task, Instant startTime, boolean success) {
        meterRegistry.counter("virtualorg.pm.tasks",
            "type", task.getType().name(),
            "status", success ? "success" : "failure"
        ).increment();
    }
}
