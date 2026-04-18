/**
 * Read/Write Split Middleware Tests
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { 
  isReadQuery,
  shouldForcePrimary,
  RoundRobinSelector,
  RandomSelector,
  getReadWriteSplitOptionsFromEnv
} from '../read-write-split';

describe('Read/Write Split Middleware', () => {
  describe('isReadQuery', () => {
    it('should identify read queries', () => {
      expect(isReadQuery({ action: 'findMany' })).toBe(true);
      expect(isReadQuery({ action: 'findFirst' })).toBe(true);
      expect(isReadQuery({ action: 'findUnique' })).toBe(true);
      expect(isReadQuery({ action: 'count' })).toBe(true);
      expect(isReadQuery({ action: 'aggregate' })).toBe(true);
    });

    it('should identify write queries', () => {
      expect(isReadQuery({ action: 'create' })).toBe(false);
      expect(isReadQuery({ action: 'update' })).toBe(false);
      expect(isReadQuery({ action: 'delete' })).toBe(false);
    });
  });

  describe('shouldForcePrimary', () => {
    it('should force primary for matching patterns', () => {
      const params = { model: 'UserTable' };
      const patterns: string[] = ['User.*'];

      const result = shouldForcePrimary(params, patterns);
      expect(result).toBe(true);
    });

    it('should not force primary when no match', () => {
      const params = { model: 'Module' };
      const patterns: string[] = ['User.*'];

      const result = shouldForcePrimary(params, patterns);
      expect(result).toBe(false);
    });

    it('should not force primary when no patterns', () => {
      const params = { model: 'User' };
      const patterns: string[] = [];

      const result = shouldForcePrimary(params, patterns);
      expect(result).toBe(false);
    });
  });

  describe('RoundRobinSelector', () => {
    it('should select replicas in round-robin order', () => {
      const selector = new RoundRobinSelector();
      const replicas = ['replica_0', 'replica_1', 'replica_2'];

      expect(selector.select(replicas)).toBe('replica_0');
      expect(selector.select(replicas)).toBe('replica_1');
      expect(selector.select(replicas)).toBe('replica_2');
      expect(selector.select(replicas)).toBe('replica_0');
    });

    it('should reset index', () => {
      const selector = new RoundRobinSelector();
      const replicas = ['replica_0', 'replica_1'];

      selector.select(replicas);
      selector.select(replicas);
      selector.reset();

      expect(selector.select(replicas)).toBe('replica_0');
    });
  });

  describe('RandomSelector', () => {
    it('should select random replica', () => {
      const selector = new RandomSelector();
      const replicas = ['replica_0', 'replica_1', 'replica_2'];

      const selected = selector.select(replicas);
      expect(replicas).toContain(selected);
    });
  });

  describe('getReadWriteSplitOptionsFromEnv', () => {
    beforeEach(() => {
      vi.clearAllMocks();
    });

    it('should get options from environment', () => {
      process.env.DATABASE_REPLICA_URLS = 'postgresql://localhost:5433/db,postgresql://localhost:5434/db';
      process.env.DATABASE_READ_SPLIT_ENABLED = 'true';
      process.env.DATABASE_REPLICA_STRATEGY = 'random';
      process.env.DATABASE_FORCE_PRIMARY_PATTERNS = 'User.*,Admin.*';

      const options = getReadWriteSplitOptionsFromEnv();

      expect(options.enabled).toBe(true);
      expect(options.replicaDatasources).toEqual(['replica_0', 'replica_1']);
      expect(options.strategy).toBe('random');
      expect(options.forcePrimaryPatterns).toEqual(['User.*', 'Admin.*']);
    });

    it('should use defaults when env vars missing', () => {
      delete process.env.DATABASE_REPLICA_URLS;
      delete process.env.DATABASE_READ_SPLIT_ENABLED;

      const options = getReadWriteSplitOptionsFromEnv();

      expect(options.enabled).toBe(false);
      expect(options.replicaDatasources).toEqual([]);
      expect(options.strategy).toBe('round-robin');
    });
  });

  describe('ReadWriteSplitStatsCollector', () => {
    it('should record read query to replica', () => {
      const collector = require('../read-write-split').statsCollector;
      
      collector.recordReadQuery('replica_0', 100);

      const stats = collector.getStats();
      expect(stats.readQueries).toBe(1);
      expect(stats.replicaQueries).toBe(1);
    });

    it('should record read query to primary', () => {
      const collector = require('../read-write-split').statsCollector;
      
      collector.recordReadQuery();

      const stats = collector.getStats();
      expect(stats.readQueries).toBe(1);
      expect(stats.primaryQueries).toBe(1);
    });

    it('should record write query', () => {
      const collector = require('../read-write-split').statsCollector;
      
      collector.recordWriteQuery();

      const stats = collector.getStats();
      expect(stats.writeQueries).toBe(1);
    });

    it('should reset stats', () => {
      const collector = require('../read-write-split').statsCollector;
      
      collector.recordReadQuery('replica_0', 100);
      collector.recordWriteQuery();
      collector.reset();

      const stats = collector.getStats();
      expect(stats.readQueries).toBe(0);
      expect(stats.writeQueries).toBe(0);
    });
  });
});
