/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */

/**
 * AEP (Agentic Event Processor) Integration Package.
 *
 * <p><b>Purpose</b><br>
 * Provides backend integration with AEP. Supports two modes:
 * - LIBRARY mode: AEP embedded in Java process (development)
 * - SERVICE mode: AEP as external HTTP service (production)
 *
 * <p><b>Architecture Overview</b><br>
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │ TypeScript Frontend (app-creator)                           │
 * │ - Canvas UI                                                 │
 * │ - Does NOT know about AEP                                   │
 * └───────────────────┬─────────────────────────────────────────┘
 *                     │
 *                     │ REST API calls to /api/...
 *                     │
 * ┌───────────────────▼─────────────────────────────────────────┐
 * │ Java Backend (YAPPC)                                        │
 * │                                                              │
 * │ ┌─────────────────────────────────────────────────────────┐ │
 * │ │ Controllers                                             │ │
 * │ │ - ShapeController                                       │ │
 * │ │ - FrameController                                       │ │
 * │ │ - AI/Auto-layout Controller                             │ │
 * │ └──────────────────────┬──────────────────────────────────┘ │
 * │                        │                                     │
 * │ ┌──────────────────────▼──────────────────────────────────┐ │
 * │ │ AepService (this package)                              │ │
 * │ │ - publishEvent()                                        │ │
 * │ │ - executeAction()                                       │ │
 * │ │ - queryEvents()                                         │ │
 * │ └──────┬────────────────────────────────────────┬─────────┘ │
 * │        │                                        │            │
 * │    ┌───▼────────────────────┐  ┌──────────────▼──────────┐ │
 * │    │ LIBRARY MODE           │  │ SERVICE MODE           │ │
 * │    │ (Development)          │  │ (Prod/Staging)         │ │
 * │    │                        │  │                        │ │
 * │    │ AepLibraryClient       │  │ AepServiceClient       │ │
 * │    │ - In-process library   │  │ - HTTP client          │ │
 * │    │ - No network overhead  │  │ - External AEP service │ │
 * │    │ - Local debug          │  │ - Scalable             │ │
 * │    └────────────┬───────────┘  └──────────────┬─────────┘ │
 * │               │                               │            │
 * │               │ Embedded                      │ HTTP      │
 * │               │ in JVM                        │ calls    │
 * │               │                               │            │
 * │    ┌──────────▼──────┐              ┌────────▼────────┐  │
 * │    │ AEP Library     │              │ AEP Service     │  │
 * │    │ JAR process     │              │ External        │  │
 * │    └─────────────────┘              └─────────────────┘  │
 * └────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p><b>Configuration</b><br>
 * Set via environment variables:
 *
 * <pre>
 * # Development (default)
 * AEP_MODE=library
 * AEP_LIBRARY_PATH=./aep-lib.jar
 *
 * # Production/Staging
 * AEP_MODE=service
 * AEP_SERVICE_HOST=aep-service.example.com
 * AEP_SERVICE_PORT=7004
 * AEP_SERVICE_TIMEOUT_MS=10000
 * </pre>
 *
 * <p><b>Components</b><br>
 *
 * <ul>
 *   <li>{@link com.ghatana.yappc.api.aep.AepMode} - Execution mode enum
 *   <li>{@link com.ghatana.yappc.api.aep.AepConfig} - Configuration management
 *   <li>{@link com.ghatana.yappc.api.aep.AepClient} - Unified interface
 *   <li>{@link com.ghatana.yappc.api.aep.AepClientFactory} - Factory for creating clients
 *   <li>{@link com.ghatana.yappc.api.aep.AepService} - Service layer
 *   <li>{@link com.ghatana.yappc.api.aep.AepException} - Exception type
 * </ul>
 *
 * <p><b>Integration Steps</b><br>
 *
 * <ol>
 *   <li>In your DI container (e.g., ProductionModule):
 *       <pre>
 * @Provides
 * static AepConfig aepConfig(String environment) {
 *   return AepConfig.fromEnvironment(environment);
 * }
 *
 * @Provides
 * static AepClient aepClient(AepConfig config) throws AepException {
 *   return AepClientFactory.create(config);
 * }
 *
 * @Provides
 * static AepService aepService(AepClient client) {
 *   return new AepService(client);
 * }
 *       </pre>
 *   <li>Inject into your controllers/services: {@code @Inject AepService aepService;}
 *   <li>Use in methods: {@code aepService.publishShapeCreatedEvent(shapeJson);}
 * </ol>
 *
 * <p><b>Key Principle</b><br>
 * <b>Frontend never talks to AEP directly</b>. All communication happens through backend APIs.
 * This maintains clean architecture and allows flexible AEP deployment options.
 *
 * @see AepService
 * @see AepConfig
 * @see AepClient
 */
package com.ghatana.yappc.api.aep;
