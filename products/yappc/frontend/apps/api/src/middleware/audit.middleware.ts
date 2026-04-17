/**
 * Audit Logging Middleware (Fastify)
 *
 * Fastify plugin that automatically records every request as an AuditLogEntry.
 * Sensitive fields are redacted before persisting.
 * Logging is fire-and-forget (errors are swallowed to avoid impacting the
 * request/response cycle).
 *
 * @doc.type module
 * @doc.purpose Automatic HTTP-level audit logging for Fastify
 * @doc.layer product
 * @doc.pattern Middleware
 */

import type { FastifyInstance, FastifyRequest, FastifyReply } from 'fastify';
import { getAuditService } from '../services/audit/audit.service';

// ============================================================================
// Config
// ============================================================================

/** Paths that should not be audited (health, metrics, etc.) */
const EXCLUDED_PATHS = new Set([
  '/health',
  '/metrics',
  '/status',
  '/alive',
  '/readiness',
  '/graphiql',
]);

/** Request body field names that should be redacted before logging */
const SENSITIVE_FIELDS = new Set([
  'password',
  'passwordHash',
  'token',
  'secret',
  'apiKey',
  'api_key',
  'creditCard',
  'ssn',
  'refreshToken',
]);

// ============================================================================
// Helpers
// ============================================================================

function redact(obj: unknown): unknown {
  const result: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(obj as Record<string, unknown>)) {
    result[key] = SENSITIVE_FIELDS.has(key) ? '[REDACTED]' : redact(value);
  }
  return result;
}

function inferAction(method: string, path: string): string {
  const resource = path.split('/').filter(Boolean)[1] ?? 'unknown';
  const methodMap: Record<string, string> = {
    GET: `READ:${resource}`,
    POST: `CREATE:${resource}`,
    PUT: `UPDATE:${resource}`,
    PATCH: `UPDATE:${resource}`,
    DELETE: `DELETE:${resource}`,
  };
  return methodMap[method.toUpperCase()] ?? `${method}:${resource}`;
}

// ============================================================================
// Plugin
// ============================================================================

/**
 * Fastify plugin that attaches an `onResponse` hook emitting audit log entries.
 *
 * @doc.type function
 * @doc.purpose Register automatic audit logging on Fastify instance
 * @doc.layer product
 * @doc.pattern Middleware
 */
export async function auditMiddleware(fastify: FastifyInstance): Promise<void> {
  fastify.addHook(
    'onResponse',
    async (request: FastifyRequest, reply: FastifyReply) => {
      const path = request.url.split('?')[0];

      // Skip excluded paths
      if (EXCLUDED_PATHS.has(path)) return;

      const user = (
        request as FastifyRequest & {
          user?: { userId?: string; role?: string };
        }
      ).user;

      const actor = user?.userId ?? request.ip ?? 'anonymous';
      const actorRole = user?.role ?? 'anonymous';

      const details = request.body
        ? JSON.stringify(redact(request.body))
        : undefined;

      void (async () => {
        try {
          await getAuditService().log({
            action: inferAction(request.method, path),
            actor,
            actorRole,
            resource: path,
            severity:
              reply.statusCode >= 500
                ? 'error'
                : reply.statusCode >= 400
                  ? 'warn'
                  : 'info',
            method: request.method,
            status: reply.statusCode,
            ipAddress: request.ip,
            userAgent: request.headers['user-agent'],
            details,
            success: reply.statusCode < 400,
          });
        } catch (err) {
          if (fastify.log?.warn) {
            fastify.log.warn(
              '[AuditMiddleware] Failed to write audit entry:',
              err
            );
          }
        }
      })();
    }
  );
}
