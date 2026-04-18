/**
 * Replica Health Checker Tests
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ReplicaHealthChecker, getReplicaHealthCheckerOptionsFromEnv } from '../replica-health-checker';

describe('Replica Health Checker', () => {
  let checker: ReplicaHealthChecker;

  beforeEach(() => {
    checker = new ReplicaHealthChecker({
      checkInterval: 30,
      maxLatency: 1000,
      maxFailures: 3,
      recoveryCheckInterval: 60,
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('addReplica', () => {
    it('should add replica to monitoring', () => {
      checker.addReplica('postgresql://localhost:5433/db');

      const statuses = checker.getAllStatuses();
      expect(statuses.replicas).toHaveLength(1);
      expect(statuses.replicas[0].url).toContain('localhost:5433');
    });
  });

  describe('addPrimary', () => {
    it('should add primary to monitoring', () => {
      checker.addPrimary('postgresql://localhost:5432/db');

      const statuses = checker.getAllStatuses();
      expect(statuses.primary).toBeDefined();
      expect(statuses.primary?.url).toContain('localhost:5432');
    });
  });

  describe('getHealthyReplicas', () => {
    it('should return only healthy replicas', () => {
      checker.addReplica('postgresql://localhost:5433/db');
      checker.addReplica('postgresql://localhost:5434/db');

      // Mark one as unhealthy
      const statuses = checker['replicas'];
      const url = Array.from(statuses.keys())[0];
      if (url) {
        statuses.set(url, { ...statuses.get(url)!, healthy: false });
      }

      const healthy = checker.getHealthyReplicas();
      expect(healthy).toHaveLength(1);
    });
  });

  describe('getOverallHealth', () => {
    it('should return healthy when primary healthy and good replica ratio', () => {
      checker.addPrimary('postgresql://localhost:5432/db');
      checker['primaryStatus'] = {
        url: 'postgresql://localhost:5432/db',
        healthy: true,
        latency: 100,
        lastChecked: new Date(),
        errorCount: 0,
      };

      checker.addReplica('postgresql://localhost:5433/db');
      checker.addReplica('postgresql://localhost:5434/db');
      checker.addReplica('postgresql://localhost:5435/db');

      // Mark 2 of 3 as healthy
      const urls = Array.from(checker['replicas'].keys());
      checker['replicas'].set(urls[0], { ...checker['replicas'].get(urls[0])!, healthy: true });
      checker['replicas'].set(urls[1], { ...checker['replicas'].get(urls[1])!, healthy: true });
      checker['replicas'].set(urls[2], { ...checker['replicas'].get(urls[2])!, healthy: false });

      const health = checker.getOverallHealth();
      expect(health).toBe('healthy');
    });

    it('should return unhealthy when primary unhealthy', () => {
      checker.addPrimary('postgresql://localhost:5432/db');
      checker['primaryStatus'] = {
        url: 'postgresql://localhost:5432/db',
        healthy: false,
        latency: 0,
        lastChecked: new Date(),
        errorCount: 5,
      };

      const health = checker.getOverallHealth();
      expect(health).toBe('unhealthy');
    });

    it('should return degraded when some replicas unhealthy', () => {
      checker.addPrimary('postgresql://localhost:5432/db');
      checker['primaryStatus'] = {
        url: 'postgresql://localhost:5432/db',
        healthy: true,
        latency: 100,
        lastChecked: new Date(),
        errorCount: 0,
      };

      checker.addReplica('postgresql://localhost:5433/db');
      checker.addReplica('postgresql://localhost:5434/db');
      checker.addReplica('postgresql://localhost:5435/db');

      // Mark 1 of 3 as healthy
      const urls = Array.from(checker['replicas'].keys());
      checker['replicas'].set(urls[0], { ...checker['replicas'].get(urls[0])!, healthy: true });
      checker['replicas'].set(urls[1], { ...checker['replicas'].get(urls[1])!, healthy: false });
      checker['replicas'].set(urls[2], { ...checker['replicas'].get(urls[2])!, healthy: false });

      const health = checker.getOverallHealth();
      expect(health).toBe('degraded');
    });
  });

  describe('maskUrl', () => {
    it('should mask password in URL', () => {
      const url = 'postgresql://user:password@localhost:5432/db';
      const masked = checker['maskUrl'](url);
      expect(masked).toContain('*****');
      expect(masked).not.toContain('password');
    });
  });
});

describe('getReplicaHealthCheckerOptionsFromEnv', () => {
  it('should get options from environment', () => {
    process.env.REPLICA_HEALTH_CHECK_INTERVAL = '60';
    process.env.REPLICA_MAX_LATENCY = '2000';
    process.env.REPLICA_MAX_FAILURES = '5';
    process.env.REPLICA_RECOVERY_CHECK_INTERVAL = '120';

    const options = getReplicaHealthCheckerOptionsFromEnv();

    expect(options.checkInterval).toBe(60);
    expect(options.maxLatency).toBe(2000);
    expect(options.maxFailures).toBe(5);
    expect(options.recoveryCheckInterval).toBe(120);
  });

  it('should use defaults when env vars missing', () => {
    delete process.env.REPLICA_HEALTH_CHECK_INTERVAL;
    delete process.env.REPLICA_MAX_LATENCY;

    const options = getReplicaHealthCheckerOptionsFromEnv();

    expect(options.checkInterval).toBe(30);
    expect(options.maxLatency).toBe(1000);
    expect(options.maxFailures).toBe(3);
    expect(options.recoveryCheckInterval).toBe(60);
  });
});
