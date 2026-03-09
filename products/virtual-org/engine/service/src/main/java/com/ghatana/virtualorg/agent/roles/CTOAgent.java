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
import java.util.List;
import java.util.Map;
import com.ghatana.virtualorg.util.DecisionExtractor;

/**
 * Chief Technology Officer Agent for technical strategy and engineering leadership.
 *
 * <p><b>Purpose</b><br>
 * Executive-level autonomous agent responsible for technical architecture, platform
 * scalability, security strategy, and engineering excellence. Provides technical
 * leadership and advises CEO on technology investments and strategic technical decisions.
 *
 * <p><b>Architecture Role</b><br>
 * C-level executive in organizational hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with executive technical capabilities</li>
 *   <li>Role: CTO in organizational hierarchy</li>
 *   <li>Reports to: CEO for strategic alignment</li>
 *   <li>Direct reports: Architect Lead, DevOps Lead, QA Lead, Team Leads</li>
 *   <li>Peers: CPO (product strategy), CFO (financial planning)</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * Technical strategy and leadership:
 * <ul>
 *   <li>Technical Architecture: Define and evolve architecture and technology stack</li>
 *   <li>Scalability: Own platform scalability, reliability (SLA 99.9%+), performance</li>
 *   <li>Security: Lead security strategy, compliance (SOC2, GDPR, HIPAA)</li>
 *   <li>Engineering Excellence: Drive best practices (code quality, testing, CI/CD)</li>
 *   <li>Technical Debt: Manage debt prioritization and platform improvements</li>
 *   <li>Technology Adoption: Evaluate and adopt new technologies, tools, frameworks</li>
 *   <li>Technical Standards: Define coding standards, architectural patterns, tooling</li>
 *   <li>Conflict Resolution: Resolve technical conflicts between architects and leads</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Executive-level technical authority:
 * <ul>
 *   <li>ARCHITECTURE: Full authority over technical architecture and stack decisions</li>
 *   <li>BUDGET: Can approve infrastructure/tooling budget up to $100K (escalate to CEO)</li>
 *   <li>TECHNICAL: Final authority on technical standards, best practices, frameworks</li>
 *   <li>SECURITY: Full authority over security architecture, policies, compliance</li>
 *   <li>HIRING: Can approve senior engineer/architect hires (escalate exec to CEO)</li>
 *   <li>PLATFORM: Final decisions on platform improvements vs new features</li>
 * </ul>
 *
 * <p><b>Escalation Framework</b><br>
 * C-level escalation paths:
 * <ul>
 *   <li>Escalate to CEO: Budget >$100K, strategic product/tech conflicts, exec hires</li>
 *   <li>Consult with CFO: Cost optimization, infrastructure budgets, ROI analysis</li>
 *   <li>Consult with CPO: Product/tech priority conflicts, feature vs platform</li>
 *   <li>Receive escalations from: Architect Lead (major architecture), DevOps Lead (infra), Team Leads (blockers)</li>
 * </ul>
 *
 * <p><b>LLM Configuration</b><br>
 * Executive-level technical reasoning:
 * <ul>
 *   <li>Model: GPT-4 Turbo or Claude 3 Opus (highest-capability models)</li>
 *   <li>Temperature: 0.3 (technical precision with strategic thinking)</li>
 *   <li>Max Tokens: 6000 (complex architectural analysis)</li>
 *   <li>System Prompt: Experienced CTO with 12+ years engineering leadership</li>
 *   <li>Context: Current architecture, tech stack, performance metrics, roadmap</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Executive technical tools:
 * <ul>
 *   <li>ArchitectureAnalysisTool: System design analysis, dependency graphs</li>
 *   <li>PerformanceProfilingTool: Latency analysis, throughput, resource usage</li>
 *   <li>SecurityScanTool: Vulnerability scanning, compliance checks</li>
 *   <li>CostAnalysisTool: Infrastructure cost breakdown, optimization</li>
 *   <li>TechRadarTool: Technology evaluation, adoption lifecycle</li>
 *   <li>All engineering tools via delegation to Architect/DevOps Leads</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CTOAgent cto = new CTOAgent(
 *     "cto-001",
 *     DecisionAuthorityProto.newBuilder()
 *         .addAllowedDecisionTypes(DecisionTypeProto.ARCHITECTURE)
 *         .addAllowedDecisionTypes(DecisionTypeProto.TECHNICAL)
 *         .addAllowedDecisionTypes(DecisionTypeProto.SECURITY)
 *         .setMaxBudget(100_000_00) // $100K
 *         .build(),
 *     eventloop, llmClient, memory, toolRegistry, toolExecutor,
 *     meterRegistry, tracer, llmConfig, memoryConfig
 * );
 *
 * // Architecture decision
 * TaskRequestProto task = TaskRequestProto.newBuilder()
 *     .setTaskId("arch-001")
 *     .setDescription("Should we migrate to microservices architecture?")
 *     .setType(TaskTypeProto.ARCHITECTURE_DECISION)
 *     .putMetadata("current_monolith_loc", "250000")
 *     .putMetadata("team_size", "30")
 *     .build();
 *
 * cto.processTask(task).whenResult(result -> {
 *     DecisionProto decision = result.getDecision();
 *     log.info("CTO Decision: {} - Rationale: {}",
 *         decision.getChoice(), decision.getRationale());
 * });
 * }</pre>
 *
 * @see AbstractVirtualOrgAgent
 * @see CEOAgent
 * @see ArchitectLeadAgent
 * @doc.type class
 * @doc.purpose CTO agent for technical strategy and engineering leadership
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class CTOAgent extends AbstractVirtualOrgAgent {


    private static final Logger log = LoggerFactory.getLogger(CTOAgent.class);

    /**
     * System prompt defining CTO agent's role, responsibilities, and decision-making framework.
     * Used for LLM instruction to ensure consistent technical reasoning.
     */
    private static final String CTO_SYSTEM_PROMPT = """
        You are the CTO (Chief Technology Officer) of a software development organization. Your role is to:
        
        1. TECHNICAL STRATEGY & ARCHITECTURE
           - Define and evolve technical architecture and technology stack
           - Approve major architectural changes and refactoring initiatives
           - Ensure platform scalability, reliability, and performance
           - Drive technology innovation and adoption
        
        2. ENGINEERING EXCELLENCE
           - Set engineering standards and best practices
           - Promote code quality, testing, and automation
           - Manage technical debt strategically
           - Foster culture of continuous improvement
        
        3. SECURITY & COMPLIANCE
           - Own security architecture and policies
           - Ensure compliance (SOC2, GDPR, HIPAA)
           - Manage security incidents and vulnerabilities
           - Drive zero-trust security model
        
        4. INFRASTRUCTURE & OPERATIONS
           - Oversee platform infrastructure and cloud strategy
           - Optimize infrastructure costs and performance
           - Ensure high availability and disaster recovery
           - Drive DevOps practices and automation
        
        5. DECISION FRAMEWORK
           - Evaluate technical decisions by: scalability, maintainability, cost, time-to-market
           - Balance technical excellence with business needs
           - Consider long-term technical health vs short-term velocity
           - Make data-driven decisions using metrics and benchmarks
           - Collaborate with CPO on product/tech tradeoffs
        
        6. ESCALATION HANDLING
           - Escalate budget >$100K to CEO
           - Escalate product/tech conflicts to CEO
           - Resolve technical conflicts from Architect and DevOps leads
        
        When processing tasks:
        - Analyze technical feasibility and scalability impact
        - Consider security, performance, and cost implications
        - Evaluate alignment with technical strategy
        - Assess technical debt and long-term maintainability
        - Provide clear technical rationale for all decisions
        - Flag risks and dependencies
        """;

    /**
     * Creates a new CTO agent with the specified configuration.
     *
     * @param agentId unique identifier for this agent
     * @param authority decision-making authority (architecture, budget limits)
     * @param eventloop ActiveJ eventloop for async execution
     * @param llmClient LLM client for technical reasoning
     * @param memory agent memory system (short-term + long-term)
     * @param toolRegistry registry of available tools
     * @param toolExecutor executor for tool operations
     * @param meterRegistry metrics registry for observability
     * @param tracer distributed tracing tracer
     * @param llmConfig LLM configuration (model, temperature, etc.)
     * @param memoryConfig memory system configuration
     */
    public CTOAgent(
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

        super(
                agentId,
                AgentRoleProto.AGENT_ROLE_CTO,
                authority,
                eventloop,
                llmClient,
                memory,
                toolRegistry,
                toolExecutor,
                meterRegistry,
                tracer,
                llmConfig,
                memoryConfig
        );

        log.info("Initialized CTO Agent: id={}, authority={}", agentId, authority);
    }

    /**
     * Called when the agent starts.
     * Initializes CTO-specific resources and emits startup event.
     *
     * @throws Exception if startup fails
     */
    @Override
    protected void onStart() throws Exception {
        log.info("CTO Agent starting: {}", getAgentId());
        
        // Initialize CTO-specific resources
        // - Load technical architecture and technology roadmap from memory
        // - Subscribe to architecture and infrastructure events
        // - Initialize technical metrics and SLO tracking
        
        log.debug("CTO Agent ready for technical leadership");
    }

    /**
     * Called when the agent stops.
     * Cleanup CTO-specific resources and persist final state.
     *
     * @throws Exception if shutdown fails
     */
    @Override
    protected void onStop() throws Exception {
        log.info("CTO Agent stopping: {}", getAgentId());
        
        // Persist technical context and decisions
        // - Save current architecture decisions to long-term memory
        // - Archive pending technical initiatives
        // - Emit agent stopped event
        
        log.debug("CTO Agent stopped gracefully");
    }

    /**
     * Process a task as the CTO agent.
     *
     * <p>CTO tasks typically involve:
     * <ul>
     *   <li>Architecture decisions (microservices, event-driven, database choices)</li>
     *   <li>Infrastructure approvals (cloud migration, scaling, cost optimization)</li>
     *   <li>Security decisions (encryption, auth, compliance)</li>
     *   <li>Technical debt prioritization</li>
     *   <li>Technology adoption (new languages, frameworks, tools)</li>
     *   <li>Conflict resolution (product vs tech, performance vs features)</li>
     * </ul>
     *
     * <p><b>Decision Process</b><br>
     * 1. Retrieve technical context from memory (architecture, standards, metrics)<br>
     * 2. Analyze technical feasibility and scalability impact<br>
     * 3. Use LLM to evaluate trade-offs and alternatives<br>
     * 4. Make decision aligned with technical strategy<br>
     * 5. Document decision rationale and implementation plan<br>
     * 6. Store decision context in organizational memory<br>
     *
     * @param request task request containing technical decision context
     * @return task response with decision and technical rationale
     * @throws Exception if task processing fails
     */
    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        
        log.info("CTO processing technical task: taskId={}, type={}, title={}", 
            task.getTaskId(), task.getType(), task.getTitle());

        Instant startTime = Instant.now();
        List<ToolProto> tools = getTools();

        // Chain all async operations
        Promise<TaskResponseProto> resultPromise = memory.retrieveContext(task)
            .then(context -> {
                log.debug("Retrieved technical context for task {}: {} chars", 
                    task.getTaskId(), context.length());

                // Build LLM prompt with technical context
                String prompt = buildCTOPrompt(task, context);

                // Call LLM for technical reasoning
                return llmClient.complete(
                    CTO_SYSTEM_PROMPT,
                    prompt,
                    tools,
                    llmConfig.getTemperature(),
                    llmConfig.getMaxTokens()
                );
            })
            .then(llmResponse -> {
                log.debug("LLM technical reasoning complete for task {}: {} tokens used", 
                    task.getTaskId(), llmResponse.tokensUsed());

                // Extract decision from LLM response
                DecisionProto decision = extractDecision(llmResponse, task);

                // Check if decision requires escalation to CEO
                if (shouldEscalateToCEO(decision, task)) {
                    log.info("CTO decision requires CEO escalation: taskId={}, reason={}", 
                        task.getTaskId(), getEscalationReason(decision, task));
                    
                    return Promise.of(createEscalationResponse(task, decision, "CEO"));
                }

                // Execute any tools if LLM suggested them
                if (!llmResponse.getToolCalls().isEmpty()) {
                    return executeToolsAndRefine(llmResponse, task, decision);
                }

                return Promise.of(decision);
            })
            .then(decision -> {
                if (decision.getType() == DecisionTypeProto.DECISION_TYPE_ESCALATED) {
                    return Promise.of(decision);
                }
                
                // Store decision in organizational memory
                return memory.storeDecision(decision, task)
                    .map(stored -> decision);
            })
            .map(decision -> {
                // Build task response
                TaskResponseProto response = TaskResponseProto.newBuilder()
                    .setTaskId(task.getTaskId())
                    .setAgentId(getAgentId())
                    .setSuccess(decision.getType() != DecisionTypeProto.DECISION_TYPE_ESCALATED)
                    .setResult("Decision: " + decision.getType().name())
                    .setReasoning(decision.getReasoning())
                    .build();

                boolean success = decision.getType() != DecisionTypeProto.DECISION_TYPE_ESCALATED;
                recordTaskMetrics(task, startTime, success);

                log.info("CTO completed technical decision: taskId={}, decision={}, confidence={}", 
                    task.getTaskId(), decision.getType(), decision.getConfidence());

                return response;
            });

        // Handle errors with whenException (side effects only)
        resultPromise.whenException(error -> {
            log.error("CTO failed to process task: taskId={}, error={}", 
                task.getTaskId(), error.getMessage(), error);
            recordTaskMetrics(task, startTime, false);
        });

        return resultPromise;
    }

    /**
     * Build CTO-specific prompt for LLM reasoning.
     */
    private String buildCTOPrompt(TaskProto task, String context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("TECHNICAL DECISION REQUEST\n");
        prompt.append("==========================\n\n");
        
        prompt.append("Task: ").append(task.getTitle()).append("\n");
        prompt.append("Description: ").append(task.getDescription()).append("\n");
        prompt.append("Type: ").append(task.getType()).append("\n\n");
        
        prompt.append("TECHNICAL CONTEXT\n");
        prompt.append("------------------\n");
        prompt.append(context).append("\n\n");
        
        prompt.append("DECISION REQUIRED\n");
        prompt.append("-----------------\n");
        prompt.append("Please analyze this technical decision and provide:\n");
        prompt.append("1. Your recommendation (approve/reject/modify)\n");
        prompt.append("2. Technical feasibility and scalability assessment\n");
        prompt.append("3. Security and performance implications\n");
        prompt.append("4. Cost estimate and infrastructure impact\n");
        prompt.append("5. Alternative approaches considered\n");
        prompt.append("6. Implementation complexity and timeline\n");
        prompt.append("7. Confidence level (0.0-1.0)\n");
        
        return prompt.toString();
    }

    /**
     * Extract decision from LLM response.
     */
    private DecisionProto extractDecision(LLMResponse llmResponse, TaskProto task) {
        return DecisionExtractor.extractDecision(
            llmResponse.getContent(),
            task,
            getAgentId()
        );
    }

    /**
     * Determine if decision should be escalated to CEO.
     *
     * <p>CTO escalates to CEO for:
     * <ul>
     *   <li>Budget decisions >$100K</li>
     *   <li>Major strategic/product conflicts</li>
     *   <li>Decisions requiring board approval</li>
     * </ul>
     */
    private boolean shouldEscalateToCEO(DecisionProto decision, TaskProto task) {
        // Escalate if budget authority exceeded
        if (task.getType() == TaskTypeProto.TASK_TYPE_BUDGET_APPROVAL) {
            // Extract budget amount from task description (simplified)
            return extractBudgetAmount(task.getDescription()) > getBudgetLimitUsd();
        }
        
        // Escalate strategic product/tech conflicts
        if (task.getType() == TaskTypeProto.TASK_TYPE_CODE_REVIEW) {
            return task.getDescription().toLowerCase().contains("product") ||
                   task.getDescription().toLowerCase().contains("strategic");
        }
        
        // Escalate low confidence on critical decisions
        return decision.getConfidence() < 0.4f && isCriticalDecision(task);
    }

    /**
     * Get escalation reason for logging and tracking.
     */
    private String getEscalationReason(DecisionProto decision, TaskProto task) {
        if (task.getType() == TaskTypeProto.TASK_TYPE_BUDGET_APPROVAL) {
            return "Budget exceeds CTO authority limit ($100K)";
        }
        if (decision.getConfidence() < 0.4f) {
            return "Low confidence on critical technical decision";
        }
        return "Strategic product/tech conflict requires CEO input";
    }

    /**
     * Create escalation response.
     */
    private DecisionProto createEscalationResponse(TaskProto task, DecisionProto decision, String escalateTo) {
        return decision.toBuilder()
            .setType(DecisionTypeProto.DECISION_TYPE_ESCALATED)
            .setReasoning(decision.getReasoning() + 
                "\n\nESCALATION: This decision requires " + escalateTo + " approval. " +
                "Reason: " + getEscalationReason(decision, task))
            .build();
    }

    /**
     * Execute tools suggested by LLM and refine decision.
     */
    private Promise<DecisionProto> executeToolsAndRefine(
            LLMResponse llmResponse, 
            TaskProto task,
            DecisionProto preliminaryDecision) {
        
        return toolExecutor.executeTools(llmResponse.getToolCalls(), task)
            .then(toolResults -> {
                String refinedRationale = preliminaryDecision.getReasoning() + 
                    "\n\nTool Analysis:\n" + DecisionExtractor.formatToolResults(toolResults);
                
                return Promise.of(preliminaryDecision.toBuilder()
                    .setReasoning(refinedRationale)
                    .build());
            });
    }
    private boolean isCriticalDecision(TaskProto task) {
        return task.getType() == TaskTypeProto.TASK_TYPE_ARCHITECTURE_DESIGN ||
               task.getType() == TaskTypeProto.TASK_TYPE_SECURITY_AUDIT;
    }

    private int extractBudgetAmount(String description) {
        // Simple extraction - production would use regex
        try {
            String lower = description.toLowerCase();
            int dollarIndex = lower.indexOf('$');
            if (dollarIndex >= 0) {
                String numPart = lower.substring(dollarIndex + 1).replaceAll("[^0-9]", "");
                if (!numPart.isEmpty()) {
                    return Integer.parseInt(numPart);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract budget amount: {}", e.getMessage());
        }
        return 0;
    }

    private void recordTaskMetrics(TaskProto task, Instant startTime, boolean success) {
        long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        
        meterRegistry.counter("virtualorg.cto.tasks",
            "type", task.getType().name(),
            "status", success ? "success" : "failure"
        ).increment();
        
        meterRegistry.timer("virtualorg.cto.task.duration",
            "type", task.getType().name()
        ).record(java.time.Duration.ofMillis(durationMs));
        
        if (success) {
            tasksCompleted.incrementAndGet();
        } else {
            tasksFailed.incrementAndGet();
        }
    }
    
    /**
     * Helper to get budget limit from authority limits map.
     */
    private double getBudgetLimitUsd() {
        if (authority.getLimitsMap().containsKey("budget")) {
            return authority.getLimitsMap().get("budget").getMaxAmount();
        }
        return 150_000.0; // Default CTO budget limit
    }
}
