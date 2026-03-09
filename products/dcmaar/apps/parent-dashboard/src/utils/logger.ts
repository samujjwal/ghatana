/**
 * Logging Service
 * Centralized logging with multiple levels and context enrichment
 */

import { captureException, captureMessage, addBreadcrumb } from '../config/sentry';
import type { SeverityLevel } from '@sentry/react';

/**
 * Log levels
 */
export const LogLevel = {
  DEBUG: 'debug',
  INFO: 'info',
  WARN: 'warn',
  ERROR: 'error',
} as const;

export type LogLevel = typeof LogLevel[keyof typeof LogLevel];

/**
 * Log context interface
 */
interface LogContext {
  [key: string]: unknown;
}

/**
 * Logger configuration
 */
interface LoggerConfig {
  minLevel: LogLevel;
  enableConsole: boolean;
  enableSentry: boolean;
  enableBreadcrumbs: boolean;
}

/**
 * Default logger configuration
 */
const DEFAULT_CONFIG: LoggerConfig = {
  minLevel: import.meta.env.DEV ? LogLevel.DEBUG : LogLevel.INFO,
  enableConsole: true,
  enableSentry: !import.meta.env.DEV,
  enableBreadcrumbs: true,
};

/**
 * Logger class
 */
class Logger {
  private config: LoggerConfig;
  private context: LogContext = {};

  constructor(config: Partial<LoggerConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }

  /**
   * Set global context
   */
  setContext(context: LogContext): void {
    this.context = { ...this.context, ...context };
  }

  /**
   * Clear global context
   */
  clearContext(): void {
    this.context = {};
  }

  /**
   * Check if level should be logged
   */
  private shouldLog(level: LogLevel): boolean {
    const levels = [LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR];
    const minIndex = levels.indexOf(this.config.minLevel);
    const currentIndex = levels.indexOf(level);
    return currentIndex >= minIndex;
  }

  /**
   * Get Sentry severity from log level
   */
  private getSentrySeverity(level: LogLevel): SeverityLevel {
    switch (level) {
      case LogLevel.DEBUG:
        return 'debug';
      case LogLevel.INFO:
        return 'info';
      case LogLevel.WARN:
        return 'warning';
      case LogLevel.ERROR:
        return 'error';
      default:
        return 'info';
    }
  }

  /**
   * Format log message with context
   */
  private formatMessage(message: string, context?: LogContext): string {
    const allContext = { ...this.context, ...context };
    const contextStr = Object.keys(allContext).length
      ? ` | Context: ${JSON.stringify(allContext)}`
      : '';
    return `${message}${contextStr}`;
  }

  /**
   * Log to console
   */
  private logToConsole(
    level: LogLevel,
    message: string,
    context?: LogContext,
    error?: Error
  ): void {
    if (!this.config.enableConsole) return;

    const timestamp = new Date().toISOString();
    const prefix = `[${timestamp}] [${level.toUpperCase()}]`;
    const allContext = { ...this.context, ...context };

    switch (level) {
      case LogLevel.DEBUG:
        console.debug(prefix, message, allContext, error);
        break;
      case LogLevel.INFO:
        console.info(prefix, message, allContext);
        break;
      case LogLevel.WARN:
        console.warn(prefix, message, allContext, error);
        break;
      case LogLevel.ERROR:
        console.error(prefix, message, allContext, error);
        break;
    }
  }

  /**
   * Add breadcrumb to Sentry
   */
  private addBreadcrumb(level: LogLevel, message: string, context?: LogContext): void {
    if (!this.config.enableBreadcrumbs || !this.config.enableSentry) return;

    addBreadcrumb({
      type: 'default',
      level: this.getSentrySeverity(level),
      message,
      data: { ...this.context, ...context },
      timestamp: Date.now() / 1000,
    });
  }

  /**
   * Debug log
   */
  debug(message: string, context?: LogContext): void {
    if (!this.shouldLog(LogLevel.DEBUG)) return;

    this.logToConsole(LogLevel.DEBUG, message, context);
    this.addBreadcrumb(LogLevel.DEBUG, message, context);
  }

  /**
   * Info log
   */
  info(message: string, context?: LogContext): void {
    if (!this.shouldLog(LogLevel.INFO)) return;

    this.logToConsole(LogLevel.INFO, message, context);
    this.addBreadcrumb(LogLevel.INFO, message, context);
  }

  /**
   * Warning log
   */
  warn(message: string, context?: LogContext, error?: Error): void {
    if (!this.shouldLog(LogLevel.WARN)) return;

    this.logToConsole(LogLevel.WARN, message, context, error);
    this.addBreadcrumb(LogLevel.WARN, message, context);

    if (this.config.enableSentry && error) {
      captureException(error, { ...this.context, ...context });
    } else if (this.config.enableSentry) {
      captureMessage(this.formatMessage(message, context), 'warning');
    }
  }

  /**
   * Error log
   */
  error(message: string, error?: Error, context?: LogContext): void {
    if (!this.shouldLog(LogLevel.ERROR)) return;

    this.logToConsole(LogLevel.ERROR, message, context, error);
    this.addBreadcrumb(LogLevel.ERROR, message, context);

    if (this.config.enableSentry) {
      if (error) {
        captureException(error, { ...this.context, ...context, errorMessage: message });
      } else {
        captureMessage(this.formatMessage(message, context), 'error');
      }
    }
  }

  /**
   * Create a child logger with additional context
   */
  child(context: LogContext): Logger {
    const childLogger = new Logger(this.config);
    childLogger.context = { ...this.context, ...context };
    return childLogger;
  }

  /**
   * Update logger configuration
   */
  configure(config: Partial<LoggerConfig>): void {
    this.config = { ...this.config, ...config };
  }
}

/**
 * Singleton logger instance
 */
export const logger = new Logger();

/**
 * Create a logger with specific context
 */
export function createLogger(context: LogContext): Logger {
  return logger.child(context);
}

/**
 * Convenience exports
 */
export const debug = logger.debug.bind(logger);
export const info = logger.info.bind(logger);
export const warn = logger.warn.bind(logger);
export const error = logger.error.bind(logger);

export default logger;
