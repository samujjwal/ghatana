/**
 * Structured Logging Utility
 * 
 * Provides centralized, structured logging with:
 * - Multiple log levels
 * - Contextual information
 * - Performance tracking
 * - Error correlation
 * - Security event logging
 */

import pino from 'pino';

export interface LogContext {
  userId?: string;
  tenantId?: string;
  requestId?: string;
  operation?: string;
  duration?: number;
  error?: Error | string;
  [key: string]: any;
}

export interface Logger {
  debug(context: LogContext, message: string): void;
  info(context: LogContext, message: string): void;
  warn(context: LogContext, message: string): void;
  error(context: LogContext, message: string): void;
  child(context: LogContext): Logger;
}

/**
 * Pino-based Logger Implementation
 */
class PinoLogger implements Logger {
  private logger: pino.Logger;

  constructor(name: string, context: LogContext = {}) {
    this.logger = pino({
      name,
      level: process.env.LOG_LEVEL || 'info',
      formatters: {
        level: (label) => ({ level: label }),
      },
      timestamp: pino.stdTimeFunctions.isoTime,
      base: {
        pid: process.pid,
        hostname: process.env.HOSTNAME || 'localhost',
        service: 'tutorputor-platform',
        version: process.env.npm_package_version || '1.0.0',
        ...context,
      },
    });
  }

  debug(context: LogContext, message: string): void {
    this.logger.debug(context, message);
  }

  info(context: LogContext, message: string): void {
    this.logger.info(context, message);
  }

  warn(context: LogContext, message: string): void {
    this.logger.warn(context, message);
  }

  error(context: LogContext, message: string): void {
    this.logger.error(context, message);
  }

  child(context: LogContext): Logger {
    return new PinoLogger('', context);
  }
}

/**
 * Security Logger
 */
export class SecurityLogger {
  private logger: Logger;

  constructor() {
    this.logger = createLogger('security');
  }

  /**
   * Log authentication events
   */
  logAuthEvent(event: 'login' | 'logout' | 'failed_login' | 'token_expired', context: {
    userId?: string;
    email?: string;
    tenantId?: string;
    ip?: string;
    userAgent?: string;
    error?: string;
  }): void {
    this.logger.info({
      event,
      ...context,
      category: 'authentication',
    }, `Authentication event: ${event}`);
  }

  /**
   * Log authorization events
   */
  logAuthzEvent(event: 'access_granted' | 'access_denied', context: {
    userId?: string;
    resource?: string;
    action?: string;
    tenantId?: string;
    reason?: string;
  }): void {
    this.logger.info({
      event,
      ...context,
      category: 'authorization',
    }, `Authorization event: ${event}`);
  }

  /**
   * Log security violations
   */
  logSecurityViolation(violation: 'sql_injection_attempt' | 'xss_attempt' | 'rate_limit_exceeded' | 'suspicious_activity', context: {
    userId?: string;
    ip?: string;
    userAgent?: string;
    details?: any;
    severity?: 'low' | 'medium' | 'high' | 'critical';
  }): void {
    this.logger.warn({
      violation,
      ...context,
      category: 'security_violation',
    }, `Security violation: ${violation}`);
  }
}

/**
 * Performance Logger
 */
export class PerformanceLogger {
  private logger: Logger;

  constructor() {
    this.logger = createLogger('performance');
  }

  /**
   * Log performance metrics
   */
  logPerformance(operation: string, context: {
    duration: number;
    success: boolean;
    userId?: string;
    tenantId?: string;
    metadata?: any;
  }): void {
    this.logger.info({
      operation,
      ...context,
      category: 'performance',
    }, `Performance: ${operation}`);
  }

  /**
   * Create performance timer
   */
  startTimer(operation: string, context: LogContext = {}): {
    end: (additionalContext?: LogContext) => void;
  } {
    const startTime = Date.now();

    return {
      end: (additionalContext?: LogContext) => {
        const duration = Date.now() - startTime;
        this.logPerformance(operation, {
          duration,
          success: true,
          ...context,
          ...additionalContext,
        });
      },
    };
  }
}

/**
 * Logger factory
 */
export function createLogger(name: string, context: LogContext = {}): Logger {
  return new PinoLogger(name, context);
}

/**
 * Global loggers
 */
export const securityLogger = new SecurityLogger();
export const performanceLogger = new PerformanceLogger();

/**
 * Request context middleware
 */
export function createRequestContext(requestId: string, context: LogContext = {}): LogContext {
  return {
    requestId,
    timestamp: new Date().toISOString(),
    ...context,
  };
}

/**
 * Error correlation
 */
export function correlateError(error: Error, context: LogContext = {}): LogContext {
  return {
    error: {
      name: error.name,
      message: error.message,
      stack: error.stack,
    },
    ...context,
  };
}

/**
 * Log sanitization for security
 */
export function sanitizeForLogging(obj: any): any {
  if (typeof obj !== 'object' || obj === null) {
    return obj;
  }

  const sensitiveFields = [
    'password',
    'secret',
    'token',
    'key',
    'creditCard',
    'ssn',
    'apiKey',
    'jwt',
  ];

  const sanitized: any = {};

  for (const [key, value] of Object.entries(obj)) {
    if (sensitiveFields.some(field => key.toLowerCase().includes(field.toLowerCase()))) {
      sanitized[key] = '[REDACTED]';
    } else if (typeof value === 'object' && value !== null) {
      sanitized[key] = sanitizeForLogging(value);
    } else {
      sanitized[key] = value;
    }
  }

  return sanitized;
}
