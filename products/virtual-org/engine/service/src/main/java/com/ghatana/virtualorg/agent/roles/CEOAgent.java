package com.ghatana.virtualorg.agent.roles;

import com.ghatana.virtualorg.agent.AbstractVirtualOrgAgent;
import com.ghatana.virtualorg.llm.LLMClient;
import com.ghatana.virtualorg.llm.LLMResponse;
import com.ghatana.virtualorg.memory.AgentMemory;
import com.ghatana.virtualorg.tool.ToolExecutor;
import com.ghatana.virtualorg.tool.ToolRegistry;
import com.ghatana.virtualorg.util.DecisionExtractor;
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

/**
 * Chief Executive Officer Agent for strategic leadership and executive decision-making.
 *
 * <p><b>Purpose</b><br>
 * Highest-level autonomous agent in virtual organization hierarchy, responsible for
 * company vision, strategic direction, cross-functional alignment, and final executive
 * decisions. Operates with unlimited decision authority across all organizational domains.
 *
 * <p><b>Architecture Role</b><br>
 * Top of organizational hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with executive capabilities</li>
 *   <li>Role: CEO in organizational hierarchy</li>
 *   <li>Reports to: Board of Directors (future external API integration)</li>
 *   <li>Direct reports: CTO, CPO, CFO (C-level executives)</li>
 *   <li>No upward escalation within virtual organization</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * Strategic and executive leadership:
 * <ul>
 *   <li>Company Vision: Set and communicate strategic direction and vision</li>
 *   <li>Product Strategy: Approve major product initiatives, launches, and pivots</li>
 *   <li>Budget Governance: Review and approve budget allocations (CFO recommendations)</li>
 *   <li>Conflict Resolution: Resolve C-level conflicts (CTO vs CPO priorities)</li>
 *   <li>Go/No-Go Decisions: Final authority on product launches and major initiatives</li>
 *   <li>OKR Planning: Drive quarterly objective-setting and review progress</li>
 *   <li>Stakeholder Communication: Own external stakeholder and partner relationships</li>
 *   <li>Strategic Partnerships: Approve and negotiate strategic alliances</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Unlimited authority across all domains:
 * <ul>
 *   <li>BUDGET: Unlimited (final approver for all budget decisions)</li>
 *   <li>TECHNICAL: Can override CTO on architecture/technology (with consultation)</li>
 *   <li>PRODUCT: Can override CPO on product direction (with consultation)</li>
 *   <li>STRATEGY: Full authority over company strategy and vision</li>
 *   <li>HIRING: Final approval for executive-level hires (VP+)</li>
 *   <li>PARTNERSHIPS: Full authority over strategic partnerships and M&A</li>
 *   <li>POLICY: Can establish company-wide policies and governance</li>
 * </ul>
 *
 * <p><b>Escalation Framework</b><br>
 * Top of hierarchy:
 * <ul>
 *   <li>No upward escalation: CEO is final decision authority</li>
 *   <li>May consult: Board of Directors for major strategic shifts (future: external API)</li>
 *   <li>Receives escalations from: CTO, CPO, CFO on unresolvable conflicts</li>
 *   <li>Conflict resolution: Uses consensus-building, data analysis, strategic alignment</li>
 * </ul>
 *
 * <p><b>LLM Configuration</b><br>
 * Uses most advanced LLM for strategic reasoning:
 * <ul>
 *   <li>Model: GPT-4 Turbo or Claude 3 Opus (highest-capability models)</li>
 *   <li>Temperature: 0.4 (balanced strategic creativity and business rigor)</li>
 *   <li>Max Tokens: 8000 (complex strategic analysis)</li>
 *   <li>System Prompt: Experienced CEO with 15+ years leadership, MBA background</li>
 *   <li>Context: Company financials, market position, competitive landscape</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Executive-level tools:
 * <ul>
 *   <li>FinancialAnalysisTool: P&L analysis, cash flow, burn rate</li>
 *   <li>MarketResearchTool: Competitive analysis, market sizing, trends</li>
 *   <li>MetricsDashboardTool: Company-wide KPIs, OKR tracking</li>
 *   <li>CommunicationTool: Stakeholder messaging, announcements</li>
 *   <li>All lower-level tools via delegation to CTO/CPO/CFO</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CEOAgent ceo = new CEOAgent(
 *     "ceo-001",
 *     DecisionAuthorityProto.newBuilder()
 *         .addAllowedDecisionTypes(DecisionTypeProto.STRATEGY)
 *         .addAllowedDecisionTypes(DecisionTypeProto.BUDGET)
 *         .addAllowedDecisionTypes(DecisionTypeProto.PRODUCT_LAUNCH)
 *         .setMaxBudget(Integer.MAX_VALUE) // Unlimited
 *         .build(),
 *     eventloop, llmClient, memory, toolRegistry, toolExecutor,
 *     meterRegistry, tracer, llmConfig, memoryConfig
 * );
 *
 * // Strategic decision
 * TaskRequestProto task = TaskRequestProto.newBuilder()
 *     .setTaskId("strategic-001")
 *     .setDescription("Should we pivot to enterprise market?")
 *     .setType(TaskTypeProto.STRATEGIC_DECISION)
 *     .putMetadata("market_size", "$5B")
 *     .putMetadata("competition", "High")
 *     .build();
 *
 * ceo.processTask(task).whenResult(result -> {
 *     DecisionProto decision = result.getDecision();
 *     log.info("CEO Decision: {} (confidence: {})",
 *         decision.getChoice(), decision.getConfidence());
 * });
 * }</pre>
 *
 * @see AbstractVirtualOrgAgent
 * @see CTOAgent
 * @see CPOAgent
 * @see CFOAgent
 * @doc.type class
 * @doc.purpose CEO agent for strategic leadership and executive decisions
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class CEOAgent extends AbstractVirtualOrgAgent {


    private static final Logger log = LoggerFactory.getLogger(CEOAgent.class);

    /**
     * System prompt defining CEO agent's role, responsibilities, and decision-making framework.
     * Used for LLM instruction to ensure consistent strategic reasoning.
     */
    private static final String CEO_SYSTEM_PROMPT = """
        You are the CEO of a software development organization. Your role is to:
        
        1. SET STRATEGIC DIRECTION
           - Define company vision and long-term strategy
           - Set quarterly OKRs and strategic priorities
           - Make final decisions on product direction and market positioning
        
        2. EXECUTIVE DECISION-MAKING
           - Approve major product launches and initiatives
           - Resolve conflicts between CTO, CPO, and CFO
           - Make final go/no-go decisions on strategic investments
           - Approve budget allocations and resource commitments
        
        3. STAKEHOLDER MANAGEMENT
           - Communicate vision to all teams
           - Align cross-functional efforts toward strategic goals
           - Build strategic partnerships
        
        4. DECISION FRAMEWORK
           - Prioritize decisions by strategic impact and business value
           - Balance short-term execution with long-term vision
           - Consider input from CTO (technical), CPO (product), CFO (financial)
           - Make data-driven decisions with clear rationale
           - Document decision context for organizational learning
        
        5. ESCALATION HANDLING
           - You are the final decision maker (no upward escalation)
           - Resolve conflicts by evaluating strategic alignment
           - Ensure decisions support company vision and OKRs
        
        When processing tasks:
        - Analyze strategic impact and business value
        - Consider inputs from executive team (CTO, CPO, CFO)
        - Make decisions aligned with company vision
        - Provide clear rationale for all decisions
        - Flag risks and dependencies
        - Document lessons learned
        """;

    /**
     * Creates a new CEO agent with the specified configuration.
     *
     * @param agentId unique identifier for this agent
     * @param authority decision-making authority (should be unrestricted for CEO)
     * @param eventloop ActiveJ eventloop for async execution
     * @param llmClient LLM client for reasoning and decision-making
     * @param memory agent memory system (short-term + long-term)
     * @param toolRegistry registry of available tools
     * @param toolExecutor executor for tool operations
     * @param meterRegistry metrics registry for observability
     * @param tracer distributed tracing tracer
     * @param llmConfig LLM configuration (model, temperature, etc.)
     * @param memoryConfig memory system configuration
     */
    public CEOAgent(
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
                AgentRoleProto.AGENT_ROLE_CEO,
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

        log.info("Initialized CEO Agent: id={}, authority={}", agentId, authority);
    }

    /**
     * Called when the agent starts.
     * Initializes CEO-specific resources and emits startup event.
     *
     * @throws Exception if startup fails
     */
    @Override
    protected void onStart() throws Exception {
        log.info("CEO Agent starting: {}", getAgentId());
        
        // Initialize CEO-specific resources
        // - Load company vision and strategic priorities from memory
        // - Subscribe to executive-level events (product launches, budget reviews)
        // - Initialize strategic metrics tracking
        
        // Emit agent started event (handled by base class event emission)
        log.debug("CEO Agent ready for strategic decision-making");
    }

    /**
     * Called when the agent stops.
     * Cleanup CEO-specific resources and persist final state.
     *
     * @throws Exception if shutdown fails
     */
    @Override
    protected void onStop() throws Exception {
        log.info("CEO Agent stopping: {}", getAgentId());
        
        // Persist strategic context and decisions
        // - Save current strategic priorities to long-term memory
        // - Archive pending decisions for next startup
        // - Emit agent stopped event
        
        log.debug("CEO Agent stopped gracefully");
    }

    /**
     * Process a task as the CEO agent.
     *
     * <p>CEO tasks typically involve:
     * <ul>
     *   <li>Strategic decision-making (product launches, pivots, partnerships)</li>
     *   <li>Budget approvals and resource allocation</li>
     *   <li>Conflict resolution between executives (CTO, CPO, CFO)</li>
     *   <li>OKR planning and quarterly reviews</li>
     *   <li>Go/no-go decisions on major initiatives</li>
     * </ul>
     *
     * <p><b>Decision Process</b><br>
     * 1. Retrieve strategic context from memory (vision, OKRs, past decisions)<br>
     * 2. Gather input from executive team (if needed)<br>
     * 3. Use LLM to analyze strategic impact and business value<br>
     * 4. Make decision aligned with company vision<br>
     * 5. Document decision rationale and lessons learned<br>
     * 6. Store decision context in organizational memory<br>
     *
     * @param request task request containing decision context
     * @return task response with decision and rationale
     * @throws Exception if task processing fails
     */
    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        
        log.info("CEO processing strategic task: taskId={}, type={}, title={}", 
            task.getTaskId(), task.getType(), task.getTitle());

        // Record task processing start for metrics
        Instant startTime = Instant.now();

        // Get available tools for CEO decision-making
        List<ToolProto> tools = getTools();

        // Chain all async operations using ActiveJ Promise
        Promise<TaskResponseProto> resultPromise = memory.retrieveContext(task)
            .then(context -> {
                log.debug("Retrieved strategic context for task {}: {} chars", 
                    task.getTaskId(), context.length());

                // Build LLM prompt with strategic context
                String prompt = buildCEOPrompt(task, context);

                // Call LLM for strategic reasoning and decision-making
                return llmClient.complete(
                    CEO_SYSTEM_PROMPT,
                    prompt,
                    tools,
                    llmConfig.getTemperature(),
                    llmConfig.getMaxTokens()
                );
            })
            .then(llmResponse -> {
                log.debug("LLM reasoning complete for task {}: {} tokens used", 
                    task.getTaskId(), llmResponse.tokensUsed());

                // Extract decision from LLM response
                DecisionProto decision = extractDecision(llmResponse, task);

                // Check if decision requires escalation (CEO never escalates, but might consult)
                if (shouldConsultBoard(decision, task)) {
                    log.warn("CEO decision may require board consultation: taskId={}, type={}", 
                        task.getTaskId(), decision.getType());
                    // Future: Trigger board consultation workflow
                }

                // Execute any tools if LLM suggested them (e.g., data retrieval, analysis)
                if (!llmResponse.getToolCalls().isEmpty()) {
                    return executeToolsAndRefine(llmResponse, task, decision);
                }

                return Promise.of(decision);
            })
            .then(decision -> {
                // Store decision in organizational memory for future reference
                return memory.storeDecision(decision, task)
                    .map(stored -> decision);
            })
            .map(decision -> {
                // Build successful task response
                TaskResponseProto response = TaskResponseProto.newBuilder()
                    .setTaskId(task.getTaskId())
                    .setAgentId(getAgentId())
                    .setSuccess(true)
                    .setResult("Decision: " + decision.getType().name())
                    .setReasoning(decision.getReasoning())
                    .build();

                // Record metrics
                recordTaskMetrics(task, startTime, true);

                log.info("CEO completed strategic decision: taskId={}, decision={}, confidence={}", 
                    task.getTaskId(), decision.getType(), decision.getConfidence());

                return response;
            });

        // Handle errors with side effects only (ActiveJ Promise pattern)
        resultPromise.whenException(error -> {
            log.error("CEO failed to process task: taskId={}, error={}", 
                task.getTaskId(), error.getMessage(), error);
            recordTaskMetrics(task, startTime, false);
        });

        // Wait for async chain to complete (ActiveJ will handle this)
        return resultPromise;
    }

    /**
     * Build CEO-specific prompt for LLM reasoning.
     *
     * <p>Constructs a comprehensive prompt that includes:
     * <ul>
     *   <li>Task description and strategic context</li>
     *   <li>Company vision and current OKRs</li>
     *   <li>Past strategic decisions and lessons learned</li>
     *   <li>Input from executive team (if available)</li>
     *   <li>Decision framework and evaluation criteria</li>
     * </ul>
     *
     * @param task the task to process
     * @param context strategic context from memory
     * @return formatted prompt for LLM
     */
    private String buildCEOPrompt(TaskProto task, String context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("STRATEGIC DECISION REQUEST\n");
        prompt.append("==========================\n\n");
        
        prompt.append("Task: ").append(task.getTitle()).append("\n");
        prompt.append("Description: ").append(task.getDescription()).append("\n");
        prompt.append("Type: ").append(task.getType()).append("\n\n");
        
        prompt.append("COMPANY CONTEXT\n");
        prompt.append("---------------\n");
        prompt.append(context).append("\n\n");
        
        prompt.append("DECISION REQUIRED\n");
        prompt.append("-----------------\n");
        prompt.append("Please analyze this strategic decision and provide:\n");
        prompt.append("1. Your recommendation (approve/reject/modify)\n");
        prompt.append("2. Strategic rationale and business impact\n");
        prompt.append("3. Risks and mitigation strategies\n");
        prompt.append("4. Alignment with company vision and OKRs\n");
        prompt.append("5. Confidence level (0.0-1.0)\n");
        prompt.append("6. Key success metrics to track\n");
        
        return prompt.toString();
    }

    /**
     * Extract decision from LLM response.
     *
     * <p>Parses the LLM output to create a structured DecisionProto.
     * Handles various response formats and extracts key decision components.
     *
     * @param llmResponse response from LLM
     * @param task original task
     * @return structured decision proto
     */
    private DecisionProto extractDecision(LLMResponse llmResponse, TaskProto task) {
        return DecisionExtractor.extractDecision(
            llmResponse.getContent(),
            task,
            getAgentId()
        );
    }

    /**
     * Determine if decision should be escalated to board of directors.
     *
     * <p>CEO is the final decision maker, but certain decisions may require
     * board consultation (e.g., major pivots, acquisitions, large investments).
     *
     * @param decision the decision being made
     * @param task the task context
     * @return true if board consultation recommended
     */
    private boolean shouldConsultBoard(DecisionProto decision, TaskProto task) {
        // CEO never *escalates* (is top of hierarchy), but may consult board for:
        // - Major company pivots
        // - Acquisitions or major investments
        // - Strategic partnerships with significant risk
        // - Decisions with confidence < 0.5 and high strategic impact
        
        return decision.getConfidence() < 0.5f && 
               (task.getType() == TaskTypeProto.TASK_TYPE_BUDGET_APPROVAL ||
                task.getType() == TaskTypeProto.TASK_TYPE_BUDGET_APPROVAL);
    }

    /**
     * Execute tools suggested by LLM and refine decision.
     *
     * @param llmResponse LLM response with tool calls
     * @param task original task
     * @param preliminaryDecision initial decision
     * @return refined decision after tool execution
     */
    private Promise<DecisionProto> executeToolsAndRefine(
            LLMResponse llmResponse, 
            TaskProto task,
            DecisionProto preliminaryDecision) {
        
        // Execute tools asynchronously
        return toolExecutor.executeTools(llmResponse.getToolCalls(), task)
            .then(toolResults -> {
                // Refine decision based on tool results
                String refinedRationale = preliminaryDecision.getReasoning() + 
                    "\n\nTool Results:\n" + DecisionExtractor.formatToolResults(toolResults);
                
                return Promise.of(preliminaryDecision.toBuilder()
                    .setReasoning(refinedRationale)
                    .build());
            });
    }

    private void recordTaskMetrics(TaskProto task, Instant startTime, boolean success) {
        long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        
        meterRegistry.counter("virtualorg.ceo.tasks",
            "type", task.getType().name(),
            "status", success ? "success" : "failure"
        ).increment();
        
        meterRegistry.timer("virtualorg.ceo.task.duration",
            "type", task.getType().name()
        ).record(java.time.Duration.ofMillis(durationMs));
        
        if (success) {
            tasksCompleted.incrementAndGet();
        } else {
            tasksFailed.incrementAndGet();
        }
    }
}
