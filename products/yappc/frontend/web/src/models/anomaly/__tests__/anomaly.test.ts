/**
 * Anomaly Model Tests
 *
 * Unit tests for SecurityAnomaly, AnomalyBaseline, and ThreatIntelligence entities.
 *
 * @doc.type test
 * @doc.purpose Unit tests for anomaly detection domain entities
 * @doc.layer product
 * @doc.pattern Entity Tests
 */

import { describe, it, expect } from 'vitest';
import {
  SecurityAnomaly,
  type JavaExecutionMetadata,
} from '../SecurityAnomaly.entity';
import { AnomalyBaseline } from '../AnomalyBaseline.entity';
import { ThreatIntelligence } from '../ThreatIntelligence.entity';

// ============================================================================
// Shared helpers
// ============================================================================

function makeMetadata(
  overrides?: Partial<JavaExecutionMetadata>
): JavaExecutionMetadata {
  return {
    executionId: 'exec-test-001',
    algorithm: 'ISOLATION_FOREST',
    executedAt: new Date(),
    processingTimeMs: 150,
    confidence: 0.92,
    ...overrides,
  };
}

// ============================================================================
// SecurityAnomaly Tests
// ============================================================================

describe('SecurityAnomaly', () => {
  describe('create()', () => {
    it('creates a valid anomaly with DETECTED status', () => {
      const anomaly = SecurityAnomaly.create({
        type: 'NETWORK_SPIKE',
        severity: 0.75,
        baseline: 100,
        observed: 350,
        description: 'Unexpected spike in outbound traffic',
        javaServiceExecutionId: 'exec-123',
        javaExecutionMetadata: makeMetadata(),
      });
      expect(anomaly.type).toBe('NETWORK_SPIKE');
      expect(anomaly.severity).toBe(0.75);
      expect(anomaly.baseline).toBe(100);
      expect(anomaly.observed).toBe(350);
      expect(anomaly.status).toBe('DETECTED');
    });

    it('generates a unique id for each instance', () => {
      const a = SecurityAnomaly.create({
        type: 'UNKNOWN',
        severity: 0.5,
        baseline: 10,
        observed: 15,
        description: 'Test',
        javaServiceExecutionId: 'x',
        javaExecutionMetadata: makeMetadata(),
      });
      const b = SecurityAnomaly.create({
        type: 'UNKNOWN',
        severity: 0.5,
        baseline: 10,
        observed: 15,
        description: 'Test',
        javaServiceExecutionId: 'x',
        javaExecutionMetadata: makeMetadata(),
      });
      expect(a.id).not.toBe(b.id);
    });

    it('allows severity of exactly 0 and 1', () => {
      expect(() =>
        SecurityAnomaly.create({
          type: 'UNKNOWN',
          severity: 0,
          baseline: 0,
          observed: 0,
          description: 'Min severity',
          javaServiceExecutionId: 'x',
          javaExecutionMetadata: makeMetadata(),
        })
      ).not.toThrow();

      expect(() =>
        SecurityAnomaly.create({
          type: 'UNKNOWN',
          severity: 1,
          baseline: 5,
          observed: 100,
          description: 'Max severity',
          javaServiceExecutionId: 'x',
          javaExecutionMetadata: makeMetadata(),
        })
      ).not.toThrow();
    });

    it('throws when severity is out of range', () => {
      expect(() =>
        SecurityAnomaly.create({
          type: 'NETWORK_SPIKE',
          severity: 1.5,
          baseline: 100,
          observed: 350,
          description: 'Over range',
          javaServiceExecutionId: 'x',
          javaExecutionMetadata: makeMetadata(),
        })
      ).toThrow('Severity must be between 0.0 and 1.0');
    });

    it('throws when description is empty', () => {
      expect(() =>
        SecurityAnomaly.create({
          type: 'NETWORK_SPIKE',
          severity: 0.5,
          baseline: 10,
          observed: 30,
          description: '',
          javaServiceExecutionId: 'x',
          javaExecutionMetadata: makeMetadata(),
        })
      ).toThrow();
    });

    it('throws when java execution id is empty', () => {
      expect(() =>
        SecurityAnomaly.create({
          type: 'NETWORK_SPIKE',
          severity: 0.5,
          baseline: 10,
          observed: 30,
          description: 'A spike',
          javaServiceExecutionId: 'x',
          javaExecutionMetadata: makeMetadata({ executionId: '' }),
        })
      ).toThrow();
    });

    it('captures optional relatedResourceIds', () => {
      const anomaly = SecurityAnomaly.create({
        type: 'UNUSUAL_DATA_ACCESS',
        severity: 0.65,
        baseline: 50,
        observed: 200,
        description: 'Unusual access pattern',
        javaServiceExecutionId: 'exec-789',
        javaExecutionMetadata: makeMetadata(),
        relatedResourceIds: ['bucket-abc', 'table-xyz'],
      });
      expect(anomaly.relatedResourceIds).toEqual(['bucket-abc', 'table-xyz']);
    });
  });

  describe('status lifecycle (immutable transitions)', () => {
    let anomaly: SecurityAnomaly;

    beforeEach(() => {
      anomaly = SecurityAnomaly.create({
        type: 'FAILED_AUTHENTICATION',
        severity: 0.6,
        baseline: 5,
        observed: 50,
        description: 'High failed login rate',
        javaServiceExecutionId: 'exec-456',
        javaExecutionMetadata: makeMetadata(),
      });
    });

    it('acknowledge() transitions to ACKNOWLEDGED and preserves id', () => {
      const acked = anomaly.acknowledge('Investigating spike in logins');
      expect(acked.status).toBe('ACKNOWLEDGED');
      expect(acked.id).toBe(anomaly.id);
      expect(acked.investigationNotes).toHaveLength(1);
    });

    it('acknowledge() returns a new instance (immutable)', () => {
      const acked = anomaly.acknowledge('notes');
      expect(acked).not.toBe(anomaly);
      expect(anomaly.status).toBe('DETECTED');
    });

    it('addRemediationStep() transitions to MITIGATED', () => {
      const mitigated = anomaly.addRemediationStep('Blocked IP range');
      expect(mitigated.status).toBe('MITIGATED');
      expect(mitigated.remediationSteps).toContain('Blocked IP range');
    });

    it('resolve() transitions to RESOLVED', () => {
      const resolved = anomaly.resolve();
      expect(resolved.status).toBe('RESOLVED');
    });

    it('markFalsePositive() transitions to FALSE_POSITIVE', () => {
      const fp = anomaly.markFalsePositive();
      expect(fp.status).toBe('FALSE_POSITIVE');
    });
  });

  describe('getters', () => {
    it('detectedAt returns a copy (not the underlying reference)', () => {
      const anomaly = SecurityAnomaly.create({
        type: 'RESOURCE_EXHAUSTION',
        severity: 0.4,
        baseline: 80,
        observed: 98,
        description: 'CPU near limit',
        javaServiceExecutionId: 'exec-x',
        javaExecutionMetadata: makeMetadata(),
      });
      const ts1 = anomaly.detectedAt;
      const ts2 = anomaly.detectedAt;
      expect(ts1).toEqual(ts2);
      expect(ts1).not.toBe(ts2); // different Date objects
    });
  });
});

// ============================================================================
// AnomalyBaseline Tests
// ============================================================================

describe('AnomalyBaseline', () => {
  function validBaselineParams() {
    return {
      metricType: 'network_traffic_bytes' as const,
      resourceId: 'subnet-12345',
      baselineValue: 1_000_000,
      threshold: 3_500_000,
      standardDeviation: 200_000,
      confidenceInterval: 0.95,
      javaServiceExecutionId: 'calc-exec-001',
      dataPointsUsed: 10_000,
    };
  }

  describe('create()', () => {
    it('creates a valid baseline with ACTIVE status', () => {
      const baseline = AnomalyBaseline.create(validBaselineParams());
      expect(baseline.metricType).toBe('network_traffic_bytes');
      expect(baseline.resourceId).toBe('subnet-12345');
      expect(baseline.status).toBe('ACTIVE');
    });

    it('defaults confidenceInterval to 0.95', () => {
      const params = validBaselineParams();
      const { confidenceInterval: _ci, ...rest } = params;
      const baseline = AnomalyBaseline.create(rest);
      expect(baseline.confidenceInterval).toBe(0.95);
    });

    it('defaults dataPointsUsed to 1000 when not provided', () => {
      const params = validBaselineParams();
      const { dataPointsUsed: _dp, ...rest } = params;
      const baseline = AnomalyBaseline.create(rest);
      expect(baseline.dataPointsUsed).toBe(1000);
    });

    it('throws when threshold is below baselineValue', () => {
      const params = { ...validBaselineParams(), threshold: 500_000 };
      expect(() => AnomalyBaseline.create(params)).toThrow('Validation failed');
    });

    it('accepts low dataPointsUsed (warning emitted at service level, not entity)', () => {
      // Per AnomalyBaseline.entity.ts: data points < 100 produces a less reliable baseline
      // but is still valid at the entity level — service layer emits the warning.
      const params = { ...validBaselineParams(), dataPointsUsed: 50 };
      expect(() => AnomalyBaseline.create(params)).not.toThrow();
    });

    it('throws when confidenceInterval is zero', () => {
      const params = { ...validBaselineParams(), confidenceInterval: 0 };
      expect(() => AnomalyBaseline.create(params)).toThrow('Validation failed');
    });
  });

  describe('isAnomaly()', () => {
    it('returns true when value exceeds threshold', () => {
      const baseline = AnomalyBaseline.create(validBaselineParams());
      expect(baseline.isAnomaly(4_000_000)).toBe(true);
    });

    it('returns false when value is within threshold', () => {
      const baseline = AnomalyBaseline.create(validBaselineParams());
      expect(baseline.isAnomaly(1_500_000)).toBe(false);
    });

    it('returns false when value equals threshold exactly', () => {
      const baseline = AnomalyBaseline.create(validBaselineParams());
      expect(baseline.isAnomaly(3_500_000)).toBe(false);
    });
  });

  describe('calculateAnomalyScore()', () => {
    it('returns 0 for values at or below baseline', () => {
      const baseline = AnomalyBaseline.create(validBaselineParams());
      expect(baseline.calculateAnomalyScore(1_000_000)).toBe(0);
      expect(baseline.calculateAnomalyScore(500_000)).toBe(0);
    });

    it('returns a positive score for values above baseline', () => {
      const baseline = AnomalyBaseline.create(validBaselineParams());
      const score = baseline.calculateAnomalyScore(1_600_000);
      expect(score).toBeGreaterThan(0);
    });

    it('clamps score to maximum of 1', () => {
      const baseline = AnomalyBaseline.create(validBaselineParams());
      const score = baseline.calculateAnomalyScore(100_000_000);
      expect(score).toBeLessThanOrEqual(1);
    });

    it('handles zero standardDeviation by returning 0 or 1', () => {
      const params = { ...validBaselineParams(), standardDeviation: 0 };
      const baseline = AnomalyBaseline.create(params);
      expect(baseline.calculateAnomalyScore(900_000)).toBe(0);
      expect(baseline.calculateAnomalyScore(1_100_000)).toBe(1);
    });
  });
});

// ============================================================================
// ThreatIntelligence Tests
// ============================================================================

describe('ThreatIntelligence', () => {
  function validThreatParams() {
    return {
      cveId: 'CVE-2025-12345',
      title: 'Critical RCE in OpenSSL',
      severity: 0.95,
      exploitAvailable: true,
      description: 'Unauthenticated remote code execution in OpenSSL 3.0.x',
      mitigation: 'Upgrade to OpenSSL 3.0.3 or later',
    };
  }

  describe('create()', () => {
    it('creates a valid threat with default source NVD', () => {
      const threat = ThreatIntelligence.create(validThreatParams());
      expect(threat.cveId).toBe('CVE-2025-12345');
      expect(threat.title).toBe('Critical RCE in OpenSSL');
      expect(threat.severity).toBe(0.95);
      expect(threat.source).toBe('NVD');
    });

    it('accepts custom source', () => {
      const threat = ThreatIntelligence.create({
        ...validThreatParams(),
        source: 'MITRE',
      });
      expect(threat.source).toBe('MITRE');
    });

    it('throws for invalid CVE ID format', () => {
      expect(() =>
        ThreatIntelligence.create({
          ...validThreatParams(),
          cveId: 'BAD-FORMAT',
        })
      ).toThrow('Validation failed');
    });

    it('throws when severity is out of range', () => {
      expect(() =>
        ThreatIntelligence.create({ ...validThreatParams(), severity: 1.5 })
      ).toThrow('Severity must be between 0.0 and 1.0');
    });

    it('throws when title is empty', () => {
      expect(() =>
        ThreatIntelligence.create({ ...validThreatParams(), title: '' })
      ).toThrow('Validation failed');
    });

    it('throws when description is empty', () => {
      expect(() =>
        ThreatIntelligence.create({ ...validThreatParams(), description: '' })
      ).toThrow('Validation failed');
    });

    it('throws when mitigation is empty', () => {
      expect(() =>
        ThreatIntelligence.create({ ...validThreatParams(), mitigation: '' })
      ).toThrow('Validation failed');
    });

    it('throws when a reference URL is invalid', () => {
      expect(() =>
        ThreatIntelligence.create({
          ...validThreatParams(),
          references: ['not-a-url'],
        })
      ).toThrow('Validation failed');
    });

    it('accepts valid reference URLs', () => {
      const threat = ThreatIntelligence.create({
        ...validThreatParams(),
        references: [
          'https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2025-12345',
        ],
      });
      expect(threat.references).toHaveLength(1);
    });
  });

  describe('isExploitableAndCritical()', () => {
    it('returns true when exploit available and severity >= 0.8', () => {
      const threat = ThreatIntelligence.create(validThreatParams()); // severity 0.95, exploit true
      expect(threat.isExploitableAndCritical()).toBe(true);
    });

    it('returns false when no exploit available', () => {
      const threat = ThreatIntelligence.create({
        ...validThreatParams(),
        exploitAvailable: false,
      });
      expect(threat.isExploitableAndCritical()).toBe(false);
    });

    it('returns false when severity below 0.8 even with exploit', () => {
      const threat = ThreatIntelligence.create({
        ...validThreatParams(),
        severity: 0.7,
        exploitAvailable: true,
      });
      expect(threat.isExploitableAndCritical()).toBe(false);
    });
  });

  describe('withAffectedVersions() (immutable update)', () => {
    it('returns a new instance with updated versions', () => {
      const original = ThreatIntelligence.create(validThreatParams());
      const updated = original.withAffectedVersions([
        'OpenSSL 3.0.0',
        'OpenSSL 3.0.1',
      ]);
      expect(updated).not.toBe(original);
      expect(updated.affectedVersions).toEqual([
        'OpenSSL 3.0.0',
        'OpenSSL 3.0.1',
      ]);
      expect(original.affectedVersions).toEqual([]);
    });
  });

  describe('withReference() (immutable update)', () => {
    it('adds a reference URL and returns new instance', () => {
      const original = ThreatIntelligence.create(validThreatParams());
      const withRef = original.withReference(
        'https://nvd.nist.gov/vuln/detail/CVE-2025-12345'
      );
      expect(withRef).not.toBe(original);
      expect(withRef.references).toHaveLength(1);
      expect(original.references).toHaveLength(0);
    });
  });
});
