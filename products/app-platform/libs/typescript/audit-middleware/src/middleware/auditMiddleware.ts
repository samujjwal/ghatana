import fp from "fastify-plugin";
import type { FastifyPluginAsync, FastifyRequest, FastifyReply } from "fastify";
import type {
  AuditStoreClient,
  AuditPayload,
  AuditActor,
  AuditResource,
  AuditOutcome,
} from "../types";

// ──────────────────────────────────────────────────────────────────────────────
// Mutation methods that MUST be audited (per K-07 LLD §2.1)
// ──────────────────────────────────────────────────────────────────────────────
const AUDITED_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

export interface AuditMiddlewareOptions {
  /** Client that forwards audit payloads to the K-07 Java service. */
  auditClient: AuditStoreClient;
  /**
   * Base URL of the K-15 calendar-service.
   * When provided, each audit entry will carry a populated `timestampBs` field
   * derived by calling `GET <calendarServiceBaseUrl>/calendar/convert?from=greg&date=<YYYY-MM-DD>`.
   * Failures are non-fatal: the field falls back to an empty string.
   * Example: "http://calendar-service:8080"
   */
  calendarServiceBaseUrl?: string;
  /**
   * Extract the actor from the request. Implement to pull from JWT / session.
   * Falls back to anonymous if not provided.
   */
  resolveActor?: (req: FastifyRequest) => AuditActor;
  /**
   * Extract tenant ID from the request (e.g., from JWT claim or header).
   * Falls back to 'UNKNOWN' if not provided.
   */
  resolveTenantId?: (req: FastifyRequest) => string;
  /**
   * Derive the resource descriptor from the route and request.
   * Default implementation uses `routerPath` and route params.
   */
  resolveResource?: (req: FastifyRequest) => AuditResource;
  /**
   * Convert route + method into a human-readable action name.
   * Default: "<METHOD>:<routerPath>" e.g. "POST:/api/orders"
   */
  resolveAction?: (req: FastifyRequest) => string;
  /**
   * Whether to fire audit logging in the background (non-blocking).
   * Default: true. Set false during testing.
   */
  fireAndForget?: boolean;
}

const defaultResolveActor = (_req: FastifyRequest): AuditActor => ({
  userId: "ANONYMOUS",
  role: "UNKNOWN",
});

const defaultResolveTenantId = (_req: FastifyRequest): string => "UNKNOWN";

const defaultResolveResource = (req: FastifyRequest): AuditResource => {
  const params = req.params as Record<string, string>;
  const paramValues = Object.values(params);
  const id = paramValues.length > 0 ? paramValues[0] : "UNKNOWN";
  return {
    type: req.routerPath ?? req.url,
    id,
  };
};

const defaultResolveAction = (req: FastifyRequest): string =>
  `${req.method}:${req.routerPath ?? req.url}`;

function mapStatusToOutcome(statusCode: number): AuditOutcome {
  if (statusCode >= 200 && statusCode < 300) return "SUCCESS";
  if (statusCode >= 400 && statusCode < 500) return "FAILURE";
  return "PARTIAL";
}

/**
 * Resolves the Bikram Sambat date string for the given ISO date by calling
 * the K-15 calendar-service REST API.
 *
 * Returns an empty string gracefully on any network or parse error — the audit
 * record is still written, just without the BS timestamp (degraded mode).
 *
 * @param isoDate            "YYYY-MM-DD" Gregorian date
 * @param calendarServiceUrl base URL of the calendar-service (e.g. "http://calendar-service:8080")
 */
async function fetchBsDate(
  isoDate: string,
  calendarServiceUrl: string,
): Promise<string> {
  try {
    const url = `${calendarServiceUrl}/calendar/convert?from=greg&date=${encodeURIComponent(isoDate)}`;
    const res = await fetch(url, {
      method: "GET",
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(1500), // 1.5 s hard timeout — audit must never block request
    });
    if (!res.ok) return "";
    const body = (await res.json()) as { bs?: { year?: number; month?: number; dayOfMonth?: number } };
    const bs = body?.bs;
    if (bs?.year == null || bs?.month == null || bs?.dayOfMonth == null) return "";
    return `${bs.year}-${String(bs.month).padStart(2, "0")}-${String(bs.dayOfMonth).padStart(2, "0")}`;
  } catch {
    // Non-fatal: calendar-service may be unavailable during degraded mode
    return "";
  }
}

const auditMiddlewarePlugin: FastifyPluginAsync<
  AuditMiddlewareOptions
> = async (fastify, options) => {
  const {
    auditClient,
    calendarServiceBaseUrl,
    resolveActor = defaultResolveActor,
    resolveTenantId = defaultResolveTenantId,
    resolveResource = defaultResolveResource,
    resolveAction = defaultResolveAction,
    fireAndForget = true,
  } = options;

  fastify.addHook(
    "onResponse",
    async (req: FastifyRequest, reply: FastifyReply) => {
      if (!AUDITED_METHODS.has(req.method)) return;

      const now = new Date();
      const gregorianIso = now.toISOString();
      const gregorianDate = gregorianIso.slice(0, 10); // "YYYY-MM-DD"

      const timestampBs = calendarServiceBaseUrl
        ? await fetchBsDate(gregorianDate, calendarServiceBaseUrl)
        : "";

      const payload: AuditPayload = {
        action: resolveAction(req),
        actor: resolveActor(req),
        resource: resolveResource(req),
        outcome: mapStatusToOutcome(reply.statusCode),
        tenantId: resolveTenantId(req),
        traceId: req.id,
        timestampGregorian: gregorianIso,
        timestampBs,
      };

      if (fireAndForget) {
        auditClient.log(payload).catch((err: unknown) => {
          req.log.error({ err, auditPayload: payload }, "Audit logging failed");
        });
      } else {
        await auditClient.log(payload);
      }
    },
  );
};

export const auditMiddleware = fp(auditMiddlewarePlugin, {
  fastify: ">=4",
  name: "ghatana-audit-middleware",
});
