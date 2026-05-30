/**
 * W-009: Safe logger for PHR web application.
 * Captures safe diagnostics with correlation IDs without writing PHI to console output.
 *
 * G11-T08: Telemetry wrappers that never include request/response payloads.
 */

export type LogLevel = 'info' | 'warn' | 'error';

export type LogEntry = {
  level: LogLevel;
  message: string;
  correlationId?: string;
  timestamp: string;
  context?: Record<string, unknown>;
};

/**
 * Safe telemetry interface for emitting metrics without PHI.
 * This wrapper ensures no request/response payloads are ever included in telemetry.
 */
export interface SafeTelemetry {
  /**
   * Increments a counter without PHI.
   * @param name - Metric name (e.g., 'phr.api.request')
   * @param value - Increment value (default 1)
   * @param tags - Safe tags (no PHI)
   */
  incrementCounter(name: string, value?: number, tags?: Record<string, string>): void;

  /**
   * Records a timing metric without PHI.
   * @param name - Metric name (e.g., 'phr.api.duration')
   * @param durationMs - Duration in milliseconds
   * @param tags - Safe tags (no PHI)
   */
  recordTiming(name: string, durationMs: number, tags?: Record<string, string>): void;

  /**
   * Records a gauge metric without PHI.
   * @param name - Metric name
   * @param value - Gauge value
   * @param tags - Safe tags (no PHI)
   */
  recordGauge(name: string, value: number, tags?: Record<string, string>): void;
}

/**
 * Safe telemetry implementation that emits to console.debug for now.
 * In production, this would integrate with a real telemetry service.
 */
class SafeTelemetryImpl implements SafeTelemetry {
  incrementCounter(name: string, value = 1, tags?: Record<string, string>): void {
    const tagString = tags ? Object.entries(tags).map(([k, v]) => `${k}=${v}`).join(',') : '';
    console.debug(`[telemetry] counter=${name},value=${value}${tagString ? `,tags=${tagString}` : ''}`);
  }

  recordTiming(name: string, durationMs: number, tags?: Record<string, string>): void {
    const tagString = tags ? Object.entries(tags).map(([k, v]) => `${k}=${v}`).join(',') : '';
    console.debug(`[telemetry] timing=${name},duration_ms=${durationMs}${tagString ? `,tags=${tagString}` : ''}`);
  }

  recordGauge(name: string, value: number, tags?: Record<string, string>): void {
    const tagString = tags ? Object.entries(tags).map(([k, v]) => `${k}=${v}`).join(',') : '';
    console.debug(`[telemetry] gauge=${name},value=${value}${tagString ? `,tags=${tagString}` : ''}`);
  }
}

export class SafeLogger {
  private entries: LogEntry[] = [];
  private maxEntries = 100;
  private telemetry: SafeTelemetry;

  constructor(telemetry?: SafeTelemetry) {
    this.telemetry = telemetry || new SafeTelemetryImpl();
  }

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

    // Emit telemetry counter for log events (without PHI)
    this.telemetry.incrementCounter('phr.web.log', 1, {
      level,
      has_correlation_id: correlationId ? 'true' : 'false',
    });
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

  /**
   * Gets the telemetry instance for direct metric emission.
   * G11-T08: Provides telemetry wrapper that never includes request/response payloads.
   */
  getTelemetry(): SafeTelemetry {
    return this.telemetry;
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

/**
 * G11-T08: Telemetry wrapper for safe metric emission without PHI.
 * Use this to emit metrics without including request/response payloads.
 */
export const telemetry = logger.getTelemetry();
