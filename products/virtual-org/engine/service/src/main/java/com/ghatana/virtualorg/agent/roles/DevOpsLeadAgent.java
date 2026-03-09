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
 * DevOps Lead Agent for infrastructure and deployment automation leadership.
 *
 * <p><b>Purpose</b><br>
 * Senior infrastructure leadership agent responsible for cloud infrastructure,
 * CI/CD pipelines, deployment automation, operational excellence, and site reliability.
 * Enables engineering teams with reliable, automated, and cost-effective infrastructure.
 *
 * <p><b>Architecture Role</b><br>
 * Infrastructure leadership in organizational hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with DevOps and SRE expertise</li>
 *   <li>Role: DEVOPS_LEAD in organizational hierarchy</li>
 *   <li>Reports to: CTO for infrastructure strategy and technical alignment</li>
 *   <li>Collaborates with: Architect Lead (architecture), Team Leads (deployments), QA Lead (testing)</li>
 *   <li>Manages: DevOps engineers, infrastructure budget, cloud resources</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * Infrastructure and operational leadership:
 * <ul>
 *   <li>Infrastructure Management: Manage cloud resources (AWS/GCP/Azure), cost optimization</li>
 *   <li>CI/CD Pipelines: Own build, test, deploy automation for all services</li>
 *   <li>Deployment Automation: Implement blue-green, canary, rolling deployments</li>
 *   <li>Site Reliability: Ensure uptime SLAs (99.9%+), incident response, disaster recovery</li>
 *   <li>Observability: Implement monitoring (Prometheus, Grafana), alerting, log aggregation</li>
 *   <li>Security: Manage secrets, IAM policies, compliance (SOC2, GDPR)</li>
 *   <li>Cost Optimization: Track and optimize infrastructure spend, rightsizing instances</li>
 *   <li>DevOps Practices: Enable teams with GitOps, infrastructure-as-code, SRE principles</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Infrastructure authority:
 * <ul>
 *   <li>INFRASTRUCTURE: Can approve infrastructure changes <$20K budget (escalate to CTO)</li>
 *   <li>DEPLOYMENTS: Full authority over deployment processes, schedules, rollback decisions</li>
 *   <li>AUTOMATION: Can implement CI/CD automation without approval</li>
 *   <li>INCIDENTS: Full authority during incident response (P0/P1 outages)</li>
 *   <li>COST_OPTIMIZATION: Can implement cost optimizations <$5K savings/month</li>
 * </ul>
 *
 * <p><b>Escalation Framework</b><br>
 * Infrastructure escalation paths:
 * <ul>
 *   <li>Escalate to CTO: Infrastructure budget >$20K, major architecture changes, SLA breaches</li>
 *   <li>Escalate to CFO: Cost optimizations requiring product feature changes</li>
 *   <li>Collaborate with Architect Lead: Infrastructure architecture decisions</li>
 *   <li>Receive escalations from: DevOps Engineers on deployment issues, infrastructure incidents</li>
 * </ul>
 *
 * <p><b>LLM Configuration</b><br>
 * Infrastructure-focused reasoning:
 * <ul>
 *   <li>Model: GPT-4 or Claude 3 Opus (complex infrastructure problem-solving)</li>
 *   <li>Temperature: 0.3 (precision in infrastructure decisions, security)</li>
 *   <li>Max Tokens: 5000 (runbooks, incident postmortems, infrastructure docs)</li>
 *   <li>System Prompt: Senior DevOps/SRE Lead with 8+ years infrastructure, cloud expertise</li>
 *   <li>Context: Current infrastructure topology, cost breakdown, SLA metrics, incidents</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Infrastructure and deployment tools:
 * <ul>
 *   <li>CloudProviderTool: AWS/GCP/Azure APIs for resource management</li>
 *   <li>KubernetesTool: K8s cluster management, deployments, scaling</li>
 *   <li>TerraformTool: Infrastructure-as-code provisioning, state management</li>
 *   <li>CI/CDTool: GitHub Actions, GitLab CI, Jenkins pipeline management</li>
 *   <li>MonitoringTool: Prometheus queries, Grafana dashboards, PagerDuty alerts</li>
 *   <li>CostAnalysisTool: Cloud cost breakdown, resource optimization recommendations</li>
 *   <li>SecretManagementTool: Vault, AWS Secrets Manager, K8s secrets</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DevOpsLeadAgent devOpsLead = new DevOpsLeadAgent(
 *     "devops-lead-001",
 *     DecisionAuthorityProto.newBuilder()
 *         .addAllowedDecisionTypes(DecisionTypeProto.INFRASTRUCTURE)
 *         .addAllowedDecisionTypes(DecisionTypeProto.DEPLOYMENTS)
 *         .addAllowedDecisionTypes(DecisionTypeProto.INCIDENTS)
 *         .setMaxBudget(20_000_00) // $20K
 *         .build(),
 *     eventloop, llmClient, memory, toolRegistry, toolExecutor,
 *     meterRegistry, tracer, llmConfig, memoryConfig
 * );
 *
 * // Incident response task
 * TaskRequestProto task = TaskRequestProto.newBuilder()
 *     .setTaskId("incident-P1-database-latency")
 *     .setDescription("Database p99 latency increased from 50ms to 500ms")
 *     .setType(TaskTypeProto.INCIDENT_RESPONSE)
 *     .setPriority(PriorityProto.CRITICAL)
 *     .putMetadata("severity", "P1")
 *     .putMetadata("affected_users", "15000")
 *     .build();
 *
 * devOpsLead.processTask(task).whenResult(result ->
 *     log.info("Incident resolution: {}", result.getDecision().getRationale())
 * );
 * }</pre>
 *
 * @see AbstractVirtualOrgAgent
 * @see CTOAgent
 * @see ArchitectLeadAgent
 * @doc.type class
 * @doc.purpose DevOps Lead agent for infrastructure and automation
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class DevOpsLeadAgent extends AbstractVirtualOrgAgent {


    private static final Logger log = LoggerFactory.getLogger(DevOpsLeadAgent.class);

    private static final String DEVOPS_SYSTEM_PROMPT = """
        You are a DevOps Lead in a software development organization. Your role is to:
        
        1. INFRASTRUCTURE & AUTOMATION
           - Manage cloud infrastructure and resources
           - Automate deployments and infrastructure provisioning
           - Implement Infrastructure as Code (Terraform, CloudFormation)
           - Optimize infrastructure costs and performance
        
        2. CI/CD & DEPLOYMENTS
           - Own CI/CD pipelines and deployment processes
           - Ensure fast, reliable, automated deployments
           - Implement deployment strategies (blue-green, canary, rolling)
           - Manage release schedules and rollback procedures
        
        3. RELIABILITY & OPERATIONS
           - Ensure high availability and system reliability
           - Implement monitoring, alerting, and observability
           - Lead incident response and postmortems
           - Drive SRE practices and SLO management
        
        4. SECURITY & COMPLIANCE
           - Manage secrets and access controls
           - Implement security policies and compliance
           - Conduct security audits and vulnerability management
           - Ensure data protection and privacy
        
        5. DECISION FRAMEWORK
           - Evaluate by: reliability, cost, automation potential, security
           - Prioritize automation and self-service
           - Balance speed with stability
           - Use infrastructure as code and GitOps practices
        
        When processing tasks:
        - Analyze infrastructure and operational impact
        - Consider cost, reliability, and automation
        - Evaluate security and compliance requirements
        - Provide clear operational rationale
        """;

    public DevOpsLeadAgent(
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

        super(agentId, AgentRoleProto.AGENT_ROLE_DEVOPS_LEAD, authority, eventloop,
                llmClient, memory, toolRegistry, toolExecutor, meterRegistry, tracer,
                llmConfig, memoryConfig);

        log.info("Initialized DevOps Lead Agent: id={}", agentId);
    }

    @Override
    protected void onStart() throws Exception {
        log.info("DevOps Lead Agent starting: {}", getAgentId());
    }

    @Override
    protected void onStop() throws Exception {
        log.info("DevOps Lead Agent stopping: {}", getAgentId());
    }

    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        log.info("DevOps Lead processing task: {}", task.getTitle());

        Instant startTime = Instant.now();

        Promise<TaskResponseProto> resultPromise = memory.retrieveContext(task)
            .then(context -> {
                String prompt = buildDevOpsPrompt(task, context);
                return llmClient.complete(DEVOPS_SYSTEM_PROMPT, prompt, getTools(),
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
            log.error("DevOps Lead failed to process task: {}", error.getMessage());
            recordMetrics(task, startTime, false);
        });

        return resultPromise;
    }

    private String buildDevOpsPrompt(TaskProto task, String context) {
        return "DEVOPS DECISION\n" +
               "Task: " + task.getTitle() + "\n" +
               "Description: " + task.getDescription() + "\n\n" +
               "Context: " + context + "\n\n" +
               "Provide infrastructure/deployment analysis and recommendation.";
    }

    private DecisionProto extractDecision(LLMResponse llmResponse, TaskProto task) {
        return DecisionExtractor.extractDecision(
            llmResponse.getContent(),
            task,
            getAgentId()
        );
    }

    private void recordMetrics(TaskProto task, Instant startTime, boolean success) {
        meterRegistry.counter("virtualorg.devops.tasks",
            "type", task.getType().name(),
            "status", success ? "success" : "failure"
        ).increment();
    }
}
