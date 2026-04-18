/**
 * Connection Pool Configuration Tests
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  getConnectionPoolConfigFromEnv,
  validateConnectionPoolConfig,
  getRecommendedPoolSize
} from '../connection-pool-config';

describe('Connection Pool Configuration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getConnectionPoolConfigFromEnv', () => {
    it('should get config from environment', () => {
      process.env.DATABASE_POOL_SIZE = '20';
      process.env.DATABASE_POOL_TIMEOUT = '15';
      process.env.DATABASE_CONNECT_TIMEOUT = '10';
      process.env.DATABASE_IDLE_TIMEOUT = '900';
      process.env.DATABASE_MAX_LIFETIME = '3600';
      process.env.DATABASE_HEALTH_CHECKS_ENABLED = 'true';
      process.env.DATABASE_HEALTH_CHECK_INTERVAL = '60';

      const config = getConnectionPoolConfigFromEnv();

      expect(config.poolSize).toBe(20);
      expect(config.poolTimeout).toBe(15);
      expect(config.connectionTimeout).toBe(10);
      expect(config.idleTimeout).toBe(900);
      expect(config.maxLifetime).toBe(3600);
      expect(config.enableHealthChecks).toBe(true);
      expect(config.healthCheckInterval).toBe(60);
    });

    it('should use defaults when env vars missing', () => {
      delete process.env.DATABASE_POOL_SIZE;
      delete process.env.DATABASE_POOL_TIMEOUT;

      const config = getConnectionPoolConfigFromEnv();

      expect(config.poolSize).toBe(10);
      expect(config.poolTimeout).toBe(10);
    });
  });

  describe('validateConnectionPoolConfig', () => {
    it('should validate correct config', () => {
      const config = {
        poolSize: 20,
        poolTimeout: 10,
        connectionTimeout: 5,
        idleTimeout: 600,
        maxLifetime: 1800,
        enableHealthChecks: true,
        healthCheckInterval: 30,
      };

      const result = validateConnectionPoolConfig(config);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should detect pool size too low', () => {
      const config = {
        poolSize: 0,
        poolTimeout: 10,
        connectionTimeout: 5,
        idleTimeout: 600,
        maxLifetime: 1800,
        enableHealthChecks: true,
        healthCheckInterval: 30,
      };

      const result = validateConnectionPoolConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Pool size must be at least 1');
    });

    it('should detect pool size too high', () => {
      const config = {
        poolSize: 150,
        poolTimeout: 10,
        connectionTimeout: 5,
        idleTimeout: 600,
        maxLifetime: 1800,
        enableHealthChecks: true,
        healthCheckInterval: 30,
      };

      const result = validateConnectionPoolConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Pool size should not exceed 100');
    });

    it('should detect idle timeout too low', () => {
      const config = {
        poolSize: 10,
        poolTimeout: 10,
        connectionTimeout: 5,
        idleTimeout: 30,
        maxLifetime: 1800,
        enableHealthChecks: true,
        healthCheckInterval: 30,
      };

      const result = validateConnectionPoolConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Idle timeout should be at least 60 seconds');
    });

    it('should detect max lifetime less than idle timeout', () => {
      const config = {
        poolSize: 10,
        poolTimeout: 10,
        connectionTimeout: 5,
        idleTimeout: 600,
        maxLifetime: 300,
        enableHealthChecks: true,
        healthCheckInterval: 30,
      };

      const result = validateConnectionPoolConfig(config);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Max lifetime must be greater than idle timeout');
    });
  });

  describe('getRecommendedPoolSize', () => {
    it('should return production pool size based on CPU', () => {
      const originalEnv = process.env.NODE_ENV;
      process.env.NODE_ENV = 'production';
      
      const size = getRecommendedPoolSize();
      
      expect(size).toBeGreaterThanOrEqual(10);
      expect(size).toBeLessThanOrEqual(50);
      
      process.env.NODE_ENV = originalEnv;
    });

    it('should return staging pool size', () => {
      const originalEnv = process.env.NODE_ENV;
      process.env.NODE_ENV = 'staging';
      
      const size = getRecommendedPoolSize();
      
      expect(size).toBeGreaterThanOrEqual(5);
      
      process.env.NODE_ENV = originalEnv;
    });

    it('should return development pool size', () => {
      const originalEnv = process.env.NODE_ENV;
      process.env.NODE_ENV = 'development';
      
      const size = getRecommendedPoolSize();
      
      expect(size).toBe(5);
      
      process.env.NODE_ENV = originalEnv;
    });
  });
});
