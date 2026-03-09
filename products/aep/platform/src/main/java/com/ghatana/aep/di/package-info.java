/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

/**
 * ActiveJ Dependency Injection modules for the AEP platform.
 *
 * <p>This package contains six DI modules that wire together all AEP services
 * using the ActiveJ {@code io.activej.inject} framework. Each module is
 * self-contained and declares its dependencies through {@code @Provides}
 * method parameters — ActiveJ resolves the full dependency graph at injector
 * creation time.
 *
 * <h2>Module Hierarchy</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │                    AEP DI Module Architecture                       │
 * ├──────────────────────────────────────────────────────────────────────┤
 * │                                                                     │
 * │  ┌─────────────────────┐   ┌───────────────────────┐               │
 * │  │   AepCoreModule     │   │ AepObservabilityModule │               │
 * │  │  PipelineEngine     │   │  AnalyticsEngine       │               │
 * │  │  OperatorCatalog    │   │  7 analytics services  │               │
 * │  │  Eventloop          │◄──│  (extends platform     │               │
 * │  │  ExecutorService    │   │   ObservabilityModule)  │               │
 * │  │  ScheduledExecutor  │   └───────────────────────┘               │
 * │  └────────┬────────────┘                                           │
 * │           │                                                         │
 * │    ┌──────┼────────────────────┬─────────────────────┐             │
 * │    │      │                    │                     │             │
 * │    ▼      ▼                    ▼                     ▼             │
 * │  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐    │
 * │  │ AepOrchestration│ │ AepConnector   │  │ AepIngress       │    │
 * │  │   Module        │  │   Module       │  │   Module         │    │
 * │  │ Orchestrator    │  │ Kafka/RMQ/SQS  │  │ RateLimiter     │    │
 * │  │ CheckpointStore │  │ S3/HTTP        │  │ Idempotency     │    │
 * │  │ ExecutionQueue  │  │ (5 backends)   │  │ HealthController│    │
 * │  └────────────────┘  └────────────────┘  │ JedisPool       │    │
 * │                                           └──────────────────┘    │
 * │                         ┌────────────────┐                        │
 * │                         │ AepPattern     │                        │
 * │                         │   Module       │                        │
 * │                         │ OperatorRegistry│                       │
 * │                         │ PatternCompiler │                       │
 * │                         └────────────────┘                        │
 * └──────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Create full AEP injector
 * Injector injector = Injector.of(
 *     new ObservabilityModule(),       // platform
 *     new AepCoreModule(),
 *     new AepOrchestrationModule(),
 *     new AepPatternModule(),
 *     new AepConnectorModule(),
 *     new AepIngressModule(),
 *     new AepObservabilityModule()
 * );
 *
 * // Retrieve any service
 * PipelineExecutionEngine engine = injector.getInstance(PipelineExecutionEngine.class);
 * Orchestrator orchestrator = injector.getInstance(Orchestrator.class);
 * PatternCompiler compiler = injector.getInstance(PatternCompiler.class);
 * }</pre>
 *
 * @see com.ghatana.aep.di.AepCoreModule
 * @see com.ghatana.aep.di.AepOrchestrationModule
 * @see com.ghatana.aep.di.AepPatternModule
 * @see com.ghatana.aep.di.AepConnectorModule
 * @see com.ghatana.aep.di.AepIngressModule
 * @see com.ghatana.aep.di.AepObservabilityModule
 */
package com.ghatana.aep.di;
