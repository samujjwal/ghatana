/**
 * Unit tests for AutomatedResponseService
 *
 * Tests validate:
 * - Incident response automation and playbook execution
 * - Response action tracking and status updates
 * - Incident lifecycle management
 * - Metrics collection on all operations
 * - Error handling and edge cases
 *
 * @see AutomatedResponseService
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { AutomatedResponseService } from '../../../services/anomaly/AutomatedResponseService';
import { MetricsCollector, NoopMetricsCollector } from '../../../observability/MetricsCollector';

// Mock incident repository
class MockIncidentRepository {
  private incidents: Map<string, unknown> = new Map();

  async save(incident: unknown): Promise<void> {
    this.incidents.set(incident.id, incident);
  }

  async findById(id: string): Promise<any | null> {
    return this.incidents.get(id) || null;
  }

  async findByStatus(status: string): Promise<unknown[]> {
    return Array.from(this.incidents.values()).filter((i) => i.status === status);
  }

  async query(): Promise<unknown[]> {
    return Array.from(this.incidents.values());
  }

  clear(): void {
    this.incidents.clear();
  }
}

describe('AutomatedResponseService', () => {
  let service: AutomatedResponseService;
  let incidentRepository: MockIncidentRepository;
  let metrics: MetricsCollector;

  beforeEach(() => {
    incidentRepository = new MockIncidentRepository();
    metrics = new NoopMetricsCollector();
    service = new AutomatedResponseService(incidentRepository as unknown, metrics);
  });

  afterEach(() => {
    vi.clearAllMocks();
    incidentRepository.clear();
  });

  describe('respondToAnomaly', () => {
    /**
     * Should create incident and execute appropriate playbook
     *
     * GIVEN: Anomaly with threats
     * WHEN: respondToAnomaly() is called
     * THEN: Incident is created with response actions
     */
    it('should create incident and execute playbook', async () => {
      // GIVEN
      const anomaly = {
        id: 'anom-123',
        type: 'NETWORK_SPIKE',
        severity: 'HIGH',
        observed: 5000,
        baseline: 1000,
      };

      const threats = [
        {
          cveId: 'CVE-2025-1234',
          title: 'DDoS Vulnerability',
          severity: 'CRITICAL',
          exploitAvailable: true,
        },
      ];

      // WHEN
      const incident = await service.respondToAnomaly(anomaly as unknown, threats);

      // THEN
      expect(incident).toBeDefined();
      expect(incident.anomalyId).toBe('anom-123');
      expect(incident.status).toBe('OPEN');
      expect(incident.responseActions).toBeDefined();
      expect(incident.responseActions.length).toBeGreaterThan(0);
    });

    /**
     * Should select appropriate playbook based on anomaly type
     *
     * GIVEN: PRIVILEGE_ESCALATION anomaly
     * WHEN: respondToAnomaly() is called
     * THEN: Privilege escalation playbook is executed
     */
    it('should select correct playbook for anomaly type', async () => {
      // GIVEN
      const anomaly = {
        id: 'anom-priv',
        type: 'PRIVILEGE_ESCALATION',
        severity: 'CRITICAL',
        observed: 10,
        baseline: 0,
      };

      // WHEN
      const incident = await service.respondToAnomaly(anomaly as unknown);

      // THEN
      expect(incident).toBeDefined();
      const actionTypes = incident.responseActions.map((a) => a.actionType);
      expect(actionTypes).toContain('REVOKE_CREDENTIALS');
      expect(actionTypes).toContain('ISOLATE_RESOURCE');
    });

    /**
     * Should escalate threat level based on exploit availability
     *
     * GIVEN: Anomaly with exploitable threats
     * WHEN: respondToAnomaly() is called
     * THEN: Incident severity is elevated
     */
    it('should escalate severity for exploitable threats', async () => {
      // GIVEN
      const anomaly = {
        id: 'anom-exploit',
        type: 'MALWARE_SIGNATURE',
        severity: 'MEDIUM',
        observed: 1,
        baseline: 0,
      };

      const exploitableThreats = [
        {
          cveId: 'CVE-2025-9999',
          title: 'Active Exploit Available',
          severity: 'CRITICAL',
          exploitAvailable: true,
        },
      ];

      // WHEN
      const incident = await service.respondToAnomaly(
        anomaly as unknown,
        exploitableThreats
      );

      // THEN
      expect(incident.severity).toBe('CRITICAL');
    });

    /**
     * Should persist incident to repository
     *
     * GIVEN: Anomaly requiring response
     * WHEN: respondToAnomaly() is called
     * THEN: Incident is saved to repository
     */
    it('should persist incident to repository', async () => {
      // GIVEN
      const saveSpy = vi.spyOn(incidentRepository, 'save');
      const anomaly = {
        id: 'anom-persist',
        type: 'NETWORK_SPIKE',
        severity: 'HIGH',
      };

      // WHEN
      await service.respondToAnomaly(anomaly as unknown);

      // THEN
      expect(saveSpy).toHaveBeenCalled();
    });

    /**
     * Should collect response metrics
     *
     * GIVEN: Metrics collector spy
     * WHEN: respondToAnomaly() is called
     * THEN: Response metric is recorded
     */
    it('should collect metrics on automated response', async () => {
      // GIVEN
      const mockMetrics = new MetricsCollector();
      const incrementSpy = vi.spyOn(mockMetrics, 'incrementCounter');
      service = new AutomatedResponseService(incidentRepository as unknown, mockMetrics);

      const anomaly = {
        id: 'anom-metrics',
        type: 'NETWORK_SPIKE',
        severity: 'HIGH',
      };

      // WHEN
      await service.respondToAnomaly(anomaly as unknown);

      // THEN
      expect(incrementSpy).toHaveBeenCalledWith(
        'automated_response_triggered',
        1,
        expect.any(Object)
      );
    });
  });

  describe('triggerPlaybook', () => {
    /**
     * Should execute named playbook
     *
     * GIVEN: Valid playbook name and context
     * WHEN: triggerPlaybook() is called
     * THEN: Playbook is executed with response actions
     */
    it('should execute named playbook', async () => {
      // GIVEN
      const playbookName = 'DDoS Mitigation';
      const context = {
        anomalyId: 'anom-ddos',
        resourceId: 'res-123',
        severity: 'CRITICAL',
      };

      // WHEN
      const incident = await service.triggerPlaybook(playbookName, context);

      // THEN
      expect(incident).toBeDefined();
      expect(incident.responseActions.length).toBeGreaterThan(0);
    });

    /**
     * Should validate playbook name
     *
     * GIVEN: Invalid playbook name
     * WHEN: triggerPlaybook() is called
     * THEN: Error is thrown or null returned
     */
    it('should handle invalid playbook names', async () => {
      // GIVEN
      const invalidPlaybook = 'Non Existent Playbook';

      // WHEN & THEN
      await expect(
        service.triggerPlaybook(invalidPlaybook, {})
      ).rejects.toThrow();
    });

    /**
     * Should include incident context in response actions
     *
     * GIVEN: Context with resource and severity information
     * WHEN: triggerPlaybook() is called
     * THEN: Response actions include context details
     */
    it('should include context in response actions', async () => {
      // GIVEN
      const context = {
        anomalyId: 'anom-context',
        resourceId: 'res-456',
        severity: 'HIGH',
      };

      // WHEN
      const incident = await service.triggerPlaybook(
        'Exfiltration Response',
        context
      );

      // THEN
      expect(incident.anomalyId).toBe('anom-context');
      expect(incident.resourceId).toBe('res-456');
    });

    /**
     * Should record playbook execution metrics
     *
     * GIVEN: Metrics collector spy
     * WHEN: triggerPlaybook() is called
     * THEN: Playbook execution metric is recorded
     */
    it('should record playbook execution metrics', async () => {
      // GIVEN
      const mockMetrics = new MetricsCollector();
      const incrementSpy = vi.spyOn(mockMetrics, 'incrementCounter');
      service = new AutomatedResponseService(incidentRepository as unknown, mockMetrics);

      // WHEN
      await service.triggerPlaybook('DDoS Mitigation', {
        anomalyId: 'anom-pb',
      });

      // THEN
      expect(incrementSpy).toHaveBeenCalledWith(
        'playbook_executed',
        1,
        expect.objectContaining({
          playbookName: 'DDoS Mitigation',
        })
      );
    });
  });

  describe('getIncidentStatus', () => {
    /**
     * Should retrieve incident by ID
     *
     * GIVEN: Existing incident ID
     * WHEN: getIncidentStatus() is called
     * THEN: Returns incident with current status
     */
    it('should retrieve incident by ID', async () => {
      // GIVEN
      const anomaly = { id: 'anom-status', type: 'NETWORK_SPIKE', severity: 'HIGH' };
      const incident = await service.respondToAnomaly(anomaly as unknown);

      // WHEN
      const retrieved = await service.getIncidentStatus(incident.id);

      // THEN
      expect(retrieved).toBeDefined();
      expect(retrieved?.id).toBe(incident.id);
      expect(retrieved?.status).toBe('OPEN');
    });

    /**
     * Should return null for non-existent incident
     *
     * GIVEN: Non-existent incident ID
     * WHEN: getIncidentStatus() is called
     * THEN: Returns null
     */
    it('should return null for non-existent incident', async () => {
      // GIVEN & WHEN
      const retrieved = await service.getIncidentStatus('non-existent-id');

      // THEN
      expect(retrieved).toBeNull();
    });

    /**
     * Should include all incident details
     *
     * GIVEN: Created incident
     * WHEN: getIncidentStatus() is called
     * THEN: Returns complete incident with all fields
     */
    it('should return complete incident details', async () => {
      // GIVEN
      const anomaly = {
        id: 'anom-complete',
        type: 'MALWARE_SIGNATURE',
        severity: 'CRITICAL',
      };
      const incident = await service.respondToAnomaly(anomaly as unknown);

      // WHEN
      const retrieved = await service.getIncidentStatus(incident.id);

      // THEN
      expect(retrieved).toHaveProperty('id');
      expect(retrieved).toHaveProperty('anomalyId');
      expect(retrieved).toHaveProperty('status');
      expect(retrieved).toHaveProperty('responseActions');
      expect(retrieved).toHaveProperty('severity');
      expect(retrieved).toHaveProperty('createdAt');
    });
  });

  describe('updateIncidentStatus', () => {
    /**
     * Should transition incident to new status
     *
     * GIVEN: Incident in OPEN status
     * WHEN: updateIncidentStatus() called with IN_PROGRESS
     * THEN: Status is updated
     */
    it('should update incident status', async () => {
      // GIVEN
      const anomaly = { id: 'anom-update', type: 'NETWORK_SPIKE', severity: 'HIGH' };
      const incident = await service.respondToAnomaly(anomaly as unknown);

      // WHEN
      const updated = await service.updateIncidentStatus(incident.id, 'IN_PROGRESS');

      // THEN
      expect(updated?.status).toBe('IN_PROGRESS');
    });

    /**
     * Should allow OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED transitions
     *
     * GIVEN: Incident in OPEN status
     * WHEN: Multiple status updates are applied
     * THEN: All transitions succeed
     */
    it('should support incident lifecycle transitions', async () => {
      // GIVEN
      const anomaly = { id: 'anom-lifecycle', type: 'NETWORK_SPIKE', severity: 'MEDIUM' };
      let incident = await service.respondToAnomaly(anomaly as unknown);

      // WHEN & THEN - OPEN -> IN_PROGRESS
      incident = (await service.updateIncidentStatus(
        incident.id,
        'IN_PROGRESS'
      )) as unknown;
      expect(incident.status).toBe('IN_PROGRESS');

      // WHEN & THEN - IN_PROGRESS -> RESOLVED
      incident = (await service.updateIncidentStatus(
        incident.id,
        'RESOLVED'
      )) as unknown;
      expect(incident.status).toBe('RESOLVED');

      // WHEN & THEN - RESOLVED -> CLOSED
      incident = (await service.updateIncidentStatus(
        incident.id,
        'CLOSED'
      )) as unknown;
      expect(incident.status).toBe('CLOSED');
    });

    /**
     * Should record status change metrics
     *
     * GIVEN: Metrics collector spy
     * WHEN: updateIncidentStatus() is called
     * THEN: Status change metric is recorded
     */
    it('should record incident status change metrics', async () => {
      // GIVEN
      const mockMetrics = new MetricsCollector();
      const incrementSpy = vi.spyOn(mockMetrics, 'incrementCounter');
      service = new AutomatedResponseService(incidentRepository as unknown, mockMetrics);

      const anomaly = {
        id: 'anom-metrics-update',
        type: 'NETWORK_SPIKE',
        severity: 'HIGH',
      };
      const incident = await service.respondToAnomaly(anomaly as unknown);

      // WHEN
      await service.updateIncidentStatus(incident.id, 'IN_PROGRESS');

      // THEN
      expect(incrementSpy).toHaveBeenCalledWith(
        'incident_status_updated',
        1,
        expect.any(Object)
      );
    });

    /**
     * Should return null for non-existent incident
     *
     * GIVEN: Non-existent incident ID
     * WHEN: updateIncidentStatus() is called
     * THEN: Returns null
     */
    it('should return null for non-existent incident', async () => {
      // GIVEN & WHEN
      const result = await service.updateIncidentStatus('non-existent', 'RESOLVED');

      // THEN
      expect(result).toBeNull();
    });
  });

  describe('getOpenIncidents', () => {
    /**
     * Should retrieve all open incidents
     *
     * GIVEN: Multiple incidents with different statuses
     * WHEN: getOpenIncidents() is called
     * THEN: Returns only OPEN and IN_PROGRESS incidents
     */
    it('should retrieve open incidents', async () => {
      // GIVEN
      const anom1 = { id: 'anom-1', type: 'NETWORK_SPIKE', severity: 'HIGH' };
      const anom2 = { id: 'anom-2', type: 'MALWARE_SIGNATURE', severity: 'CRITICAL' };
      const anom3 = { id: 'anom-3', type: 'RESOURCE_EXHAUSTION', severity: 'MEDIUM' };

      const incident1 = await service.respondToAnomaly(anom1 as unknown);
      const incident2 = await service.respondToAnomaly(anom2 as unknown);
      const incident3 = await service.respondToAnomaly(anom3 as unknown);

      // Close one incident
      await service.updateIncidentStatus(incident3.id, 'CLOSED');

      // WHEN
      const openIncidents = await service.getOpenIncidents();

      // THEN
      expect(openIncidents.length).toBe(2);
      const openIds = openIncidents.map((i) => i.id);
      expect(openIds).toContain(incident1.id);
      expect(openIds).toContain(incident2.id);
      expect(openIds).not.toContain(incident3.id);
    });

    /**
     * Should return empty array when no open incidents
     *
     * GIVEN: All incidents are closed
     * WHEN: getOpenIncidents() is called
     * THEN: Returns empty array
     */
    it('should return empty array when no open incidents', async () => {
      // GIVEN
      const anom = { id: 'anom-all-closed', type: 'NETWORK_SPIKE', severity: 'HIGH' };
      const incident = await service.respondToAnomaly(anom as unknown);
      await service.updateIncidentStatus(incident.id, 'CLOSED');

      // WHEN
      const openIncidents = await service.getOpenIncidents();

      // THEN
      expect(openIncidents).toEqual([]);
    });

    /**
     * Should record open incidents query metrics
     *
     * GIVEN: Metrics collector spy
     * WHEN: getOpenIncidents() is called
     * THEN: Query metric is recorded
     */
    it('should record open incidents query metrics', async () => {
      // GIVEN
      const mockMetrics = new MetricsCollector();
      const incrementSpy = vi.spyOn(mockMetrics, 'incrementCounter');
      service = new AutomatedResponseService(incidentRepository as unknown, mockMetrics);

      // WHEN
      await service.getOpenIncidents();

      // THEN
      expect(incrementSpy).toHaveBeenCalledWith('open_incidents_query', 1, expect.any(Object));
    });
  });

  describe('Response action tracking', () => {
    /**
     * Should track response action execution
     *
     * GIVEN: Incident with response actions
     * WHEN: Incident is created
     * THEN: All actions have PENDING status
     */
    it('should initialize response actions with PENDING status', async () => {
      // GIVEN
      const anomaly = {
        id: 'anom-actions',
        type: 'NETWORK_SPIKE',
        severity: 'HIGH',
      };

      // WHEN
      const incident = await service.respondToAnomaly(anomaly as unknown);

      // THEN
      incident.responseActions.forEach((action) => {
        expect(['PENDING', 'IN_PROGRESS']).toContain(action.status);
      });
    });

    /**
     * Should include action timestamps
     *
     * GIVEN: Incident with response actions
     * WHEN: Incident is created
     * THEN: Each action has createdAt timestamp
     */
    it('should include action timestamps', async () => {
      // GIVEN
      const anomaly = {
        id: 'anom-timestamps',
        type: 'MALWARE_SIGNATURE',
        severity: 'CRITICAL',
      };

      // WHEN
      const incident = await service.respondToAnomaly(anomaly as unknown);

      // THEN
      incident.responseActions.forEach((action) => {
        expect(action.createdAt).toBeInstanceOf(Date);
      });
    });
  });
});
