/**
 * Generated AEP API Client
 * 
 * T-31: This file contains comprehensive types generated from OpenAPI specification.
 * To regenerate: pnpm generate-client
 * 
 * Source: ../../contracts/openapi.yaml
 */

// =========================================================================
// T-33: Standard Response Envelopes
// =========================================================================

export interface ListEnvelope<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  nextCursor?: string;
}

export interface MutationEnvelope {
  operationId: string;
  status: "SUCCESS" | "PENDING" | "FAILED";
  resource: string;
  auditId?: string;
}

export interface ErrorEnvelope {
  code: string;
  message: string;
  correlationId: string;
  retryable: boolean;
  details?: Record<string, unknown>;
}

export interface ValidationErrorEnvelope {
  code: "VALIDATION_ERROR";
  message: string;
  valid: false;
  errors: Array<{
    field: string;
    message: string;
    code: string;
  }>;
}

// =========================================================================
// Health & System Types
// =========================================================================

export interface HealthResponse {
  status: "UP" | "DOWN";
  timestamp: string;
  version?: string;
  service: string;
}

export interface ReadyResponse {
  ready: true;
  checks?: Record<string, boolean>;
}

export interface NotReadyResponse {
  ready: false;
  reason: string;
  degradedDependencies?: string[];
}

export interface InfoResponse {
  service: string;
  version: string;
  description: string;
  timestamp: string;
}

// =========================================================================
// Pipeline Types
// =========================================================================

export interface Pipeline {
  id: string;
  name: string;
  description?: string;
  tenantId?: string;
  version: number;
  active: boolean;
  config?: string;
  stages: PipelineStage[];
  status: "draft" | "active" | "paused" | "archived";
  createdAt: string;
  updatedAt: string;
  createdBy?: string;
  updatedBy?: string;
}

export interface PipelineStage {
  id: string;
  name: string;
  kind: string;
  workflow: AgentRef[];
  connectorIds?: string[];
  connectors?: ConnectorSpec[];
  description?: string;
}

export interface AgentRef {
  id: string;
  agent: string;
  role?: string;
  inputsSpec?: Record<string, unknown>;
  outputsSpec?: Record<string, unknown>;
  config?: Record<string, unknown>;
}

export interface ConnectorSpec {
  id: string;
  type: string;
  direction: "in" | "out" | "both";
  encoding?: string;
  config?: Record<string, unknown>;
}

export interface PipelineCreateRequest {
  name: string;
  description?: string;
  tenantId?: string;
  stages: PipelineStage[];
}

export interface PipelineUpdateRequest {
  name?: string;
  description?: string;
  stages?: PipelineStage[];
  status?: "draft" | "active" | "paused" | "archived";
}

export interface ValidationResult {
  valid: boolean;
  errors?: Array<{
    field: string;
    message: string;
  }>;
}

// =========================================================================
// Pattern Types
// =========================================================================

export interface Pattern {
  id: string;
  name: string;
  type: string;
  status: string;
  description?: string;
  config?: Record<string, unknown>;
  threshold?: number;
}

export interface PatternCreateRequest {
  name: string;
  type: string;
  description?: string;
  threshold?: number;
  config?: string;
}

// =========================================================================
// Agent Types
// =========================================================================

export interface Agent {
  id: string;
  name: string;
  description?: string;
  version: string;
  status: "active" | "inactive" | "deprecated";
  capabilities?: string[];
}

// =========================================================================
// Event Types
// =========================================================================

export interface EventRequest {
  tenantId?: string;
  type: string;
  payload: Record<string, unknown>;
  timestamp?: string;
  correlationId?: string;
}

export interface EventResponse {
  eventId: string;
  success: boolean;
  pipelineId?: string;
  runId?: string;
}

export interface BatchEventRequest {
  tenantId: string;
  events: EventRequest[];
  idempotencyKey?: string;
}

export interface BatchEventResponse {
  results: Array<{
    eventId: string;
    success: boolean;
    piiDetected?: boolean;
    consentDenied?: boolean;
    idempotencySkipped?: boolean;
  }>;
  totalProcessed: number;
  failedCount: number;
}

export interface EventStreamMessage {
  id: string;
  event: string;
  data: string;
}

// =========================================================================
// Audit Types
// =========================================================================

export interface AuditLogRequest {
  action: string;
  resourceType: string;
  resourceId: string;
  tenantId: string;
  actor: string;
  details?: Record<string, unknown>;
}

export interface AuditLogEntry {
  id: string;
  timestamp: string;
  action: string;
  resourceType: string;
  resourceId: string;
  tenantId: string;
  actor: string;
  details?: Record<string, unknown>;
}

// =========================================================================
// Consent Types
// =========================================================================

export interface ConsentRecordRequest {
  userId: string;
  purpose: string;
  granted: boolean;
  tenantId?: string;
  metadata?: Record<string, unknown>;
}

export interface ConsentDecision {
  userId: string;
  purpose: string;
  granted: boolean;
  recordedAt: string;
  expiresAt?: string;
}

// =========================================================================
// AI Suggestion Types
// =========================================================================

export interface AiSuggestStagesRequest {
  description: string;
  goal?: string;
  existingStages?: Array<{
    name: string;
    kind?: string;
  }>;
}

export interface SuggestedStage {
  name: string;
  kind: string;
  description?: string;
}

export interface AiSuggestionEnvelope<T> {
  suggestions: T[];
  confidence: number;
  rationale: string;
  evidence: string[];
  auditHook?: string;
  surface: string;
}

// =========================================================================
// Capabilities Types
// =========================================================================

export interface CapabilitiesManifest {
  dataCloud: boolean;
  redis: boolean;
  analyticsStore: boolean;
  aiSuggestions: boolean;
  gdprCompliance: boolean;
  piiEnforcement: "LOG" | "REDACT" | "BLOCK";
  episodeLearning: boolean;
  serverSideConsent: boolean;
  durableSessions: boolean;
  sseStreaming: boolean;
  tenantAuthorization: boolean;
  version: string;
}

// =========================================================================
// API Paths (OpenAPI fetch compatible)
// =========================================================================

export type paths = {
  "/health": {
    get: {
      responses: {
        200: { content: { "application/json": HealthResponse } };
      };
    };
  };
  "/ready": {
    get: {
      responses: {
        200: { content: { "application/json": ReadyResponse } };
        503: { content: { "application/json": NotReadyResponse } };
      };
    };
  };
  "/api/v1/pipelines": {
    get: {
      parameters: {
        query?: { tenantId?: string; page?: number; pageSize?: number; cursor?: string };
      };
      responses: {
        200: { content: { "application/json": ListEnvelope<Pipeline> } };
        400: { content: { "application/json": ErrorEnvelope } };
      };
    };
    post: {
      requestBody: { content: { "application/json": PipelineCreateRequest } };
      responses: {
        201: { content: { "application/json": MutationEnvelope } };
        422: { content: { "application/json": ValidationErrorEnvelope } };
      };
    };
  };
  "/api/v1/pipelines/{id}": {
    get: {
      parameters: { path: { id: string }; query?: { tenantId?: string } };
      responses: {
        200: { content: { "application/json": Pipeline } };
        404: { content: { "application/json": ErrorEnvelope } };
      };
    };
  };
  "/api/v1/events": {
    post: {
      requestBody: { content: { "application/json": EventRequest } };
      responses: {
        200: { content: { "application/json": EventResponse } };
      };
    };
  };
  "/api/v1/surfaces": {
    get: {
      responses: {
        200: { content: { "application/json": CapabilitiesManifest } };
      };
    };
  };
};

// =========================================================================
// Helper Types
// =========================================================================

export type SuccessResponse<T extends keyof paths> = paths[T] extends {
  get: { responses: { 200: { content: { "application/json": infer R } } } }
} ? R : never;

export type RequestBody<T extends keyof paths> = paths[T] extends {
  post: { requestBody: { content: { "application/json": infer R } } }
} ? R : never;
