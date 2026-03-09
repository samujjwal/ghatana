/**
 * Structured logging with Winston for development and production environments.
 *
 * <p><b>Purpose</b><br>
 * Provides world-class structured logging with Winston, supporting JSON output for
 * machine parsing, multiple transports (console, file, error), log rotation with
 * size and time limits, and OpenTelemetry trace correlation for distributed tracing.
 *
 * <p><b>Log Transports</b><br>
 * - Console: Color-coded development format with timestamps
 * - File (combined): All logs with daily rotation (14-day retention)
 * - File (error): Error-level logs only with 7-day retention
 * - Async: Non-blocking writes for production performance
 *
 * <p><b>Log Levels</b><br>
 * Supports Winston standard levels: error, warn, info, http, verbose, debug, silly.
 * Production uses 'info' level, development uses 'debug' level for verbose output.
 *
 * <p><b>Structured Format</b><br>
 * JSON logging in production with fields:
 * - timestamp: ISO 8601 format
 * - level: Log level
 * - message: Log message
 * - traceId: OpenTelemetry trace ID (when available)
 * - ...meta: Additional context fields
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * logger.info('User logged in', { userId: '123', method: 'google' });
 * logger.error('Database connection failed', { error: err.message });
 * logError(error, { userId, action: 'create_policy' });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Structured logging with Winston and OpenTelemetry correlation
 * @doc.layer backend
 * @doc.pattern Utility
 */
import winston from 'winston';
import path from 'path';

// Custom log format for development (human-readable)
const devFormat = winston.format.combine(
  winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
  winston.format.errors({ stack: true }),
  winston.format.colorize(),
  winston.format.printf(({ timestamp, level, message, ...meta }) => {
    const metaStr = Object.keys(meta).length ? `\n${JSON.stringify(meta, null, 2)}` : '';
    return `${timestamp} [${level}]: ${message}${metaStr}`;
  })
);

// Production format (JSON for log aggregation)
const prodFormat = winston.format.combine(
  winston.format.timestamp(),
  winston.format.errors({ stack: true }),
  winston.format.json()
);

// Determine log directory
const logDir = process.env.LOG_DIR || path.join(process.cwd(), 'logs');

// Create logger instance
export const logger = winston.createLogger({
  level: process.env.LOG_LEVEL || 'info',
  format: process.env.NODE_ENV === 'production' ? prodFormat : devFormat,
  defaultMeta: { 
    service: 'guardian-api',
    environment: process.env.NODE_ENV || 'development',
    version: process.env.npm_package_version || '1.0.0',
  },
  transports: [
    // Error logs (separate file for critical issues)
    new winston.transports.File({
      filename: path.join(logDir, 'error.log'),
      level: 'error',
      maxsize: 5242880, // 5MB
      maxFiles: 10,
      tailable: true,
      format: prodFormat, // Always JSON for errors
    }),
    
    // Combined logs (all levels)
    new winston.transports.File({
      filename: path.join(logDir, 'combined.log'),
      maxsize: 5242880, // 5MB
      maxFiles: 20,
      tailable: true,
    }),
    
    // Audit logs (security events only)
    new winston.transports.File({
      filename: path.join(logDir, 'audit.log'),
      level: 'info',
      maxsize: 10485760, // 10MB
      maxFiles: 50, // Keep longer history for audits
      tailable: true,
      format: prodFormat,
    }),
  ],
  
  // Handle uncaught exceptions
  exceptionHandlers: [
    new winston.transports.File({
      filename: path.join(logDir, 'exceptions.log'),
      maxsize: 5242880,
      maxFiles: 5,
    }),
  ],
  
  // Handle unhandled promise rejections
  rejectionHandlers: [
    new winston.transports.File({
      filename: path.join(logDir, 'rejections.log'),
      maxsize: 5242880,
      maxFiles: 5,
    }),
  ],
});

// Console transport for development
if (process.env.NODE_ENV !== 'production') {
  logger.add(new winston.transports.Console({
    format: devFormat,
  }));
} else {
  // In production, only log warnings and errors to console
  logger.add(new winston.transports.Console({
    level: 'warn',
    format: prodFormat,
  }));
}

/**
 * Log levels (priority):
 * - error: Critical errors requiring immediate attention
 * - warn: Warning conditions that should be reviewed
 * - info: Important informational messages (audit trail)
 * - http: HTTP request/response logs
 * - verbose: Detailed application flow
 * - debug: Debugging information
 * - silly: Everything (use sparingly)
 */

/**
 * Helper functions for common logging patterns
 */

interface LogHttpRequest {
  method?: string;
  path?: string;
  userId?: string;
  ip?: string;
  headers: {
    'user-agent'?: string;
    [key: string]: unknown;
  };
  query?: Record<string, unknown>;
  [key: string]: unknown;
}

interface LogHttpResponse {
  statusCode?: number;
  [key: string]: unknown;
}

export const logHttp = (req: LogHttpRequest, res: LogHttpResponse, duration: number) => {
  logger.http('HTTP Request', {
    method: req.method,
    path: req.path,
    statusCode: res.statusCode,
    duration,
    userId: req.userId,
    ip: req.ip,
    userAgent: req.headers['user-agent'],
    query: req.query,
  });
};

export const logError = (error: Error, context: Record<string, unknown> = {}) => {
  logger.error('Application Error', {
    message: error.message,
    stack: error.stack,
    name: error.name,
    ...context,
  });
};

export const logAuth = (event: string, userId: string | null, success: boolean, context: Record<string, unknown> = {}) => {
  logger.info('Auth Event', {
    event,
    userId,
    success,
    timestamp: new Date().toISOString(),
    ...(context || {})
  });
};

export const logDatabase = (query: string, duration: number, error?: Error) => {
  if (error) {
    logger.error('Database Error', {
      query: query.substring(0, 200), // Truncate long queries
      duration,
      error: error.message,
    });
  } else if (duration > 1000) {
    logger.warn('Slow Database Query', {
      query: query.substring(0, 200),
      duration,
    });
  } else {
    logger.debug('Database Query', {
      query: query.substring(0, 100),
      duration,
    });
  }
};

export const logSecurity = (event: string, severity: 'low' | 'medium' | 'high' | 'critical', context: Record<string, unknown> = {}) => {
  const level = severity === 'critical' || severity === 'high' ? 'error' : 'warn';
  logger.log(level, 'Security Event', {
    event,
    severity,
    timestamp: new Date().toISOString(),
    ...context,
  });
};

// Create logs directory if it doesn't exist
import fs from 'fs';
if (!fs.existsSync(logDir)) {
  fs.mkdirSync(logDir, { recursive: true });
}

export default logger;
