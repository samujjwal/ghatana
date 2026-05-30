/**
 * Tests for mobileDiagnostics — verifies safe diagnostic event recording
 * without PHI exposure.
 *
 * G11-T10: Log redaction test fixtures for telemetry wrappers.
 */

import {
  recordDiagnosticEvent,
  getDiagnosticEvents,
  clearDiagnosticEvents,
  getDiagnosticSummary,
  getTelemetry,
  type SafeTelemetry,
} from '../mobileDiagnostics';

describe('mobileDiagnostics', () => {
  beforeEach(() => {
    clearDiagnosticEvents();
  });

  it('records a diagnostic event with code and correlation ID', () => {
    recordDiagnosticEvent('PHR_MOBILE_LOCALE_LOAD_FAILED', 'corr-123');
    const events = getDiagnosticEvents();

    expect(events).toHaveLength(1);
    expect(events[0]?.code).toBe('PHR_MOBILE_LOCALE_LOAD_FAILED');
    expect(events[0]?.correlationId).toBe('corr-123');
    expect(events[0]?.timestamp).toBeGreaterThan(0);
  });

  it('records a diagnostic event without correlation ID', () => {
    recordDiagnosticEvent('PHR_MOBILE_OFFLINE_CACHE_MISS');
    const events = getDiagnosticEvents();

    expect(events).toHaveLength(1);
    expect(events[0]?.code).toBe('PHR_MOBILE_OFFLINE_CACHE_MISS');
    expect(events[0]?.correlationId).toBeUndefined();
  });

  it('maintains maximum event limit (100)', () => {
    for (let i = 0; i < 150; i++) {
      recordDiagnosticEvent(`EVENT_${i}`, `corr-${i}`);
    }

    const events = getDiagnosticEvents();
    expect(events).toHaveLength(100);
    // Should keep the most recent events
    expect(events[0]?.code).toBe('EVENT_50');
    expect(events[99]?.code).toBe('EVENT_149');
  });

  it('clears all diagnostic events', () => {
    recordDiagnosticEvent('EVENT_1', 'corr-1');
    recordDiagnosticEvent('EVENT_2', 'corr-2');
    clearDiagnosticEvents();

    const events = getDiagnosticEvents();
    expect(events).toHaveLength(0);
  });

  it('returns a copy of events, not the internal array', () => {
    recordDiagnosticEvent('EVENT_1', 'corr-1');
    const events1 = getDiagnosticEvents();
    const events2 = getDiagnosticEvents();

    expect(events1).not.toBe(events2);
    expect(events1).toEqual(events2);
  });

  it('provides diagnostic summary grouped by code', () => {
    recordDiagnosticEvent('EVENT_A', 'corr-1');
    recordDiagnosticEvent('EVENT_A', 'corr-2');
    recordDiagnosticEvent('EVENT_B', 'corr-3');

    const summary = getDiagnosticSummary();
    expect(summary).toEqual({
      EVENT_A: 2,
      EVENT_B: 1,
    });
  });

  it('returns empty summary when no events recorded', () => {
    const summary = getDiagnosticSummary();
    expect(summary).toEqual({});
  });

  it('does not expose PHI data', () => {
    // This test ensures the API design prevents PHI exposure
    // Only codes and correlation IDs are allowed
    recordDiagnosticEvent('PHR_MOBILE_ENCRYPTION_ERROR', 'corr-xyz');
    const events = getDiagnosticEvents();

    expect(events[0]).toHaveProperty('code');
    expect(events[0]).toHaveProperty('correlationId');
    expect(events[0]).toHaveProperty('timestamp');
    // No PHI-related properties
    expect(events[0]).not.toHaveProperty('patientId');
    expect(events[0]).not.toHaveProperty('name');
    expect(events[0]).not.toHaveProperty('medicalData');
  });
});

describe('SafeTelemetry - G11-T10', () => {
  it('should increment counter without PHI', () => {
    const consoleSpy = jest.spyOn(console, 'debug').mockImplementation(() => {});
    const telemetry = getTelemetry();

    telemetry.incrementCounter('test.metric', 1, { tag: 'value' });

    expect(consoleSpy).toHaveBeenCalledWith(
      '[telemetry] counter=test.metric,value=1,tags=tag=value'
    );

    consoleSpy.mockRestore();
  });

  it('should record timing without PHI', () => {
    const consoleSpy = jest.spyOn(console, 'debug').mockImplementation(() => {});
    const telemetry = getTelemetry();

    telemetry.recordTiming('test.timing', 1234, { operation: 'fetch' });

    expect(consoleSpy).toHaveBeenCalledWith(
      '[telemetry] timing=test.timing,duration_ms=1234,tags=operation=fetch'
    );

    consoleSpy.mockRestore();
  });

  it('should record gauge without PHI', () => {
    const consoleSpy = jest.spyOn(console, 'debug').mockImplementation(() => {});
    const telemetry = getTelemetry();

    telemetry.recordGauge('test.gauge', 42, { resource: 'memory' });

    expect(consoleSpy).toHaveBeenCalledWith(
      '[telemetry] gauge=test.gauge,value=42,tags=resource=memory'
    );

    consoleSpy.mockRestore();
  });
});
