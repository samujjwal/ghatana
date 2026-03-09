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
 * QA Lead Agent for quality assurance strategy and testing leadership.
 *
 * <p><b>Purpose</b><br>
 * Senior quality leadership agent responsible for QA strategy, test planning,
 * quality gates, automation, and ensuring software quality across all releases.
 * Guardian of product quality and customer satisfaction.
 *
 * <p><b>Architecture Role</b><br>
 * Quality leadership in organizational hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with quality assurance expertise</li>
 *   <li>Role: QA_LEAD in organizational hierarchy</li>
 *   <li>Reports to: CTO for technical quality strategy</li>
 *   <li>Collaborates with: Team Leads (test execution), Product Manager (requirements), DevOps (deployment)</li>
 *   <li>Manages: QA engineers, test automation infrastructure</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * Quality strategy and execution:
 * <ul>
 *   <li>QA Strategy: Define quality assurance strategy, testing standards, best practices</li>
 *   <li>Test Planning: Own test planning, coverage requirements, testing pyramids</li>
 *   <li>Quality Gates: Review and approve release quality gates (code coverage, defect density)</li>
 *   <li>Team Management: Manage QA engineers, coordinate testing efforts across features</li>
 *   <li>Automation: Drive test automation strategy, framework selection, CI/CD integration</li>
 *   <li>Metrics: Track quality metrics (defect trends, test coverage, escaped defects)</li>
 *   <li>Quality Improvements: Coordinate with engineering on quality initiatives</li>
 *   <li>Release Readiness: Validate release readiness, sign off on production deployments</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Quality governance authority:
 * <ul>
 *   <li>QUALITY_STANDARDS: Full authority over quality standards and testing requirements</li>
 *   <li>RELEASE_BLOCKING: Can block releases that fail quality criteria (escalate to CTO if disputed)</li>
 *   <li>TEST_STRATEGY: Full authority over testing approach (unit, integration, E2E, performance)</li>
 *   <li>DEFECT_PRIORITIZATION: Can prioritize critical/high-severity bug fixes</li>
 *   <li>AUTOMATION: Can mandate test automation for critical paths</li>
 * </ul>
 *
 * <p><b>Escalation Framework</b><br>
 * Quality-focused escalation paths:
 * <ul>
 *   <li>Escalate to CTO: Quality/speed conflicts, major quality issues blocking business goals</li>
 *   <li>Escalate to CPO: Product quality vs feature velocity tradeoffs</li>
 *   <li>Collaborate with Team Leads: Test execution coordination, quality improvements</li>
 *   <li>Receive escalations from: QA Engineers on critical defects, test infrastructure issues</li>
 * </ul>
 *
 * <p><b>LLM Configuration</b><br>
 * Quality-focused reasoning:
 * <ul>
 *   <li>Model: GPT-4 or Claude 3 Sonnet (analytical quality assessment)</li>
 *   <li>Temperature: 0.2 (precision in quality analysis and defect assessment)</li>
 *   <li>Max Tokens: 4000 (detailed test plans, quality reports)</li>
 *   <li>System Prompt: Senior QA Lead with 8+ years testing, quality engineering</li>
 *   <li>Context: Test coverage metrics, defect trends, release history, quality SLAs</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Quality assurance tools:
 * <ul>
 *   <li>TestManagementTool: Test case management, execution tracking, coverage reports</li>
 *   <li>DefectTrackingTool: Bug lifecycle, severity/priority, defect trends</li>
 *   <li>CodeCoverageTool: Unit test coverage, branch coverage, mutation testing</li>
 *   <li>PerformanceTestingTool: Load testing, stress testing, latency profiling</li>
 *   <li>AutomationFrameworkTool: Selenium, Cypress, Playwright, API testing</li>
 *   <li>QualityMetricsTool: Defect density, escaped defects, test effectiveness</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * QALeadAgent qaLead = new QALeadAgent(
 *     "qa-lead-001",
 *     DecisionAuthorityProto.newBuilder()
 *         .addAllowedDecisionTypes(DecisionTypeProto.QUALITY_STANDARDS)
 *         .addAllowedDecisionTypes(DecisionTypeProto.RELEASE_BLOCKING)
 *         .addAllowedDecisionTypes(DecisionTypeProto.TEST_STRATEGY)
 *         .build(),
 *     eventloop, llmClient, memory, toolRegistry, toolExecutor,
 *     meterRegistry, tracer, llmConfig, memoryConfig
 * );
 *
 * // Release quality gate review
 * TaskRequestProto task = TaskRequestProto.newBuilder()
 *     .setTaskId("release-review-v2.5")
 *     .setDescription("Review quality gates for v2.5 release")
 *     .setType(TaskTypeProto.QUALITY_REVIEW)
 *     .putMetadata("code_coverage", "87%")
 *     .putMetadata("critical_defects", "0")
 *     .putMetadata("high_defects", "2")
 *     .build();
 *
 * qaLead.processTask(task).whenResult(result -> {
 *     DecisionProto decision = result.getDecision();
 *     if (decision.getChoice().equals("APPROVED")) {
 *         log.info("Release approved: {}", decision.getRationale());
 *     } else {
 *         log.warn("Release blocked: {}", decision.getRationale());
 *     }
 * });
 * }</pre>
 *
 * @see AbstractVirtualOrgAgent
 * @see CTOAgent
 * @see TeamLeadAgent
 * @doc.type class
 * @doc.purpose QA Lead agent for quality strategy and testing
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class QALeadAgent extends AbstractVirtualOrgAgent {


    private static final Logger log = LoggerFactory.getLogger(QALeadAgent.class);

    private static final String QA_SYSTEM_PROMPT = """
        You are a QA Lead in a software development organization. Your role is to:
        
        1. QUALITY STRATEGY
           - Define quality standards and testing requirements
           - Set quality gates for releases
           - Drive shift-left testing culture
           - Ensure comprehensive test coverage
        
        2. TEST PLANNING & EXECUTION
           - Plan test strategies for features and releases
           - Coordinate testing efforts across teams
           - Review test plans and test cases
           - Ensure critical paths are tested
        
        3. AUTOMATION & TOOLING
           - Drive test automation adoption
           - Build and maintain test infrastructure
           - Implement CI/CD quality gates
           - Optimize test execution speed
        
        4. QUALITY METRICS & IMPROVEMENT
           - Track quality metrics (defect density, test coverage, escape rate)
           - Analyze quality trends and root causes
           - Drive continuous quality improvement
           - Report on quality status to leadership
        
        5. DECISION FRAMEWORK
           - Evaluate by: quality risk, test coverage, defect severity
           - Balance comprehensive testing with delivery speed
           - Use data-driven quality assessment
           - Protect customer experience
        
        When processing tasks:
        - Analyze quality risk and impact
        - Consider test coverage and confidence
        - Evaluate severity and customer impact
        - Provide clear quality rationale
        """;

    public QALeadAgent(
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

        super(agentId, AgentRoleProto.AGENT_ROLE_QA_LEAD, authority, eventloop,
                llmClient, memory, toolRegistry, toolExecutor, meterRegistry, tracer,
                llmConfig, memoryConfig);

        log.info("Initialized QA Lead Agent: id={}", agentId);
    }

    @Override
    protected void onStart() throws Exception {
        log.info("QA Lead Agent starting: {}", getAgentId());
    }

    @Override
    protected void onStop() throws Exception {
        log.info("QA Lead Agent stopping: {}", getAgentId());
    }

    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        log.info("QA Lead processing task: {}", task.getTitle());

        Instant startTime = Instant.now();

        Promise<TaskResponseProto> resultPromise = memory.retrieveContext(task)
            .then(context -> {
                String prompt = buildQAPrompt(task, context);
                return llmClient.complete(QA_SYSTEM_PROMPT, prompt, getTools(),
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
            log.error("QA Lead failed to process task: {}", error.getMessage());
            recordMetrics(task, startTime, false);
        });

        return resultPromise;
    }

    private String buildQAPrompt(TaskProto task, String context) {
        return "QA DECISION\n" +
               "Task: " + task.getTitle() + "\n" +
               "Description: " + task.getDescription() + "\n\n" +
               "Context: " + context + "\n\n" +
               "Provide quality assessment and recommendation.";
    }

    private DecisionProto extractDecision(LLMResponse llmResponse, TaskProto task) {
        return DecisionExtractor.extractDecision(
            llmResponse.getContent(),
            task,
            getAgentId()
        );
    }

    private void recordMetrics(TaskProto task, Instant startTime, boolean success) {
        meterRegistry.counter("virtualorg.qalead.tasks",
            "type", task.getType().name(),
            "status", success ? "success" : "failure"
        ).increment();
    }
}
