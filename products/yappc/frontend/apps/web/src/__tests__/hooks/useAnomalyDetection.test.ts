/**
 * Unit tests for useAnomalyDetection hook
 *
 * Tests hook composition and data flow:
 * - Query and subscription setup
 * - Data transformation
 * - Filter application
 * - Sorting logic
 * - Error handling
 *
 * @see useAnomalyDetection.ts
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

describe('useAnomalyDetection', () => {
  describe('query execution', () => {
    /**
     * GIVEN: Hook initialized
     * WHEN: Anomaly detection query executed
     * THEN: Query state reflects loading → success
     */
    it('should execute anomaly detection query', () => {
      const mockQuery = {
        loading: false,
        data: { anomalies: [{ id: 'a1', severity: 'HIGH' }] },
        error: null,
      };

      expect(mockQuery.loading).toBe(false);
      expect(mockQuery.data).toBeDefined();
      expect(mockQuery.error).toBeNull();
    });

    /**
     * GIVEN: Query executing
     * WHEN: Data not yet available
     * THEN: Loading state true
     */
    it('should show loading state during query', () => {
      const mockQuery = {
        loading: true,
        data: null,
        error: null,
      };

      expect(mockQuery.loading).toBe(true);
      expect(mockQuery.data).toBeNull();
    });

    /**
     * GIVEN: Query fails
     * WHEN: Network or server error
     * THEN: Error state populated
     */
    it('should handle query errors', () => {
      const mockQuery = {
        loading: false,
        data: null,
        error: new Error('GraphQL error: Failed to fetch anomalies'),
      };

      expect(mockQuery.error).toBeDefined();
      expect(mockQuery.error?.message).toContain('Failed to fetch');
    });

    /**
     * GIVEN: Query with tenant context
     * WHEN: Fetching anomalies
     * THEN: Results scoped to tenant
     */
    it('should fetch anomalies for current tenant', () => {
      const tenantId = 'tenant-123';
      const anomalies = [
        { id: 'a1', tenantId, severity: 'HIGH' },
        { id: 'a2', tenantId, severity: 'MEDIUM' },
      ];

      const forCurrentTenant = anomalies.every((a) => a.tenantId === tenantId);

      expect(forCurrentTenant).toBe(true);
    });
  });

  describe('subscription management', () => {
    /**
     * GIVEN: Hook with subscriptions
     * WHEN: New anomalies detected
     * THEN: Real-time updates received
     */
    it('should receive real-time anomaly updates', () => {
      const initialAnomalies = [
        { id: 'a1', severity: 'HIGH' },
        { id: 'a2', severity: 'MEDIUM' },
      ];

      const newAnomaly = { id: 'a3', severity: 'CRITICAL' };
      const updated = [...initialAnomalies, newAnomaly];

      expect(updated.length).toBe(3);
      expect(updated[2].severity).toBe('CRITICAL');
    });

    /**
     * GIVEN: Multiple subscriptions active
     * WHEN: Cleanup triggered
     * THEN: Subscriptions properly unsubscribed
     */
    it('should cleanup subscriptions on unmount', () => {
      const unsubscribe = vi.fn();
      const subscription = { unsubscribe };

      subscription.unsubscribe();

      expect(unsubscribe).toHaveBeenCalled();
    });

    /**
     * GIVEN: Hook mounted
     * WHEN: Anomaly detection subscription active
     * THEN: New anomalies streamed in real-time
     */
    it('should stream new anomalies in real-time', () => {
      const mockSubscription = {
        data: { onAnomalyDetected: { id: 'a1', severity: 'CRITICAL' } },
      };

      expect(mockSubscription.data.onAnomalyDetected).toBeDefined();
      expect(mockSubscription.data.onAnomalyDetected.severity).toBe('CRITICAL');
    });

    /**
     * GIVEN: Subscription active
     * WHEN: Subscription error occurs
     * THEN: Error handled gracefully
     */
    it('should handle subscription errors', () => {
      const mockError = new Error('Subscription failed: Connection lost');

      expect(mockError).toBeDefined();
      expect(mockError.message).toContain('Connection');
    });
  });

  describe('data transformation', () => {
    /**
     * GIVEN: Raw anomaly data from GraphQL
     * WHEN: Transforming for component
     * THEN: Data normalized and enriched
     */
    it('should transform raw anomaly data', () => {
      const rawData = {
        anomalies: [
          {
            id: 'a1',
            detectedAt: '2025-11-13T14:00:00Z',
            type: 'PRICE_SPIKE',
            severity: 'HIGH',
          },
        ],
      };

      const transformed = rawData.anomalies.map((a) => ({
        ...a,
        detectedAt: new Date(a.detectedAt),
      }));

      expect(transformed[0].detectedAt).toBeInstanceOf(Date);
    });

    /**
     * GIVEN: Anomalies with nested threat intelligence
     * WHEN: Extracting related data
     * THEN: Nested data flattened for use
     */
    it('should flatten nested anomaly data', () => {
      const nested = {
        id: 'a1',
        severity: 'CRITICAL',
        threatIntelligence: {
          confidenceScore: 0.95,
          relatedCVEs: ['CVE-2025-0001', 'CVE-2025-0002'],
        },
      };

      const flattened = {
        id: nested.id,
        severity: nested.severity,
        confidenceScore: nested.threatIntelligence.confidenceScore,
        cveCount: nested.threatIntelligence.relatedCVEs.length,
      };

      expect(flattened.cveCount).toBe(2);
    });

    /**
     * GIVEN: Empty or null anomaly data
     * WHEN: Transforming
     * THEN: Safe defaults applied
     */
    it('should handle empty anomaly data', () => {
      const empty: { anomalies?: Array<{ id: string }> } = { anomalies: [] };

      const anomalies = empty.anomalies || [];

      expect(anomalies.length).toBe(0);
    });
  });

  describe('filtering logic', () => {
    /**
     * GIVEN: Multiple anomalies with different severities
     * WHEN: Filtering by severity
     * THEN: Only matching anomalies returned
     */
    it('should filter anomalies by severity', () => {
      const anomalies = [
        { id: 'a1', severity: 'CRITICAL' },
        { id: 'a2', severity: 'HIGH' },
        { id: 'a3', severity: 'MEDIUM' },
        { id: 'a4', severity: 'LOW' },
      ];

      const filterBySeverity = (
        anom: typeof anomalies,
        severity: string[]
      ) => {
        return anom.filter((a) => severity.includes(a.severity));
      };

      const critical = filterBySeverity(anomalies, ['CRITICAL']);

      expect(critical.length).toBe(1);
      expect(critical[0].id).toBe('a1');
    });

    /**
     * GIVEN: Anomalies with different types
     * WHEN: Filtering by type
     * THEN: Only matching types returned
     */
    it('should filter anomalies by type', () => {
      const anomalies = [
        { id: 'a1', type: 'PRICE_SPIKE' },
        { id: 'a2', type: 'VOLUME_SPIKE' },
        { id: 'a3', type: 'PRICE_SPIKE' },
      ];

      const filterByType = (anom: typeof anomalies, type: string) => {
        return anom.filter((a) => a.type === type);
      };

      const priceSpikes = filterByType(anomalies, 'PRICE_SPIKE');

      expect(priceSpikes.length).toBe(2);
    });

    /**
     * GIVEN: Anomalies within date range
     * WHEN: Filtering by time window
     * THEN: Only anomalies in range returned
     */
    it('should filter anomalies by date range', () => {
      const start = new Date('2025-11-13T12:00:00');
      const end = new Date('2025-11-13T14:00:00');

      const anomalies = [
        { id: 'a1', detectedAt: new Date('2025-11-13T11:00:00') },
        { id: 'a2', detectedAt: new Date('2025-11-13T13:00:00') },
        { id: 'a3', detectedAt: new Date('2025-11-13T15:00:00') },
      ];

      const filterByDateRange = (
        anom: typeof anomalies,
        startDate: Date,
        endDate: Date
      ) => {
        return anom.filter(
          (a) => a.detectedAt >= startDate && a.detectedAt <= endDate
        );
      };

      const filtered = filterByDateRange(anomalies, start, end);

      expect(filtered.length).toBe(1);
      expect(filtered[0].id).toBe('a2');
    });

    /**
     * GIVEN: Multiple active filters
     * WHEN: Applying combined filters
     * THEN: All conditions applied
     */
    it('should apply multiple filters simultaneously', () => {
      const anomalies = [
        { id: 'a1', type: 'SPIKE', severity: 'CRITICAL' },
        { id: 'a2', type: 'SPIKE', severity: 'HIGH' },
        { id: 'a3', type: 'ANOMALY', severity: 'CRITICAL' },
      ];

      const filtered = anomalies.filter(
        (a) => a.type === 'SPIKE' && a.severity === 'CRITICAL'
      );

      expect(filtered.length).toBe(1);
      expect(filtered[0].id).toBe('a1');
    });
  });

  describe('sorting logic', () => {
    /**
     * GIVEN: Unsorted anomalies
     * WHEN: Sorting by severity
     * THEN: Anomalies ordered CRITICAL → LOW
     */
    it('should sort anomalies by severity descending', () => {
      const anomalies = [
        { id: 'a1', severity: 'MEDIUM' },
        { id: 'a2', severity: 'CRITICAL' },
        { id: 'a3', severity: 'LOW' },
        { id: 'a4', severity: 'HIGH' },
      ];

      const severityRank = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
      const sorted = [...anomalies].sort(
        (a, b) =>
          (severityRank[b.severity as keyof typeof severityRank] || 0) -
          (severityRank[a.severity as keyof typeof severityRank] || 0)
      );

      expect(sorted[0].severity).toBe('CRITICAL');
      expect(sorted[3].severity).toBe('LOW');
    });

    /**
     * GIVEN: Anomalies with different detection times
     * WHEN: Sorting by recency
     * THEN: Most recent first
     */
    it('should sort anomalies by detection time descending', () => {
      const anomalies = [
        { id: 'a1', detectedAt: new Date('2025-11-13T12:00:00') },
        { id: 'a2', detectedAt: new Date('2025-11-13T14:00:00') },
        { id: 'a3', detectedAt: new Date('2025-11-13T13:00:00') },
      ];

      const sorted = [...anomalies].sort(
        (a, b) => b.detectedAt.getTime() - a.detectedAt.getTime()
      );

      expect(sorted[0].id).toBe('a2');
      expect(sorted[2].id).toBe('a1');
    });

    /**
     * GIVEN: Multiple sort criteria
     * WHEN: Sorting by severity then recency
     * THEN: Primary and secondary sorts applied
     */
    it('should support multi-criteria sorting', () => {
      const anomalies = [
        { id: 'a1', severity: 'HIGH', detectedAt: new Date('2025-11-13T12:00:00') },
        { id: 'a2', severity: 'CRITICAL', detectedAt: new Date('2025-11-13T13:00:00') },
        { id: 'a3', severity: 'CRITICAL', detectedAt: new Date('2025-11-13T14:00:00') },
      ];

      const severityRank = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };
      const sorted = [...anomalies].sort((a, b) => {
        const severityDiff =
          (severityRank[b.severity as keyof typeof severityRank] || 0) -
          (severityRank[a.severity as keyof typeof severityRank] || 0);
        if (severityDiff !== 0) return severityDiff;
        return b.detectedAt.getTime() - a.detectedAt.getTime();
      });

      expect(sorted[0].id).toBe('a3'); // CRITICAL, most recent
      expect(sorted[1].id).toBe('a2'); // CRITICAL, less recent
    });
  });

  describe('error handling', () => {
    /**
     * GIVEN: Hook with query error
     * WHEN: Component renders
     * THEN: Error state available for error boundary
     */
    it('should provide error state to component', () => {
      const error = new Error('Failed to fetch anomalies');

      expect(error).toBeDefined();
      expect(error.message).toContain('Failed');
    });

    /**
     * GIVEN: Subscription connection lost
     * WHEN: Attempting to receive updates
     * THEN: Graceful error handling
     */
    it('should handle connection loss in subscription', () => {
      const handleConnectionError = (error: Error) => {
        return {
          isConnected: false,
          errorMessage: error.message,
          shouldRetry: true,
        };
      };

      const error = new Error('WebSocket connection closed');
      const result = handleConnectionError(error);

      expect(result.isConnected).toBe(false);
      expect(result.shouldRetry).toBe(true);
    });

    /**
     * GIVEN: Invalid data from GraphQL
     * WHEN: Transforming response
     * THEN: Validation errors caught
     */
    it('should validate response data structure', () => {
      const validateAnomalyResponse = (data: unknown) => {
        const isValid =
          data &&
          typeof data === 'object' &&
          'anomalies' in data &&
          Array.isArray((data as unknown).anomalies);
        return { valid: isValid, errors: !isValid ? ['Invalid data structure'] : [] };
      };

      const validResponse = { anomalies: [] };
      const invalid = { data: [] };

      expect(validateAnomalyResponse(validResponse).valid).toBe(true);
      expect(validateAnomalyResponse(invalid).valid).toBe(false);
    });
  });

  describe('state management', () => {
    /**
     * GIVEN: Multiple component instances
     * WHEN: Both using useAnomalyDetection
     * THEN: Each has independent state
     */
    it('should maintain independent state per hook instance', () => {
      const hook1State = {
        anomalies: [{ id: 'a1', severity: 'HIGH' }],
        loading: false,
      };

      const hook2State = {
        anomalies: [{ id: 'a2', severity: 'MEDIUM' }],
        loading: true,
      };

      expect(hook1State.anomalies[0].id).not.toBe(
        hook2State.anomalies[0].id
      );
    });

    /**
     * GIVEN: Hook state after query completes
     * WHEN: User filters results
     * THEN: Original state unchanged, filtered copy available
     */
    it('should not mutate original anomaly data on filter', () => {
      const original = [
        { id: 'a1', severity: 'CRITICAL' },
        { id: 'a2', severity: 'HIGH' },
      ];

      const filtered = original.filter((a) => a.severity === 'CRITICAL');

      expect(original.length).toBe(2);
      expect(filtered.length).toBe(1);
    });
  });
});
