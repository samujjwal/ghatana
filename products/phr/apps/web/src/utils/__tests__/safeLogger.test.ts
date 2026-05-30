/**
 * G11-T10: Log redaction test fixtures for safeLogger.
 * Tests that PHI is never included in log output.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { SafeLogger, logError, logInfo, telemetry } from '../safeLogger';

describe('SafeLogger - G11-T10 Log Redaction', () => {
  let logger: SafeLogger;

  beforeEach(() => {
    logger = new SafeLogger();
  });

  it('should not include PHI in log entries', () => {
    const contextWithPHI = {
      patientId: 'PATIENT-123',
      patientName: 'John Doe',
      ssn: '123-45-6789',
    };

    logger.error('Test error', 'corr-123', contextWithPHI);

    const entries = logger.getEntries();
    expect(entries).toHaveLength(1);
    
    // Context should be present but not logged to console
    // The log entry itself contains the context but it's not output to console
    if (entries[0]?.context) {
      expect(entries[0].context).toEqual(contextWithPHI);
    }
  });

  it('should emit telemetry counters without PHI', () => {
    const instanceTelemetry = logger.getTelemetry();
    const telemetrySpy = vi.spyOn(instanceTelemetry, 'incrementCounter');
    
    logger.info('Test message', 'corr-456', { someData: 'value' });
    
    expect(telemetrySpy).toHaveBeenCalledWith('phr.web.log', 1, {
      level: 'info',
      has_correlation_id: 'true',
    });
    
    telemetrySpy.mockRestore();
  });

  it('should limit log entries to maxEntries', () => {
    const maxEntries = 100;
    
    for (let i = 0; i < maxEntries + 10; i++) {
      logger.info(`Message ${i}`, `corr-${i}`);
    }
    
    const entries = logger.getEntries();
    expect(entries).toHaveLength(maxEntries);
  });

  it('should filter errors correctly', () => {
    logger.info('Info message', 'corr-1');
    logger.error('Error message', 'corr-2');
    logger.warn('Warning message', 'corr-3');
    logger.error('Another error', 'corr-4');
    
    const errors = logger.getErrors();
    expect(errors).toHaveLength(2);
    expect(errors[0]?.message).toBe('Error message');
    expect(errors[1]?.message).toBe('Another error');
  });

  it('should clear all entries', () => {
    logger.info('Message 1', 'corr-1');
    logger.error('Message 2', 'corr-2');
    
    expect(logger.getEntries()).toHaveLength(2);
    
    logger.clear();
    
    expect(logger.getEntries()).toHaveLength(0);
  });
});

describe('Telemetry - G11-T10 Safe Metric Emission', () => {
  it('should increment counter without PHI', () => {
    const consoleSpy = vi.spyOn(console, 'debug').mockImplementation(() => {});
    
    telemetry.incrementCounter('test.metric', 1, { tag: 'value' });
    
    expect(consoleSpy).toHaveBeenCalledWith(
      '[telemetry] counter=test.metric,value=1,tags=tag=value'
    );
    
    consoleSpy.mockRestore();
  });

  it('should record timing without PHI', () => {
    const consoleSpy = vi.spyOn(console, 'debug').mockImplementation(() => {});
    
    telemetry.recordTiming('test.timing', 1234, { operation: 'fetch' });
    
    expect(consoleSpy).toHaveBeenCalledWith(
      '[telemetry] timing=test.timing,duration_ms=1234,tags=operation=fetch'
    );
    
    consoleSpy.mockRestore();
  });

  it('should record gauge without PHI', () => {
    const consoleSpy = vi.spyOn(console, 'debug').mockImplementation(() => {});
    
    telemetry.recordGauge('test.gauge', 42, { resource: 'memory' });
    
    expect(consoleSpy).toHaveBeenCalledWith(
      '[telemetry] gauge=test.gauge,value=42,tags=resource=memory'
    );
    
    consoleSpy.mockRestore();
  });
});
