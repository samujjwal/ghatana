/**
 * Audit Logging Middleware
 *
 * <p><b>Purpose</b><br>
 * Express middleware that automatically logs all requests and responses to
 * the audit trail. Captures actor, action, resource, method, status, and
 * metadata for comprehensive compliance tracking.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const auditMiddleware = new AuditLoggingMiddleware(auditService, tenantExtractor);
 * app.use(auditMiddleware.middleware());
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Request/response audit logging middleware
 * @doc.layer product
 * @doc.pattern Middleware
 */

import { Request, Response, NextFunction } from 'express';
import { PrismaClient } from '@prisma/client';

/**
 * Interface for audit log entry to be created
 */
export interface AuditLogData {
  tenantId: string;
  actor: string;
  action: string;
  resource: string;
  method: string;
  status: number;
  severity: 'info' | 'warning' | 'critical';
  details?: string;
  ipAddress?: string;
  userAgent?: string;
  responseTime?: number;
  error?: string;
}

/**
 * Function type for extracting tenant from request
 */
type TenantExtractor = (req: Request) => string;

/**
 * Function type for extracting actor from request
 */
type ActorExtractor = (req: Request) => string;

/**
 * AuditLoggingMiddleware handles automatic audit logging for all requests
 */
export class AuditLoggingMiddleware {
  /**
   * Sensitive fields to redact from audit logs
   */
  private readonly SENSITIVE_FIELDS = [
    'password',
    'token',
    'secret',
    'apiKey',
    'creditCard',
    'ssn',
  ];

  /**
   * Routes to exclude from audit logging
   */
  private readonly EXCLUDED_ROUTES = [
    '/health',
    '/metrics',
    '/status',
    '/alive',
    '/readiness',
  ];

  /**
   * Creates a new AuditLoggingMiddleware instance.
   *
   * @param prisma - Prisma client for database access
   * @param tenantExtractor - Function to extract tenant ID from request
   * @param actorExtractor - Function to extract actor ID from request
   */
  constructor(
    private prisma: PrismaClient,
    private tenantExtractor: TenantExtractor,
    private actorExtractor: ActorExtractor = (req) =>
      req.headers['x-user-id'] as string || 'anonymous'
  ) { }

  /**
   * Creates Express middleware function.
   *
   * <p><b>Purpose</b><br>
   * Returns the actual middleware function for use with Express app.
   * Records request start time, intercepts response, and logs to audit trail
   * before response is sent.
   *
   * @returns Express middleware function
   */
  middleware() {
    return (req: Request, res: Response, next: NextFunction) => {
      // Skip excluded routes
      if (this.isExcludedRoute(req.path)) {
        return next();
      }

      // Capture start time
      const startTime = Date.now();

      // Store original send method
      const originalSend = res.send;
      const self = this;

      // Override res.send to capture response
      res.send = function (data: unknown) {
        const duration = Date.now() - startTime;

        // Create audit log entry
        self.auditLog(req, res, data, duration).catch((err: unknown) => {
          console.error('Failed to log audit entry:', err);
        });

        // Call original send
        return originalSend.call(this, data);
      };

      next();
    };
  }

  /**
   * Logs audit entry for a request/response cycle.
   *
   * <p><b>Purpose</b><br>
   * Creates comprehensive audit log entry with all relevant metadata,
   * redacts sensitive information, and persists to database.
   *
   * @param req - Express request object
   * @param res - Express response object
   * @param responseBody - Response body (if available)
   * @param duration - Request duration in milliseconds
   * @returns Promise<void>
   * @private
   */
  private async auditLog(
    req: Request,
    res: Response,
    responseBody: unknown,
    duration: number
  ): Promise<void> {
    try {
      const tenantId = this.tenantExtractor(req);
      const actor = this.actorExtractor(req);

      // Determine action from method and path
      const action = this.inferAction(req.method, req.path);

      // Determine resource
      const resource = this.extractResource(req.path);

      // Determine severity based on status code
      const severity = this.determineSeverity(res.statusCode);

      // Redact sensitive data
      const sanitizedBody = this.redactSensitiveData(
        req.body || {}
      );
      const sanitizedResponse = this.redactSensitiveData(
        typeof responseBody === 'string'
          ? JSON.parse(responseBody)
          : responseBody
      );

      const auditEntry: AuditLogData = {
        tenantId,
        actor,
        action,
        resource,
        method: req.method,
        status: res.statusCode,
        severity,
        details: JSON.stringify({
          path: req.path,
          query: req.query,
          body: sanitizedBody,
          response: sanitizedResponse,
        }),
        ipAddress: this.extractIPAddress(req),
        userAgent: req.headers['user-agent'],
        responseTime: duration,
        error: res.statusCode >= 400 ? sanitizedResponse?.error : undefined,
      };

      // Save to database
      await this.prisma.auditLogEntry.create({
        data: {
          id: `audit-${Date.now()}-${Math.random()}`,
          tenantId: auditEntry.tenantId,
          actor: auditEntry.actor,
          actorRole: 'user',
          action: auditEntry.action,
          resource: auditEntry.resource,
          method: auditEntry.method,
          status: auditEntry.status,
          severity: auditEntry.severity,
          details: auditEntry.details,
          ipAddress: auditEntry.ipAddress,
          userAgent: auditEntry.userAgent,
          responseTime: auditEntry.responseTime,
          error: auditEntry.error,
          timestamp: new Date(),
        },
      });

      // Log critical events to console as well
      if (severity === 'critical') {
        console.warn(
          `[AUDIT] CRITICAL: ${action} on ${resource} by ${actor} from ${auditEntry.ipAddress}`
        );
      }
    } catch (error) {
      console.error('Audit logging error:', error);
      // Don't throw - don't let logging failures break the request
    }
  }

  /**
   * Infers action type from HTTP method and path.
   *
   * <p><b>Purpose</b><br>
   * Maps HTTP verbs and path patterns to semantic action names for better
   * readability in audit trails.
   *
   * @param method - HTTP method
   * @param path - Request path
   * @returns Action string
   * @private
   */
  private inferAction(method: string, path: string): string {
    const segments = path.split('/').filter((s) => s);
    const resource = segments[0] || 'unknown';

    const actionMap: { [key: string]: string } = {
      GET: 'read',
      POST: 'create',
      PUT: 'update',
      PATCH: 'modify',
      DELETE: 'delete',
      HEAD: 'check',
      OPTIONS: 'query',
    };

    const action = actionMap[method] || 'unknown';
    return `${resource}.${action}`;
  }

  /**
   * Extracts resource identifier from request path.
   *
   * @param path - Request path
   * @returns Resource identifier
   * @private
   */
  private extractResource(path: string): string {
    const segments = path.split('/').filter((s) => s);
    // First segment is typically the resource type
    return segments[0] || 'root';
  }

  /**
   * Determines severity level based on HTTP status code.
   *
   * @param statusCode - HTTP status code
   * @returns Severity level
   * @private
   */
  private determineSeverity(
    statusCode: number
  ): 'info' | 'warning' | 'critical' {
    if (statusCode < 400) return 'info';
    if (statusCode < 500) return 'warning';
    return 'critical';
  }

  /**
   * Redacts sensitive information from objects.
   *
   * <p><b>Purpose</b><br>
   * Removes or masks sensitive fields (passwords, tokens, etc.) before
   * storing in audit logs to prevent credential exposure.
   *
   * @param data - Object to redact
   * @returns Redacted object
   * @private
   */
  private redactSensitiveData(data: unknown): unknown {
    if (!data || typeof data !== 'object') {
      return data;
    }

    const redacted = Array.isArray(data) ? [...data] : { ...data };

    for (const field of this.SENSITIVE_FIELDS) {
      if (field in redacted) {
        redacted[field] = '[REDACTED]';
      }
    }

    // Recursively redact nested objects
    for (const key in redacted) {
      if (typeof redacted[key] === 'object') {
        redacted[key] = this.redactSensitiveData(redacted[key]);
      }
    }

    return redacted;
  }

  /**
   * Extracts client IP address from request.
   *
   * <p><b>Purpose</b><br>
   * Handles various proxy configurations to get the actual client IP
   * (X-Forwarded-For, X-Real-IP, or connection socket).
   *
   * @param req - Express request object
   * @returns IP address string
   * @private
   */
  private extractIPAddress(req: Request): string {
    const forwarded = req.headers['x-forwarded-for'];
    if (typeof forwarded === 'string') {
      return forwarded.split(',')[0].trim();
    }

    const realIP = req.headers['x-real-ip'];
    if (typeof realIP === 'string') {
      return realIP;
    }

    return (
      req.socket.remoteAddress ||
      req.connection.remoteAddress ||
      'unknown'
    );
  }

  /**
   * Checks if route should be excluded from audit logging.
   *
   * @param path - Request path
   * @returns boolean
   * @private
   */
  private isExcludedRoute(path: string): boolean {
    return this.EXCLUDED_ROUTES.some((excluded) => path.startsWith(excluded));
  }
}

