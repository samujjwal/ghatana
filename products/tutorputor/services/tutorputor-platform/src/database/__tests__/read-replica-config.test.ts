/**
 * Read Replica Configuration Tests
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { 
  getReadReplicaConfigFromEnv, 
  validateReadReplicaConfig,
  maskUrl 
} from '../read-replica-config';

describe('Read Replica Configuration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('maskUrl', () => {
    it('should mask password in URL', () => {
      const url = 'postgresql://user:password@localhost:5432/db';
      const masked = maskUrl(url);
      expect(masked).toContain('*****');
      expect(masked).not.toContain('password');
    });

    it('should handle invalid URL gracefully', () => {
      const masked = maskUrl('invalid-url');
      expect(masked).toBe('invalid-url');
    });
  });

  describe('getReadReplicaConfigFromEnv', () => {
    it('should get config from environment', () => {
      process.env.DATABASE_URL = 'postgresql://user:pass@localhost:5432/db';
      process.env.DATABASE_REPLICA_URLS = 'postgresql://user:pass@localhost:5433/db,postgresql://user:pass@localhost:5434/db';
      process.env.DATABASE_READ_SPLIT_ENABLED = 'true';

      const config = getReadReplicaConfigFromEnv();

      expect(config.primaryUrl).toBe('postgresql://user:pass@localhost:5432/db');
      expect(config.replicaUrls).toHaveLength(2);
      expect(config.enableReadSplit).toBe(true);
    });

    it('should throw error when DATABASE_URL missing', () => {
      delete process.env.DATABASE_URL;

      expect(() => getReadReplicaConfigFromEnv()).toThrow('DATABASE_URL environment variable is required');
    });
  });

  describe('validateReadReplicaConfig', () => {
    it('should validate correct config', () => {
      const config = {
        primaryUrl: 'postgresql://user:pass@localhost:5432/db',
        replicaUrls: ['postgresql://user:pass@localhost:5433/db'],
        replicaCount: 1,
        enableReadSplit: true,
        connectionTimeout: 5,
        poolTimeout: 10,
        poolSize: 10,
      };

      const result = validateReadReplicaConfig(config);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should detect missing primary URL', () => {
      const config = {
        primaryUrl: '',
        replicaUrls: [],
        replicaCount: 0,
        enableReadSplit: true,
        connectionTimeout: 5,
        poolTimeout: 10,
        poolSize: 10,
      };

      const result = validateReadReplicaConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Primary database URL is required');
    });

    it('should detect no replicas', () => {
      const config = {
        primaryUrl: 'postgresql://user:pass@localhost:5432/db',
        replicaUrls: [],
        replicaCount: 0,
        enableReadSplit: true,
        connectionTimeout: 5,
        poolTimeout: 10,
        poolSize: 10,
      };

      const result = validateReadReplicaConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('At least one replica URL is required');
    });

    it('should detect invalid pool size', () => {
      const config = {
        primaryUrl: 'postgresql://user:pass@localhost:5432/db',
        replicaUrls: ['postgresql://user:pass@localhost:5433/db'],
        replicaCount: 1,
        enableReadSplit: true,
        connectionTimeout: 5,
        poolTimeout: 10,
        poolSize: 0,
      };

      const result = validateReadReplicaConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Pool size must be at least 1');
    });
  });
});
