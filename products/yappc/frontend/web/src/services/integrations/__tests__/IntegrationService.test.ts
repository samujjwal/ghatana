/**
 * IntegrationService Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';
import {
  getIntegrations,
  getIntegration,
  registerIntegration,
  connectIntegration,
  disconnectIntegration,
  removeIntegration,
  checkHealth,
  resetIntegrations,
  type Integration,
} from '../IntegrationService';

describe('IntegrationService', () => {
  beforeEach(() => {
    resetIntegrations();
  });

  describe('registerIntegration', () => {
    it('should register a new integration', () => {
      const integration = registerIntegration({
        name: 'GitHub',
        category: 'vcs',
        description: 'GitHub integration',
        config: { repo: 'my-repo' },
      });

      expect(integration.id).toMatch(/^int-/);
      expect(integration.name).toBe('GitHub');
      expect(integration.status).toBe('pending');
    });

    it('should throw if name already registered', () => {
      registerIntegration({
        name: 'GitHub',
        category: 'vcs',
        description: 'GitHub',
        config: {},
      });

      expect(() =>
        registerIntegration({
          name: 'GitHub',
          category: 'vcs',
          description: 'Duplicate',
          config: {},
        }),
      ).toThrow(/already registered/);
    });

    it('should persist to storage', () => {
      registerIntegration({
        name: 'Slack',
        category: 'chat',
        description: 'Slack notifications',
        config: {},
      });

      const all = getIntegrations();
      expect(all.length).toBe(1);
      expect(all[0].name).toBe('Slack');
    });
  });

  describe('getIntegration', () => {
    it('should find by id', () => {
      const created = registerIntegration({
        name: 'Jira',
        category: 'custom',
        description: 'Jira',
        config: {},
      });

      const found = getIntegration(created.id);
      expect(found?.name).toBe('Jira');
    });

    it('should return undefined for unknown id', () => {
      expect(getIntegration('nope')).toBeUndefined();
    });
  });

  describe('connectIntegration', () => {
    it('should set status to connected', () => {
      const created = registerIntegration({
        name: 'GitHub',
        category: 'vcs',
        description: 'GitHub',
        config: {},
      });

      const connected = connectIntegration(created.id, { token: 'abc' });
      expect(connected.status).toBe('connected');
      expect(connected.connectedAt).toBeDefined();
      expect(connected.config.token).toBe('abc');
    });

    it('should throw for unknown id', () => {
      expect(() => connectIntegration('nope', {})).toThrow(/not found/);
    });
  });

  describe('disconnectIntegration', () => {
    it('should set status to disconnected', () => {
      const created = registerIntegration({
        name: 'GitHub',
        category: 'vcs',
        description: 'GitHub',
        config: {},
      });
      connectIntegration(created.id, {});

      const disconnected = disconnectIntegration(created.id);
      expect(disconnected.status).toBe('disconnected');
    });

    it('should throw for unknown id', () => {
      expect(() => disconnectIntegration('nope')).toThrow(/not found/);
    });
  });

  describe('removeIntegration', () => {
    it('should remove from storage', () => {
      const created = registerIntegration({
        name: 'GitHub',
        category: 'vcs',
        description: 'GitHub',
        config: {},
      });

      removeIntegration(created.id);
      expect(getIntegrations().length).toBe(0);
    });

    it('should be safe for unknown id', () => {
      registerIntegration({ name: 'A', category: 'vcs', description: 'A', config: {} });
      removeIntegration('nope');
      expect(getIntegrations().length).toBe(1);
    });
  });

  describe('checkHealth', () => {
    it('should return health for connected integration', () => {
      const integration: Integration = {
        id: 'int-1',
        name: 'GitHub',
        category: 'vcs',
        status: 'connected',
        description: 'GitHub',
        config: {},
      };

      const health = checkHealth(integration);
      expect(health.integrationId).toBe('int-1');
      expect(health.uptime).toBe(0.99);
      expect(health.latency).toBeGreaterThanOrEqual(0);
    });

    it('should return zero uptime for disconnected integration', () => {
      const integration: Integration = {
        id: 'int-2',
        name: 'Slack',
        category: 'chat',
        status: 'disconnected',
        description: 'Slack',
        config: {},
      };

      const health = checkHealth(integration);
      expect(health.uptime).toBe(0);
      expect(health.latency).toBe(-1);
    });

    it('should return high error rate for errored integration', () => {
      const integration: Integration = {
        id: 'int-3',
        name: 'CI',
        category: 'ci-cd',
        status: 'error',
        description: 'CI',
        config: {},
      };

      const health = checkHealth(integration);
      expect(health.errorRate).toBe(0.15);
    });
  });

  describe('resetIntegrations', () => {
    it('should clear all integrations', () => {
      registerIntegration({ name: 'A', category: 'vcs', description: 'A', config: {} });
      registerIntegration({ name: 'B', category: 'chat', description: 'B', config: {} });

      resetIntegrations();
      expect(getIntegrations().length).toBe(0);
    });
  });
});
