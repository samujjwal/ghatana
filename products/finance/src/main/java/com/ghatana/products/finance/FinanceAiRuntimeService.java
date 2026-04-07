package com.ghatana.products.finance;

import com.ghatana.finance.ai.FinanceAIModule;
import com.ghatana.finance.ai.FinanceFraudDetectionKernelAgent;
import com.ghatana.kernel.ai.AgentOrchestrator;
import com.ghatana.kernel.ai.AutonomyManager;
import com.ghatana.kernel.ai.ModelGovernanceService;
import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.platform.database.connection.ConnectionPool;
import io.activej.inject.Injector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Managed finance AI runtime.
 *
 * @doc.type class
 * @doc.purpose Owns finance AI injector and optional persistent datasource lifecycle inside the product runtime
 * @doc.layer product
 * @doc.pattern Service
 */
public final class FinanceAiRuntimeService
    implements KernelLifecycleAware, AgentOrchestrator, ModelGovernanceService, AutonomyManager {

    private static final Logger log = LoggerFactory.getLogger(FinanceAiRuntimeService.class);

    private final FinanceAiRuntimeConfig config;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private ConnectionPool connectionPool;
    private Injector injector;
    private AgentOrchestrator orchestrator;
    private ModelGovernanceService governanceService;
    private AutonomyManager autonomyManager;

    public FinanceAiRuntimeService(FinanceAiRuntimeConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    @Override
    public Promise<Void> start() {
        if (!started.compareAndSet(false, true)) {
            return Promise.complete();
        }

        log.info("Starting Finance AI runtime (persistent={})", config.isPersistenceEnabled());
        try {
            if (config.isPersistenceEnabled()) {
                connectionPool = ConnectionPool.create(config.getDataSourceConfig().orElseThrow());
                injector = Injector.of(FinanceAIModule.create(connectionPool.getDataSource()));
            } else {
                injector = Injector.of(FinanceAIModule.create());
            }

            orchestrator = injector.getInstance(AgentOrchestrator.class);
            governanceService = injector.getInstance(ModelGovernanceService.class);
            autonomyManager = injector.getInstance(AutonomyManager.class);
            FinanceFraudDetectionKernelAgent fraudAgent = injector.getInstance(FinanceFraudDetectionKernelAgent.class);
            orchestrator.registerAgent(fraudAgent);

            return Promise.complete();
        } catch (RuntimeException exception) {
            started.set(false);
            closePool();
            throw exception;
        }
    }

    @Override
    public Promise<Void> stop() {
        if (!started.compareAndSet(true, false)) {
            return Promise.complete();
        }

        log.info("Stopping Finance AI runtime");
        injector = null;
        orchestrator = null;
        governanceService = null;
        autonomyManager = null;
        closePool();
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return started.get() && injector != null && (connectionPool == null || connectionPool.isRunning());
    }

    @Override
    public String getName() {
        return "finance-ai-runtime";
    }

    public boolean isPersistenceEnabled() {
        return config.isPersistenceEnabled();
    }

    @Override
    public AgentResponse executeAgent(KernelAgent agent, AgentRequest request) {
        return requireOrchestrator().executeAgent(agent, request);
    }

    @Override
    public void registerAgent(KernelAgent agent) {
        requireOrchestrator().registerAgent(agent);
    }

    @Override
    public void unregisterAgent(String agentId) {
        requireOrchestrator().unregisterAgent(agentId);
    }

    @Override
    public List<KernelAgent> getAvailableAgents() {
        return requireOrchestrator().getAvailableAgents();
    }

    @Override
    public WorkflowResult executeAgentWorkflow(List<KernelAgent> agents, AgentRequest request) {
        return requireOrchestrator().executeAgentWorkflow(agents, request);
    }

    @Override
    public KernelAgent getAgent(String agentId) {
        return requireOrchestrator().getAgent(agentId);
    }

    @Override
    public Promise<AgentResponse> executeAgentAsync(KernelAgent agent, AgentRequest request) {
        return requireOrchestrator().executeAgentAsync(agent, request);
    }

    @Override
    public ModelApproval getModelApproval(String modelId) {
        return requireGovernance().getModelApproval(modelId);
    }

    @Override
    public void validateModelUsage(String modelId, AgentRequest request) {
        requireGovernance().validateModelUsage(modelId, request);
    }

    @Override
    public void recordModelPerformance(String modelId, ModelPerformanceMetrics metrics) {
        requireGovernance().recordModelPerformance(modelId, metrics);
    }

    @Override
    public boolean isModelCompliant(String modelId, CompliancePolicy policy) {
        return requireGovernance().isModelCompliant(modelId, policy);
    }

    @Override
    public void registerModel(ModelRegistration model) {
        requireGovernance().registerModel(model);
    }

    @Override
    public ModelMetadata getModelMetadata(String modelId) {
        return requireGovernance().getModelMetadata(modelId);
    }

    @Override
    public void configureAutonomyLevel(String agentId, AutonomyLevel level) {
        requireAutonomy().configureAutonomyLevel(agentId, level);
    }

    @Override
    public boolean requiresHumanReview(AgentRequest request, KernelAgent agent) {
        return requireAutonomy().requiresHumanReview(request, agent);
    }

    @Override
    public void recordAutonomousDecision(AutonomousDecision decision) {
        requireAutonomy().recordAutonomousDecision(decision);
    }

    @Override
    public List<AutonomousDecision> getAutonomousDecisions(String agentId, TimeWindow window) {
        return requireAutonomy().getAutonomousDecisions(agentId, window);
    }

    @Override
    public AutonomyLevel getAutonomyLevel(String agentId) {
        return requireAutonomy().getAutonomyLevel(agentId);
    }

    @Override
    public void approveDecision(String decisionId, String approver) {
        requireAutonomy().approveDecision(decisionId, approver);
    }

    @Override
    public void rejectDecision(String decisionId, String rejector, String reason) {
        requireAutonomy().rejectDecision(decisionId, rejector, reason);
    }

    private AgentOrchestrator requireOrchestrator() {
        if (orchestrator == null) {
            throw new IllegalStateException("Finance AI runtime is not started");
        }
        return orchestrator;
    }

    private ModelGovernanceService requireGovernance() {
        if (governanceService == null) {
            throw new IllegalStateException("Finance AI runtime is not started");
        }
        return governanceService;
    }

    private AutonomyManager requireAutonomy() {
        if (autonomyManager == null) {
            throw new IllegalStateException("Finance AI runtime is not started");
        }
        return autonomyManager;
    }

    private void closePool() {
        if (connectionPool != null) {
            connectionPool.close();
            connectionPool = null;
        }
    }
}