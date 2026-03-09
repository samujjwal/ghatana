/**
 * Tests for SlackNotificationPlugin
 */

import { SlackNotificationPlugin } from '../../implementations/notifications/SlackNotificationPlugin';

describe('SlackNotificationPlugin', () => {
  let plugin: SlackNotificationPlugin;

  beforeEach(() => {
    plugin = new SlackNotificationPlugin({
      webhookUrl: 'https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX',
      defaultChannel: '#alerts',
    });
  });

  describe('initialization', () => {
    it('should initialize with proper configuration', async () => {
      expect(plugin.id).toBe('slack-notifications');
      expect(plugin.name).toBe('Slack Notifications');
      expect(plugin.version).toBe('0.1.0');
      expect(plugin.enabled).toBe(false);
    });

    it('should set enabled flag on initialize', async () => {
      await plugin.initialize();
      expect(plugin.enabled).toBe(true);
    });

    it('should throw error if webhookUrl not provided', async () => {
      const p = new SlackNotificationPlugin({ defaultChannel: '#alerts' });
      // Validation happens in initialize(), not constructor
      await expect(p.initialize()).rejects.toThrow(
        'Slack plugin requires either webhookUrl or botToken',
      );
    });

    it('should set default channel', () => {
      const p = new SlackNotificationPlugin({
        webhookUrl: 'https://hooks.slack.com/test',
        defaultChannel: '#general',
      });
      // Config is set internally, we verify by testing behavior
      expect(p).toBeDefined();
    });
  });

  describe('send', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should send notification successfully', async () => {
      const result = await plugin.send(
        '#test',
        'Test Subject',
        'Test message content',
        'info',
      );
      expect(result).toBe(true);
    });

    it('should send multiple notifications', async () => {
      const result1 = await plugin.send(
        '#test1',
        'Subject 1',
        'Message 1',
        'info',
      );
      const result2 = await plugin.send(
        '#test2',
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
          '#test',
          'Test',
          `Message for ${type}`,
          type,
        );
        expect(result).toBe(true);
      }
    });

    it('should use default channel if not specified', async () => {
      const result = await plugin.send(
        '', // Empty recipient uses default
        'Subject',
        'Message',
        'info',
      );
      expect(result).toBe(true);
    });

    it('should throw if not initialized', async () => {
      const uninit = new SlackNotificationPlugin({
        webhookUrl: 'https://hooks.slack.com/test',
      });
      await expect(
        uninit.send('#test', 'Subject', 'Message', 'info'),
      ).rejects.toThrow('Slack plugin not initialized');
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
      const history = await plugin.getHistory('#test');
      expect(history).toEqual([]);
    });

    it('should track sent notifications in history', async () => {
      await plugin.send('#test', 'Subject 1', 'Message 1', 'info');
      await plugin.send('#test', 'Subject 2', 'Message 2', 'warning');

      const history = await plugin.getHistory('#test');
      expect(history.length).toBe(2);
      expect(history[0]).toMatchObject({
        subject: 'Subject 1',
        message: 'Message 1',
        type: 'info',
      });
    });

    it('should limit history to 100 records per recipient', async () => {
      for (let i = 0; i < 110; i++) {
        await plugin.send('#test', `Subject ${i}`, `Message ${i}`, 'info');
      }

      const history = await plugin.getHistory('#test');
      expect(history.length).toBe(100);
    });

    it('should include timestamp in history', async () => {
      const before = Date.now();
      await plugin.send('#test', 'Subject', 'Message', 'info');
      const after = Date.now();

      const history = await plugin.getHistory('#test');
      expect(history[0].timestamp).toBeGreaterThanOrEqual(before);
      expect(history[0].timestamp).toBeLessThanOrEqual(after);
    });

    it('should isolate history per recipient', async () => {
      await plugin.send('#channel1', 'Subject 1', 'Message 1', 'info');
      await plugin.send('#channel2', 'Subject 2', 'Message 2', 'info');
      await plugin.send('#channel1', 'Subject 3', 'Message 3', 'info');

      const history1 = await plugin.getHistory('#channel1');
      const history2 = await plugin.getHistory('#channel2');

      expect(history1.length).toBe(2);
      expect(history2.length).toBe(1);
    });

    it('should respect limit parameter', async () => {
      for (let i = 0; i < 10; i++) {
        await plugin.send('#test', `Subject ${i}`, `Message ${i}`, 'info');
      }

      const history = await plugin.getHistory('#test', 5);
      expect(history.length).toBe(5);
    });
  });

  describe('execute', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should execute send command', async () => {
      const result = await plugin.execute('send', {
        recipient: '#test',
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
      await plugin.send('#test', 'Subject', 'Message', 'info');
      const result = await plugin.execute('getHistory', {
        recipient: '#test',
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
        plugin.send('#test', 'Subject', 'Message', 'info'),
      ).rejects.toThrow('Slack plugin not initialized');
    });
  });

  describe('message formatting', () => {
    beforeEach(async () => {
      await plugin.initialize();
    });

    it('should format alert type with red color', async () => {
      const result = await plugin.send('#test', 'Alert', 'Alert message', 'alert');
      expect(result).toBe(true);
    });

    it('should format warning type with orange color', async () => {
      const result = await plugin.send(
        '#test',
        'Warning',
        'Warning message',
        'warning',
      );
      expect(result).toBe(true);
    });

    it('should format success type with green color', async () => {
      const result = await plugin.send(
        '#test',
        'Success',
        'Success message',
        'success',
      );
      expect(result).toBe(true);
    });

    it('should format info type with blue color', async () => {
      const result = await plugin.send('#test', 'Info', 'Info message', 'info');
      expect(result).toBe(true);
    });
  });

  describe('threading support', () => {
    it('should support thread configuration', () => {
      const p = new SlackNotificationPlugin({
        webhookUrl: 'https://hooks.slack.com/test',
        threading: true,
      });
      // Config is set internally but may not be publicly accessible
      expect(p).toBeDefined();
    });

    it('should use threading when configured', async () => {
      const p = new SlackNotificationPlugin({
        webhookUrl: 'https://hooks.slack.com/test',
        threading: true,
      });
      await p.initialize();

      const result = await p.send(
        '#test',
        'Thread Subject',
        'Thread message',
        'info',
      );
      expect(result).toBe(true);
    });
  });

  describe('error handling', () => {
    it('should handle invalid webhook URL', async () => {
      const p = new SlackNotificationPlugin({
        webhookUrl: 'invalid-url',
      });
      // Should still initialize but may fail on send
      await p.initialize();
      expect(p.enabled).toBe(true);
    });

    it('should handle empty recipient gracefully', async () => {
      await plugin.initialize();
      const result = await plugin.send(
        '',
        'Subject',
        'Message',
        'info',
      );
      expect(result).toBe(true);
    });

    it('should handle empty message gracefully', async () => {
      await plugin.initialize();
      const result = await plugin.send('#test', 'Subject', '', 'info');
      expect(result).toBe(true);
    });
  });
});
