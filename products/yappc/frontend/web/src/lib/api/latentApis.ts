/**
 * YAPPC Latent APIs Module
 *
 * This module consolidates all latent/unmounted APIs that are NOT in the route manifest.
 * These APIs are marked as @experimental and should only be used with explicit feature flags.
 * Latent APIs should either be mounted/validated or archived.
 *
 * @doc.type module
 * @doc.purpose Consolidation of latent/unmounted APIs marked as experimental
 * @doc.layer product
 * @doc.pattern API Client
 * @experimental
 */

import { parseJsonResponse, readErrorResponse } from '@/lib/http';

// ─────────────────────────────────────────────────────────────────────────────
// Domain types
// ─────────────────────────────────────────────────────────────────────────────

export interface ApiError {
  readonly status: number;
  readonly message: string;
  readonly type?: string;
}

export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly type?: string,
  ) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal fetch helpers
// ─────────────────────────────────────────────────────────────────────────────

async function handleError(response: Response, context: string): Promise<never> {
  const message = await readErrorResponse(response, `${context} failed (${response.status})`);
  let type: string | undefined;
  try {
    const ct = response.headers.get('content-type') ?? '';
    if (ct.includes('problem+json')) {
      const body = JSON.parse(message) as { type?: unknown };
      if (typeof body.type === 'string') type = body.type;
    }
  } catch {
    // non-parseable, ignore
  }
  throw new ApiRequestError(response.status, message, type);
}

async function get<T>(path: string, context: string): Promise<T> {
  const response = await fetch(path, {
    method: 'GET',
    headers: { Accept: 'application/json' },
    credentials: 'same-origin',
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<T>(response, context);
}

async function getWithHeaders<T>(
  path: string,
  context: string,
  headers: Readonly<Record<string, string>>,
): Promise<T> {
  const response = await fetch(path, {
    method: 'GET',
    headers: { Accept: 'application/json', ...headers },
    credentials: 'same-origin',
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<T>(response, context);
}

async function post<TRequest, TResponse>(path: string, body: TRequest, context: string): Promise<TResponse> {
  const response = await fetch(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<TResponse>(response, context);
}

async function postWithHeaders<TRequest, TResponse>(
  path: string,
  body: TRequest,
  context: string,
  headers: Readonly<Record<string, string>>,
): Promise<TResponse> {
  const response = await fetch(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...headers,
    },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<TResponse>(response, context);
}

async function patch<TRequest, TResponse>(path: string, body: TRequest, context: string): Promise<TResponse> {
  const response = await fetch(path, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<TResponse>(response, context);
}

async function patchWithHeaders<TRequest, TResponse>(
  path: string,
  body: TRequest,
  context: string,
  headers: Readonly<Record<string, string>>,
): Promise<TResponse> {
  const response = await fetch(path, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...headers,
    },
    credentials: 'same-origin',
    body: JSON.stringify(body),
  });
  if (!response.ok) return handleError(response, context);
  return parseJsonResponse<TResponse>(response, context);
}

// ─────────────────────────────────────────────────────────────────────────────
// Billing (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Billing API is not mounted in route manifest
 */
export const billing = {
  getSummary: <T>() => get<T>('/api/billing', 'billing.getSummary'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Operations (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Operations API is not mounted in route manifest
 */
export const operations = {
  getOnCallSchedule: <T>() => get<T>('/api/oncall', 'operations.getOnCallSchedule'),
  getServiceTopology: <T>() => get<T>('/api/services/topology', 'operations.getServiceTopology'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Collaboration (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Collaboration API is not mounted in route manifest
 */
export const collaboration = {
  getActivityFeed: <T>() => get<T>('/api/activity', 'collaboration.getActivityFeed'),
  getTeamHub: <T>() => get<T>('/api/teams/hub', 'collaboration.getTeamHub'),
  getMessageChannels: <T>() => get<T>('/api/messages/channels', 'collaboration.getMessageChannels'),
  getChannelMessages: <T>(channelId: string) =>
    get<T>(`/api/messages/channels/${encodeURIComponent(channelId)}`, 'collaboration.getChannelMessages'),
  getCalendarEvents: <T>() => get<T>('/api/calendar/events', 'collaboration.getCalendarEvents'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Settings (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Settings API is not mounted in route manifest
 */
export const settings = {
  getWorkspaceSettings: <T>() => get<T>('/api/settings', 'settings.getWorkspaceSettings'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Anomalies (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Anomalies API is not mounted in route manifest
 */
export const anomalies = {
  query: <T>(body: {
    tenantId: string;
    startDate: Date;
    endDate: Date;
    severity?: string;
    status?: string;
  }) => post<typeof body, T>('/api/anomalies', body, 'anomalies.query'),
  byUser: <T>(userId: string, tenantId: string) =>
    getWithHeaders<T>(`/api/anomalies/user/${encodeURIComponent(userId)}`, 'anomalies.byUser', {
      'X-Tenant-Id': tenantId,
    }),
  updateStatus: <T>(anomalyId: string, tenantId: string, body: { status: string; notes?: string }) =>
    patchWithHeaders<typeof body, T>(`/api/anomalies/${encodeURIComponent(anomalyId)}/status`, body, 'anomalies.updateStatus', {
      'X-Tenant-Id': tenantId,
    }),
  createInvestigation: <T>(anomalyId: string, tenantId: string, body: { assignedTo: string }) =>
    postWithHeaders<typeof body, T>(`/api/anomalies/${encodeURIComponent(anomalyId)}/investigation`, body, 'anomalies.createInvestigation', {
      'X-Tenant-Id': tenantId,
    }),
  baselines: <T>(tenantId: string) =>
    getWithHeaders<T>('/api/anomaly-baselines', 'anomalies.baselines', { 'X-Tenant-Id': tenantId }),
  threatIntelligence: <T>(tenantId: string) =>
    getWithHeaders<T>('/api/threat-intelligence', 'anomalies.threatIntelligence', { 'X-Tenant-Id': tenantId }),
  riskScores: <T>(tenantId: string) =>
    getWithHeaders<T>('/api/risk-scores', 'anomalies.riskScores', { 'X-Tenant-Id': tenantId }),
  detail: <T>(anomalyId: string, tenantId: string) =>
    getWithHeaders<T>(`/api/anomalies/${encodeURIComponent(anomalyId)}`, 'anomalies.detail', { 'X-Tenant-Id': tenantId }),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Canvas (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Canvas API is not mounted in route manifest
 */
export const canvas = {
  save: <T>(body: { projectId: string; canvasId: string; data: unknown }) =>
    post<typeof body, T>('/api/canvas', body, 'canvas.save'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Error Reporting (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Error Reporting API is not mounted in route manifest
 */
export const errorReporting = {
  report: <T>(body: {
    message: string;
    stack?: string;
    componentStack?: string;
    errorId?: string;
    context?: string;
    timestamp: string;
    userAgent?: string;
    url?: string;
  }) => post<typeof body, T>('/api/errors', body, 'errorReporting.report'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Personas (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Personas API is not mounted in route manifest
 */
export const personas = {
  derive: (body: { projectId: string; intent?: string }) =>
    post<typeof body, unknown[]>('/api/personas/derive', body, 'personas.derive'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Phases (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Phases API is not mounted in route manifest
 */
export const phases = {
  list: () => get<unknown[]>('/api/phases', 'phases.list'),
  next: (phase: string, projectId: string) => {
    const qs = projectId ? `?${new URLSearchParams({ projectId }).toString()}` : '';
    return get<unknown>(`/api/phases/${encodeURIComponent(phase)}/next${qs}`, 'phases.next');
  },
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Telemetry (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Telemetry API is not mounted in route manifest
 */
export const telemetry = {
  reportError: async (
    body: {
      message: string;
      stack?: string;
      componentName?: string;
      url: string;
      userAgent: string;
      dataClassification?: string;
      tenantId?: string;
      userId?: string;
    },
    consent: {
      userTelemetryConsent: boolean;
      tenantTelemetryConsent: boolean;
      dataClassification: string;
      allowSensitiveTelemetry?: boolean;
      tenantId?: string;
      userId?: string;
    },
  ): Promise<{ accepted: boolean; blockedReason?: string }> => {
    if (!consent.tenantTelemetryConsent) {
      return { accepted: false, blockedReason: 'Tenant telemetry consent is not enabled.' };
    }
    if (!consent.userTelemetryConsent) {
      return { accepted: false, blockedReason: 'User telemetry consent is not enabled.' };
    }
    const blockedClassifications = new Set(['SENSITIVE', 'CREDENTIALS', 'REGULATED', 'RESTRICTED']);
    if (blockedClassifications.has(consent.dataClassification as any) && consent.allowSensitiveTelemetry !== true) {
      return {
        accepted: false,
        blockedReason: `Telemetry blocked for ${consent.dataClassification.toLowerCase()} data classification.`,
      };
    }

    const payload = {
      ...body,
      dataClassification: consent.dataClassification,
      ...(consent.tenantId ? { tenantId: consent.tenantId } : {}),
      ...(consent.userId ? { userId: consent.userId } : {}),
    };

    return post<typeof payload, { accepted: boolean; blockedReason?: string }>(
      '/api/telemetry/frontend-errors',
      payload,
      'telemetry.reportError',
    );
  },
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Audit (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Audit API is not mounted in route manifest
 */
export const audit = {
  emit: (body: {
    type: string;
    userId: string;
    projectId: string;
    artifactId?: string;
    flowStage: string;
    phase: string;
    metadata?: Record<string, unknown>;
    description: string;
  }) =>
    post<typeof body, { id: string; timestamp: string } & typeof body>('/api/audit/events', body, 'audit.emit'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// User Data (GDPR / CCPA) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental User Data API is not mounted in route manifest
 */
export const userData = {
  requestDeletion: async (): Promise<{ statusUrl: string | null }> => {
    const response = await fetch('/api/users/me/data', {
      method: 'DELETE',
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    });
    if (!response.ok) {
      const message = await readErrorResponse(response, 'userData.requestDeletion failed');
      throw new ApiRequestError(response.status, message);
    }
    const locationHeader = response.headers.get('Location');
    let statusUrl = locationHeader;
    if (!locationHeader && response.status !== 204) {
      try {
        const body = await parseJsonResponse<{ statusUrl?: string }>(response, 'userData.requestDeletion');
        statusUrl = body.statusUrl ?? null;
      } catch {
        statusUrl = null;
      }
    }
    return { statusUrl };
  },
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Execution Results (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Results API is not mounted in route manifest
 */
export const results = {
  list: () => get<unknown[]>('/api/results', 'results.list'),
  get: (executionId: string) => get<unknown>(`/api/results/${encodeURIComponent(executionId)}`, 'results.get'),
  phase: (executionId: string) => get<unknown>(`/api/results/${encodeURIComponent(executionId)}/phase`, 'results.phase'),
  forProject: (projectId: string) => get<unknown[]>(`/api/projects/${encodeURIComponent(projectId)}/results`, 'results.forProject'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Rate Limit (page-level read model) - NOT in route manifest
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @experimental Rate Limit API is not mounted in route manifest
 */
export const rateLimit = {
  status: (identifier: string = 'me') =>
    get<{
      identifier: string;
      tier: string;
      used: number;
      limit: number;
      remaining: number;
      percentage: number;
      resetTime: string;
      isLimited: boolean;
      lastRequestAt?: string;
      statusColor: string;
      statusLabel: string;
    }>(`/api/rate-limit/status/${encodeURIComponent(identifier)}`, 'rateLimit.status'),
  tiers: () =>
    get<{
      name: string;
      description: string;
      requestsPerHour: number;
      requestsPerDay: number;
      burstSize: number;
      monthlyCost: number;
      features: string[];
    }[]>('/api/rate-limit/tiers', 'rateLimit.tiers'),
  upgradeRequests: () =>
    get<{
      id: string;
      userId: string;
      requestedTier: string;
      currentTier: string;
      status: string;
      createdAt: string;
      processedAt?: string;
    }[]>('/api/rate-limit/upgrade-requests', 'rateLimit.upgradeRequests'),
  upgrade: (body: { requestedTier: string }) =>
    post<typeof body, unknown>('/api/rate-limit/upgrade', body, 'rateLimit.upgrade'),
  reset: (body: { userId?: string }) => post<typeof body, unknown>('/api/rate-limit/reset', body, 'rateLimit.reset'),
  downgrade: () => post<Record<string, never>, unknown>('/api/rate-limit/downgrade', {}, 'rateLimit.downgrade'),
} as const;

// ─────────────────────────────────────────────────────────────────────────────
// Export
// ─────────────────────────────────────────────────────────────────────────────

/**
 * YAPPC Latent APIs
 * All APIs in this module are NOT in the route manifest and are marked as @experimental.
 * Use only with explicit feature flags and plan to either mount or archive these APIs.
 */
export const latentApis = {
  billing,
  operations,
  collaboration,
  settings,
  anomalies,
  canvas,
  errorReporting,
  personas,
  phases,
  telemetry,
  audit,
  userData,
  results,
  rateLimit,
} as const;
