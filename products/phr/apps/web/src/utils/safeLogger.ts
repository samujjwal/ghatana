/**
 * W-009: Safe logger for PHR web application.
 * Replaces console.error/console.log with safe diagnostics that include correlation IDs.
 */

export type LogLevel = 'info' | 'warn' | 'error';

export type LogEntry = {
  level: LogLevel;
  message: string;
  correlationId?: string;
  timestamp: string;
  context?: Record<string, unknown>;
};

export class SafeLogger {
  private entries: LogEntry[] = [];
  private maxEntries = 100;

  log(level: LogLevel, message: string, correlationId?: string, context?: Record<string, unknown>): void {
    const entry: LogEntry = {
      level,
      message,
      correlationId,
      timestamp: new Date().toISOString(),
      context,
    };

    this.entries.push(entry);
    
    // Keep only the last maxEntries
    if (this.entries.length > this.maxEntries) {
      this.entries.shift();
    }

    // Still log to console for development, but with safe formatting
    const consoleMessage = `[${entry.timestamp}] [${level.toUpperCase()}] ${entry.message}${entry.correlationId ? ` [${entry.correlationId}]` : ''}`;
    
    switch (level) {
      case 'error':
        console.error(consoleMessage, context);
        break;
      case 'warn':
        console.warn(consoleMessage, context);
        break;
      case 'info':
      default:
        console.log(consoleMessage, context);
        break;
    }
  }

  info(message: string, correlationId?: string, context?: Record<string, unknown>): void {
    this.log('info', message, correlationId, context);
  }

  warn(message: string, correlationId?: string, context?: Record<string, unknown>): void {
    this.log('warn', message, correlationId, context);
  }

  error(message: string, correlationId?: string, context?: Record<string, unknown>): void {
    this.log('error', message, correlationId, context);
  }

  getEntries(): LogEntry[] {
    return [...this.entries];
  }

  clear(): void {
    this.entries = [];
  }

  getErrors(): LogEntry[] {
    return this.entries.filter(e => e.level === 'error');
  }
}

// Singleton instance
export const logger = new SafeLogger();

export function logError(message: string, correlationId?: string, context?: Record<string, unknown>): void {
  logger.error(message, correlationId, context);
}

export function logWarn(message: string, correlationId?: string, context?: Record<string, unknown>): void {
  logger.warn(message, correlationId, context);
}

export function logInfo(message: string, correlationId?: string, context?: Record<string, unknown>): void {
  logger.info(message, correlationId, context);
}
