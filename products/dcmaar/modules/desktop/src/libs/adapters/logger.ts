/**
 * Structured logger implementation for adapter observability.
 * Outputs JSON logs with correlation IDs and metadata.
 */

import type { Logger } from './types';

export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

export interface LogEntry {
  timestamp: string;
  level: LogLevel;
  message: string;
  workspaceId?: string;
  correlationId?: string;
  meta?: Record<string, unknown>;
  error?: {
    name: string;
    message: string;
    stack?: string;
  };
}

export interface LoggerConfig {
  level: LogLevel;
  workspaceId?: string;
  correlationId?: string;
  sink?: (entry: LogEntry) => void;
}

const LOG_LEVELS: Record<LogLevel, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
};

export class StructuredLogger implements Logger {
  private readonly config: LoggerConfig;

  constructor(config: LoggerConfig) {
    this.config = config;
  }

  debug(message: string, meta?: Record<string, unknown>): void {
    this.log('debug', message, undefined, meta);
  }

  info(message: string, meta?: Record<string, unknown>): void {
    this.log('info', message, undefined, meta);
  }

  warn(message: string, meta?: Record<string, unknown>): void {
    this.log('warn', message, undefined, meta);
  }

  error(message: string, error?: Error, meta?: Record<string, unknown>): void {
    this.log('error', message, error, meta);
  }

  private log(
    level: LogLevel,
    message: string,
    error?: Error,
    meta?: Record<string, unknown>,
  ): void {
    if (LOG_LEVELS[level] < LOG_LEVELS[this.config.level]) {
      return;
    }

    const entry: LogEntry = {
      timestamp: new Date().toISOString(),
      level,
      message,
      workspaceId: this.config.workspaceId,
      correlationId: this.config.correlationId,
      meta,
    };

    if (error) {
      entry.error = {
        name: error.name,
        message: error.message,
        stack: error.stack,
      };
    }

    if (this.config.sink) {
      this.config.sink(entry);
    } else {
      this.defaultSink(entry);
    }
  }

  private defaultSink(entry: LogEntry): void {
    const formatted = JSON.stringify(entry);

    switch (entry.level) {
      case 'debug':
      case 'info':
        console.log(formatted);
        break;
      case 'warn':
        console.warn(formatted);
        break;
      case 'error':
        console.error(formatted);
        break;
    }
  }

  child(overrides: Partial<LoggerConfig>): StructuredLogger {
    return new StructuredLogger({
      ...this.config,
      ...overrides,
    });
  }
}

export const createLogger = (config: LoggerConfig): Logger => {
  return new StructuredLogger(config);
};
