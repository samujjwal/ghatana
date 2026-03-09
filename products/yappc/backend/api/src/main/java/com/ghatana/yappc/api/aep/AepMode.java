/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module - AEP Integration
 */
package com.ghatana.yappc.api.aep;

/**
 * AEP (Agentic Event Processor) Execution Modes.
 *
 * <p><b>Purpose</b><br>
 * Defines how AEP (Agentic Event Processor) operates in the YAPPC backend:
 *
 * <ul>
 *   <li><b>LIBRARY</b> - AEP runs as an embedded in-process library (default for development)
 *   <li><b>SERVICE</b> - AEP runs as an external microservice (production/staging)
 * </ul>
 *
 * <p><b>Architecture Decision</b><br>
 * YAPPC frontend (TypeScript) does NOT configure AEP mode. The backend handles all AEP
 * integration:
 *
 * <pre>
 * TypeScript Frontend (app-creator)
 *     ↓ (REST calls to /api/...)
 *     ↓
 * Java Backend (YAPPC)
 *     ├─ AEP as Library Mode (dev): Embedded in Java process
 *     └─ AEP as Service Mode (prod): HTTP calls to external AEP service
 *
 * Frontend never knows which mode is active - it just calls backend APIs.
 * </pre>
 *
 * <p><b>Configuration</b><br>
 * Set via environment variable:
 *
 * <pre>
 * AEP_MODE=library     # Development (default)
 * AEP_MODE=service     # Production/Staging
 * </pre>
 *
 * @see AepConfig
 * @see AepClientFactory
  *
 * @doc.type enum
 * @doc.purpose aep mode
 * @doc.layer product
 * @doc.pattern Enum
 */
public enum AepMode {
  /**
   * AEP runs as an embedded in-process library.
   *
   * <p><b>Use Case</b>: Development and testing
   *
   * <p><b>Benefits</b>:
   * - No network overhead
   * - Easier debugging
   * - Single process (simpler deployment)
   * - Shared memory with YAPPC
   */
  LIBRARY,

  /**
   * AEP runs as an external microservice.
   *
   * <p><b>Use Case</b>: Staging, Production, Scalable deployments
   *
   * <p><b>Benefits</b>:
   * - Independent scaling
   * - Technology flexibility (AEP can be any language)
   * - Resource isolation
   * - Fault isolation (AEP crash doesn't crash YAPPC)
   */
  SERVICE
}
