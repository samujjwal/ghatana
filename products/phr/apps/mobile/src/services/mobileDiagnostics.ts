/**
 * Safe mobile diagnostics module.
 *
 * Provides a controlled way to expose diagnostic information without leaking PHI.
 * Only event codes and correlation IDs are allowed - no PHI data.
 *
 * G11-T08: Telemetry wrappers that never include request/response payloads.
 *
 * @doc.type service
 * @doc.purpose Safe mobile diagnostics without PHI exposure
 * @doc.layer mobile
 */

export interface DiagnosticEvent {
  code: string;
  correlationId?: string;
  timestamp: number;
}

/**
 * Safe telemetry interface for emitting metrics without PHI.
 * This wrapper ensures no request/response payloads are ever included in telemetry.
 */
export interface SafeTelemetry {
  /**
   * Increments a counter without PHI.
   * @param name - Metric name (e.g., 'phr.mobile.request')
   * @param value - Increment value (default 1)
   * @param tags - Safe tags (no PHI)
   */
  incrementCounter(name: string, value?: number, tags?: Record<string, string>): void;

  /**
   * Records a timing metric without PHI.
   * @param name - Metric name (e.g., 'phr.mobile.duration')
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

const MAX_DIAGNOSTIC_EVENTS = 100;
const diagnosticEvents: DiagnosticEvent[] = [];
const telemetry: SafeTelemetry = new SafeTelemetryImpl();

/**
 * Records a diagnostic event with an event code and optional correlation ID.
 * No PHI data should be passed to this function.
 */
export function recordDiagnosticEvent(code: string, correlationId?: string): void {
  const event: DiagnosticEvent = {
    code,
    correlationId,
    timestamp: Date.now(),
  };

  diagnosticEvents.push(event);

  // Keep only the most recent events
  if (diagnosticEvents.length > MAX_DIAGNOSTIC_EVENTS) {
    diagnosticEvents.shift();
  }

  // Emit telemetry counter for diagnostic events (without PHI)
  telemetry.incrementCounter('phr.mobile.diagnostic', 1, {
    code,
    has_correlation_id: correlationId ? 'true' : 'false',
  });
}

/**
 * Returns the list of diagnostic events.
 * Only event codes and correlation IDs are included - no PHI.
 */
export function getDiagnosticEvents(): DiagnosticEvent[] {
  return [...diagnosticEvents];
}

/**
 * Clears all diagnostic events.
 */
export function clearDiagnosticEvents(): void {
  diagnosticEvents.length = 0;
}

/**
 * Returns a summary of diagnostic events grouped by code.
 * Useful for identifying patterns without exposing PHI.
 */
export function getDiagnosticSummary(): Record<string, number> {
  const summary: Record<string, number> = {};
  for (const event of diagnosticEvents) {
    summary[event.code] = (summary[event.code] || 0) + 1;
  }
  return summary;
}

/**
 * G11-T08: Telemetry wrapper for safe metric emission without PHI.
 * Use this to emit metrics without including request/response payloads.
 */
export function getTelemetry(): SafeTelemetry {
  return telemetry;
}
