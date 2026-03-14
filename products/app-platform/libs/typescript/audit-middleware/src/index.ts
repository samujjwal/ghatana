/**
 * @ghatana/audit-middleware
 *
 * Fastify plugin that automatically publishes audit log entries to the
 * K-07 Finance Audit Framework for every mutating HTTP request
 * (POST, PUT, PATCH, DELETE).
 *
 * @example
 * ```typescript
 * import Fastify from 'fastify';
 * import { auditMiddleware, HttpAuditClient } from '@ghatana/audit-middleware';
 *
 * const app = Fastify();
 * const auditClient = new HttpAuditClient({ baseUrl: 'http://app-platform:8080' });
 *
 * await app.register(auditMiddleware, {
 *   auditClient,
 *   resolveActor: (req) => ({
 *     userId: req.user?.id ?? 'ANONYMOUS',
 *     role: req.user?.role ?? 'UNKNOWN',
 *     ipAddress: req.ip,
 *     sessionId: req.headers['x-session-id'] as string,
 *   }),
 *   resolveTenantId: (req) => req.headers['x-tenant-id'] as string ?? 'DEFAULT',
 * });
 * ```
 */

export { auditMiddleware } from "./middleware/auditMiddleware";
export type { AuditMiddlewareOptions } from "./middleware/auditMiddleware";
export { HttpAuditClient } from "./client/HttpAuditClient";
export type { HttpAuditClientConfig } from "./client/HttpAuditClient";
export type {
  AuditOutcome,
  AuditActor,
  AuditResource,
  AuditPayload,
  AuditReceipt,
  AuditStoreClient,
} from "./types";
export { HttpAuditClient } from "./client/HttpAuditClient";
export type { HttpAuditClientConfig } from "./client/HttpAuditClient";
export type {
  AuditPayload,
  AuditReceipt,
  AuditActor,
  AuditResource,
  AuditOutcome,
  AuditStoreClient,
} from "./types";
