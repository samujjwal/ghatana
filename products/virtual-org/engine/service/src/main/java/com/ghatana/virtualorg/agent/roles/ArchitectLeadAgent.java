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
 * Architect Lead Agent for system architecture and technical design leadership.
 *
 * <p><b>Purpose</b><br>
 * Senior technical leadership agent responsible for system architecture, design reviews,
 * technical standards enforcement, and architectural guidance. Reports to CTO and provides
 * authoritative architectural direction to engineering teams.
 *
 * <p><b>Architecture Role</b><br>
 * Senior technical leadership in organizational hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with architectural expertise</li>
 *   <li>Role: ARCHITECT_LEAD in organizational hierarchy</li>
 *   <li>Reports to: CTO for strategic alignment and major architecture decisions</li>
 *   <li>Influences: Team Leads, Senior Engineers (architectural guidance)</li>
 *   <li>Collaborates with: DevOps Lead (infrastructure), QA Lead (testing strategy)</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * Architectural leadership and governance:
 * <ul>
 *   <li>System Architecture: Design and review system architecture, module boundaries</li>
 *   <li>Technical Standards: Define and enforce coding standards, design patterns, best practices</li>
 *   <li>Architecture Reviews: Conduct design reviews, approve architecture documents (ADRs)</li>
 *   <li>Technical Guidance: Mentor engineers on architectural decisions, pattern selection</li>
 *   <li>Technical Debt: Assess and prioritize tech debt reduction strategies</li>
 *   <li>Technology Evaluation: Evaluate new frameworks, libraries, architectural approaches</li>
 *   <li>Non-Functional Requirements: Ensure scalability, maintainability, performance, security</li>
 *   <li>Cross-Cutting Concerns: Own logging, monitoring, error handling, caching patterns</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Senior architectural authority:
 * <ul>
 *   <li>ARCHITECTURE: Can approve architecture changes up to 10K LOC (escalate to CTO)</li>
 *   <li>DESIGN: Full authority over design patterns, technical standards, code organization</li>
 *   <li>TECHNICAL_DEBT: Can prioritize tech debt work up to 2 weeks effort</li>
 *   <li>CODE_REVIEW: Can mandate architecture-level code changes, refactoring</li>
 *   <li>TECHNOLOGY: Can approve new libraries/frameworks within budget (escalate to CTO)</li>
 * </ul>
 *
 * <p><b>Escalation Framework</b><br>
 * Senior technical escalation paths:
 * <ul>
 *   <li>Escalate to CTO: Major architecture changes (>10K LOC), cross-system impacts, budget >$10K</li>
 *   <li>Consult with DevOps Lead: Infrastructure implications, deployment strategies</li>
 *   <li>Consult with QA Lead: Testing strategies, quality gates</li>
 *   <li>Receive escalations from: Senior Engineers (complex design), Team Leads (architecture questions)</li>
 * </ul>
 *
 * <p><b>LLM Configuration</b><br>
 * Expert architectural reasoning:
 * <ul>
 *   <li>Model: GPT-4 or Claude 3 Opus (high-capability architectural reasoning)</li>
 *   <li>Temperature: 0.2 (architectural precision and consistency)</li>
 *   <li>Max Tokens: 5000 (complex architectural analysis, ADRs)</li>
 *   <li>System Prompt: Principal Architect with 10+ years architecture experience</li>
 *   <li>Context: Current architecture diagrams, ADRs, tech stack, NFRs</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Architectural analysis tools:
 * <ul>
 *   <li>ArchitectureDiagramTool: Generate/analyze C4 diagrams, dependency graphs</li>
 *   <li>StaticAnalysisTool: Code complexity, coupling metrics, architecture violations</li>
 *   <li>PerformanceAnalysisTool: Latency profiling, resource usage, bottleneck detection</li>
 *   <li>DependencyAnalysisTool: Module dependencies, circular dependency detection</li>
 *   <li>CodeReviewTool: Architecture-level code review, pattern enforcement</li>
 *   <li>DocumentationTool: Generate ADRs, architecture documentation</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ArchitectLeadAgent architect = new ArchitectLeadAgent(
 *     "architect-lead-001",
 *     DecisionAuthorityProto.newBuilder()
 *         .addAllowedDecisionTypes(DecisionTypeProto.ARCHITECTURE)
 *         .addAllowedDecisionTypes(DecisionTypeProto.DESIGN)
 *         .addAllowedDecisionTypes(DecisionTypeProto.TECHNICAL_DEBT)
 *         .setMaxComplexity(10_000) // 10K LOC
 *         .build(),
 *     eventloop, llmClient, memory, toolRegistry, toolExecutor,
 *     meterRegistry, tracer, llmConfig, memoryConfig
 * );
 *
 * // Architecture review task
 * TaskRequestProto task = TaskRequestProto.newBuilder()
 *     .setTaskId("arch-review-42")
 *     .setDescription("Review microservices split proposal for user service")
 *     .setType(TaskTypeProto.ARCHITECTURE_REVIEW)
 *     .putMetadata("module_size", "5000")
 *     .putMetadata("dependencies", "3")
 *     .build();
 *
 * architect.processTask(task).whenResult(result -> {
 *     DecisionProto decision = result.getDecision();
 *     log.info("Architecture review: {} - {}",
 *         decision.getChoice(), decision.getRationale());
 * });
 * }</pre>
 *
 * @see AbstractVirtualOrgAgent
 * @see CTOAgent
 * @see TeamLeadAgent
 * @doc.type class
 * @doc.purpose Architect Lead agent for system architecture and design
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class ArchitectLeadAgent extends AbstractVirtualOrgAgent {


    private static final Logger log = LoggerFactory.getLogger(ArchitectLeadAgent.class);

    private static final String ARCHITECT_SYSTEM_PROMPT = """
        You are an Architect Lead in a software development organization. Your role is to:
        
        1. SYSTEM ARCHITECTURE & DESIGN
           - Design scalable, maintainable system architecture
           - Review and approve technical design documents
           - Define architectural patterns and best practices
           - Ensure architecture aligns with business requirements
        
        2. TECHNICAL STANDARDS
           - Define and enforce coding standards and conventions
           - Set architectural guardrails and quality gates
           - Promote clean architecture and SOLID principles
           - Drive consistency across codebases
        
        3. TECHNICAL LEADERSHIP
           - Guide engineers on architectural decisions
           - Conduct architecture reviews and design critiques
           - Mentor engineers on design thinking
           - Resolve technical disagreements
        
        4. TECHNICAL DEBT MANAGEMENT
           - Assess technical debt and impact
           - Prioritize refactoring and improvements
           - Balance new features with platform health
           - Track and report technical debt metrics
        
        5. DECISION FRAMEWORK
           - Evaluate by: scalability, maintainability, performance, cost
           - Consider long-term architectural health
           - Use proven patterns and best practices
           - Make data-driven decisions with metrics
        
        When processing tasks:
        - Analyze architectural impact and trade-offs
        - Consider scalability and performance implications
        - Evaluate maintainability and technical debt
        - Provide clear architectural rationale
        - Escalate major changes to CTO
        """;

    public ArchitectLeadAgent(
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

        super(agentId, AgentRoleProto.AGENT_ROLE_ARCHITECT, authority, eventloop,
                llmClient, memory, toolRegistry, toolExecutor, meterRegistry, tracer,
                llmConfig, memoryConfig);

        log.info("Initialized Architect Lead Agent: id={}", agentId);
    }

    @Override
    protected void onStart() throws Exception {
        log.info("Architect Lead Agent starting: {}", getAgentId());
    }

    @Override
    protected void onStop() throws Exception {
        log.info("Architect Lead Agent stopping: {}", getAgentId());
    }

    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        log.info("Architect processing task: {}", task.getTitle());

        Instant startTime = Instant.now();

        Promise<TaskResponseProto> resultPromise = memory.retrieveContext(task)
            .then(context -> {
                String prompt = buildArchitectPrompt(task, context);
                return llmClient.complete(ARCHITECT_SYSTEM_PROMPT, prompt, getTools(),
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
            log.error("Architect failed to process task: {}", error.getMessage());
            recordMetrics(task, startTime, false);
        });

        return resultPromise;
    }

    private String buildArchitectPrompt(TaskProto task, String context) {
        return "ARCHITECTURE DECISION\n" +
               "Task: " + task.getTitle() + "\n" +
               "Description: " + task.getDescription() + "\n\n" +
               "Context: " + context + "\n\n" +
               "Provide architectural analysis and recommendation.";
    }

    private DecisionProto extractDecision(LLMResponse llmResponse, TaskProto task) {
        return DecisionExtractor.extractDecision(
            llmResponse.getContent(),
            task,
            getAgentId()
        );
    }

    private void recordMetrics(TaskProto task, Instant startTime, boolean success) {
        meterRegistry.counter("virtualorg.architect.tasks",
            "type", task.getType().name(),
            "status", success ? "success" : "failure"
        ).increment();
    }
}
