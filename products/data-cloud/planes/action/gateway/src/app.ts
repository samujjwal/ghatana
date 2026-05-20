import Fastify, {
  FastifyInstance,
  FastifyRequest,
  FastifyReply,
} from "fastify";
import fastifyWebsocket from "@fastify/websocket";
import fastifyCors from "@fastify/cors";
import WebSocket from "ws";
import { randomUUID } from "node:crypto";
import { verifyJwt, extractBearerToken } from "./jwt.js";
import type { JwtPayload } from "./jwt.js";
import { GatewayMetrics } from "./metrics.js";
import type {
  KernelLifecycleApiHandlers,
  KernelLifecycleApiRequest,
  KernelLifecycleApiResponse,
} from "@ghatana/kernel-lifecycle";
import {
  AgentLifecycleActionResultSchema,
  AgentLifecycleActionRequestSchema,
  type AgentLifecycleActionRequest,
  KernelLifecycleEventSchema,
  LifecycleArtifactManifestRefSchema,
  LifecycleHealthSnapshotRefSchema,
  LifecycleMemoryRecordSchema,
  LifecycleProvenanceRecordSchema,
  LifecycleRuntimeTruthSnapshotSchema,
} from "@ghatana/kernel-product-contracts";
import { z } from "zod";
import type { ProviderRecord, ProviderStorePort } from "./provider-store.js";
export type { GatewayMetricsSnapshot } from "./metrics.js";
export { GatewayMetrics } from "./metrics.js";
const ApprovalRequestSchema = z.object({
  requestId: z.string(),
  productUnitId: z.string(),
  runId: z.string(),
  requestedAction: z.string(),
  riskLevel: z.string().optional(),
});

const ApprovalDecisionSchema = z.object({
  requestId: z.string(),
  approved: z.boolean(),
  userId: z.string(),
  decisionReason: z.string().optional(),
});

/**
 * Maximum proxy response body size in bytes (16 MiB).
 * Responses larger than this are truncated with a 502 to prevent unbounded memory use.
 * Exported so tests can verify the limit without hard-coding it.
 */
export const MAX_PROXY_BODY_BYTES = 16 * 1024 * 1024; // 16 MiB

/**
 * Read a Fetch API `Response` body up to `maxBytes`.
 * Returns `{ body, truncated }`:
 *  - `body`: concatenated bytes (at most `maxBytes` long).
 *  - `truncated`: true when the stream exceeded `maxBytes`.
 */
async function readBodyCapped(
  res: Response,
  maxBytes: number,
): Promise<{ body: Uint8Array; truncated: boolean }> {
  if (!res.body) {
    return { body: new Uint8Array(0), truncated: false };
  }
  const reader = res.body.getReader();
  const chunks: Uint8Array[] = [];
  let totalBytes = 0;
  let truncated = false;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      const remaining = maxBytes - totalBytes;
      if (value.length <= remaining) {
        chunks.push(value);
        totalBytes += value.length;
      } else {
        // Partial last chunk
        chunks.push(value.slice(0, remaining));
        totalBytes += remaining;
        truncated = true;
        break;
      }
    }
  } finally {
    reader.cancel().catch(() => {});
  }

  const body = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    body.set(chunk, offset);
    offset += chunk.length;
  }
  return { body, truncated };
}

const CORRELATION_ID_HEADER = "x-correlation-id";
const TRACEPARENT_HEADER = "traceparent";

export const DEFAULT_REQUEST_BODY_LIMIT_BYTES = 1 * 1024 * 1024; // 1 MiB
export const DEFAULT_RATE_LIMIT_WINDOW_MS = 60 * 1000; // 60s
export const DEFAULT_RATE_LIMIT_MAX_REQUESTS = 300;
export const DEFAULT_BACKEND_TIMEOUT_MS = 10 * 1000; // 10s
export const DEFAULT_BACKEND_RETRY_ATTEMPTS = 3;
export const DEFAULT_BACKEND_RETRY_INITIAL_BACKOFF_MS = 150;
export const DEFAULT_BACKEND_RETRY_MAX_BACKOFF_MS = 2000;
export const DEFAULT_BREAKER_FAILURE_THRESHOLD = 5;
export const DEFAULT_BREAKER_OPEN_MS = 30 * 1000; // 30s

export interface AgentLifecycleActionServicePort {
  handle(request: unknown): Promise<unknown>;
}

export interface AgentLifecycleTraceLedgerEntry {
  readonly correlationId: string;
  readonly traceId?: string;
  readonly spanId?: string;
  readonly requestId: string;
  readonly productUnitId: string;
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
  readonly agentId: string;
  readonly agentVersion: string;
  readonly action: string;
  readonly outcome:
    | "received"
    | "policy-denied"
    | "requires-approval"
    | "accepted"
    | "failed"
    | "fallback-recorded";
  readonly riskLevel: string;
  readonly reasonCode?: string;
  readonly evidenceRefs: readonly string[];
  readonly fallbackMode: string;
  readonly observedAt: string;
}

export interface AgentLifecycleTraceLedgerPort {
  append(entry: AgentLifecycleTraceLedgerEntry): Promise<void> | void;
}

export type KernelLifecycleApiPort = Pick<
  KernelLifecycleApiHandlers,
  | "listProductUnits"
  | "getProductUnit"
  | "createLifecyclePlan"
  | "executeLifecyclePhase"
  | "listLifecycleRuns"
  | "getLifecycleRun"
  | "getGateResultManifest"
  | "getArtifactManifest"
  | "getDeploymentManifest"
  | "getVerifyHealthReport"
  | "requestApproval"
  | "submitApprovalDecision"
>;

function extractHeaderTenantId(
  value: string | string[] | undefined,
): string | null {
  if (typeof value === "string" && value.trim().length > 0) {
    return value.trim();
  }
  if (Array.isArray(value) && value.length > 0 && value[0].trim().length > 0) {
    return value[0].trim();
  }
  return null;
}

function resolveHeaderTenantId(
  headers: FastifyRequest["headers"],
): string | null {
  return (
    extractHeaderTenantId(headers["x-tenant-id"]) ??
    extractHeaderTenantId(headers["x-ghatana-tenant-id"])
  );
}

function extractHeaderWorkspaceId(
  headers: FastifyRequest["headers"],
): string | null {
  const value = headers["x-ghatana-workspace-id"];
  if (typeof value === "string" && value.trim().length > 0) {
    return value.trim();
  }
  if (Array.isArray(value) && value.length > 0 && value[0].trim().length > 0) {
    return value[0].trim();
  }
  return null;
}

function extractHeaderProjectId(
  headers: FastifyRequest["headers"],
): string | null {
  const value = headers["x-ghatana-project-id"];
  if (typeof value === "string" && value.trim().length > 0) {
    return value.trim();
  }
  if (Array.isArray(value) && value.length > 0 && value[0].trim().length > 0) {
    return value[0].trim();
  }
  return null;
}

function extractPayloadTenantId(payload: JwtPayload): string | null {
  const tenantId = payload["tenant_id"] ?? payload["tenantId"];
  return typeof tenantId === "string" && tenantId.trim().length > 0
    ? tenantId.trim()
    : null;
}

function extractUserId(payload: JwtPayload): string | null {
  const userId = payload["sub"] ?? payload["userId"];
  return typeof userId === "string" && userId.trim().length > 0
    ? userId.trim()
    : null;
}

function extractRoles(payload: JwtPayload): string[] {
  const roles = payload["roles"];
  if (Array.isArray(roles)) {
    return roles.filter(
      (r): r is string => typeof r === "string" && r.trim().length > 0,
    );
  }
  return [];
}

function extractStringArrayClaim(
  payload: JwtPayload,
  claim: string,
): readonly string[] {
  const value = payload[claim];
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter(
    (item): item is string =>
      typeof item === "string" && item.trim().length > 0,
  );
}

const AGENT_LIFECYCLE_RISK_ORDER: readonly AgentLifecycleActionRequest["riskLevel"][] =
  ["low", "medium", "high", "critical"] as const;

function riskLevelRank(
  riskLevel: AgentLifecycleActionRequest["riskLevel"],
): number {
  return AGENT_LIFECYCLE_RISK_ORDER.indexOf(riskLevel);
}

function extractMaxRiskLevel(
  payload: JwtPayload,
): AgentLifecycleActionRequest["riskLevel"] {
  const value = payload["maxRiskLevel"];
  if (
    value === "low" ||
    value === "medium" ||
    value === "high" ||
    value === "critical"
  ) {
    return value;
  }
  return "medium";
}

function extractWorkspaceId(payload: JwtPayload): string | null {
  const workspaceId = payload["workspaceId"];
  return typeof workspaceId === "string" && workspaceId.trim().length > 0
    ? workspaceId.trim()
    : null;
}

function extractProjectId(payload: JwtPayload): string | null {
  const projectId = payload["projectId"];
  return typeof projectId === "string" && projectId.trim().length > 0
    ? projectId.trim()
    : null;
}

function extractCorrelationId(
  value: string | string[] | undefined,
): string | null {
  if (typeof value === "string" && value.trim().length > 0) {
    return value.trim();
  }
  if (Array.isArray(value) && value.length > 0 && value[0].trim().length > 0) {
    return value[0].trim();
  }
  return null;
}

/**
 * Parse a W3C Trace Context `traceparent` header into its traceId and spanId
 * components for OTel-aligned structured log correlation.
 *
 * Format: `{version}-{traceId(32hex)}-{parentId(16hex)}-{flags(2hex)}`
 * Returns `null` when the header is absent or malformed.
 */
function parseTraceparent(
  traceparent: string,
): { traceId: string; spanId: string } | null {
  const parts = traceparent.split("-");
  if (parts.length < 4) {
    return null;
  }
  const traceId = parts[1];
  const spanId = parts[2];
  if (
    typeof traceId === "string" &&
    traceId.length === 32 &&
    typeof spanId === "string" &&
    spanId.length === 16
  ) {
    return { traceId, spanId };
  }
  return null;
}

function resolveCorrelationId(request: FastifyRequest): string {
  return (
    extractCorrelationId(request.headers[CORRELATION_ID_HEADER]) ?? randomUUID()
  );
}

function resolveTraceparent(request: FastifyRequest): string {
  const current = request.headers[TRACEPARENT_HEADER];
  if (typeof current === "string" && current.trim().length > 0) {
    return current.trim();
  }
  const traceId = randomUUID().replace(/-/g, "");
  const spanId = randomUUID().replace(/-/g, "").slice(0, 16);
  return `00-${traceId}-${spanId}-01`;
}

function extractReasonCode(body: unknown): string | undefined {
  if (typeof body === "object" && body !== null && "reasonCode" in body) {
    const reasonCode = (body as { readonly reasonCode?: unknown }).reasonCode;
    return typeof reasonCode === "string" ? reasonCode : undefined;
  }
  return undefined;
}

function extractProviderMode(body: unknown): string | undefined {
  if (typeof body === "object" && body !== null && "providerMode" in body) {
    const providerMode = (body as { readonly providerMode?: unknown })
      .providerMode;
    return typeof providerMode === "string" ? providerMode : undefined;
  }
  return undefined;
}

function extractProductUnitId(request: FastifyRequest): string | undefined {
  const params = request.params as Record<string, string | undefined>;
  return params.productUnitId;
}

function extractPhase(request: FastifyRequest): string | undefined {
  const body = request.body as Record<string, unknown> | undefined;
  if (body && typeof body === "object" && "phase" in body) {
    const phase = body.phase;
    return typeof phase === "string" ? phase : undefined;
  }
  return undefined;
}

function extractRunId(request: FastifyRequest): string | undefined {
  const params = request.params as Record<string, string | undefined>;
  return params.runId;
}

function extractDecision(request: FastifyRequest): string | undefined {
  const body = request.body as Record<string, unknown> | undefined;
  if (body && typeof body === "object" && "approved" in body) {
    const approved = body.approved;
    return typeof approved === "boolean"
      ? approved
        ? "approved"
        : "rejected"
      : undefined;
  }
  return undefined;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function shouldRetryStatus(status: number): boolean {
  return status === 502 || status === 503 || status === 504;
}

async function checkBackendReadiness(
  backendUrl: string,
  correlationId: string,
): Promise<Response> {
  return fetch(`${backendUrl}/health`, {
    method: "GET",
    headers: { [CORRELATION_ID_HEADER]: correlationId },
  });
}

/**
 * Maximum WebSocket message size in bytes (1 MiB).
 * Messages from clients exceeding this are rejected and the connection is closed.
 * Exported so tests can verify the limit without hard-coding it.
 */
export const MAX_WS_MESSAGE_BYTES = 1 * 1024 * 1024; // 1 MiB

/**
 * WebSocket idle timeout in milliseconds (5 minutes).
 * A session is considered idle when no message arrives from either side within this period.
 * An idle session is closed with code 1001 (Going Away).
 * Exported so tests can verify the default without hard-coding it.
 */
export const WS_IDLE_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

/**
 * WebSocket heartbeat (ping) interval in milliseconds (30 seconds).
 * The gateway pings the client at this interval; if no pong is received within the interval
 * the session is considered dead and is terminated.
 * Exported so tests can verify the default without hard-coding it.
 */
export const WS_HEARTBEAT_INTERVAL_MS = 30 * 1000; // 30 seconds

export interface GatewayConfig {
  jwtSecret: string;
  backendUrl: string;
  allowedOrigins: string[];
  /**
   * Action Plane deployment profile. Production, staging, and sovereign profiles
   * must derive tenant identity from authenticated JWT/API-key context.
   */
  deploymentProfile?: "local" | "test" | "embedded" | "staging" | "production" | "sovereign";
  /**
   * API key tenant association map. Keys are raw gateway API keys; values carry
   * tenant/workspace/project claims associated at key creation time.
   */
  apiKeys?: Readonly<Record<string, ApiKeyAuthContext>>;
  logger?: boolean;
  metrics?: GatewayMetrics;
  /** Override the WebSocket idle timeout (ms). Default: WS_IDLE_TIMEOUT_MS. */
  wsIdleTimeoutMs?: number;
  /** Override the WebSocket heartbeat interval (ms). Default: WS_HEARTBEAT_INTERVAL_MS. */
  wsHeartbeatIntervalMs?: number;
  /** Override the maximum WebSocket message size (bytes). Default: MAX_WS_MESSAGE_BYTES. */
  wsMaxMessageBytes?: number;
  /** Max accepted JSON/body payload size. Default: DEFAULT_REQUEST_BODY_LIMIT_BYTES. */
  requestBodyLimitBytes?: number;
  /** Rate-limit window in milliseconds. Default: DEFAULT_RATE_LIMIT_WINDOW_MS. */
  rateLimitWindowMs?: number;
  /** Maximum requests per window per key. Default: DEFAULT_RATE_LIMIT_MAX_REQUESTS. */
  rateLimitMaxRequests?: number;
  /** Backend timeout in milliseconds. Default: DEFAULT_BACKEND_TIMEOUT_MS. */
  backendTimeoutMs?: number;
  /** Retry attempts for transient backend failures. Default: DEFAULT_BACKEND_RETRY_ATTEMPTS. */
  backendRetryAttempts?: number;
  /** Initial retry backoff in milliseconds. Default: DEFAULT_BACKEND_RETRY_INITIAL_BACKOFF_MS. */
  backendRetryInitialBackoffMs?: number;
  /** Maximum retry backoff in milliseconds. Default: DEFAULT_BACKEND_RETRY_MAX_BACKOFF_MS. */
  backendRetryMaxBackoffMs?: number;
  /** Number of failures before opening breaker. Default: DEFAULT_BREAKER_FAILURE_THRESHOLD. */
  backendBreakerFailureThreshold?: number;
  /** Breaker open duration in milliseconds. Default: DEFAULT_BREAKER_OPEN_MS. */
  backendBreakerOpenMs?: number;
  /**
   * Governed Kernel lifecycle action service for agent-proposed product work.
   * When absent, agentic lifecycle routes fail closed instead of proxying to raw tools.
   */
  agentLifecycleActionService?: AgentLifecycleActionServicePort;
  /**
   * Append-only evidence sink for agent lifecycle decisions.
   * Production deployments should back this with Data Cloud's trace ledger.
   */
  agentLifecycleTraceLedger?: AgentLifecycleTraceLedgerPort;
  /**
   * Product-neutral Kernel lifecycle API handlers for Studio/product clients.
   * When absent, /api/kernel routes fail closed instead of proxying to backendUrl.
   */
  kernelLifecycleApi?: KernelLifecycleApiPort;
  /**
   * Durable storage port for Kernel provider records (events, artifacts, health, approvals, etc.).
   * When absent, provider endpoints fail closed. Production should use a Data Cloud-backed implementation.
   */
  providerStore?: ProviderStorePort;
}

export interface ApiKeyAuthContext {
  readonly tenantId: string;
  readonly workspaceId?: string | undefined;
  readonly projectId?: string | undefined;
  readonly userId?: string | undefined;
  readonly roles?: readonly string[] | undefined;
}

type AuthMethod = "jwt" | "api-key";

interface AuthenticatedRequestContext {
  readonly method: AuthMethod;
  readonly tenantId: string | null;
  readonly workspaceId: string | null;
  readonly projectId: string | null;
  readonly userId: string | null;
  readonly roles: readonly string[];
  readonly payload: JwtPayload;
}

function strictTenantProfile(
  profile: GatewayConfig["deploymentProfile"],
): boolean {
  return (
    profile === "production" ||
    profile === "staging" ||
    profile === "sovereign"
  );
}

function extractApiKey(
  request: FastifyRequest,
): string | null {
  const headerValue = request.headers["x-api-key"];
  if (typeof headerValue === "string" && headerValue.trim().length > 0) {
    return headerValue.trim();
  }
  if (Array.isArray(headerValue) && headerValue[0]?.trim()) {
    return headerValue[0].trim();
  }
  return null;
}

function resolveApiKeyContext(
  apiKey: string,
  apiKeys: GatewayConfig["apiKeys"],
): AuthenticatedRequestContext | null {
  const context = apiKeys?.[apiKey];
  if (!context) {
    return null;
  }
  return {
    method: "api-key",
    tenantId: context.tenantId,
    workspaceId: context.workspaceId ?? null,
    projectId: context.projectId ?? null,
    userId: context.userId ?? `api-key:${context.tenantId}`,
    roles: context.roles ?? [],
    payload: {
      sub: context.userId ?? `api-key:${context.tenantId}`,
      tenant_id: context.tenantId,
      tenantId: context.tenantId,
      workspaceId: context.workspaceId,
      projectId: context.projectId,
      roles: context.roles,
      authMethod: "api-key",
    },
  };
}

function tenantHintFromQuery(request: FastifyRequest): string | null {
  const query = request.query as Record<string, unknown>;
  const tenantId = query["tenantId"];
  return typeof tenantId === "string" && tenantId.trim().length > 0
    ? tenantId.trim()
    : null;
}

async function appendAgentLifecycleTrace(
  ledger: AgentLifecycleTraceLedgerPort | undefined,
  entry: AgentLifecycleTraceLedgerEntry,
): Promise<void> {
  if (!ledger) {
    return;
  }
  await ledger.append(entry);
}

function agentLifecycleTraceEntry(
  request: AgentLifecycleActionRequest,
  context: {
    readonly traceId?: string;
    readonly spanId?: string;
    readonly outcome: AgentLifecycleTraceLedgerEntry["outcome"];
    readonly reasonCode?: string;
    readonly observedAt?: string;
  },
): AgentLifecycleTraceLedgerEntry {
  return {
    correlationId: request.correlationId,
    ...(context.traceId === undefined ? {} : { traceId: context.traceId }),
    ...(context.spanId === undefined ? {} : { spanId: context.spanId }),
    requestId: request.requestId,
    productUnitId: request.productUnitId,
    tenantId: request.scope.tenantId,
    workspaceId: request.scope.workspaceId,
    projectId: request.scope.projectId,
    agentId: request.requestedByAgent,
    agentVersion: request.requestedByAgentVersion,
    action: request.requestedAction,
    outcome: context.outcome,
    riskLevel: request.riskLevel,
    ...(context.reasonCode === undefined
      ? {}
      : { reasonCode: context.reasonCode }),
    evidenceRefs: request.evidenceRefs,
    fallbackMode: request.fallbackMode,
    observedAt: context.observedAt ?? new Date().toISOString(),
  };
}

export async function buildApp(
  config: GatewayConfig,
): Promise<FastifyInstance> {
  const fastify = Fastify({
    logger: config.logger ?? false,
    bodyLimit: config.requestBodyLimitBytes ?? DEFAULT_REQUEST_BODY_LIMIT_BYTES,
  });
  const metrics = config.metrics ?? new GatewayMetrics();
  const wsIdleTimeoutMs = config.wsIdleTimeoutMs ?? WS_IDLE_TIMEOUT_MS;
  const wsHeartbeatIntervalMs =
    config.wsHeartbeatIntervalMs ?? WS_HEARTBEAT_INTERVAL_MS;
  const wsMaxMessageBytes = config.wsMaxMessageBytes ?? MAX_WS_MESSAGE_BYTES;
  const rateLimitWindowMs =
    config.rateLimitWindowMs ?? DEFAULT_RATE_LIMIT_WINDOW_MS;
  const rateLimitMaxRequests =
    config.rateLimitMaxRequests ?? DEFAULT_RATE_LIMIT_MAX_REQUESTS;
  const backendTimeoutMs =
    config.backendTimeoutMs ?? DEFAULT_BACKEND_TIMEOUT_MS;
  const backendRetryAttempts =
    config.backendRetryAttempts ?? DEFAULT_BACKEND_RETRY_ATTEMPTS;
  const backendRetryInitialBackoffMs =
    config.backendRetryInitialBackoffMs ??
    DEFAULT_BACKEND_RETRY_INITIAL_BACKOFF_MS;
  const backendRetryMaxBackoffMs =
    config.backendRetryMaxBackoffMs ?? DEFAULT_BACKEND_RETRY_MAX_BACKOFF_MS;
  const backendBreakerFailureThreshold =
    config.backendBreakerFailureThreshold ?? DEFAULT_BREAKER_FAILURE_THRESHOLD;
  const backendBreakerOpenMs =
    config.backendBreakerOpenMs ?? DEFAULT_BREAKER_OPEN_MS;

  const requestCounters = new Map<
    string,
    { count: number; resetAtMs: number }
  >();
  const breakerState: { failures: number; openUntilMs: number } = {
    failures: 0,
    openUntilMs: 0,
  };

  function rateLimitKey(request: FastifyRequest): string {
    const tenantId = resolveHeaderTenantId(request.headers);
    if (tenantId) {
      return `tenant:${tenantId}`;
    }
    return `ip:${request.ip}`;
  }

  function isRateLimited(request: FastifyRequest, nowMs: number): boolean {
    const key = rateLimitKey(request);
    const current = requestCounters.get(key);
    if (!current || nowMs > current.resetAtMs) {
      requestCounters.set(key, {
        count: 1,
        resetAtMs: nowMs + rateLimitWindowMs,
      });
      return false;
    }
    current.count += 1;
    requestCounters.set(key, current);
    return current.count > rateLimitMaxRequests;
  }

  function isBreakerOpen(nowMs: number): boolean {
    return nowMs < breakerState.openUntilMs;
  }

  function markBackendSuccess(): void {
    breakerState.failures = 0;
  }

  function markBackendFailure(nowMs: number): void {
    breakerState.failures += 1;
    if (breakerState.failures >= backendBreakerFailureThreshold) {
      breakerState.openUntilMs = nowMs + backendBreakerOpenMs;
      breakerState.failures = 0;
    }
  }

  async function fetchWithResilience(
    url: string,
    init: RequestInit,
    correlationId: string,
  ): Promise<Response> {
    const nowMs = Date.now();
    if (isBreakerOpen(nowMs)) {
      throw new Error("BACKEND_CIRCUIT_OPEN");
    }

    let attempt = 0;
    let backoffMs = backendRetryInitialBackoffMs;
    let lastErr: unknown = null;

    while (attempt < backendRetryAttempts) {
      attempt += 1;
      const abortController = new AbortController();
      const timeout = setTimeout(
        () => abortController.abort(),
        backendTimeoutMs,
      );

      try {
        const response = await fetch(url, {
          ...init,
          signal: abortController.signal,
        });
        clearTimeout(timeout);

        if (
          shouldRetryStatus(response.status) &&
          attempt < backendRetryAttempts
        ) {
          await sleep(backoffMs);
          backoffMs = Math.min(backoffMs * 2, backendRetryMaxBackoffMs);
          continue;
        }

        markBackendSuccess();
        return response;
      } catch (err: unknown) {
        clearTimeout(timeout);
        lastErr = err;
        if (attempt < backendRetryAttempts) {
          fastify.log.warn(
            { correlationId, attempt, err },
            "Gateway backend call failed; retrying with backoff",
          );
          await sleep(backoffMs);
          backoffMs = Math.min(backoffMs * 2, backendRetryMaxBackoffMs);
          continue;
        }
      }
    }

    markBackendFailure(Date.now());
    throw lastErr instanceof Error ? lastErr : new Error("BACKEND_UNREACHABLE");
  }

  fastify.addHook("onRequest", async (request, reply) => {
    const correlationId = resolveCorrelationId(request);
    const traceparent = resolveTraceparent(request);
    const startedAtMs = Date.now();
    const traceContext = parseTraceparent(traceparent);

    reply.header(CORRELATION_ID_HEADER, correlationId);
    reply.header(TRACEPARENT_HEADER, traceparent);

    (request as FastifyRequest & { correlationId?: string }).correlationId =
      correlationId;
    (request as FastifyRequest & { traceparent?: string }).traceparent =
      traceparent;
    (request as FastifyRequest & { startedAtMs?: number }).startedAtMs =
      startedAtMs;
    (request as FastifyRequest & { traceId?: string }).traceId =
      traceContext?.traceId;
    (request as FastifyRequest & { spanId?: string }).spanId =
      traceContext?.spanId;

    if (isRateLimited(request, startedAtMs)) {
      metrics.recordAuthFailure("rate_limited");
      fastify.log.warn(
        {
          correlationId,
          traceId: traceContext?.traceId,
          spanId: traceContext?.spanId,
          method: request.method,
          path: request.url,
          rateLimitKey: rateLimitKey(request),
        },
        "Gateway rate limit exceeded",
      );
      return reply.status(429).send({
        error: "Too Many Requests",
        message: "Rate limit exceeded for gateway",
        correlationId,
      });
    }
  });

  fastify.addHook("onResponse", async (request, reply) => {
    const startedAtMs = (request as FastifyRequest & { startedAtMs?: number })
      .startedAtMs;
    const correlationId =
      (request as FastifyRequest & { correlationId?: string }).correlationId ??
      resolveCorrelationId(request);
    const traceId = (request as FastifyRequest & { traceId?: string }).traceId;
    const spanId = (request as FastifyRequest & { spanId?: string }).spanId;
    const durationMs =
      typeof startedAtMs === "number" ? Date.now() - startedAtMs : undefined;

    fastify.log.info(
      {
        correlationId,
        traceId,
        spanId,
        method: request.method,
        path: request.url,
        statusCode: reply.statusCode,
        durationMs,
      },
      "Gateway request completed",
    );
  });

  await fastify.register(fastifyCors, {
    origin: config.allowedOrigins,
    methods: ["GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"],
    allowedHeaders: [
      "Content-Type",
      "Authorization",
      "X-API-Key",
      "X-Tenant-Id",
      "X-Correlation-ID",
      "X-Ghatana-Tenant-Id",
      "X-Ghatana-Workspace-Id",
      "X-Ghatana-Project-Id",
    ],
    credentials: true,
  });

  await fastify.register(fastifyWebsocket);

  // ── Authentication preHandler ────────────────────────────────────────────────
  async function authenticate(
    request: FastifyRequest,
    reply: FastifyReply,
  ): Promise<void> {
    const correlationId =
      (request as FastifyRequest & { correlationId?: string }).correlationId ??
      resolveCorrelationId(request);
    const token = extractBearerToken(request.headers.authorization);
    const apiKey = extractApiKey(request);
    if (!token && !apiKey) {
      metrics.recordAuthFailure("missing_token");
      reply.header(CORRELATION_ID_HEADER, correlationId);
      void reply.status(401).send({
        error: "Unauthorized",
        reasonCode: "TENANT_REQUIRED",
        message: "Missing Bearer token or API key",
        correlationId,
      });
      return;
    }
    try {
      let authContext: AuthenticatedRequestContext;
      if (token) {
        const payload = verifyJwt(token, config.jwtSecret);
        authContext = {
          method: "jwt",
          tenantId: extractPayloadTenantId(payload),
          workspaceId: extractWorkspaceId(payload),
          projectId: extractProjectId(payload),
          userId: extractUserId(payload),
          roles: extractRoles(payload),
          payload,
        };
      } else {
        const apiKeyContext = resolveApiKeyContext(apiKey ?? "", config.apiKeys);
        if (!apiKeyContext) {
          metrics.recordAuthFailure("invalid_api_key");
          reply.header(CORRELATION_ID_HEADER, correlationId);
          void reply.status(401).send({
            error: "Unauthorized",
            reasonCode: "TENANT_REQUIRED",
            message: "Invalid API key",
            correlationId,
          });
          return;
        }
        authContext = apiKeyContext;
      }

      const strictTenant = strictTenantProfile(config.deploymentProfile ?? "local");
      const headerTenantId = resolveHeaderTenantId(request.headers);
      const queryTenantId = tenantHintFromQuery(request);
      const payloadTenantId = authContext.tenantId;
      if (strictTenant && !payloadTenantId) {
        metrics.recordAuthFailure("missing_tenant_claim");
        reply.header(CORRELATION_ID_HEADER, correlationId);
        void reply.status(400).send({
          error: "Bad Request",
          reasonCode: "MISSING_TENANT_CLAIM",
          message:
            "Authenticated identity must include tenant_id claim or API-key tenant association",
          correlationId,
        });
        return;
      }
      if (
        headerTenantId &&
        payloadTenantId &&
        headerTenantId !== payloadTenantId
      ) {
        metrics.recordTenantMismatch();
        metrics.recordAuthFailure("tenant_mismatch");
        reply.header(CORRELATION_ID_HEADER, correlationId);
        void reply.status(403).send({
          error: "Forbidden",
          reasonCode: "TENANT_MISMATCH",
          message: "Tenant mismatch between X-Tenant-Id header and JWT payload",
          correlationId,
        });
        return;
      }
      if (
        queryTenantId &&
        payloadTenantId &&
        queryTenantId !== payloadTenantId
      ) {
        metrics.recordTenantMismatch();
        metrics.recordAuthFailure("tenant_mismatch");
        reply.header(CORRELATION_ID_HEADER, correlationId);
        void reply.status(403).send({
          error: "Forbidden",
          reasonCode: "TENANT_MISMATCH",
          message:
            "Tenant mismatch between tenantId query parameter and authenticated tenant",
          correlationId,
        });
        return;
      }
      (request as FastifyRequest & { user: JwtPayload }).user = authContext.payload;
      (request as FastifyRequest & { authContext: AuthenticatedRequestContext }).authContext =
        authContext;
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Invalid token";
      metrics.recordAuthFailure("invalid_token");
      reply.header(CORRELATION_ID_HEADER, correlationId);
      void reply
        .status(401)
        .send({ error: "Unauthorized", message: msg, correlationId });
    }
  }

  // ── Health probe (no auth) ───────────────────────────────────────────────────
  fastify.get("/health", async (request, reply) => {
    const correlationId = resolveCorrelationId(request);
    reply.header("x-correlation-id", correlationId);
    return { status: "ok", timestamp: new Date().toISOString(), correlationId };
  });

  fastify.get("/ready", async (request, reply) => {
    const correlationId =
      (request as FastifyRequest & { correlationId?: string }).correlationId ??
      resolveCorrelationId(request);
    reply.header("x-correlation-id", correlationId);

    let backendRes: Response;
    try {
      backendRes = await fetchWithResilience(
        `${config.backendUrl}/health`,
        {
          method: "GET",
          headers: { [CORRELATION_ID_HEADER]: correlationId },
        },
        correlationId,
      );
    } catch (err: unknown) {
      fastify.log.error(
        err,
        `Backend readiness probe failed at ${config.backendUrl}/health`,
      );
      return reply.status(503).send({
        status: "not-ready",
        dependency: "aep-backend",
        message: "AEP backend unreachable",
        correlationId,
      });
    }

    if (!backendRes.ok) {
      return reply.status(503).send({
        status: "not-ready",
        dependency: "aep-backend",
        message: `AEP backend health probe returned ${backendRes.status}`,
        correlationId,
      });
    }

    return {
      status: "ready",
      dependency: "aep-backend",
      correlationId,
    };
  });

  fastify.post(
    "/api/v1/agentic/lifecycle-actions",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const correlationId =
        (request as FastifyRequest & { correlationId?: string })
          .correlationId ?? resolveCorrelationId(request);
      const traceId = (request as FastifyRequest & { traceId?: string })
        .traceId;
      const spanId = (request as FastifyRequest & { spanId?: string }).spanId;
      const startedAtMs = Date.now();
      reply.header(CORRELATION_ID_HEADER, correlationId);

      const service = config.agentLifecycleActionService;
      if (!service) {
        metrics.recordAgenticAction("service_unavailable");
        fastify.log.error(
          { correlationId },
          "Agent lifecycle action service is not configured; refusing raw action-plane execution",
        );
        return reply.status(503).send({
          error: "Service Unavailable",
          message:
            "Agent lifecycle actions require the governed Kernel lifecycle service",
          correlationId,
        });
      }

      // Extract authz metadata from JWT
      const user = (request as FastifyRequest & { user: JwtPayload }).user;
      const userId = extractUserId(user);
      const allowedTools = extractStringArrayClaim(user, "allowedTools");
      const allowedProductUnitIds = extractStringArrayClaim(
        user,
        "allowedProductUnitIds",
      );
      const maxRiskLevel = extractMaxRiskLevel(user);
      const jwtTenantId = extractPayloadTenantId(user);
      const jwtWorkspaceId = extractWorkspaceId(user);
      const jwtProjectId = extractProjectId(user);

      // Validate scope for tenant/workspace/project
      const headerTenantId = resolveHeaderTenantId(request.headers);
      const headerWorkspaceId = extractHeaderWorkspaceId(request.headers);
      const headerProjectId = extractHeaderProjectId(request.headers);
      const effectiveTenantId = jwtTenantId ?? headerTenantId;
      const effectiveWorkspaceId = jwtWorkspaceId ?? headerWorkspaceId;
      const effectiveProjectId = jwtProjectId ?? headerProjectId;

      if (!effectiveTenantId || !effectiveWorkspaceId || !effectiveProjectId) {
        metrics.recordAgenticAction("scope_denied");
        fastify.log.warn(
          { correlationId, userId },
          "Tenant, workspace, and project scope required for agentic actions",
        );
        return reply.status(403).send({
          error: "Forbidden",
          message:
            "Tenant, workspace, and project scope required for agentic actions",
          correlationId,
        });
      }

      // Validate request schema
      const parsedRequest = AgentLifecycleActionRequestSchema.safeParse(
        request.body,
      );
      if (!parsedRequest.success) {
        metrics.recordAgenticAction("schema_validation_failed");
        fastify.log.warn(
          { correlationId, userId, issues: parsedRequest.error.issues },
          "Agent lifecycle action request schema validation failed",
        );
        return reply.status(400).send({
          error: "Bad Request",
          message: "Invalid agent lifecycle action request",
          issues: parsedRequest.error.issues.map((issue) => ({
            path: issue.path.map((segment) => String(segment)).join("."),
            message: issue.message,
          })),
          correlationId,
        });
      }

      const actionRequest = parsedRequest.data;
      await appendAgentLifecycleTrace(
        config.agentLifecycleTraceLedger,
        agentLifecycleTraceEntry(actionRequest, {
          traceId,
          spanId,
          outcome: "received",
        }),
      );

      // Validate scope matches request
      if (actionRequest.scope.tenantId !== effectiveTenantId) {
        metrics.recordAgenticAction("scope_denied");
        fastify.log.warn(
          {
            correlationId,
            userId,
            requestTenantId: actionRequest.scope.tenantId,
            effectiveTenantId,
          },
          "Tenant scope mismatch",
        );
        return reply.status(403).send({
          error: "Forbidden",
          message: "Tenant scope mismatch between request and auth context",
          correlationId,
        });
      }

      if (actionRequest.scope.workspaceId !== effectiveWorkspaceId) {
        metrics.recordAgenticAction("scope_denied");
        fastify.log.warn(
          {
            correlationId,
            userId,
            requestWorkspaceId: actionRequest.scope.workspaceId,
            effectiveWorkspaceId,
          },
          "Workspace scope mismatch",
        );
        return reply.status(403).send({
          error: "Forbidden",
          message: "Workspace scope mismatch between request and auth context",
          correlationId,
        });
      }

      if (actionRequest.scope.projectId !== effectiveProjectId) {
        metrics.recordAgenticAction("scope_denied");
        fastify.log.warn(
          {
            correlationId,
            userId,
            requestProjectId: actionRequest.scope.projectId,
            effectiveProjectId,
          },
          "Project scope mismatch",
        );
        return reply.status(403).send({
          error: "Forbidden",
          message: "Project scope mismatch between request and auth context",
          correlationId,
        });
      }

      if (
        allowedProductUnitIds.length > 0 &&
        !allowedProductUnitIds.includes(actionRequest.productUnitId)
      ) {
        metrics.recordAgenticAction("policy_denied:product_scope");
        await appendAgentLifecycleTrace(
          config.agentLifecycleTraceLedger,
          agentLifecycleTraceEntry(actionRequest, {
            traceId,
            spanId,
            outcome: "policy-denied",
            reasonCode: "product-scope-denied",
          }),
        );
        fastify.log.warn(
          {
            correlationId,
            userId,
            productUnitId: actionRequest.productUnitId,
            allowedProductUnitIds,
          },
          "Agent lifecycle action denied by product scope policy",
        );
        return reply.status(403).send({
          error: "Forbidden",
          reasonCode: "product-scope-denied",
          message: "Agent is not permitted to operate on this ProductUnit",
          correlationId,
        });
      }

      const permittedToolIds = actionRequest.toolPermissions
        .filter(
          (permission) =>
            permission.granted &&
            permission.allowedActions.includes(actionRequest.requestedAction),
        )
        .map((permission) => permission.toolId);
      if (permittedToolIds.length === 0) {
        metrics.recordAgenticAction("policy_denied:missing_tool_permission");
        await appendAgentLifecycleTrace(
          config.agentLifecycleTraceLedger,
          agentLifecycleTraceEntry(actionRequest, {
            traceId,
            spanId,
            outcome: "policy-denied",
            reasonCode: "missing-tool-permission",
          }),
        );
        return reply.status(403).send({
          error: "Forbidden",
          reasonCode: "missing-tool-permission",
          message:
            "Agent lifecycle action requires a granted matching tool permission",
          correlationId,
        });
      }

      if (
        allowedTools.length > 0 &&
        permittedToolIds.every((toolId) => !allowedTools.includes(toolId))
      ) {
        metrics.recordAgenticAction("policy_denied:tool_not_allowed");
        await appendAgentLifecycleTrace(
          config.agentLifecycleTraceLedger,
          agentLifecycleTraceEntry(actionRequest, {
            traceId,
            spanId,
            outcome: "policy-denied",
            reasonCode: "tool-not-allowed",
          }),
        );
        fastify.log.warn(
          { correlationId, userId, permittedToolIds, allowedTools },
          "Agent lifecycle action denied by tool policy",
        );
        return reply.status(403).send({
          error: "Forbidden",
          reasonCode: "tool-not-allowed",
          message:
            "Agent is not permitted to use the requested Kernel lifecycle tool",
          correlationId,
        });
      }

      if (
        riskLevelRank(actionRequest.riskLevel) > riskLevelRank(maxRiskLevel)
      ) {
        metrics.recordAgenticAction("policy_denied:risk_threshold");
        await appendAgentLifecycleTrace(
          config.agentLifecycleTraceLedger,
          agentLifecycleTraceEntry(actionRequest, {
            traceId,
            spanId,
            outcome: "policy-denied",
            reasonCode: "risk-threshold-exceeded",
          }),
        );
        fastify.log.warn(
          {
            correlationId,
            userId,
            riskLevel: actionRequest.riskLevel,
            maxRiskLevel,
          },
          "Agent lifecycle action denied by risk threshold policy",
        );
        return reply.status(403).send({
          error: "Forbidden",
          reasonCode: "risk-threshold-exceeded",
          message:
            "Agent lifecycle action risk exceeds the authenticated risk threshold",
          correlationId,
        });
      }

      try {
        const result = await service.handle(actionRequest);
        const durationMs = Date.now() - startedAtMs;
        const parsedResult = AgentLifecycleActionResultSchema.safeParse(result);
        if (!parsedResult.success) {
          metrics.recordAgenticAction("invalid_kernel_result", durationMs);
          await appendAgentLifecycleTrace(
            config.agentLifecycleTraceLedger,
            agentLifecycleTraceEntry(actionRequest, {
              traceId,
              spanId,
              outcome: "failed",
              reasonCode: "invalid-kernel-result",
            }),
          );
          fastify.log.error(
            { correlationId, issues: parsedResult.error.issues },
            "Kernel lifecycle service returned an invalid agent action result",
          );
          return reply.status(502).send({
            error: "Bad Gateway",
            reasonCode: "invalid-kernel-result",
            message:
              "Kernel lifecycle service returned an invalid agent action result",
            correlationId,
          });
        }

        // Determine result status for metrics
        const actionResult = parsedResult.data;
        const resultStatus = actionResult.policyDecision;
        if (resultStatus === "allowed") {
          metrics.recordAgenticAction("accepted", durationMs);
          await appendAgentLifecycleTrace(
            config.agentLifecycleTraceLedger,
            agentLifecycleTraceEntry(actionRequest, {
              traceId,
              spanId,
              outcome: "accepted",
            }),
          );
        } else if (resultStatus === "requires-approval") {
          metrics.recordAgenticAction("requires_approval", durationMs);
          await appendAgentLifecycleTrace(
            config.agentLifecycleTraceLedger,
            agentLifecycleTraceEntry(actionRequest, {
              traceId,
              spanId,
              outcome: "requires-approval",
              reasonCode: actionResult.failure?.reasonCode,
            }),
          );
        } else if (resultStatus === "denied") {
          metrics.recordAgenticAction("denied", durationMs);
          await appendAgentLifecycleTrace(
            config.agentLifecycleTraceLedger,
            agentLifecycleTraceEntry(actionRequest, {
              traceId,
              spanId,
              outcome: "policy-denied",
              reasonCode:
                actionResult.failure?.reasonCode ?? "kernel-policy-denied",
            }),
          );
        }

        await appendAgentLifecycleTrace(
          config.agentLifecycleTraceLedger,
          agentLifecycleTraceEntry(actionRequest, {
            traceId,
            spanId,
            outcome: "fallback-recorded",
            reasonCode: actionRequest.fallbackMode,
          }),
        );

        // Audit log event
        fastify.log.info(
          {
            correlationId,
            traceId,
            spanId,
            userId,
            requestId: actionRequest.requestId,
            productUnitId: actionRequest.productUnitId,
            requestedAction: actionRequest.requestedAction,
            lifecyclePhase: actionRequest.lifecyclePhase,
            riskLevel: actionRequest.riskLevel,
            resultStatus,
            durationMs,
          },
          "Agent lifecycle action request completed",
        );

        return reply.status(200).send(actionResult);
      } catch (err: unknown) {
        const durationMs = Date.now() - startedAtMs;
        metrics.recordAgenticAction("failed", durationMs);
        if (parsedRequest.success) {
          await appendAgentLifecycleTrace(
            config.agentLifecycleTraceLedger,
            agentLifecycleTraceEntry(parsedRequest.data, {
              traceId,
              spanId,
              outcome: "failed",
              reasonCode:
                err instanceof Error ? err.message : "agent-action-failed",
            }),
          );
        }
        fastify.log.warn(
          { correlationId, userId, err },
          "Agent lifecycle action request rejected",
        );
        return reply.status(400).send({
          error: "Bad Request",
          message:
            err instanceof Error
              ? err.message
              : "Invalid agent lifecycle action request",
          correlationId,
        });
      }
    },
  );

  async function dispatchKernelLifecycleApi(
    operation: keyof KernelLifecycleApiPort,
    request: FastifyRequest,
    reply: FastifyReply,
  ): Promise<FastifyReply> {
    const correlationId =
      (request as FastifyRequest & { correlationId?: string }).correlationId ??
      resolveCorrelationId(request);
    const traceId = (request as FastifyRequest & { traceId?: string }).traceId;
    const spanId = (request as FastifyRequest & { spanId?: string }).spanId;
    reply.header(CORRELATION_ID_HEADER, correlationId);

    const kernelLifecycleApi = config.kernelLifecycleApi;
    if (kernelLifecycleApi === undefined) {
      metrics.recordKernelLifecycleRequest(String(operation), 503);
      fastify.log.error(
        { correlationId, operation },
        "Kernel lifecycle API is not configured; refusing backend proxy fallback",
      );
      return reply.status(503).send({
        error: "Service Unavailable",
        message:
          "Kernel lifecycle API requires an injected KernelLifecycleApiHandlers instance",
        correlationId,
      });
    }

    // Extract authz metadata from JWT
    const user = (request as FastifyRequest & { user: JwtPayload }).user;
    const userId = extractUserId(user);
    const roles = extractRoles(user);
    const jwtWorkspaceId = extractWorkspaceId(user);
    const jwtProjectId = extractProjectId(user);

    // Validate workspace/project scope for lifecycle operations
    const headerWorkspaceId = extractHeaderWorkspaceId(request.headers);
    const headerProjectId = extractHeaderProjectId(request.headers);
    const effectiveWorkspaceId = jwtWorkspaceId ?? headerWorkspaceId;
    const effectiveProjectId = jwtProjectId ?? headerProjectId;

    // Lifecycle operations require workspace and project scope
    const lifecycleOperations: readonly (keyof KernelLifecycleApiPort)[] = [
      "createLifecyclePlan",
      "executeLifecyclePhase",
      "listLifecycleRuns",
      "getLifecycleRun",
      "getGateResultManifest",
      "getArtifactManifest",
      "getDeploymentManifest",
      "getVerifyHealthReport",
      "requestApproval",
      "submitApprovalDecision",
    ];

    if (lifecycleOperations.includes(operation)) {
      if (!effectiveWorkspaceId || !effectiveProjectId) {
        metrics.recordKernelLifecycleRequest(String(operation), 403);
        fastify.log.warn(
          { correlationId, operation, userId },
          "Workspace and project scope required for lifecycle operations",
        );
        return reply.status(403).send({
          error: "Forbidden",
          message:
            "Workspace and project scope required for lifecycle operations",
          correlationId,
        });
      }
    }

    try {
      const apiRequest = toKernelLifecycleApiRequest(request, {
        userId,
        roles,
        effectiveWorkspaceId,
        effectiveProjectId,
      });
      const response = await kernelLifecycleApi[operation](apiRequest);

      // Extract metrics labels from response
      const reasonCode = extractReasonCode(response.body);
      const providerMode = extractProviderMode(response.body);

      metrics.recordKernelLifecycleRequest(
        String(operation),
        response.statusCode,
        providerMode,
        reasonCode,
      );

      // Audit log metadata
      fastify.log.info(
        {
          correlationId,
          traceId,
          spanId,
          operation,
          userId,
          productUnitId: extractProductUnitId(request),
          phase: extractPhase(request),
          runId: extractRunId(request),
          decision: extractDecision(request),
          status: response.statusCode,
          reasonCode,
          providerMode,
        },
        "Kernel lifecycle API request completed",
      );

      return sendKernelLifecycleApiResponse(reply, response);
    } catch (err: unknown) {
      metrics.recordKernelLifecycleRequest(String(operation), 500);
      fastify.log.error(
        { correlationId, operation, err },
        "Kernel lifecycle API handler failed",
      );
      return reply.status(500).send({
        error: "Internal Server Error",
        message: "Kernel lifecycle API handler failed",
        correlationId,
      });
    }
  }

  function toKernelLifecycleApiRequest(
    request: FastifyRequest,
    authzMetadata?: {
      userId: string | null;
      roles: string[];
      effectiveWorkspaceId: string | null;
      effectiveProjectId: string | null;
    },
  ): KernelLifecycleApiRequest {
    const headers: Record<string, string> = {};
    for (const [key, value] of Object.entries(request.headers)) {
      if (typeof value === "string") {
        headers[key] = value;
      }
    }

    // Add authz metadata as headers
    if (authzMetadata?.userId) {
      headers["x-user-id"] = authzMetadata.userId;
    }
    if (authzMetadata?.roles && authzMetadata.roles.length > 0) {
      headers["x-user-roles"] = authzMetadata.roles.join(",");
    }
    if (authzMetadata?.effectiveWorkspaceId) {
      headers["x-ghatana-workspace-id"] = authzMetadata.effectiveWorkspaceId;
    }
    if (authzMetadata?.effectiveProjectId) {
      headers["x-ghatana-project-id"] = authzMetadata.effectiveProjectId;
    }

    return {
      params: request.params as Record<string, string | undefined>,
      query: request.query as Record<
        string,
        string | number | boolean | undefined
      >,
      body: request.body,
      headers,
    };
  }

  function sendKernelLifecycleApiResponse(
    reply: FastifyReply,
    response: KernelLifecycleApiResponse,
  ): FastifyReply {
    for (const [header, value] of Object.entries(response.headers)) {
      reply.header(header, value);
    }
    return reply.status(response.statusCode).send(response.body);
  }

  fastify.get(
    "/api/kernel/product-units",
    { preHandler: [authenticate] },
    async (request, reply) =>
      dispatchKernelLifecycleApi("listProductUnits", request, reply),
  );
  fastify.get(
    "/api/kernel/product-units/:productUnitId",
    { preHandler: [authenticate] },
    async (request, reply) =>
      dispatchKernelLifecycleApi("getProductUnit", request, reply),
  );
  fastify.post(
    "/api/kernel/product-units/:productUnitId/lifecycle/plans",
    { preHandler: [authenticate] },
    async (request, reply) =>
      dispatchKernelLifecycleApi("createLifecyclePlan", request, reply),
  );
  fastify.post(
    "/api/kernel/product-units/:productUnitId/lifecycle/execute",
    { preHandler: [authenticate] },
    async (request, reply) =>
      dispatchKernelLifecycleApi("executeLifecyclePhase", request, reply),
  );
  fastify.get(
    "/api/kernel/product-units/:productUnitId/lifecycle/runs",
    { preHandler: [authenticate] },
    async (request, reply) =>
      dispatchKernelLifecycleApi("listLifecycleRuns", request, reply),
  );
  fastify.get(
    "/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId",
    { preHandler: [authenticate] },
    async (request, reply) =>
      dispatchKernelLifecycleApi("getLifecycleRun", request, reply),
  );
  fastify.get(
    "/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId/gate-result-manifest",
    { preHandler: [authenticate] },
    async (request, reply) =>
      dispatchKernelLifecycleApi("getGateResultManifest", request, reply),
  );
  fastify.get(
    "/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId/artifact-manifest",
    { preHandler: [authenticate] },
    async (request, reply) =>
      dispatchKernelLifecycleApi("getArtifactManifest", request, reply),
  );
  fastify.get(
    "/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId/deployment-manifest",
    { preHandler: [authenticate] },
    async (request, reply) =>
      dispatchKernelLifecycleApi("getDeploymentManifest", request, reply),
  );
  fastify.get(
    "/api/kernel/product-units/:productUnitId/lifecycle/runs/:runId/verify-health-report",
    { preHandler: [authenticate] },
    async (request, reply) =>
      dispatchKernelLifecycleApi("getVerifyHealthReport", request, reply),
  );
  fastify.post(
    "/api/kernel/approvals",
    { preHandler: [authenticate] },
    async (request, reply) =>
      dispatchKernelLifecycleApi("requestApproval", request, reply),
  );
  fastify.post(
    "/api/kernel/approvals/:approvalId/decisions",
    { preHandler: [authenticate] },
    async (request, reply) =>
      dispatchKernelLifecycleApi("submitApprovalDecision", request, reply),
  );

  // ── Kernel Provider Endpoints (explicit routes, not generic proxy) ───────────
  // Durable storage for provider records must be injected; production must fail closed otherwise.
  const providerStore = config.providerStore;

  function sendProviderStoreUnavailable(
    reply: FastifyReply,
    correlationId: string,
  ): FastifyReply {
    return reply.status(503).send({
      success: false,
      error: "Kernel provider store is not configured",
      reasonCode: "PROVIDER_STORE_UNAVAILABLE",
      correlationId,
    });
  }

  function generateProviderRef(): string {
    return `ref-${randomUUID()}`;
  }

  function redactSensitiveFields(data: unknown): unknown {
    if (typeof data !== "object" || data === null) {
      return data;
    }
    if (Array.isArray(data)) {
      return data.map(redactSensitiveFields);
    }
    const redacted: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(data)) {
      if (key === "authToken" || key === "authorization" || key === "token") {
        redacted[key] = "[REDACTED]";
      } else if (key === "privacyClassification" && value === "restricted") {
        redacted[key] = value;
      } else if (
        (key === "content" || key === "payload" || key === "contentRef") &&
        typeof data === "object" &&
        "privacyClassification" in data &&
        (data as Record<string, unknown>).privacyClassification === "restricted"
      ) {
        redacted[key] = "[REDACTED]";
      } else {
        redacted[key] = redactSensitiveFields(value);
      }
    }
    return redacted;
  }

  function validateScopeForProvider(request: FastifyRequest): {
    readonly tenantId: string;
    readonly workspaceId: string;
    readonly projectId: string;
    readonly userId: string;
    readonly correlationId: string;
  } | null {
    const typedRequest = request as FastifyRequest & {
      user: JwtPayload;
      correlationId?: string;
    };
    const user = typedRequest.user;
    const tenantId =
      extractPayloadTenantId(user) ?? resolveHeaderTenantId(request.headers);
    const workspaceId =
      extractWorkspaceId(user) ?? extractHeaderWorkspaceId(request.headers);
    const projectId =
      extractProjectId(user) ?? extractHeaderProjectId(request.headers);
    const userId = extractUserId(user);
    const correlationId =
      typedRequest.correlationId ?? resolveCorrelationId(request);

    if (!tenantId || !workspaceId || !projectId || !userId) {
      return null;
    }
    return { tenantId, workspaceId, projectId, userId, correlationId };
  }

  // POST /api/v1/kernel/providers/events
  fastify.post(
    "/api/v1/kernel/providers/events",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const parsedEvent = KernelLifecycleEventSchema.safeParse(request.body);
      if (!parsedEvent.success) {
        return reply.status(400).send({
          success: false,
          error: "Invalid event schema",
          reasonCode: "INVALID_SCHEMA",
          issues: parsedEvent.error.issues,
          correlationId: scope.correlationId,
        });
      }
      const ref = generateProviderRef();
      const createdAt = new Date().toISOString();
      const record: ProviderRecord = {
        id: ref,
        tenantId: scope.tenantId,
        workspaceId: scope.workspaceId,
        projectId: scope.projectId,
        providerType: "events",
        providerRef: ref,
        data: parsedEvent.data,
        privacyClassification: undefined,
        expiresAt: undefined,
        createdAt,
        createdBy: scope.userId,
      };
      await providerStore.save(record);
      return reply
        .status(200)
        .send({ success: true, ref, correlationId: scope.correlationId });
    },
  );

  // GET /api/v1/kernel/providers/events (list)
  fastify.get(
    "/api/v1/kernel/providers/events",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const query = request.query as {
        productUnitId?: string;
        runId?: string;
        limit?: string;
        cursor?: string;
      };
      const limit = query.limit ? parseInt(query.limit, 10) : 100;
      const filters: Record<string, unknown> = {};
      if (query.productUnitId) filters.productUnitId = query.productUnitId;
      if (query.runId) filters.runId = query.runId;
      const records = await providerStore.listByProviderType(
        scope,
        "events",
        filters,
        limit,
      );
      const items = records.map((r) => ({
        ref: r.providerRef,
        ...(r.data as Record<string, unknown>),
      }));
      return reply
        .status(200)
        .send({ success: true, items, correlationId: scope.correlationId });
    },
  );

  // POST /api/v1/kernel/providers/artifacts
  fastify.post(
    "/api/v1/kernel/providers/artifacts",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const parsedArtifact = LifecycleArtifactManifestRefSchema.safeParse(
        request.body,
      );
      if (!parsedArtifact.success) {
        return reply.status(400).send({
          success: false,
          error: "Invalid artifact manifest schema",
          reasonCode: "INVALID_SCHEMA",
          issues: parsedArtifact.error.issues,
          correlationId: scope.correlationId,
        });
      }
      const ref = generateProviderRef();
      const createdAt = new Date().toISOString();
      const record: ProviderRecord = {
        id: ref,
        tenantId: scope.tenantId,
        workspaceId: scope.workspaceId,
        projectId: scope.projectId,
        providerType: "artifacts",
        providerRef: ref,
        data: parsedArtifact.data,
        privacyClassification: undefined,
        expiresAt: undefined,
        createdAt,
        createdBy: scope.userId,
      };
      await providerStore.save(record);
      return reply
        .status(200)
        .send({ success: true, ref, correlationId: scope.correlationId });
    },
  );

  // GET /api/v1/kernel/providers/artifacts (list)
  fastify.get(
    "/api/v1/kernel/providers/artifacts",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const query = request.query as {
        productUnitId?: string;
        runId?: string;
        limit?: string;
        cursor?: string;
      };
      const limit = query.limit ? parseInt(query.limit, 10) : 100;
      const filters: Record<string, unknown> = {};
      if (query.productUnitId) filters.productUnitId = query.productUnitId;
      if (query.runId) filters.runId = query.runId;
      const records = await providerStore.listByProviderType(
        scope,
        "artifacts",
        filters,
        limit,
      );
      const items = records.map((r) => ({
        ref: r.providerRef,
        ...(r.data as Record<string, unknown>),
      }));
      return reply
        .status(200)
        .send({ success: true, items, correlationId: scope.correlationId });
    },
  );

  // POST /api/v1/kernel/providers/health
  fastify.post(
    "/api/v1/kernel/providers/health",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const parsedHealth = LifecycleHealthSnapshotRefSchema.safeParse(
        request.body,
      );
      if (!parsedHealth.success) {
        return reply.status(400).send({
          success: false,
          error: "Invalid health snapshot schema",
          reasonCode: "INVALID_SCHEMA",
          issues: parsedHealth.error.issues,
          correlationId: scope.correlationId,
        });
      }
      const ref = generateProviderRef();
      const createdAt = new Date().toISOString();
      const record: ProviderRecord = {
        id: ref,
        tenantId: scope.tenantId,
        workspaceId: scope.workspaceId,
        projectId: scope.projectId,
        providerType: "health",
        providerRef: ref,
        data: parsedHealth.data,
        privacyClassification: undefined,
        expiresAt: undefined,
        createdAt,
        createdBy: scope.userId,
      };
      await providerStore.save(record);
      return reply
        .status(200)
        .send({ success: true, ref, correlationId: scope.correlationId });
    },
  );

  // GET /api/v1/kernel/providers/health/:productUnitId/latest
  fastify.get(
    "/api/v1/kernel/providers/health/:productUnitId/latest",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const params = request.params as { productUnitId: string };
      const filters: Record<string, unknown> = {
        productUnitId: params.productUnitId,
      };
      const record = await providerStore.findLatestByProviderType(
        scope,
        "health",
        filters,
      );
      if (!record) {
        return reply.status(404).send({
          success: false,
          error: "No health snapshot found",
          reasonCode: "NOT_FOUND",
          correlationId: scope.correlationId,
        });
      }
      return reply.status(200).send({
        success: true,
        ref: record.providerRef,
        ...(record.data as Record<string, unknown>),
        correlationId: scope.correlationId,
      });
    },
  );

  // POST /api/v1/kernel/providers/approvals/requests
  fastify.post(
    "/api/v1/kernel/providers/approvals/requests",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const parsedRequest = ApprovalRequestSchema.safeParse(request.body);
      if (!parsedRequest.success) {
        return reply.status(400).send({
          success: false,
          error: "Invalid approval request schema",
          reasonCode: "INVALID_SCHEMA",
          issues: parsedRequest.error.issues,
          correlationId: scope.correlationId,
        });
      }
      const ref = generateProviderRef();
      const createdAt = new Date().toISOString();
      const record: ProviderRecord = {
        id: ref,
        tenantId: scope.tenantId,
        workspaceId: scope.workspaceId,
        projectId: scope.projectId,
        providerType: "approvals-requests",
        providerRef: ref,
        data: parsedRequest.data,
        privacyClassification: undefined,
        expiresAt: undefined,
        createdAt,
        createdBy: scope.userId,
      };
      await providerStore.save(record);
      return reply
        .status(200)
        .send({ success: true, ref, correlationId: scope.correlationId });
    },
  );

  // POST /api/v1/kernel/providers/approvals/decisions
  fastify.post(
    "/api/v1/kernel/providers/approvals/decisions",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const parsedDecision = ApprovalDecisionSchema.safeParse(request.body);
      if (!parsedDecision.success) {
        return reply.status(400).send({
          success: false,
          error: "Invalid approval decision schema",
          reasonCode: "INVALID_SCHEMA",
          issues: parsedDecision.error.issues,
          correlationId: scope.correlationId,
        });
      }
      const ref = generateProviderRef();
      const createdAt = new Date().toISOString();
      const record: ProviderRecord = {
        id: ref,
        tenantId: scope.tenantId,
        workspaceId: scope.workspaceId,
        projectId: scope.projectId,
        providerType: "approvals-decisions",
        providerRef: ref,
        data: parsedDecision.data,
        privacyClassification: undefined,
        expiresAt: undefined,
        createdAt,
        createdBy: scope.userId,
      };
      await providerStore.save(record);
      return reply
        .status(200)
        .send({ success: true, ref, correlationId: scope.correlationId });
    },
  );

  // POST /api/v1/kernel/providers/provenance
  fastify.post(
    "/api/v1/kernel/providers/provenance",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const parsedProvenance = LifecycleProvenanceRecordSchema.safeParse(
        request.body,
      );
      if (!parsedProvenance.success) {
        return reply.status(400).send({
          success: false,
          error: "Invalid provenance record schema",
          reasonCode: "INVALID_SCHEMA",
          issues: parsedProvenance.error.issues,
          correlationId: scope.correlationId,
        });
      }
      const ref = generateProviderRef();
      const createdAt = new Date().toISOString();
      const record: ProviderRecord = {
        id: ref,
        tenantId: scope.tenantId,
        workspaceId: scope.workspaceId,
        projectId: scope.projectId,
        providerType: "provenance",
        providerRef: ref,
        data: parsedProvenance.data,
        privacyClassification: undefined,
        expiresAt: undefined,
        createdAt,
        createdBy: scope.userId,
      };
      await providerStore.save(record);
      return reply
        .status(200)
        .send({ success: true, ref, correlationId: scope.correlationId });
    },
  );

  // GET /api/v1/kernel/providers/provenance (list)
  fastify.get(
    "/api/v1/kernel/providers/provenance",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const query = request.query as {
        productUnitId?: string;
        runId?: string;
        limit?: string;
        cursor?: string;
      };
      const limit = query.limit ? parseInt(query.limit, 10) : 100;
      const filters: Record<string, unknown> = {};
      if (query.productUnitId) filters.productUnitId = query.productUnitId;
      if (query.runId) filters.runId = query.runId;
      const records = await providerStore.listByProviderType(
        scope,
        "provenance",
        filters,
        limit,
      );
      const items = records.map((r) => ({
        ref: r.providerRef,
        ...(r.data as Record<string, unknown>),
      }));
      return reply
        .status(200)
        .send({ success: true, items, correlationId: scope.correlationId });
    },
  );

  // POST /api/v1/kernel/providers/memory
  fastify.post(
    "/api/v1/kernel/providers/memory",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const parsedMemory = LifecycleMemoryRecordSchema.safeParse(request.body);
      if (!parsedMemory.success) {
        return reply.status(400).send({
          success: false,
          error: "Invalid memory record schema",
          reasonCode: "INVALID_SCHEMA",
          issues: parsedMemory.error.issues,
          correlationId: scope.correlationId,
        });
      }
      const ref = generateProviderRef();
      const createdAt = new Date().toISOString();
      const expiresAt = parsedMemory.data.retention?.expiresAt;
      const record: ProviderRecord = {
        id: ref,
        tenantId: scope.tenantId,
        workspaceId: scope.workspaceId,
        projectId: scope.projectId,
        providerType: "memory",
        providerRef: ref,
        data: parsedMemory.data,
        privacyClassification: parsedMemory.data.privacyClassification,
        expiresAt,
        createdAt,
        createdBy: scope.userId,
      };
      await providerStore.save(record);
      return reply
        .status(200)
        .send({ success: true, ref, correlationId: scope.correlationId });
    },
  );

  // GET /api/v1/kernel/providers/memory (list)
  fastify.get(
    "/api/v1/kernel/providers/memory",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const query = request.query as {
        productUnitId?: string;
        runId?: string;
        limit?: string;
        cursor?: string;
      };
      const limit = query.limit ? parseInt(query.limit, 10) : 100;
      const filters: Record<string, unknown> = {};
      if (query.productUnitId) filters.productUnitId = query.productUnitId;
      if (query.runId) filters.runId = query.runId;
      const records = await providerStore.listByProviderType(
        scope,
        "memory",
        filters,
        limit,
      );
      const items = records.map((r) => ({
        ref: r.providerRef,
        ...(r.data as Record<string, unknown>),
      }));
      return reply
        .status(200)
        .send({ success: true, items, correlationId: scope.correlationId });
    },
  );

  // POST /api/v1/kernel/providers/runtime-truth
  fastify.post(
    "/api/v1/kernel/providers/runtime-truth",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const parsedRuntimeTruth = LifecycleRuntimeTruthSnapshotSchema.safeParse(
        request.body,
      );
      if (!parsedRuntimeTruth.success) {
        return reply.status(400).send({
          success: false,
          error: "Invalid runtime truth snapshot schema",
          reasonCode: "INVALID_SCHEMA",
          issues: parsedRuntimeTruth.error.issues,
          correlationId: scope.correlationId,
        });
      }
      const ref = generateProviderRef();
      const createdAt = new Date().toISOString();
      const record: ProviderRecord = {
        id: ref,
        tenantId: scope.tenantId,
        workspaceId: scope.workspaceId,
        projectId: scope.projectId,
        providerType: "runtime-truth",
        providerRef: ref,
        data: parsedRuntimeTruth.data,
        privacyClassification: undefined,
        expiresAt: undefined,
        createdAt,
        createdBy: scope.userId,
      };
      await providerStore.save(record);
      return reply
        .status(200)
        .send({ success: true, ref, correlationId: scope.correlationId });
    },
  );

  // GET /api/v1/kernel/providers/runtime-truth/:productUnitId/latest
  fastify.get(
    "/api/v1/kernel/providers/runtime-truth/:productUnitId/latest",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const scope = validateScopeForProvider(request);
      if (!scope) {
        const correlationId = resolveCorrelationId(request);
        return reply.status(403).send({
          success: false,
          error: "Tenant, workspace, project scope required",
          reasonCode: "SCOPE_DENIED",
          correlationId,
        });
      }
      if (providerStore === undefined) {
        return sendProviderStoreUnavailable(reply, scope.correlationId);
      }
      const params = request.params as { productUnitId: string };
      const filters: Record<string, unknown> = {
        productUnitId: params.productUnitId,
      };
      const record = await providerStore.findLatestByProviderType(
        scope,
        "runtime-truth",
        filters,
      );
      if (!record) {
        return reply.status(404).send({
          success: false,
          error: "No runtime truth snapshot found",
          reasonCode: "NOT_FOUND",
          correlationId: scope.correlationId,
        });
      }
      return reply.status(200).send({
        success: true,
        ref: record.providerRef,
        ...(record.data as Record<string, unknown>),
        correlationId: scope.correlationId,
      });
    },
  );

  // ── HTTP reverse-proxy → AEP Java backend ───────────────────────────────────
  // T-17: Gateway is the sole external edge; backend auth becomes trust-internal-only
  fastify.all(
    "/api/*",
    { preHandler: [authenticate] },
    async (request, reply) => {
      const targetUrl = `${config.backendUrl}${request.url}`;
      const correlationId =
        (request as FastifyRequest & { correlationId?: string })
          .correlationId ?? resolveCorrelationId(request);
      const traceparent =
        (request as FastifyRequest & { traceparent?: string }).traceparent ??
        resolveTraceparent(request);

      const proxyHeaders: Record<string, string> = {};
      if (request.headers["content-type"]) {
        proxyHeaders["content-type"] = request.headers["content-type"];
      }
      if (request.headers.authorization) {
        proxyHeaders["authorization"] = request.headers.authorization;
      }
      const authContext = (request as FastifyRequest & {
        authContext?: AuthenticatedRequestContext;
      }).authContext;
      const payloadTenantId =
        authContext?.tenantId ??
        extractPayloadTenantId((request as FastifyRequest & { user: JwtPayload }).user);
      const headerTenantId = resolveHeaderTenantId(request.headers);
      const effectiveTenantId = payloadTenantId ?? headerTenantId;
      if (effectiveTenantId) {
        proxyHeaders["x-tenant-id"] = effectiveTenantId;
      }
      // T-17: Mark request as coming from trusted gateway (internal auth)
      proxyHeaders["x-gateway-trusted"] = "true";
      proxyHeaders["x-gateway-source"] = "aep-gateway";
      proxyHeaders[CORRELATION_ID_HEADER] = correlationId;
      proxyHeaders[TRACEPARENT_HEADER] = traceparent;

      const method = request.method;
      const hasBody =
        method !== "GET" && method !== "HEAD" && method !== "DELETE";
      const body =
        hasBody && request.body != null
          ? JSON.stringify(request.body)
          : undefined;

      let backendRes: Response;
      try {
        const startMs = Date.now();
        backendRes = await fetchWithResilience(
          targetUrl,
          { method, headers: proxyHeaders, body },
          correlationId,
        );
        metrics.recordBackendLatency(Date.now() - startMs);
      } catch (err: unknown) {
        fastify.log.error(err, `Backend unreachable at ${targetUrl}`);
        metrics.recordBackendUnreachable();
        metrics.recordHttpProxyRequest(502);
        reply.header(CORRELATION_ID_HEADER, correlationId);
        return reply.status(502).send({
          error: "Bad Gateway",
          message: "AEP backend unreachable",
          correlationId,
        });
      }

      metrics.recordHttpProxyRequest(backendRes.status);
      const ct = backendRes.headers.get("content-type");

      const { body: responseBody, truncated } = await readBodyCapped(
        backendRes,
        MAX_PROXY_BODY_BYTES,
      );
      if (truncated) {
        fastify.log.warn(
          { correlationId, targetUrl },
          "Proxy response exceeded size limit; body was capped",
        );
        reply.header("x-correlation-id", correlationId);
        return reply.status(502).send({
          error: "Bad Gateway",
          message: "Backend response too large",
          correlationId,
        });
      }

      reply.status(backendRes.status);
      reply.header("x-correlation-id", correlationId);
      if (ct) reply.header("content-type", ct);
      return reply.send(Buffer.from(responseBody));
    },
  );

  // ── SSE event stream proxy (canonical path: /events/stream) ──────────────────
  fastify.get("/events/stream", async (request, reply) => {
    const correlationId =
      (request as FastifyRequest & { correlationId?: string }).correlationId ??
      resolveCorrelationId(request);
    const traceparent =
      (request as FastifyRequest & { traceparent?: string }).traceparent ??
      resolveTraceparent(request);
    const token =
      extractBearerToken(request.headers.authorization) ??
      (request.query as Record<string, string>)["token"] ??
      null;
    if (!token) {
      metrics.recordSseRejected();
      metrics.recordAuthFailure("missing_token");
      reply.header(CORRELATION_ID_HEADER, correlationId);
      return reply
        .status(401)
        .send({ error: "Authentication required", correlationId });
    }
    let payload: JwtPayload;
    try {
      payload = verifyJwt(token, config.jwtSecret);
    } catch {
      metrics.recordSseRejected();
      metrics.recordAuthFailure("invalid_token");
      reply.header(CORRELATION_ID_HEADER, correlationId);
      return reply
        .status(403)
        .send({ error: "Invalid or expired token", correlationId });
    }

    const query = request.query as Record<string, string>;
    const queryTenantId =
      typeof query.tenantId === "string" && query.tenantId.trim().length > 0
        ? query.tenantId.trim()
        : null;
    const jwtTenantId = extractPayloadTenantId(payload);
    if (strictTenantProfile(config.deploymentProfile ?? "local") && !jwtTenantId) {
      metrics.recordSseRejected();
      metrics.recordAuthFailure("missing_tenant_claim");
      reply.header(CORRELATION_ID_HEADER, correlationId);
      return reply.status(400).send({
        error: "Bad Request",
        reasonCode: "MISSING_TENANT_CLAIM",
        message: "Authenticated identity must include tenant_id claim",
        correlationId,
      });
    }
    if (queryTenantId && jwtTenantId && queryTenantId !== jwtTenantId) {
      metrics.recordSseRejected();
      metrics.recordTenantMismatch();
      reply.header(CORRELATION_ID_HEADER, correlationId);
      return reply.status(403).send({
        error: "Forbidden",
        reasonCode: "TENANT_MISMATCH",
        message:
          "Tenant mismatch between tenantId query parameter and JWT payload",
        correlationId,
      });
    }

    const params = new URLSearchParams();
    const effectiveTenantId = jwtTenantId ?? queryTenantId;
    if (effectiveTenantId) params.set("tenantId", effectiveTenantId);

    const backendUrl = `${config.backendUrl}/events/stream?${params.toString()}`;
    const backendRes = await fetchWithResilience(
      backendUrl,
      {
        method: "GET",
        headers: {
          authorization: `Bearer ${token}`,
          accept: "text/event-stream",
          [CORRELATION_ID_HEADER]: correlationId,
          [TRACEPARENT_HEADER]: traceparent,
        },
      },
      correlationId,
    ).catch(() => null);

    if (!backendRes || !backendRes.ok || !backendRes.body) {
      metrics.recordSseRejected();
      metrics.recordBackendUnreachable();
      reply.header(CORRELATION_ID_HEADER, correlationId);
      return reply.status(502).send({
        error: "Bad Gateway",
        message: "SSE backend unreachable",
        correlationId,
      });
    }

    metrics.recordSseAccepted();
    reply.raw.writeHead(200, {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive",
      "X-Accel-Buffering": "no",
      "X-Correlation-ID": correlationId,
    });

    const reader = backendRes.body.getReader();
    const pump = async () => {
      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          reply.raw.write(value);
        }
      } finally {
        reply.raw.end();
      }
    };
    pump();

    request.raw.on("close", () => {
      reader.cancel().catch(() => {});
    });
  });

  // ── WebSocket event-tailing proxy (legacy path: /tail/events) ────────────────
  await fastify.register(async function wsRoutes(scopedFastify) {
    scopedFastify.get("/tail/events", { websocket: true }, (con, req) => {
      const clientSocket = ("socket" in con ? con.socket : con) as WebSocket;
      const correlationId =
        extractCorrelationId(req.headers[CORRELATION_ID_HEADER]) ??
        extractCorrelationId(
          (req.query as Record<string, string>)["correlationId"],
        ) ??
        randomUUID();
      const traceparent =
        typeof req.headers[TRACEPARENT_HEADER] === "string" &&
        req.headers[TRACEPARENT_HEADER].trim().length > 0
          ? req.headers[TRACEPARENT_HEADER].trim()
          : `00-${randomUUID().replace(/-/g, "")}-${randomUUID().replace(/-/g, "").slice(0, 16)}-01`;
      const queryToken = (req.query as Record<string, string>)["token"];
      const token =
        extractBearerToken(req.headers.authorization) ?? queryToken ?? null;
      if (!token) {
        metrics.recordWsRejected();
        metrics.recordAuthFailure("missing_token");
        scopedFastify.log.warn(
          { correlationId },
          "WS /tail/events rejected: missing token (4001)",
        );
        clientSocket.close(4001, "Authentication required");
        return;
      }
      let payload: JwtPayload;
      try {
        payload = verifyJwt(token, config.jwtSecret);
      } catch {
        metrics.recordWsRejected();
        metrics.recordAuthFailure("invalid_token");
        scopedFastify.log.warn(
          { correlationId },
          "WS /tail/events rejected: invalid token (4003)",
        );
        clientSocket.close(4003, "Invalid or expired token");
        return;
      }
      metrics.recordWsAccepted();
      let wsCloseRecorded = false;
      function recordWsClosedOnce(): void {
        if (!wsCloseRecorded) {
          wsCloseRecorded = true;
          metrics.recordWsClosed();
        }
      }

      const queryTenantId =
        typeof (req.query as Record<string, string>)["tenantId"] === "string" &&
        (req.query as Record<string, string>)["tenantId"].trim().length > 0
          ? (req.query as Record<string, string>)["tenantId"].trim()
          : null;
      const jwtTenantId = extractPayloadTenantId(payload);
      const headerTenantId = extractHeaderTenantId(req.headers["x-tenant-id"]);
      if (strictTenantProfile(config.deploymentProfile ?? "local") && !jwtTenantId) {
        metrics.recordWsRejected();
        metrics.recordAuthFailure("missing_tenant_claim");
        clientSocket.close(4003, "Missing tenant claim");
        return;
      }
      if (headerTenantId && jwtTenantId && headerTenantId !== jwtTenantId) {
        metrics.recordWsRejected();
        metrics.recordTenantMismatch();
        clientSocket.close(4003, "Tenant mismatch");
        return;
      }
      if (queryTenantId && jwtTenantId && queryTenantId !== jwtTenantId) {
        metrics.recordWsRejected();
        metrics.recordTenantMismatch();
        clientSocket.close(4003, "Tenant mismatch");
        return;
      }
      const tenantId = jwtTenantId ?? headerTenantId;

      const backendWsUrl =
        config.backendUrl.replace(/^http/, "ws") + "/api/v1/tail/events";
      const backendHeaders: Record<string, string> = {
        authorization: `Bearer ${token}`,
        [CORRELATION_ID_HEADER]: correlationId,
        [TRACEPARENT_HEADER]: traceparent,
        "x-gateway-trusted": "true",
        "x-gateway-source": "aep-gateway",
      };
      if (tenantId) {
        backendHeaders["x-tenant-id"] = tenantId;
      }

      const backendWs = new WebSocket(backendWsUrl, {
        headers: backendHeaders,
        maxPayload: wsMaxMessageBytes,
      });

      // Buffer messages from the client until the backend WS is open.
      // Without this, messages sent before the backend handshake completes are silently dropped.
      const pendingClientMessages: string[] = [];

      // ── Idle timeout ──────────────────────────────────────────────────────────
      // Reset on any message from either side; close with 1001 if no traffic arrives.
      let idleTimer: ReturnType<typeof setTimeout> | null = null;

      function resetIdleTimer(): void {
        if (idleTimer !== null) clearTimeout(idleTimer);
        idleTimer = setTimeout(() => {
          scopedFastify.log.warn(
            { correlationId },
            "WS /tail/events idle timeout; closing (1001)",
          );
          clientSocket.close(1001, "Idle timeout");
          if (backendWs.readyState !== WebSocket.CLOSED) {
            backendWs.close(1001, "Idle timeout");
          }
        }, wsIdleTimeoutMs);
      }

      function clearIdleTimer(): void {
        if (idleTimer !== null) {
          clearTimeout(idleTimer);
          idleTimer = null;
        }
      }

      resetIdleTimer();

      // ── Heartbeat (client) ────────────────────────────────────────────────────
      // Gateway pings the client periodically. If no pong arrives within the next
      // interval the session is considered dead and terminated.
      let clientAlive = true;
      const heartbeatTimer = setInterval(() => {
        if (!clientAlive) {
          scopedFastify.log.warn(
            { correlationId },
            "WS /tail/events client missed pong; terminating",
          );
          clearIdleTimer();
          clientSocket.terminate();
          if (backendWs.readyState !== WebSocket.CLOSED) {
            backendWs.close(1001, "Client heartbeat failed");
          }
          return;
        }
        clientAlive = false;
        if (clientSocket.readyState === WebSocket.OPEN) {
          clientSocket.ping();
        }
      }, wsHeartbeatIntervalMs);

      clientSocket.on("pong", () => {
        clientAlive = true;
        // Note: pong (heartbeat response) does NOT reset the idle timer.
        // Idle timeout tracks application-level DATA messages, not control frames.
      });

      // ── Cleanup ───────────────────────────────────────────────────────────────
      function cleanup(): void {
        clearIdleTimer();
        clearInterval(heartbeatTimer);
      }

      backendWs.on("open", () => {
        for (const msg of pendingClientMessages) {
          if (backendWs.readyState === WebSocket.OPEN) {
            backendWs.send(msg);
          }
        }
        pendingClientMessages.length = 0;
      });
      backendWs.on("message", (data) => {
        resetIdleTimer();
        if (clientSocket.readyState === WebSocket.OPEN) {
          clientSocket.send(data.toString());
        }
      });
      backendWs.on("error", (err) => {
        cleanup();
        metrics.recordBackendUnreachable();
        scopedFastify.log.error(
          { err, correlationId },
          "WS /tail/events backend error; closing client (1011)",
        );
        recordWsClosedOnce();
        clientSocket.close(1011, "Backend connection failed");
      });
      backendWs.on("close", () => {
        cleanup();
        if (clientSocket.readyState === WebSocket.OPEN) {
          scopedFastify.log.info(
            { correlationId },
            "WS /tail/events backend closed; closing client (1000)",
          );
          recordWsClosedOnce();
          clientSocket.close(1000, "Backend closed connection");
        }
      });

      clientSocket.on("message", (msg) => {
        resetIdleTimer();
        // ── Backpressure: enforce max message size ─────────────────────────────
        const msgStr = msg.toString();
        if (Buffer.byteLength(msgStr, "utf8") > wsMaxMessageBytes) {
          scopedFastify.log.warn(
            { correlationId, size: Buffer.byteLength(msgStr, "utf8") },
            "WS /tail/events: client message exceeds max size; closing (1009)",
          );
          cleanup();
          clientSocket.close(1009, "Message too large");
          if (backendWs.readyState !== WebSocket.CLOSED) {
            backendWs.close(1009, "Client message too large");
          }
          return;
        }
        if (backendWs.readyState === WebSocket.OPEN) {
          backendWs.send(msgStr);
        } else {
          pendingClientMessages.push(msgStr);
        }
      });
      clientSocket.on("close", () => {
        cleanup();
        recordWsClosedOnce();
        if (backendWs.readyState !== WebSocket.CLOSED) {
          backendWs.close();
        }
      });
    });
  });

  // ── Metrics endpoint (no auth — internal/ops use only) ───────────────────────
  fastify.get("/metrics", async (_request, reply) => {
    return reply.status(200).send(metrics.snapshot());
  });

  return fastify;
}
