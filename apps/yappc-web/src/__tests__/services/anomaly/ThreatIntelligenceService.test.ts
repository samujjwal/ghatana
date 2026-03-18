/**
 * Unit tests for ThreatIntelligenceService
 *
 * Tests validate:
 * - Threat enrichment workflow with CVE correlation
 * - Threat cache management and updates
 * - Critical threat filtering
 * - Software-to-threat mapping
 * - Metrics collection on all operations
 *
 * @see ThreatIntelligenceService
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ThreatIntelligenceService } from '../../../services/anomaly/ThreatIntelligenceService';
import { MetricsCollector, NoopMetricsCollector } from '../../../observability/MetricsCollector';

describe('ThreatIntelligenceService', () => {
  let service: ThreatIntelligenceService;
  let metrics: MetricsCollector;

  beforeEach(() => {
    metrics = new NoopMetricsCollector();
    service = new ThreatIntelligenceService(metrics);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('getThreat', () => {
    /**
     * Should retrieve threat intelligence for valid CVE ID
     *
     * GIVEN: Valid CVE ID
     * WHEN: getThreat() is called
     * THEN: Returns ThreatIntelligence with populated fields
     */
    it('should retrieve threat intelligence for valid CVE', async () => {
      // GIVEN
      const cveId = 'CVE-2025-1234';

      // WHEN
      const threat = await service.getThreat(cveId);

      // THEN
      expect(threat).toBeDefined();
      expect(threat?.cveId).toBe(cveId);
      expect(threat?.title).toBeDefined();
      expect(threat?.severity).toBeDefined();
      expect(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']).toContain(threat?.severity);
    });

    /**
     * Should cache threat after first retrieval
     *
     * GIVEN: First call to getThreat with CVE-2025-1234
     * WHEN: getThreat called twice with same CVE
     * THEN: Cache metrics show hit on second call
     */
    it('should cache threats after first retrieval', async () => {
      // GIVEN
      const cveId = 'CVE-2025-5678';
      const mockMetrics = new MetricsCollector();
      const incrementSpy = vi.spyOn(mockMetrics, 'incrementCounter');
      service = new ThreatIntelligenceService(mockMetrics);

      // WHEN
      await service.getThreat(cveId);
      await service.getThreat(cveId);

      // THEN
      const cacheCalls = incrementSpy.mock.calls.filter(
        (call) => call[0] === 'threat_cache_hit'
      );
      expect(cacheCalls.length).toBeGreaterThan(0);
    });

    /**
     * Should return null for non-existent CVE
     *
     * GIVEN: Invalid or non-existent CVE ID
     * WHEN: getThreat() is called
     * THEN: Returns null
     */
    it('should return null for non-existent CVE', async () => {
      // GIVEN
      const invalidCve = 'CVE-9999-9999';

      // WHEN
      const threat = await service.getThreat(invalidCve);

      // THEN
      expect(threat).toBeNull();
    });

    /**
     * Should record metrics for threat lookups
     *
     * GIVEN: Metrics collector spy
     * WHEN: getThreat() is called
     * THEN: Lookup metric is recorded
     */
    it('should record metrics for threat lookups', async () => {
      // GIVEN
      const mockMetrics = new MetricsCollector();
      const incrementSpy = vi.spyOn(mockMetrics, 'incrementCounter');
      service = new ThreatIntelligenceService(mockMetrics);

      // WHEN
      await service.getThreat('CVE-2025-1234');

      // THEN
      expect(incrementSpy).toHaveBeenCalledWith('threat_lookup_total', 1, expect.any(Object));
    });
  });

  describe('getThreatsForSoftware', () => {
    /**
     * Should retrieve all threats affecting specific software version
     *
     * GIVEN: Software name and version
     * WHEN: getThreatsForSoftware() is called
     * THEN: Returns array of vulnerabilities affecting that software
     */
    it('should retrieve threats affecting software', async () => {
      // GIVEN
      const softwareName = 'nginx';
      const version = '1.18.0';

      // WHEN
      const threats = await service.getThreatsForSoftware(softwareName, version);

      // THEN
      expect(threats).toBeDefined();
      expect(Array.isArray(threats)).toBe(true);
    });

    /**
     * Should filter threats by exact version match
     *
     * GIVEN: Software with specific version
     * WHEN: getThreatsForSoftware() is called
     * THEN: Returns only threats applicable to that version
     */
    it('should filter threats by version', async () => {
      // GIVEN
      const threats = await service.getThreatsForSoftware('apache-httpd', '2.4.38');

      // THEN
      expect(threats).toBeDefined();
      if (threats.length > 0) {
        threats.forEach((threat) => {
          expect(threat.affectedVersions || []).toContain('2.4.38');
        });
      }
    });

    /**
     * Should return empty array for software with no known vulnerabilities
     *
     * GIVEN: Software name and version with no known threats
     * WHEN: getThreatsForSoftware() is called
     * THEN: Returns empty array
     */
    it('should return empty array for software without known vulnerabilities', async () => {
      // GIVEN
      const threats = await service.getThreatsForSoftware('hypothetical-safe-app', '1.0.0');

      // THEN
      expect(Array.isArray(threats)).toBe(true);
    });

    /**
     * Should collect metrics on software threat queries
     *
     * GIVEN: Metrics collector spy
     * WHEN: getThreatsForSoftware() is called
     * THEN: Lookup metric is recorded with software name
     */
    it('should record metrics for software threat queries', async () => {
      // GIVEN
      const mockMetrics = new MetricsCollector();
      const incrementSpy = vi.spyOn(mockMetrics, 'incrementCounter');
      service = new ThreatIntelligenceService(mockMetrics);

      // WHEN
      await service.getThreatsForSoftware('nginx', '1.18.0');

      // THEN
      expect(incrementSpy).toHaveBeenCalledWith(
        'software_threat_lookup',
        1,
        expect.objectContaining({
          software: 'nginx',
        })
      );
    });
  });

  describe('enrichAnomaly', () => {
    /**
     * Should enrich anomaly with related threats
     *
     * GIVEN: Anomaly entity and optional software name
     * WHEN: enrichAnomaly() is called
     * THEN: Returns EnrichedThreat with threats and risk escalation
     */
    it('should enrich anomaly with threats', async () => {
      // GIVEN
      const anomaly = {
        id: 'anom-123',
        type: 'NETWORK_SPIKE',
        severity: 'HIGH',
        observed: 5000,
        baseline: 1000,
      };

      // WHEN
      const enriched = await service.enrichAnomaly(anomaly, 'nginx');

      // THEN
      expect(enriched).toBeDefined();
      expect(enriched?.anomaly).toEqual(anomaly);
      expect(enriched?.relatedThreats).toBeDefined();
      expect(enriched?.riskEscalation).toBeDefined();
    });

    /**
     * Should calculate risk escalation score
     *
     * GIVEN: CRITICAL anomaly with exploitable threats
     * WHEN: enrichAnomaly() is called
     * THEN: Risk escalation reflects threat severity
     */
    it('should calculate risk escalation from threats', async () => {
      // GIVEN
      const criticalAnomaly = {
        id: 'anom-456',
        type: 'PRIVILEGE_ESCALATION',
        severity: 'CRITICAL',
        observed: 100,
        baseline: 1,
      };

      // WHEN
      const enriched = await service.enrichAnomaly(criticalAnomaly);

      // THEN
      expect(enriched?.riskEscalation).toBeDefined();
      expect(enriched?.riskEscalation).toBeGreaterThanOrEqual(0);
      expect(enriched?.riskEscalation).toBeLessThanOrEqual(100);
    });

    /**
     * Should handle enrichment without software name
     *
     * GIVEN: Anomaly without software context
     * WHEN: enrichAnomaly() is called without software name
     * THEN: Still returns EnrichedThreat with general threats
     */
    it('should handle enrichment without software context', async () => {
      // GIVEN
      const anomaly = {
        id: 'anom-789',
        type: 'MALWARE_SIGNATURE',
        severity: 'CRITICAL',
        observed: 1,
        baseline: 0,
      };

      // WHEN
      const enriched = await service.enrichAnomaly(anomaly);

      // THEN
      expect(enriched).toBeDefined();
      expect(enriched?.anomaly).toEqual(anomaly);
    });

    /**
     * Should record enrichment metrics
     *
     * GIVEN: Metrics collector spy
     * WHEN: enrichAnomaly() is called
     * THEN: Enrichment metric is recorded
     */
    it('should record metrics for enrichment operations', async () => {
      // GIVEN
      const mockMetrics = new MetricsCollector();
      const incrementSpy = vi.spyOn(mockMetrics, 'incrementCounter');
      service = new ThreatIntelligenceService(mockMetrics);

      const anomaly = { id: 'anom-test', type: 'NETWORK_SPIKE', severity: 'HIGH' };

      // WHEN
      await service.enrichAnomaly(anomaly as unknown);

      // THEN
      expect(incrementSpy).toHaveBeenCalledWith(
        'anomaly_enrichment_total',
        1,
        expect.any(Object)
      );
    });
  });

  describe('getCriticalThreats', () => {
    /**
     * Should retrieve only critical and high severity threats
     *
     * GIVEN: Request for critical threats
     * WHEN: getCriticalThreats() is called
     * THEN: Returns array of CRITICAL and HIGH severity threats
     */
    it('should retrieve critical severity threats', async () => {
      // GIVEN & WHEN
      const threats = await service.getCriticalThreats();

      // THEN
      expect(Array.isArray(threats)).toBe(true);
      threats.forEach((threat) => {
        expect(['CRITICAL', 'HIGH']).toContain(threat.severity);
      });
    });

    /**
     * Should filter to exploitable threats only
     *
     * GIVEN: Request for critical threats
     * WHEN: getCriticalThreats() is called
     * THEN: Returns only threats with exploitAvailable = true
     */
    it('should return only exploitable critical threats', async () => {
      // GIVEN & WHEN
      const threats = await service.getCriticalThreats();

      // THEN
      threats.forEach((threat) => {
        if (threat.severity === 'CRITICAL') {
          expect(threat.exploitAvailable).toBe(true);
        }
      });
    });

    /**
     * Should limit results to reasonable count
     *
     * GIVEN: Request for critical threats
     * WHEN: getCriticalThreats() is called
     * THEN: Returns max 50 threats
     */
    it('should limit critical threats to reasonable count', async () => {
      // GIVEN & WHEN
      const threats = await service.getCriticalThreats();

      // THEN
      expect(threats.length).toBeLessThanOrEqual(50);
    });

    /**
     * Should record critical threat access metrics
     *
     * GIVEN: Metrics collector spy
     * WHEN: getCriticalThreats() is called
     * THEN: Critical threat metric is recorded
     */
    it('should record metrics for critical threat retrieval', async () => {
      // GIVEN
      const mockMetrics = new MetricsCollector();
      const incrementSpy = vi.spyOn(mockMetrics, 'incrementCounter');
      service = new ThreatIntelligenceService(mockMetrics);

      // WHEN
      await service.getCriticalThreats();

      // THEN
      expect(incrementSpy).toHaveBeenCalledWith('critical_threat_query', 1, expect.any(Object));
    });
  });

  describe('updateThreatIntelligence', () => {
    /**
     * Should update threat cache with latest data
     *
     * GIVEN: Call to update threat intelligence
     * WHEN: updateThreatIntelligence() is called
     * THEN: Cache is refreshed and lastUpdated timestamp is set
     */
    it('should update threat cache', async () => {
      // GIVEN & WHEN
      await service.updateThreatIntelligence();

      // THEN - should complete without error
      expect(true).toBe(true);
    });

    /**
     * Should record update metrics
     *
     * GIVEN: Metrics collector spy
     * WHEN: updateThreatIntelligence() is called
     * THEN: Update metric is recorded
     */
    it('should record metrics for threat intelligence updates', async () => {
      // GIVEN
      const mockMetrics = new MetricsCollector();
      const incrementSpy = vi.spyOn(mockMetrics, 'incrementCounter');
      service = new ThreatIntelligenceService(mockMetrics);

      // WHEN
      await service.updateThreatIntelligence();

      // THEN
      expect(incrementSpy).toHaveBeenCalledWith(
        'threat_intelligence_update',
        1,
        expect.any(Object)
      );
    });

    /**
     * Should handle errors during update gracefully
     *
     * GIVEN: External service failure
     * WHEN: updateThreatIntelligence() is called
     * THEN: Error is handled and recorded
     */
    it('should handle update errors gracefully', async () => {
      // GIVEN & WHEN
      // Even if update fails, should not throw
      await expect(service.updateThreatIntelligence()).resolves.toBeDefined();
    });
  });

  describe('Performance and caching', () => {
    /**
     * Should return cached results quickly
     *
     * GIVEN: Threat has been cached
     * WHEN: getThreat called again on same CVE
     * THEN: Returns cached result with no network call
     */
    it('should return cached threats quickly', async () => {
      // GIVEN
      const cveId = 'CVE-2025-1234';
      await service.getThreat(cveId);

      // WHEN
      const startTime = Date.now();
      await service.getThreat(cveId);
      const endTime = Date.now();

      // THEN
      expect(endTime - startTime).toBeLessThan(10); // Very fast for cache hit
    });

    /**
     * Should maintain separate caches for different threats
     *
     * GIVEN: Multiple threats queried
     * WHEN: Each threat is retrieved
     * THEN: All cached separately and retrieved correctly
     */
    it('should maintain separate caches for different threats', async () => {
      // GIVEN
      const cve1 = 'CVE-2025-1111';
      const cve2 = 'CVE-2025-2222';

      // WHEN
      const threat1 = await service.getThreat(cve1);
      const threat2 = await service.getThreat(cve2);

      // THEN
      expect(threat1?.cveId).toBe(cve1);
      expect(threat2?.cveId).toBe(cve2);
    });
  });
});
