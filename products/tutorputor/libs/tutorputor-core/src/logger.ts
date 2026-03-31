/**
 * @doc.type utility
 * @doc.purpose Structured logging utility for production-grade logging
 * @doc.layer platform
 * @doc.pattern Utility
 */

type FastifyRequestLike = { log: Logger };

export interface LogContext {
  [key: string]: unknown;
}

export interface Logger {
  info(context: LogContext, message?: string): void;
  info(message: string): void;
  error(context: LogContext, message?: string): void;
  error(message: string): void;
  warn(context: LogContext, message?: string): void;
  warn(message: string): void;
  debug(context: LogContext, message?: string): void;
  debug(message: string): void;
}

/**
 * Create a logger from a Fastify request.
 * Uses the request's built-in Pino logger for structured logging.
 */
export function createLogger(request: FastifyRequestLike): Logger {
  return request.log;
}

/**
 * Create a standalone logger for use outside of request context.
 * Uses Pino for structured logging with proper formatting.
 */
export function createStandaloneLogger(context?: LogContext): Logger {
  // This will be replaced with actual Pino logger instance
  // For now, provide a compatible interface
  const baseContext = context || {};
  
  return {
    info(contextOrMessage: LogContext | string, message?: string): void {
      if (typeof contextOrMessage === 'string') {
        console.info(JSON.stringify({ ...baseContext, msg: contextOrMessage, level: 'info' }));
      } else {
        console.info(JSON.stringify({ ...baseContext, ...contextOrMessage, msg: message, level: 'info' }));
      }
    },
    error(contextOrMessage: LogContext | string, message?: string): void {
      if (typeof contextOrMessage === 'string') {
        console.error(JSON.stringify({ ...baseContext, msg: contextOrMessage, level: 'error' }));
      } else {
        console.error(JSON.stringify({ ...baseContext, ...contextOrMessage, msg: message, level: 'error' }));
      }
    },
    warn(contextOrMessage: LogContext | string, message?: string): void {
      if (typeof contextOrMessage === 'string') {
        console.warn(JSON.stringify({ ...baseContext, msg: contextOrMessage, level: 'warn' }));
      } else {
        console.warn(JSON.stringify({ ...baseContext, ...contextOrMessage, msg: message, level: 'warn' }));
      }
    },
    debug(contextOrMessage: LogContext | string, message?: string): void {
      if (typeof contextOrMessage === 'string') {
        console.debug(JSON.stringify({ ...baseContext, msg: contextOrMessage, level: 'debug' }));
      } else {
        console.debug(JSON.stringify({ ...baseContext, ...contextOrMessage, msg: message, level: 'debug' }));
      }
    },
  };
}

/**
 * Helper function to log errors with proper context.
 */
export function logError(
  logger: Logger,
  error: Error,
  context: LogContext = {}
): void {
  logger.error({
    err: error,
    errorType: error.constructor.name,
    stack: error.stack,
    ...context,
  }, error.message);
}

/**
 * Helper function to log operation start.
 */
export function logOperationStart(
  logger: Logger,
  operation: string,
  context: LogContext = {}
): void {
  logger.info({
    operation,
    phase: 'start',
    ...context,
  }, `Starting ${operation}`);
}

/**
 * Helper function to log operation completion.
 */
export function logOperationComplete(
  logger: Logger,
  operation: string,
  context: LogContext = {}
): void {
  logger.info({
    operation,
    phase: 'complete',
    ...context,
  }, `Completed ${operation}`);
}
