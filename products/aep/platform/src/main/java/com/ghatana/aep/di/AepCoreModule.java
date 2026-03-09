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
     * Provides a default agent event operator for testing purposes.
     *
     * <p>This is a minimal implementation that satisfies the dependency injection
     * requirements for the DeadLetterOperator. In production, specific agents
     * should be provided by their respective modules.
     *
     * @return a default agent event operator
     */
    @Provides
    AgentEventOperator agentEventOperator() {
        // Create a simple no-op agent for testing
        TypedAgent<java.util.Map<String, Object>, java.util.Map<String, Object>> noopAgent = new TypedAgent<java.util.Map<String, Object>, java.util.Map<String, Object>>() {
            @Override
            public AgentDescriptor descriptor() {
                return AgentDescriptor.builder()
                    .agentId("noop-agent")
                    .name("No-op Agent")
                    .version("1.0.0")
                    .type(com.ghatana.agent.AgentType.DETERMINISTIC)
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
            public Promise<AgentResult<java.util.Map<String, Object>>> process(AgentContext ctx, java.util.Map<String, Object> input) {
                java.util.Map<String, Object> result = new java.util.HashMap<>();
                result.put("status", "processed");
                result.put("input", input);
                return Promise.of(AgentResult.success(result, "noop-agent", Duration.ofMillis(1)));
            }
        };
        return new AgentEventOperator(noopAgent);
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
