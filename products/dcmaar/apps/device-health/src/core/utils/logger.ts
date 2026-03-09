/**
 * Production-grade logging system for DCMAAR extension
 * Provides configurable log levels and proper environment-based filtering
 */

export enum LogLevel {
  SILENT = 0,
  ERROR = 1,
  WARN = 2,
  INFO = 3,
  DEBUG = 4,
}

// Remove unused interface - keeping for potential future structured logging

class Logger {
  private level: LogLevel;
  private prefix: string;

  constructor(context: string = 'DCMAAR', level?: LogLevel) {
    this.prefix = `[${context}]`;
    this.level = level ?? this.getEnvironmentLogLevel();
  }

  private getEnvironmentLogLevel(): LogLevel {
    // In production builds (when NODE_ENV is production), default to ERROR only
    const isDevelopment = process.env.NODE_ENV !== 'production';
    const envLevel = process.env.DCMAAR_LOG_LEVEL;

    if (envLevel) {
      switch (envLevel.toLowerCase()) {
        case 'silent':
          return LogLevel.SILENT;
        case 'error':
          return LogLevel.ERROR;
        case 'warn':
          return LogLevel.WARN;
        case 'info':
          return LogLevel.INFO;
        case 'debug':
          return LogLevel.DEBUG;
        default:
          break;
      }
    }

    // Default levels based on environment
    return isDevelopment ? LogLevel.INFO : LogLevel.ERROR;
  }

  private shouldLog(level: LogLevel): boolean {
    return level <= this.level;
  }

  private formatMessage(level: LogLevel, message: string, data?: unknown): string {
    const timestamp = new Date().toISOString();
    const levelName = LogLevel[level];
    let formatted = `${timestamp} ${this.prefix}[${levelName}] ${message}`;

    if (data !== undefined) {
      formatted += ' ' + (typeof data === 'object' ? JSON.stringify(data) : String(data));
    }

    return formatted;
  }

  error(message: string, data?: unknown): void {
    if (this.shouldLog(LogLevel.ERROR)) {
      console.error(this.formatMessage(LogLevel.ERROR, message, data));
    }
  }

  warn(message: string, data?: unknown): void {
    if (this.shouldLog(LogLevel.WARN)) {
      console.warn(this.formatMessage(LogLevel.WARN, message, data));
    }
  }

  info(message: string, data?: unknown): void {
    if (this.shouldLog(LogLevel.INFO)) {
      console.info(this.formatMessage(LogLevel.INFO, message, data));
    }
  }

  debug(message: string, data?: unknown): void {
    if (this.shouldLog(LogLevel.DEBUG)) {
      console.debug(this.formatMessage(LogLevel.DEBUG, message, data));
    }
  }

  /**
   * Create a child logger with additional context
   */
  child(context: string): Logger {
    return new Logger(`${this.prefix.slice(1, -1)}:${context}`, this.level);
  }

  /**
   * Set log level dynamically
   */
  setLevel(level: LogLevel): void {
    this.level = level;
  }
}

// Default logger instance
export const logger = new Logger();

// Context-specific loggers
export const persistenceLogger = logger.child('persistence');
export const transportLogger = logger.child('transport');
export const ingestLogger = logger.child('ingest');
export const contentLogger = logger.child('content');
export const intentLogger = logger.child('intent');

/**
 * Development-only features that are stripped out in production
 */
export class DevFeatures {
  private static isDevelopment = process.env.NODE_ENV !== 'production';

  /**
   * Execute code only in development
   */
  static dev(fn: () => void): void {
    if (this.isDevelopment) {
      try {
        fn();
      } catch (error) {
        logger.error('Development feature failed', error);
      }
    }
  }

  /**
   * Conditional logging for development/debug scenarios
   */
  static devLog(message: string, data?: unknown): void {
    if (this.isDevelopment) {
      logger.debug(`[DEV] ${message}`, data);
    }
  }
}
