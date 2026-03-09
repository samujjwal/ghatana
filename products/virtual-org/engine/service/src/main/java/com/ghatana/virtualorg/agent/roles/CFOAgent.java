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
 * Chief Financial Officer Agent for financial strategy and fiscal leadership.
 *
 * <p><b>Purpose</b><br>
 * Executive-level autonomous agent responsible for financial strategy, budget management,
 * cost optimization, revenue planning, and financial compliance. Provides financial
 * leadership and advises CEO on investments, pricing, and fiscal health.
 *
 * <p><b>Architecture Role</b><br>
 * C-level financial executive in organizational hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with executive financial capabilities</li>
 *   <li>Role: CFO in organizational hierarchy</li>
 *   <li>Reports to: CEO for strategic alignment and major financial decisions</li>
 *   <li>Peers: CTO (technical investments), CPO (product pricing)</li>
 *   <li>Influences: All department leads (budget allocation, cost control)</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * Financial leadership and governance:
 * <ul>
 *   <li>Financial Planning: Define and manage company budget, annual/quarterly planning</li>
 *   <li>Budget Management: Approve department budgets, track expenditures, variance analysis</li>
 *   <li>Cost Optimization: Drive cost reduction initiatives, ROI analysis, unit economics</li>
 *   <li>Revenue Strategy: Own revenue forecasting, pricing strategy, monetization models</li>
 *   <li>Financial Compliance: Ensure GAAP compliance, audit readiness, financial reporting</li>
 *   <li>Cash Flow Management: Manage runway, cash reserves, working capital</li>
 *   <li>Investment Evaluation: Evaluate capex/opex investments, calculate IRR/NPV</li>
 *   <li>Financial Guidance: Advise CEO/executives on financial implications of strategic decisions</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Executive financial authority:
 * <ul>
 *   <li>BUDGET: Can approve budgets up to $200K (escalate to CEO for larger amounts)</li>
 *   <li>COST_OPTIMIZATION: Full authority over cost reduction initiatives and efficiency programs</li>
 *   <li>PRICING: Can approve pricing changes up to ±15% (escalate strategic changes to CEO)</li>
 *   <li>INVESTMENTS: Can approve infrastructure/tooling investments <$100K</li>
 *   <li>FINANCIAL_REPORTING: Full authority over financial metrics, dashboards, board reporting</li>
 *   <li>VENDOR_CONTRACTS: Can approve vendor contracts <$50K annually</li>
 * </ul>
 *
 * <p><b>Escalation Framework</b><br>
 * C-level financial escalation paths:
 * <ul>
 *   <li>Escalate to CEO: Budget >$200K, major investments >$100K, strategic pricing (>15%), fundraising</li>
 *   <li>Consult with CTO: Infrastructure cost optimization, technical ROI, build vs buy</li>
 *   <li>Consult with CPO: Product pricing strategy, revenue forecasts, customer LTV</li>
 *   <li>Receive escalations from: Department Leads (budget requests), DevOps (infra costs), PM (feature costs)</li>
 * </ul>
 *
 * <p><b>LLM Configuration</b><br>
 * Financial reasoning and analysis:
 * <ul>
 *   <li>Model: GPT-4 Turbo or Claude 3 Opus (complex financial modeling)</li>
 *   <li>Temperature: 0.2 (financial precision, accurate calculations)</li>
 *   <li>Max Tokens: 6000 (detailed financial analysis, budget reports)</li>
 *   <li>System Prompt: Experienced CFO with 12+ years finance, MBA/CPA background</li>
 *   <li>Context: P&L statements, balance sheet, cash flow, burn rate, revenue metrics</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Financial analysis tools:
 * <ul>
 *   <li>FinancialModelingTool: P&L projections, scenario analysis, sensitivity analysis</li>
 *   <li>BudgetManagementTool: Budget tracking, variance analysis, forecasting</li>
 *   <li>CostAnalysisTool: Unit economics, cost breakdown, margin analysis</li>
 *   <li>PricingAnalysisTool: Price elasticity, competitive pricing, revenue optimization</li>
 *   <li>CashFlowTool: Runway calculation, burn rate, cash reserves monitoring</li>
 *   <li>InvestmentAnalysisTool: ROI, IRR, NPV, payback period calculations</li>
 *   <li>ComplianceTool: GAAP compliance checks, audit trail, financial reporting</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CFOAgent cfo = new CFOAgent(
 *     "cfo-001",
 *     DecisionAuthorityProto.newBuilder()
 *         .addAllowedDecisionTypes(DecisionTypeProto.BUDGET)
 *         .addAllowedDecisionTypes(DecisionTypeProto.COST_OPTIMIZATION)
 *         .addAllowedDecisionTypes(DecisionTypeProto.PRICING)
 *         .setMaxBudget(200_000_00) // $200K
 *         .build(),
 *     eventloop, llmClient, memory, toolRegistry, toolExecutor,
 *     meterRegistry, tracer, llmConfig, memoryConfig
 * );
 *
 * // Budget approval task
 * TaskRequestProto task = TaskRequestProto.newBuilder()
 *     .setTaskId("budget-approval-q1-infra")
 *     .setDescription("Approve Q1 infrastructure budget request of $150K")
 *     .setType(TaskTypeProto.BUDGET_DECISION)
 *     .putMetadata("amount", "150000")
 *     .putMetadata("category", "infrastructure")
 *     .putMetadata("roi_projection", "20%")
 *     .build();
 *
 * cfo.processTask(task).whenResult(result -> {
 *     DecisionProto decision = result.getDecision();
 *     log.info("Budget decision: {} - Rationale: {}",
 *         decision.getChoice(), decision.getRationale());
 * });
 * }</pre>
 *
 * @see AbstractVirtualOrgAgent
 * @see CEOAgent
 * @see CTOAgent
 * @see CPOAgent
 * @doc.type class
 * @doc.purpose CFO agent for financial strategy and fiscal leadership
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class CFOAgent extends AbstractVirtualOrgAgent {


    private static final Logger log = LoggerFactory.getLogger(CFOAgent.class);

    private static final String CFO_SYSTEM_PROMPT = """
        You are the CFO (Chief Financial Officer) of a software development organization. Your role is to:
        
        1. FINANCIAL STRATEGY & PLANNING
           - Define company financial strategy and targets
           - Own annual and quarterly budget planning
           - Forecast revenue, costs, and cash flow
           - Set financial KPIs and metrics
        
        2. BUDGET MANAGEMENT
           - Approve budget requests and allocations
           - Monitor spending against budget
           - Drive cost optimization initiatives
           - Ensure efficient resource allocation
        
        3. REVENUE & PRICING
           - Own pricing strategy and revenue planning
           - Analyze pricing models and profitability
           - Optimize revenue per customer
           - Manage revenue forecasting and reporting
        
        4. FINANCIAL ANALYSIS & ROI
           - Evaluate investment opportunities and ROI
           - Analyze financial impact of initiatives
           - Provide cost-benefit analysis for decisions
           - Track and report financial performance
        
        5. COMPLIANCE & RISK
           - Ensure financial compliance and controls
           - Manage financial risk and cash flow
           - Own financial reporting and audits
           - Maintain financial transparency
        
        6. DECISION FRAMEWORK
           - Evaluate decisions by: ROI, cost-effectiveness, financial risk
           - Use data-driven financial analysis
           - Balance short-term costs with long-term value
           - Consider cash flow impact and runway
        
        7. ESCALATION HANDLING
           - Escalate budget >$200K to CEO
           - Escalate strategic pricing changes to CEO
           - Consult CTO on infrastructure costs
           - Consult CPO on product pricing
        
        When processing tasks:
        - Analyze financial impact and ROI
        - Consider budget constraints and cash flow
        - Evaluate cost-benefit tradeoffs
        - Assess financial risk and mitigation
        - Provide clear financial rationale
        """;

    public CFOAgent(
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
                AgentRoleProto.AGENT_ROLE_CFO,
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

        log.info("Initialized CFO Agent: id={}, authority={}", agentId, authority);
    }

    @Override
    protected void onStart() throws Exception {
        log.info("CFO Agent starting: {}", getAgentId());
        // Load financial data, budgets, forecasts from memory
        log.debug("CFO Agent ready for financial leadership");
    }

    @Override
    protected void onStop() throws Exception {
        log.info("CFO Agent stopping: {}", getAgentId());
        // Persist financial decisions and budget state
        log.debug("CFO Agent stopped gracefully");
    }

    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        
        log.info("CFO processing financial task: taskId={}, type={}, title={}", 
            task.getTaskId(), task.getType(), task.getTitle());

        Instant startTime = Instant.now();
        List<ToolProto> tools = getTools();

        Promise<TaskResponseProto> resultPromise = memory.retrieveContext(task)
            .then(context -> {
                String prompt = buildCFOPrompt(task, context);
                return llmClient.complete(CFO_SYSTEM_PROMPT, prompt, tools, 
                    llmConfig.getTemperature(), llmConfig.getMaxTokens());
            })
            .then(llmResponse -> {
                DecisionProto decision = extractDecision(llmResponse, task);

                if (shouldEscalateToCEO(decision, task)) {
                    log.info("CFO decision requires CEO escalation: taskId={}", task.getTaskId());
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
            log.error("CFO failed to process task: {}", error.getMessage(), error);
            recordTaskMetrics(task, startTime, false);
        });

        return resultPromise;
    }

    private String buildCFOPrompt(TaskProto task, String context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("FINANCIAL DECISION REQUEST\n=========================\n\n");
        prompt.append("Task: ").append(task.getTitle()).append("\n");
        prompt.append("Description: ").append(task.getDescription()).append("\n\n");
        
        prompt.append("FINANCIAL CONTEXT\n------------------\n");
        prompt.append(context).append("\n\n");
        
        prompt.append("DECISION REQUIRED\n------------------\n");
        prompt.append("Analyze and provide:\n");
        prompt.append("1. Recommendation (approve/reject/modify)\n");
        prompt.append("2. Financial impact and ROI analysis\n");
        prompt.append("3. Budget implications and cash flow impact\n");
        prompt.append("4. Cost-benefit analysis\n");
        prompt.append("5. Financial risks and mitigation\n");
        prompt.append("6. Alternative options considered\n");
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
        // Escalate budget >$200K
        if (task.getType() == TaskTypeProto.TASK_TYPE_BUDGET_APPROVAL) {
            return extractBudgetAmount(task.getDescription()) > getBudgetLimitUsd();
        }
        
        // Escalate major pricing strategy changes
        String desc = task.getDescription().toLowerCase();
        if (desc.contains("pricing strategy") || desc.contains("major investment")) {
            return true;
        }
        
        // Escalate low confidence on critical financial decisions
        return decision.getConfidence() < 0.5f && isCriticalFinancialDecision(task);
    }

    private boolean isCriticalFinancialDecision(TaskProto task) {
        return task.getType() == TaskTypeProto.TASK_TYPE_BUDGET_APPROVAL ||
               task.getDescription().toLowerCase().contains("investment");
    }

    private DecisionProto createEscalationResponse(TaskProto task, DecisionProto decision, String escalateTo) {
        return decision.toBuilder()
            .setType(DecisionTypeProto.DECISION_TYPE_ESCALATED)
            .setReasoning(decision.getReasoning() + 
                "\n\nESCALATED to " + escalateTo + ": Budget exceeds CFO authority ($200K)")
            .build();
    }

    private Promise<DecisionProto> executeToolsAndRefine(
            LLMResponse llmResponse, TaskProto task, DecisionProto decision) {
        return toolExecutor.executeTools(llmResponse.getToolCalls(), task)
            .map(toolResults -> decision.toBuilder()
                .setReasoning(decision.getReasoning() + "\n\nFinancial Analysis:\n" + 
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
        
        meterRegistry.counter("virtualorg.cfo.tasks",
            "type", task.getType().name(),
            "status", success ? "success" : "failure"
        ).increment();
        
        meterRegistry.timer("virtualorg.cfo.task.duration",
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
        return 200_000.0; // Default CFO budget limit
    }
}
