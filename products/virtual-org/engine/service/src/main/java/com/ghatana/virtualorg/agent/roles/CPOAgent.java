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
 * Chief Product Officer Agent for product strategy and customer success.
 *
 * <p><b>Purpose</b><br>
 * Executive-level autonomous agent responsible for product vision, roadmap, feature
 * prioritization, and customer satisfaction. Balances customer needs with business goals
 * and coordinates product execution across engineering, design, and marketing.
 *
 * <p><b>Architecture Role</b><br>
 * C-level product executive in organizational hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with executive product capabilities</li>
 *   <li>Role: CPO in organizational hierarchy</li>
 *   <li>Reports to: CEO for strategic product alignment</li>
 *   <li>Peers: CTO (product-tech balance), CFO (product economics)</li>
 *   <li>Manages: Product Managers, Product Marketing, Design teams</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * Product strategy and leadership:
 * <ul>
 *   <li>Product Vision: Define and evolve product vision aligned with company strategy</li>
 *   <li>Product Roadmap: Own multi-quarter product roadmap, feature prioritization framework</li>
 *   <li>Launch Decisions: Make go/no-go decisions on feature launches, product releases</li>
 *   <li>Customer Focus: Drive customer-centric development, NPS improvement, user research</li>
 *   <li>Lifecycle Management: Manage product lifecycle (discovery, delivery, growth, sunset)</li>
 *   <li>Product Metrics: Own product KPIs (DAU, retention, activation, revenue per user)</li>
 *   <li>Conflict Resolution: Resolve product-tech tradeoffs, customer needs vs constraints</li>
 *   <li>Product Planning: Lead quarterly product planning, OKR definition, success criteria</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Executive product authority:
 * <ul>
 *   <li>PRODUCT_ROADMAP: Full authority over product roadmap and feature prioritization</li>
 *   <li>BUDGET: Can approve product/marketing budget up to $50K (escalate to CEO/CFO)</li>
 *   <li>FEATURE_LAUNCHES: Can approve feature launches meeting quality gates and KPIs</li>
 *   <li>PRICING: Can approve pricing changes up to ±20% (escalate strategic changes to CEO)</li>
 *   <li>PARTNERSHIPS: Can approve product partnerships (escalate strategic alliances to CEO)</li>
 *   <li>UX_CHANGES: Full authority over user experience, design direction</li>
 * </ul>
 *
 * <p><b>Escalation Framework</b><br>
 * C-level product escalation paths:
 * <ul>
 *   <li>Escalate to CEO: Budget >$50K, strategic product pivots, major pricing (>20%), M&A</li>
 *   <li>Consult with CTO: Product-tech tradeoffs, technical feasibility, platform vs features</li>
 *   <li>Consult with CFO: Pricing strategy, revenue impact, product economics, unit LTV</li>
 *   <li>Receive escalations from: Product Managers (roadmap conflicts), Marketing (positioning)</li>
 * </ul>
 *
 * <p><b>LLM Configuration</b><br>
 * Product-focused strategic reasoning:
 * <ul>
 *   <li>Model: GPT-4 Turbo or Claude 3 Opus (strategic product thinking)</li>
 *   <li>Temperature: 0.4 (balanced product creativity and business discipline)</li>
 *   <li>Max Tokens: 6000 (product strategy docs, roadmap analysis)</li>
 *   <li>System Prompt: Experienced CPO with 12+ years product leadership, MBA background</li>
 *   <li>Context: Product metrics, user feedback, competitive landscape, market trends</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Product strategy tools:
 * <ul>
 *   <li>RoadmapPlanningTool: Multi-quarter roadmap, dependency tracking, timeline modeling</li>
 *   <li>UserResearchTool: User interviews, surveys, usability testing, feedback aggregation</li>
 *   <li>ProductAnalyticsTool: DAU/MAU, retention cohorts, funnel analysis, feature adoption</li>
 *   <li>CompetitiveAnalysisTool: Feature comparison, market positioning, SWOT analysis</li>
 *   <li>PricingStrategyTool: Price optimization, elasticity analysis, willingness-to-pay</li>
 *   <li>A/BTestingTool: Experiment design, statistical significance, impact analysis</li>
 *   <li>CustomerSuccessTool: NPS tracking, support tickets, churn analysis</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CPOAgent cpo = new CPOAgent(
 *     "cpo-001",
 *     DecisionAuthorityProto.newBuilder()
 *         .addAllowedDecisionTypes(DecisionTypeProto.PRODUCT_ROADMAP)
 *         .addAllowedDecisionTypes(DecisionTypeProto.FEATURE_LAUNCHES)
 *         .addAllowedDecisionTypes(DecisionTypeProto.PRICING)
 *         .setMaxBudget(50_000_00) // $50K
 *         .build(),
 *     eventloop, llmClient, memory, toolRegistry, toolExecutor,
 *     meterRegistry, tracer, llmConfig, memoryConfig
 * );
 *
 * // Product roadmap decision
 * TaskRequestProto task = TaskRequestProto.newBuilder()
 *     .setTaskId("roadmap-q2-prioritization")
 *     .setDescription("Prioritize Q2 roadmap: AI features vs platform reliability")
 *     .setType(TaskTypeProto.PRODUCT_DECISION)
 *     .putMetadata("ai_features_revenue", "$500K ARR potential")
 *     .putMetadata("platform_reliability_cost", "15% eng capacity")
 *     .putMetadata("customer_nps", "42")
 *     .build();
 *
 * cpo.processTask(task).whenResult(result -> {
 *     DecisionProto decision = result.getDecision();
 *     log.info("Product decision: {} - Rationale: {}",
 *         decision.getChoice(), decision.getRationale());
 * });
 * }</pre>
 *
 * @see AbstractVirtualOrgAgent
 * @see CEOAgent
 * @see CTOAgent
 * @see CFOAgent
 * @see ProductManagerAgent
 * @doc.type class
 * @doc.purpose CPO agent for product strategy and customer success
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class CPOAgent extends AbstractVirtualOrgAgent {


    private static final Logger log = LoggerFactory.getLogger(CPOAgent.class);

    private static final String CPO_SYSTEM_PROMPT = """
        You are the CPO (Chief Product Officer) of a software development organization. Your role is to:
        
        1. PRODUCT STRATEGY & VISION
           - Define and evolve product vision and strategy
           - Own product roadmap and feature prioritization
           - Align product with market needs and business goals
           - Drive product differentiation and competitive positioning
        
        2. FEATURE PRIORITIZATION & DELIVERY
           - Prioritize features by customer value and business impact
           - Make go/no-go decisions on feature launches
           - Balance customer requests with strategic direction
           - Manage product backlog and sprint planning
        
        3. CUSTOMER SUCCESS
           - Champion customer needs and feedback
           - Own product metrics (adoption, retention, NPS, satisfaction)
           - Drive continuous product improvement
           - Manage customer escalations and critical issues
        
        4. CROSS-FUNCTIONAL COLLABORATION
           - Work with CTO on product/tech tradeoffs
           - Collaborate with CFO on pricing and revenue strategy
           - Partner with Marketing/Sales on go-to-market
           - Coordinate with Engineering on delivery timelines
        
        5. DECISION FRAMEWORK
           - Evaluate decisions by: customer value, business impact, market timing
           - Use data-driven prioritization (user research, analytics, feedback)
           - Balance short-term wins with long-term product vision
           - Consider technical feasibility and resource constraints
        
        6. ESCALATION HANDLING
           - Escalate budget >$50K to CEO
           - Escalate strategic pivots to CEO
           - Consult CTO on product/tech conflicts
           - Resolve PM escalations on feature prioritization
        
        When processing tasks:
        - Analyze customer value and business impact
        - Consider market positioning and competitive landscape
        - Evaluate alignment with product vision and strategy
        - Assess resource requirements and delivery timeline
        - Provide clear product rationale for all decisions
        """;

    public CPOAgent(
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
                AgentRoleProto.AGENT_ROLE_CPO,
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

        log.info("Initialized CPO Agent: id={}, authority={}", agentId, authority);
    }

    @Override
    protected void onStart() throws Exception {
        log.info("CPO Agent starting: {}", getAgentId());
        // Load product vision, roadmap, customer feedback from memory
        log.debug("CPO Agent ready for product leadership");
    }

    @Override
    protected void onStop() throws Exception {
        log.info("CPO Agent stopping: {}", getAgentId());
        // Persist product decisions and roadmap state
        log.debug("CPO Agent stopped gracefully");
    }

    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        
        log.info("CPO processing product task: taskId={}, type={}, title={}", 
            task.getTaskId(), task.getType(), task.getTitle());

        Instant startTime = Instant.now();
        List<ToolProto> tools = getTools();

        Promise<TaskResponseProto> resultPromise = memory.retrieveContext(task)
            .then(context -> {
                String prompt = buildCPOPrompt(task, context);
                return llmClient.complete(CPO_SYSTEM_PROMPT, prompt, tools, 
                    llmConfig.getTemperature(), llmConfig.getMaxTokens());
            })
            .then(llmResponse -> {
                DecisionProto decision = extractDecision(llmResponse, task);

                if (shouldEscalateToCEO(decision, task)) {
                    log.info("CPO decision requires CEO escalation: taskId={}", task.getTaskId());
                    return Promise.of(createEscalationResponse(task, decision, "CEO"));
                }

                if (!llmResponse.getToolCalls().isEmpty()) {
                    return executeToolsAndRefine(llmResponse, task, decision);
                }

                return Promise.of(decision);
            })
            .then(decision -> {
                if (decision.getType() == DecisionTypeProto.DECISION_TYPE_ESCALATED) {
                    return Promise.of(decision);
                }
                
                return memory.storeDecision(decision, task).map(stored -> decision);
            })
            .map(decision -> {
                TaskResponseProto response = TaskResponseProto.newBuilder()
                    .setTaskId(task.getTaskId())
                    .setAgentId(getAgentId())
                    .setSuccess(decision.getType() != DecisionTypeProto.DECISION_TYPE_ESCALATED)
                    .setResult("Decision: " + decision.getType().name())
                    .setReasoning(decision.getReasoning())
                    .build();

                boolean success = decision.getType() != DecisionTypeProto.DECISION_TYPE_ESCALATED;
                recordTaskMetrics(task, startTime, success);

                return response;
            });

        // Handle errors with whenException (side effects only)
        resultPromise.whenException(error -> {
            log.error("CPO failed to process task: {}", error.getMessage(), error);
            recordTaskMetrics(task, startTime, false);
        });

        return resultPromise;
    }

    private String buildCPOPrompt(TaskProto task, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("PRODUCT DECISION REQUEST\n=======================\n\n");
        prompt.append("Task: ").append(task.getTitle()).append("\n");
        prompt.append("Description: ").append(task.getDescription()).append("\n\n");
        
        prompt.append("PRODUCT CONTEXT\n----------------\n");
        prompt.append(context).append("\n\n");
        
        prompt.append("DECISION REQUIRED\n------------------\n");
        prompt.append("Analyze and provide:\n");
        prompt.append("1. Recommendation (approve/reject/modify)\n");
        prompt.append("2. Customer value and business impact\n");
        prompt.append("3. Market positioning and competitive advantage\n");
        prompt.append("4. Resource requirements and timeline\n");
        prompt.append("5. Risks and mitigation strategies\n");
        prompt.append("6. Success metrics\n");
        prompt.append("7. Confidence level (0.0-1.0)\n");
        
        return prompt.toString();
    }

    private DecisionProto extractDecision(LLMResponse llmResponse, TaskProto task) {
        return DecisionExtractor.extractDecision(
            llmResponse.getContent(),
            task,
            getAgentId()
        );
    }

    private boolean shouldEscalateToCEO(DecisionProto decision, TaskProto task) {
        // Escalate budget >$50K
        if (task.getType() == TaskTypeProto.TASK_TYPE_BUDGET_APPROVAL) {
            return extractBudgetAmount(task.getDescription()) > getBudgetLimitUsd();
        }
        
        // Escalate strategic product pivots
        String desc = task.getDescription().toLowerCase();
        if (desc.contains("pivot") || desc.contains("strategic shift")) {
            return true;
        }
        
        // Escalate low confidence on critical decisions
        return decision.getConfidence() < 0.4f && 
               task.getType() == TaskTypeProto.TASK_TYPE_DEPLOYMENT;
    }

    private DecisionProto createEscalationResponse(TaskProto task, DecisionProto decision, String escalateTo) {
        return decision.toBuilder()
            .setType(DecisionTypeProto.DECISION_TYPE_ESCALATED)
            .setReasoning(decision.getReasoning() + "\n\nESCALATED to " + escalateTo)
            .build();
    }

    private Promise<DecisionProto> executeToolsAndRefine(
            LLMResponse llmResponse, TaskProto task, DecisionProto decision) {
        return toolExecutor.executeTools(llmResponse.getToolCalls(), task)
            .map(toolResults -> decision.toBuilder()
                .setReasoning(decision.getReasoning() + "\n\nTool Results:\n" + 
                    DecisionExtractor.formatToolResults(toolResults))
                .build());
    }

    private int extractBudgetAmount(String description) {
        try {
            int dollarIndex = description.indexOf('$');
            if (dollarIndex >= 0) {
                String numPart = description.substring(dollarIndex + 1).replaceAll("[^0-9]", "");
                if (!numPart.isEmpty()) return Integer.parseInt(numPart);
            }
        } catch (Exception e) {
            log.warn("Failed to extract budget: {}", e.getMessage());
        }
        return 0;
    }

    private void recordTaskMetrics(TaskProto task, Instant startTime, boolean success) {
        long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        
        meterRegistry.counter("virtualorg.cpo.tasks",
            "type", task.getType().name(),
            "status", success ? "success" : "failure"
        ).increment();
        
        meterRegistry.timer("virtualorg.cpo.task.duration",
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
        return 100_000.0; // Default CPO budget limit
    }
}
