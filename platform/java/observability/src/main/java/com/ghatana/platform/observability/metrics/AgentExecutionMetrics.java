package com.ghatana.platform.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.concurrent.TimeUnit;

/**
 * @doc.type class
 * @doc.purpose Standardizes OpenTelemetry metrics for agent execution context
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class AgentExecutionMetrics implements MeterBinder {

    private final String agentType;
    private final String tenantId;
    private final String pipelineId;

    private Timer executionTimer;
    private Counter successCounter;
    private Counter failureCounter;
    private Counter stalledCounter;

    public AgentExecutionMetrics(String agentType, String tenantId, String pipelineId) {
        this.agentType = agentType;
        this.tenantId = tenantId;
        this.pipelineId = pipelineId;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        this.executionTimer = Timer.builder("agent.execution.duration")
                .description("Time taken for an agent to execute its cognitive cycle")
                .tags("agent.type", agentType, "tenant.id", tenantId, "pipeline.id", pipelineId)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.successCounter = Counter.builder("agent.execution.success")
                .description("Number of successful agent cycle executions")
                .tags("agent.type", agentType, "tenant.id", tenantId, "pipeline.id", pipelineId)
                .register(registry);

        this.failureCounter = Counter.builder("agent.execution.failure")
                .description("Number of failed agent cycle executions")
                .tags("agent.type", agentType, "tenant.id", tenantId, "pipeline.id", pipelineId)
                .register(registry);
                
        this.stalledCounter = Counter.builder("agent.execution.stalls")
                .description("eBPF tracked agent execution stalls")
                .tags("agent.type", agentType, "tenant.id", tenantId, "pipeline.id", pipelineId)
                .register(registry);
    }

    public void recordExecution(long startMs, boolean success) {
        if (executionTimer != null) {
            executionTimer.record(System.currentTimeMillis() - startMs, TimeUnit.MILLISECONDS);
        }
        if (success && successCounter != null) {
            successCounter.increment();
        } else if (!success && failureCounter != null) {
            failureCounter.increment();
        }
    }
    
    public void recordStall() {
        if (stalledCounter != null) {
            stalledCounter.increment();
        }
    }
}
