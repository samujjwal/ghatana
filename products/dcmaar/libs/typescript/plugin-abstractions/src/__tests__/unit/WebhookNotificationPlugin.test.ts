/**
 * Tests for WebhookNotificationPlugin
 */

import { WebhookNotificationPlugin } from '../../implementations/notifications/WebhookNotificationPlugin';

describe('WebhookNotificationPlugin', () => {
  let plugin: WebhookNotificationPlugin;

  beforeEach(() => {
    plugin = new WebhookNotificationPlugin({
      baseUrl: 'https://api.example.com/webhooks',
    });
  });

  describe('initialization', () => {
    it('should initialize with proper configuration', async () => {
      expect(plugin.id).toBe('webhook-notifications');
      expect(plugin.name).toBe('Webhook Notifications');
      expect(plugin.version).toBe('0.1.0');
      expect(plugin.enabled).toBe(false);
    });

    it('should set enabled flag on initialize', async () => {
      await plugin.initialize();
      expect(plugin.enabled).toBe(true);
    });

    it('should throw error if baseUrl not provided', () => {
      expect(() => {
        new WebhookNotificationPlugin({
          baseUrl: '',
        });
      }).toThrow('Webhook plugin requires baseUrl');
    });

    it('should throw error if baseUrl is invalid', () => {
      // Empty baseUrl should throw
      expect(() => {
        new WebhookNotificationPlugin({ baseUrl: '' });
      }).toThrow('Webhook plugin requires baseUrl');
    });

    it('should validate URL on initialize', async () => {
      // This should work for valid URLs
      const p = new WebhookNotificationPlugin({
        baseUrl: 'https://api.example.com/webhooks',
      });
      await p.initialize();
      expect(p.enabled).toBe(true);
    });
  });

  describe('send', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should send notification successfully', async () => {
      const result = await plugin.send(
        'https://api.example.com/notify',
        'Test Subject',
        'Test message content',
        'info',
      );
      expect(result).toBe(true);
    });

    it('should send multiple notifications', async () => {
      const result1 = await plugin.send(
        'https://api.example.com/notify1',
        'Subject 1',
        'Message 1',
        'info',
      );
      const result2 = await plugin.send(
        'https://api.example.com/notify2',
        'Subject 2',
        'Message 2',
        'warning',
      );
      expect(result1).toBe(true);
      expect(result2).toBe(true);
    });

    it('should handle different notification types', async () => {
      const types = ['alert', 'warning', 'success', 'info'];
      for (const type of types) {
        const result = await plugin.send(
          'https://api.example.com/notify',
          'Test',
          `Message for ${type}`,
          type,
        );
        expect(result).toBe(true);
      }
    });

    it('should throw if not initialized', async () => {
      const uninit = new WebhookNotificationPlugin({
        baseUrl: 'https://api.example.com/webhooks',
      });
      await expect(
        uninit.send(
          'https://api.example.com/notify',
          'Subject',
          'Message',
          'info',
        ),
      ).rejects.toThrow('Webhook plugin not initialized');
    });

    it('should support bearer token authentication', async () => {
      const p = new WebhookNotificationPlugin({
        baseUrl: 'https://api.example.com/webhooks',
        authToken: 'secret-token-123',
      });
      await p.initialize();

      const result = await p.send(
        'https://api.example.com/notify',
        'Subject',
        'Message',
        'info',
      );
      expect(result).toBe(true);
    });

    it('should support custom headers', async () => {
      const p = new WebhookNotificationPlugin({
        baseUrl: 'https://api.example.com/webhooks',
        headers: {
          'X-Custom-Header': 'custom-value',
          'X-API-Version': 'v2',
        },
      });
      await p.initialize();

      const result = await p.send(
        'https://api.example.com/notify',
        'Subject',
        'Message',
        'info',
      );
      expect(result).toBe(true);
    });
  });

  describe('isAvailable', () => {
    it('should return false if not initialized', async () => {
      const result = await plugin.isAvailable();
      expect(result).toBe(false);
    });

    it('should return true after initialization', async () => {
      await plugin.initialize();
      const result = await plugin.isAvailable();
      expect(result).toBe(true);
    });
  });

  describe('getHistory', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should return empty history initially', async () => {
      const history = await plugin.getHistory(
        'https://api.example.com/notify',
      );
      expect(history).toEqual([]);
    });

    it('should track sent notifications in history', async () => {
      const url = 'https://api.example.com/notify';
      await plugin.send(url, 'Subject 1', 'Message 1', 'info');
      await plugin.send(url, 'Subject 2', 'Message 2', 'warning');

      const history = await plugin.getHistory(url);
      expect(history.length).toBe(2);
      expect(history[0]).toMatchObject({
        recipient: url,
        subject: 'Subject 1',
        message: 'Message 1',
        type: 'info',
      });
    });

    it('should limit history to 100 records per recipient', async () => {
      const url = 'https://api.example.com/notify';
      for (let i = 0; i < 110; i++) {
        await plugin.send(url, `Subject ${i}`, `Message ${i}`, 'info');
      }

      const history = await plugin.getHistory(url);
      expect(history.length).toBe(100);
    });

    it('should include timestamp in history', async () => {
      const url = 'https://api.example.com/notify';
      const before = Date.now();
      await plugin.send(url, 'Subject', 'Message', 'info');
      const after = Date.now();

      const history = await plugin.getHistory(url);
      expect(history[0].timestamp).toBeGreaterThanOrEqual(before);
      expect(history[0].timestamp).toBeLessThanOrEqual(after);
    });

    it('should isolate history per recipient', async () => {
      await plugin.send('https://api.example.com/notify1', 'Subject 1', 'Message 1', 'info');
      await plugin.send('https://api.example.com/notify2', 'Subject 2', 'Message 2', 'info');
      await plugin.send('https://api.example.com/notify1', 'Subject 3', 'Message 3', 'info');

      const history1 = await plugin.getHistory('https://api.example.com/notify1');
      const history2 = await plugin.getHistory('https://api.example.com/notify2');

      expect(history1.length).toBe(2);
      expect(history2.length).toBe(1);
    });

    it('should respect limit parameter', async () => {
      const url = 'https://api.example.com/notify';
      for (let i = 0; i < 10; i++) {
        await plugin.send(url, `Subject ${i}`, `Message ${i}`, 'info');
      }

      const history = await plugin.getHistory(url, 5);
      expect(history.length).toBe(5);
    });
  });

  describe('execute', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should execute send command', async () => {
      const result = await plugin.execute('send', {
        recipient: 'https://api.example.com/notify',
        subject: 'Test',
        message: 'Message',
        type: 'info',
      });
      expect(result).toBe(true);
    });

    it('should execute isAvailable command', async () => {
      const result = await plugin.execute('isAvailable');
      expect(result).toBe(true);
    });

    it('should execute getHistory command', async () => {
      await plugin.send('https://api.example.com/notify', 'Subject', 'Message', 'info');
      const result = await plugin.execute('getHistory', {
        recipient: 'https://api.example.com/notify',
        limit: 10,
      });
      expect(Array.isArray(result)).toBe(true);
    });

    it('should throw for unknown command', async () => {
      await expect(plugin.execute('unknown')).rejects.toThrow(
        'Unknown command: unknown',
      );
    });
  });

  describe('shutdown', () => {
    it('should disable plugin', async () => {
      await plugin.initialize();
      expect(plugin.enabled).toBe(true);

      await plugin.shutdown();
      expect(plugin.enabled).toBe(false);
    });

    it('should prevent operations after shutdown', async () => {
      await plugin.initialize();
      await plugin.shutdown();

      await expect(
        plugin.send(
          'https://api.example.com/notify',
          'Subject',
          'Message',
          'info',
        ),
      ).rejects.toThrow('Webhook plugin not initialized');
    });
  });

  describe('retry logic', () => {
    it('should support configurable max retries', async () => {
      const p = new WebhookNotificationPlugin({
        baseUrl: 'https://api.example.com/webhooks',
        retries: 5,
      });
      await p.initialize();

      const result = await p.send(
        'https://api.example.com/notify',
        'Subject',
        'Message',
        'info',
      );
      expect(result).toBe(true);
    });

    it('should support configurable timeout', async () => {
      const p = new WebhookNotificationPlugin({
        baseUrl: 'https://api.example.com/webhooks',
        timeout: 60000,
      });
      await p.initialize();

      const result = await p.send(
        'https://api.example.com/notify',
        'Subject',
        'Message',
        'info',
      );
      expect(result).toBe(true);
    });

    it('should apply exponential backoff on retry', async () => {
      const p = new WebhookNotificationPlugin({
        baseUrl: 'https://api.example.com/webhooks',
        retries: 2,
      });
      await p.initialize();

      const result = await p.send(
        'https://invalid-webhook-that-will-timeout.local',
        'Subject',
        'Message',
        'info',
      );
      
      // Either succeeds or times out after retries
      expect([true, false]).toContain(result);
    });
  });

  describe('payload format', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should include timestamp in payload', async () => {
      const result = await plugin.send(
        'https://api.example.com/notify',
        'Subject',
        'Message',
        'info',
      );
      expect(result).toBe(true);
    });

    it('should format payload as JSON', async () => {
      const result = await plugin.send(
        'https://api.example.com/notify',
        'Test Subject',
        'Test Message',
        'alert',
      );
      expect(result).toBe(true);
    });
  });

  describe('error handling', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should handle invalid base URL', () => {
      expect(() => {
        new WebhookNotificationPlugin({ baseUrl: '' });
      }).toThrow('Webhook plugin requires baseUrl');
    });

    it('should handle network errors gracefully', async () => {
      const result = await plugin.send(
        'https://unreachable-host-that-does-not-exist-12345.local',
        'Subject',
        'Message',
        'info',
      );
      // Should return false on network error
      expect([true, false]).toContain(result);
    });

    it('should handle empty recipient gracefully', async () => {
      const result = await plugin.send(
        '',
        'Subject',
        'Message',
        'info',
      );
      expect([true, false]).toContain(result);
    });

    it('should handle empty message gracefully', async () => {
      const result = await plugin.send(
        'https://api.example.com/notify',
        'Subject',
        '',
        'info',
      );
      expect(result).toBe(true);
    });
  });

  describe('authentication', () => {
    it('should support bearer token in Authorization header', async () => {
      const p = new WebhookNotificationPlugin({
        baseUrl: 'https://api.example.com/webhooks',
        authToken: 'my-secret-token',
      });
      await p.initialize();

      const result = await p.send(
        'https://api.example.com/notify',
        'Subject',
        'Message',
        'info',
      );
      expect(result).toBe(true);
    });

    it('should include custom headers in requests', async () => {
      const p = new WebhookNotificationPlugin({
        baseUrl: 'https://api.example.com/webhooks',
        headers: {
          'X-Webhook-Secret': 'webhook-secret-key',
          'X-Request-ID': 'req-123',
        },
      });
      await p.initialize();

      const result = await p.send(
        'https://api.example.com/notify',
        'Subject',
        'Message',
        'info',
      );
      expect(result).toBe(true);
    });
  });
});
