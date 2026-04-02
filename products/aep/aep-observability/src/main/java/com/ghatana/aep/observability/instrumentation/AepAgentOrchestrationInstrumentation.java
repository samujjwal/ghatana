package com.ghatana.aep.observability.instrumentation;

import com.ghatana.aep.observability.tracing.AepTracingProvider;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * @doc.type class
 * @doc.purpose Instrumentation for AEP agent orchestration with distributed tracing
 * @doc.layer observability
 * @doc.pattern Instrumentation
 */
public class AepAgentOrchestrationInstrumentation {

    private static final Logger log = LoggerFactory.getLogger(AepAgentOrchestrationInstrumentation.class);
    private final AepTracingProvider tracingProvider;

    public AepAgentOrchestrationInstrumentation() {
        this.tracingProvider = AepTracingProvider.getInstance();
    }

    /**
     * Instrument pipeline deployment with tracing
     */
    public <T> T instrumentPipelineDeployment(
            String pipelineId,
            String tenantId,
            PipelineDeploymentTask<T> task) throws Exception {

        Span span = tracingProvider.startPipelineDeploymentSpan(pipelineId, tenantId);
        long startTime = System.currentTimeMillis();

        try (Scope scope = tracingProvider.createScope(span)) {
            log.info("Deploying pipeline: {}", pipelineId);

            T result = task.execute();

            long duration = System.currentTimeMillis() - startTime;
            tracingProvider.recordSuccess(span, duration);

            log.info("Pipeline deployment successful - duration: {}ms", duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            tracingProvider.recordError(span, e, duration);
            throw e;
        } finally {
            MDC.clear();
        }
    }

    /**
     * Instrument agent execution with tracing
     */
    public <T> T instrumentAgentExecution(
            String agentId,
            String pipelineId,
            String tenantId,
            AgentExecutionTask<T> task) throws Exception {

        Span span = tracingProvider.startAgentExecutionSpan(agentId, pipelineId, tenantId);
        long startTime = System.currentTimeMillis();

        try (Scope scope = tracingProvider.createScope(span)) {
            log.info("Executing agent: {} in pipeline: {}", agentId, pipelineId);

            T result = task.execute();

            long duration = System.currentTimeMillis() - startTime;

            // Add SLA validation
            if (duration > 5000) {
                span.setAttribute("sla.warning", true);
                log.warn("Agent execution exceeded SLA threshold: {}ms > 5000ms", duration);
            }

            tracingProvider.recordSuccess(span, duration);

            log.info("Agent execution successful - duration: {}ms", duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            tracingProvider.recordError(span, e, duration);
            log.error("Agent execution failed: {} - {}", agentId, e.getMessage());
            throw e;
        } finally {
            MDC.remove("agentId");
        }
    }

    /**
     * Instrument event processing with tracing
     */
    public <T> T instrumentEventProcessing(
            String eventId,
            String eventType,
            String tenantId,
            EventProcessingTask<T> task) throws Exception {

        Span span = tracingProvider.startEventProcessingSpan(eventId, eventType, tenantId);
        long startTime = System.currentTimeMillis();

        try (Scope scope = tracingProvider.createScope(span)) {
            log.info("Processing event: {} of type: {}", eventId, eventType);

            T result = task.execute();

            long duration = System.currentTimeMillis() - startTime;

            // Add performance metrics
            if (duration > 1000) {
                span.setAttribute("performance.slow_processing", true);
            }

            tracingProvider.recordSuccess(span, duration);

            log.info("Event processing successful - duration: {}ms", duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            tracingProvider.recordError(span, e, duration);
            log.error("Event processing failed: {} - {}", eventId, e.getMessage());
            throw e;
        } finally {
            MDC.remove("eventId");
            MDC.remove("eventType");
        }
    }

    /**
     * Instrument batch operation with tracing
     */
    public void instrumentBatchOperation(
            String batchId,
            String tenantId,
            int totalItems,
            BatchOperationTask task) throws Exception {

        Span batchSpan = tracingProvider.getTracer().spanBuilder("aep.batch.operation")
                .setAttribute("batch.id", batchId)
                .setAttribute("tenant.id", tenantId)
                .setAttribute("batch.total_items", totalItems)
                .setAttribute("correlation.id", MDC.get("correlationId"))
                .startSpan();

        long startTime = System.currentTimeMillis();

        try (Scope scope = tracingProvider.createScope(batchSpan)) {
            log.info("Starting batch operation: {} with {} items", batchId, totalItems);

            task.execute();

            long duration = System.currentTimeMillis() - startTime;
            long perItemDuration = duration / Math.max(totalItems, 1);

            tracingProvider.recordSuccess(batchSpan, duration);

            log.info("Batch operation completed - total: {}ms, per-item avg: {}ms",
                    duration, perItemDuration);

            if (perItemDuration > 100) {
                log.warn("Batch operation per-item duration exceeded threshold: {}ms", perItemDuration);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            tracingProvider.recordError(batchSpan, e, duration);
            log.error("Batch operation failed: {} - {}", batchId, e.getMessage());
            throw e;
        }
    }

    /**
     * Functional interface for pipeline deployment task
     */
    @FunctionalInterface
    public interface PipelineDeploymentTask<T> {
        T execute() throws Exception;
    }

    /**
     * Functional interface for agent execution task
     */
    @FunctionalInterface
    public interface AgentExecutionTask<T> {
        T execute() throws Exception;
    }

    /**
     * Functional interface for event processing task
     */
    @FunctionalInterface
    public interface EventProcessingTask<T> {
        T execute() throws Exception;
    }

    /**
     * Functional interface for batch operation task
     */
    @FunctionalInterface
    public interface BatchOperationTask {
        void execute() throws Exception;
    }
}
