/**
 * W-009: Safe logger for PHR web application.
 * Captures safe diagnostics with correlation IDs without writing PHI to console output.
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

    if (typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('phr-diagnostic', {
        detail: {
          code: `PHR_WEB_${level.toUpperCase()}`,
          level,
          correlationId: entry.correlationId,
          timestamp: entry.timestamp,
        },
      }));
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
