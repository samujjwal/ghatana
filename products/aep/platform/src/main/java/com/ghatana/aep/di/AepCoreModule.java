/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.event.AepEventCloudFactory;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.aep.operator.AgentEventOperator;
import com.ghatana.aep.operator.DeadLetterOperator;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.HealthStatus;
import com.ghatana.agent.AgentType;
import com.ghatana.catalog.adapters.eventcloud.DataCloudEventTypeRepository;
import com.ghatana.catalog.ports.EventTypeRepository;
import io.activej.promise.Promise;
import com.ghatana.core.operator.catalog.DefaultOperatorCatalog;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import com.ghatana.core.pipeline.PipelineExecutionEngine;
import com.ghatana.platform.resilience.DeadLetterQueue;
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * ActiveJ DI module for AEP core components.
 *
 * <p>Provides the fundamental building blocks of the AEP platform:
 * <ul>
 *   <li>{@link PipelineExecutionEngine} — DAG-based pipeline executor</li>
 *   <li>{@link OperatorCatalog} — thread-safe operator registry for pipeline stages</li>
 *   <li>{@link Eventloop} — ActiveJ async event loop</li>
 *   <li>{@link ExecutorService} — shared thread pool for blocking operations</li>
 *   <li>{@link ScheduledExecutorService} — scheduler for periodic/delayed tasks</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(new AepCoreModule());
 * PipelineExecutionEngine engine = injector.getInstance(PipelineExecutionEngine.class);
 * OperatorCatalog catalog = injector.getInstance(OperatorCatalog.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for AEP core pipeline and operator services
 * @doc.layer product
 * @doc.pattern Module
 * @see PipelineExecutionEngine
 * @see OperatorCatalog
 */
public class AepCoreModule extends AbstractModule {

    /**
     * Resolves the worker thread pool size.
     *
     * <p>Reads {@code AEP_WORKER_THREADS} from the environment first so that
     * container deployments with CPU limits (e.g. Kubernetes {@code resources.limits.cpu})
     * can override the JVM's {@link Runtime#availableProcessors()} which returns
     * the <em>host</em> CPU count, not the container quota.
     */
    private static final int DEFAULT_THREAD_POOL_SIZE = resolveWorkerThreads();

    private static int resolveWorkerThreads() {
        String envVal = System.getenv("AEP_WORKER_THREADS");
        if (envVal != null && !envVal.isBlank()) {
            try {
                int n = Integer.parseInt(envVal.strip());
                if (n > 0) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * Provides the pipeline execution engine.
     *
     * <p>The engine is stateless — all per-execution state flows through
     * {@link com.ghatana.core.pipeline.PipelineExecutionContext}, making it
     * safe to share as a singleton across concurrent pipeline runs.
     *
     * @return singleton pipeline execution engine
     */
    @Provides
    PipelineExecutionEngine pipelineExecutionEngine() {
        return new PipelineExecutionEngine();
    }

    /**
     * Provides the default AEP EventCloud implementation.
     *
     * <p>Prefers discovered Data Cloud EventLogStore-backed implementation and
     * falls back to in-memory only when no provider is available.
     *
     * @return event cloud instance
     */
    @Provides
    EventCloud eventCloud() {
        return AepEventCloudFactory.createDefault();
    }

    /**
     * Provides the event-type repository backed by the AEP EventCloud.
     *
     * <p>Replaces the {@code InMemoryEventTypeRepository} with a durable,
     * event-sourced implementation that stores {@code eventtype.registered /
     * eventtype.updated / eventtype.deleted} events in the active EventCloud
     * connector (gRPC, HTTP, or EventLogStore).
     *
     * <p>The in-memory projection is rebuilt from EventCloud on first access
     * (lazy replay) so existing data is never lost across restarts.
     *
     * @param eventCloud active EventCloud connector
     * @return durable EventTypeRepository singleton
     * @doc.type method
     * @doc.purpose Durable, event-sourced EventType catalog via AEP EventCloud
     * @doc.layer product
     * @doc.pattern Repository, EventSourced
     */
    @Provides
    EventTypeRepository eventTypeRepository(EventCloud eventCloud) {
        DataCloudEventTypeRepository repo = new DataCloudEventTypeRepository(eventCloud);
        repo.replayFromEventCloud();
        return repo;
    }

    /**
     * Provides the operator catalog bound to its interface.
     *
     * <p>Returns a {@link DefaultOperatorCatalog} — a thread-safe, in-memory
     * catalog backed by {@code ConcurrentHashMap}. Custom operators can be
     * registered post-injection via {@link OperatorCatalog#register}.
     *
     * @return singleton operator catalog
     */
    @Provides
    OperatorCatalog operatorCatalog() {
        return new DefaultOperatorCatalog();
    }

    /**
     * Provides the ActiveJ event loop.
     *
     * <p>The event loop is the heart of the async runtime. All non-blocking
     * services (connectors, health controllers) share this single loop.
     * The loop is created but <b>not started</b>; lifecycle management is
     * handled by the ActiveJ launcher or explicit {@code eventloop.run()}.
     *
     * @return singleton event loop
     */
    @Provides
    Eventloop eventloop() {
        return Eventloop.builder()
                .withCurrentThread()
                .build();
    }

    /**
     * Provides a shared thread pool for blocking and CPU-intensive operations.
     *
     * <p>Used by connectors (S3, SQS), preprocessing services (eventization,
     * normalization), and other components that need off-eventloop execution.
     * Pool size defaults to the number of available processors.
     *
     * @return shared executor service
     */
    @Provides
    ExecutorService executorService() {
        return Executors.newFixedThreadPool(
                DEFAULT_THREAD_POOL_SIZE,
                r -> {
                    Thread t = new Thread(r, "aep-worker");
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * Provides a scheduled executor for periodic and delayed tasks.
     *
     * <p>Used by connectors for polling intervals, S3 multipart upload
     * timeouts, and other time-based scheduling needs. Uses 2 threads
     * to avoid head-of-line blocking between scheduled tasks.
     *
     * @return shared scheduled executor
     */
    @Provides
    ScheduledExecutorService scheduledExecutorService() {
        return Executors.newScheduledThreadPool(
                2,
                r -> {
                    Thread t = new Thread(r, "aep-scheduler");
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * Provides the AEP dead-letter queue for failed event storage.
     *
     * <p>Shared singleton: bounded to 50 000 events, TTL of 7 days,
     * replay enabled. Consumed by {@code DeadLetterOperator} to park
     * events that could not be processed by any pipeline stage.
     *
     * <p>Capacity and TTL are intentionally conservative — for high-volume
     * production workloads configure a persistent DLQ via Data Cloud instead.
     *
     * @return singleton dead-letter queue
     */
    @Provides
    DeadLetterQueue deadLetterQueue() {
        return DeadLetterQueue.builder()
                .maxSize(50_000)
                .ttl(Duration.ofDays(7))
                .enableReplay(true)
                .build();
    }

    /**
     * Provides a default agent event operator.
     *
     * <p>This fallback implementation is activated when no product module
     * (e.g. {@code YappcIntegrationModule}) overrides this binding with a real agent.
     * It logs every unhandled event at ERROR level and emits a {@code _error} key
     * in the result so downstream operators (DLQ, metrics) can detect unrouted events.
     *
     * <p><b>Production</b>: override this binding by providing a real
     * {@link AgentEventOperator} in your product module (e.g. after wiring
     * {@code YappcAgentSystem} and {@code CatalogAgentDispatcher}).
     *
     * @doc.type method
     * @doc.purpose Default fallback AgentEventOperator that surfaces unrouted events as errors
     * @doc.layer product
     * @doc.pattern Null Object
     * @return fallback agent event operator
     */
    @Provides
    AgentEventOperator agentEventOperator() {
        TypedAgent<java.util.Map<String, Object>, java.util.Map<String, Object>> fallback =
                new TypedAgent<java.util.Map<String, Object>, java.util.Map<String, Object>>() {

            private static final org.slf4j.Logger agentLog =
                    org.slf4j.LoggerFactory.getLogger("aep.fallback-agent");

            @Override
            public AgentDescriptor descriptor() {
                return AgentDescriptor.builder()
                    .agentId("fallback-agent")
                    .name("Fallback Agent — no product agent wired")
                    .version("1.0.0")
                    .type(AgentType.DETERMINISTIC)
                    .build();
            }

            @Override
            public Promise<Void> initialize(AgentConfig config) {
                return Promise.complete();
            }

            @Override
            public Promise<Void> shutdown() {
                return Promise.complete();
            }

            @Override
            public Promise<HealthStatus> healthCheck() {
                return Promise.of(HealthStatus.HEALTHY);
            }

            @Override
            public Promise<AgentResult<java.util.Map<String, Object>>> process(
                    AgentContext ctx, java.util.Map<String, Object> input) {
                agentLog.error(
                    "[AEP] No product AgentEventOperator bound. Event dropped without processing. "
                    + "Wire a real agent via YappcIntegrationModule or override agentEventOperator() "
                    + "in a product DI module. agentId={} turnId={}",
                    ctx != null ? ctx.getAgentId() : "unknown",
                    ctx != null ? ctx.getTurnId()  : "unknown");
                java.util.Map<String, Object> errorResult = new java.util.LinkedHashMap<>(input);
                errorResult.put("_error", "no_agent_configured");
                errorResult.put("_status", "DROPPED");
                return Promise.of(AgentResult.failure(
                    new IllegalStateException(
                        "No product agent configured — bind AgentEventOperator in your product DI module"),
                    "fallback-agent",
                    Duration.ZERO));
            }
        };
        return new AgentEventOperator(fallback);
    }

    /**
     * Provides the AEP dead-letter operator.
     *
     * <p>Wraps the primary {@link AgentEventOperator} with dead-letter routing so
     * that any event which fails pipeline processing is parked in the shared
     * {@link DeadLetterQueue} rather than propagating the error upstream.
     *
     * @param delegate        the primary agent event operator
     * @param deadLetterQueue the shared dead-letter queue
     * @return singleton dead-letter operator
     */
    @Provides
    DeadLetterOperator deadLetterOperator(AgentEventOperator delegate, DeadLetterQueue deadLetterQueue) {
        return DeadLetterOperator.builder()
                .delegate(delegate)
                .deadLetterQueue(deadLetterQueue)
                .build();
    }
}
