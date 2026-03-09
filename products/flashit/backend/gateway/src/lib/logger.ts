/**
 * Structured Logger
 * 
 * Replaces direct fastify.log calls with structured logging including correlation IDs
 * 
 * @doc.type service
 * @doc.purpose Structured logging with request correlation
 * @doc.layer infrastructure
 * @doc.pattern Service
 */

import { randomUUID } from 'crypto';
import type { FastifyRequest } from 'fastify';

export interface LogContext {
  correlationId?: string;
  userId?: string;
  method?: string;
  url?: string;
  statusCode?: number;
  duration?: number;
  error?: Error | unknown;
  [key: string]: unknown;
}

export class Logger {
  private context: LogContext;

  constructor(context: LogContext = {}) {
    this.context = context;
  }

  /**
   * Create logger from Fastify request with auto-correlation ID
   */
  static fromRequest(request: FastifyRequest): Logger {
    const correlationId = (request.headers['x-correlation-id'] as string) || randomUUID();
    
    return new Logger({
      correlationId,
      method: request.method,
      url: request.url,
      userId: (request as any).user?.userId,
    });
  }

  /**
   * Create logger with custom context
   */
  static create(context: LogContext): Logger {
    return new Logger({
      correlationId: randomUUID(),
      ...context,
    });
  }

  /**
   * Add additional context to logger (returns new instance)
   */
  child(additionalContext: LogContext): Logger {
    return new Logger({
      ...this.context,
      ...additionalContext,
    });
  }

  /**
   * Log info level message
   */
  info(message: string, data?: LogContext): void {
    this.log('info', message, data);
  }

  /**
   * Log warning level message
   */
  warn(message: string, data?: LogContext): void {
    this.log('warn', message, data);
  }

  /**
   * Log error level message
   */
  error(message: string, error?: Error | unknown, data?: LogContext): void {
    const errorData: LogContext = {
      ...data,
      error: error instanceof Error ? {
        name: error.name,
        message: error.message,
        stack: error.stack,
      } : error,
    };
    
    this.log('error', message, errorData);
  }

  /**
   * Log debug level message (only in development)
   */
  debug(message: string, data?: LogContext): void {
    if (process.env.NODE_ENV !== 'production') {
      this.log('debug', message, data);
    }
  }

  /**
   * Internal logging method
   */
  private log(level: 'info' | 'warn' | 'error' | 'debug', message: string, data?: LogContext): void {
    const logEntry = {
      timestamp: new Date().toISOString(),
      level,
      message,
      ...this.context,
      ...data,
    };

    // In production, output as JSON for log aggregators
    if (process.env.NODE_ENV === 'production') {
      console.log(JSON.stringify(logEntry));
    } else {
      // In development, pretty print
      const { correlationId, userId, method, url, error: errorObj, ...rest } = logEntry;
      
      const prefix = [
        correlationId ? `[${correlationId.slice(0, 8)}]` : '',
        userId ? `[user:${userId.slice(0, 8)}]` : '',
        method && url ? `[${method} ${url}]` : '',
      ].filter(Boolean).join(' ');

      console.log(`${prefix} ${level.toUpperCase()}: ${message}`);
      
      if (Object.keys(rest).length > 2) { // More than just timestamp and level
        console.log('  Data:', JSON.stringify(rest, null, 2));
      }

      if (errorObj && typeof errorObj === 'object') {
        console.error('  Error:', errorObj);
      }
    }
  }

  /**
   * Log HTTP request completion
   */
  logRequest(statusCode: number, duration: number): void {
    const level = statusCode >= 500 ? 'error' : statusCode >= 400 ? 'warn' : 'info';
    
    this.log(level, 'HTTP Request Completed', {
      statusCode,
      duration,
      durationMs: `${duration}ms`,
    });
  }

  /**
   * Log business event for audit trail
   */
  logBusinessEvent(eventType: string, data: LogContext): void {
    this.info(`Business Event: ${eventType}`, {
      eventType,
      ...data,
    });
  }
}

/**
 * Fastify plugin for attaching logger to requests
 */
export async function registerLoggerPlugin(app: any): Promise<void> {
  app.decorateRequest('logger', null);

  app.addHook('onRequest', async (request: any) => {
    request.logger = Logger.fromRequest(request);
    
    // Log incoming request
    request.logger.debug('Incoming Request', {
      headers: request.headers,
      query: request.query,
    });
  });

  app.addHook('onResponse', async (request: any, reply: any) => {
    const duration = reply.getResponseTime();
    request.logger.logRequest(reply.statusCode, duration);
  });

  // Add correlation ID to response headers
  app.addHook('onSend', async (request: any, reply: any) => {
    reply.header('X-Correlation-Id', request.logger.context.correlationId);
  });
}

// Export singleton for non-request contexts
export const systemLogger = Logger.create({ service: 'flashit-api' });
