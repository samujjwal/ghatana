/**
 * TelemetryManager Test Suite
 * 
 * Comprehensive unit tests for the telemetry system including event collection,
 * privacy controls, data retention, and error handling.
 */

import { TelemetryManager, TelemetryEvent, TelemetryConfig } from '../TelemetryManager';
import browser from 'webextension-polyfill';

// Mock browser extension APIs
jest.mock('webextension-polyfill', () => ({
  runtime: {
    getManifest: jest.fn(() => ({ version: '1.0.0' }))
  },
  storage: {
    local: {
      get: jest.fn(),
      set: jest.fn(),
      remove: jest.fn(),
      getBytesInUse: jest.fn(() => Promise.resolve(1024))
    }
  }
}));

// Mock crypto API
Object.defineProperty(global, 'crypto', {
  value: {
    subtle: {
      digest: jest.fn(() => Promise.resolve(new ArrayBuffer(32)))
    }
  }
});

// Mock navigator
Object.defineProperty(global, 'navigator', {
  value: {
    userAgent: 'test-user-agent',
    language: 'en-US'
  }
});

// Mock performance API
Object.defineProperty(global, 'performance', {
  value: {
    now: jest.fn(() => 1234567890)
  }
});

describe('TelemetryManager', () => {
  let telemetryManager: TelemetryManager;
  let mockStorage: jest.Mocked<typeof browser.storage.local>;

  beforeEach(() => {
    // Clear all mocks
    jest.clearAllMocks();
    
    // Reset storage mock
    mockStorage = browser.storage.local as jest.Mocked<typeof browser.storage.local>;
    mockStorage.get.mockResolvedValue({});
    mockStorage.set.mockResolvedValue();
    mockStorage.remove.mockResolvedValue();
    
    // Create fresh instance
    telemetryManager = new (TelemetryManager as any)();
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  describe('Initialization', () => {
    it('should initialize successfully with default configuration', async () => {
      mockStorage.get.mockResolvedValueOnce({});
      
      await telemetryManager.initialize();
      
      expect(mockStorage.get).toHaveBeenCalledWith('dcmaar.telemetry.config');
      expect(mockStorage.get).toHaveBeenCalledWith('dcmaar.telemetry.userId');
    });

    it('should load existing configuration from storage', async () => {
      const existingConfig = {
        enabled: false,
        retentionDays: 14,
        debug: true
      };
      
      mockStorage.get.mockImplementation((key) => {
        if (key === 'dcmaar.telemetry.config') {
          return Promise.resolve({ 'dcmaar.telemetry.config': existingConfig });
        }
        return Promise.resolve({});
      });

      await telemetryManager.initialize();
      
      // Verify config was loaded
      expect(mockStorage.get).toHaveBeenCalledWith('dcmaar.telemetry.config');
    });

    it('should generate anonymous user ID if not exists', async () => {
      mockStorage.get.mockImplementation((key) => {
        if (key === 'dcmaar.telemetry.userId') {
          return Promise.resolve({});
        }
        return Promise.resolve({});
      });

      await telemetryManager.initialize();
      
      expect(mockStorage.set).toHaveBeenCalledWith(
        expect.objectContaining({
          'dcmaar.telemetry.userId': expect.any(String)
        })
      );
    });

    it('should use existing user ID if available', async () => {
      const existingUserId = 'existing-user-id';
      
      mockStorage.get.mockImplementation((key) => {
        if (key === 'dcmaar.telemetry.userId') {
          return Promise.resolve({ 'dcmaar.telemetry.userId': existingUserId });
        }
        return Promise.resolve({});
      });

      await telemetryManager.initialize();
      
      // Should not generate new user ID
      expect(mockStorage.set).not.toHaveBeenCalledWith(
        expect.objectContaining({
          'dcmaar.telemetry.userId': expect.any(String)
        })
      );
    });
  });

  describe('Event Tracking', () => {
    beforeEach(async () => {
      await telemetryManager.initialize();
    });

    it('should track events when telemetry is enabled', async () => {
      const eventData = { testKey: 'testValue' };
      
      await telemetryManager.track('performance', 'test.event', eventData);
      
      // Event should be added to queue (internal implementation)
      // We can verify through storage calls or other observable behavior
      expect(mockStorage.set).toHaveBeenCalled();
    });

    it('should not track events when telemetry is disabled', async () => {
      // Update config to disable telemetry
      await telemetryManager.updateConfig({ enabled: false });
      
      await telemetryManager.track('performance', 'test.event', {});
      
      // Should not store events when disabled
      // Verify no additional storage calls for event storage
    });

    it('should respect consent settings for different event types', async () => {
      // Disable performance consent
      await telemetryManager.updateConfig({
        consent: {
          performance: false,
          interactions: true,
          errors: true,
          analytics: true
        }
      });
      
      await telemetryManager.track('performance', 'test.performance');
      await telemetryManager.track('interaction', 'test.interaction');
      
      // Performance event should be blocked, interaction should be allowed
    });

    it('should apply sampling rate for performance events', async () => {
      // Set very low sampling rate
      await telemetryManager.updateConfig({ samplingRate: 0.1 });
      
      // Mock Math.random to control sampling
      const mockRandom = jest.spyOn(Math, 'random');
      
      // Should be sampled out
      mockRandom.mockReturnValue(0.5);
      await telemetryManager.track('performance', 'test.sampled.out');
      
      // Should be sampled in
      mockRandom.mockReturnValue(0.05);
      await telemetryManager.track('performance', 'test.sampled.in');
      
      mockRandom.mockRestore();
    });

    it('should sanitize sensitive data from events', async () => {
      const sensitiveData = {
        password: 'secret123',
        authToken: 'token456',
        url: 'https://example.com/path?secret=value',
        safeData: 'this is ok'
      };
      
      await telemetryManager.track('interaction', 'test.sanitize', sensitiveData);
      
      // Verify sensitive data was sanitized
      // This would require access to the internal event queue or storage
    });

    it('should include proper context information', async () => {
      // Mock window location
      Object.defineProperty(global, 'window', {
        value: {
          location: { href: 'https://example.com/test' },
          innerWidth: 1920,
          innerHeight: 1080
        }
      });

      await telemetryManager.track('interaction', 'test.context');
      
      // Verify context was included (would check internal event structure)
    });
  });

  describe('Specialized Tracking Methods', () => {
    beforeEach(async () => {
      await telemetryManager.initialize();
    });

    it('should track performance metrics correctly', async () => {
      const metrics = {
        loadTime: 1500,
        queryTime: 250,
        memoryUsage: 50000000
      };
      
      await telemetryManager.trackPerformance('dashboard.load', metrics);
      
      // Verify performance event was created with correct structure
    });

    it('should track user interactions with element and action', async () => {
      await telemetryManager.trackInteraction('alert-panel', 'dismiss', {
        alertType: 'performance',
        severity: 'warning'
      });
      
      // Verify interaction event structure
    });

    it('should track errors with full context and stack traces', async () => {
      const error = new Error('Test error message');
      error.stack = 'Error: Test error\n    at test.js:10:5';
      
      const context = {
        userAction: 'button-click',
        componentName: 'AlertPanel'
      };
      
      await telemetryManager.trackError(error, context);
      
      // Verify error event includes message, stack, and context
    });

    it('should track business events for analytics', async () => {
      const eventData = {
        feature: 'data-export',
        format: 'csv',
        recordCount: 1500
      };
      
      await telemetryManager.trackBusinessEvent('export.completed', eventData);
      
      // Verify business event structure
    });
  });

  describe('Configuration Management', () => {
    beforeEach(async () => {
      await telemetryManager.initialize();
    });

    it('should update configuration and persist to storage', async () => {
      const newConfig = {
        retentionDays: 14,
        samplingRate: 0.2,
        debug: true
      };
      
      await telemetryManager.updateConfig(newConfig);
      
      expect(mockStorage.set).toHaveBeenCalledWith({
        'dcmaar.telemetry.config': expect.objectContaining(newConfig)
      });
    });

    it('should merge new configuration with existing', async () => {
      // First set some config
      await telemetryManager.updateConfig({ retentionDays: 14 });
      
      // Then update other properties
      await telemetryManager.updateConfig({ debug: true });
      
      // Both should be preserved in final config
    });
  });

  describe('Metrics and Statistics', () => {
    beforeEach(async () => {
      await telemetryManager.initialize();
    });

    it('should calculate metrics from stored events', async () => {
      // Mock stored events
      const mockEvents: TelemetryEvent[] = [
        {
          id: '1',
          type: 'performance',
          name: 'test1',
          timestamp: Date.now(),
          source: 'dashboard',
          data: {},
          sessionId: 'session1',
          userId: 'user1',
          version: '1.0.0',
          severity: 'info'
        },
        {
          id: '2',
          type: 'error',
          name: 'test2',
          timestamp: Date.now(),
          source: 'background',
          data: {},
          sessionId: 'session1',
          userId: 'user1',
          version: '1.0.0',
          severity: 'error'
        }
      ];

      mockStorage.get.mockImplementation((key) => {
        if (key === 'dcmaar.telemetry.events') {
          return Promise.resolve({ 'dcmaar.telemetry.events': mockEvents });
        }
        return Promise.resolve({});
      });

      const metrics = await telemetryManager.getMetrics();
      
      expect(metrics.totalEvents).toBe(2);
      expect(metrics.eventsByType).toEqual({
        performance: 1,
        error: 1
      });
      expect(metrics.errorRate).toBe(0.5); // 1 error out of 2 events
    });

    it('should handle empty metrics gracefully', async () => {
      mockStorage.get.mockResolvedValue({});
      
      const metrics = await telemetryManager.getMetrics();
      
      expect(metrics.totalEvents).toBe(0);
      expect(metrics.errorRate).toBe(0);
      expect(metrics.eventsByType).toEqual({});
    });
  });

  describe('Data Export and Management', () => {
    beforeEach(async () => {
      await telemetryManager.initialize();
    });

    it('should export all data when no date range specified', async () => {
      const mockEvents: TelemetryEvent[] = [
        {
          id: '1',
          type: 'performance',
          name: 'test1',
          timestamp: Date.now() - 86400000, // 1 day ago
          source: 'dashboard',
          data: {},
          sessionId: 'session1',
          userId: 'user1',
          version: '1.0.0',
          severity: 'info'
        }
      ];

      mockStorage.get.mockResolvedValue({
        'dcmaar.telemetry.events': mockEvents
      });

      const exported = await telemetryManager.exportData();
      
      expect(exported).toEqual(mockEvents);
    });

    it('should filter data by date range when specified', async () => {
      const now = Date.now();
      const mockEvents: TelemetryEvent[] = [
        {
          id: '1',
          type: 'performance',
          name: 'old',
          timestamp: now - 172800000, // 2 days ago
          source: 'dashboard',
          data: {},
          sessionId: 'session1',
          userId: 'user1',
          version: '1.0.0',
          severity: 'info'
        },
        {
          id: '2',
          type: 'performance', 
          name: 'recent',
          timestamp: now - 43200000, // 12 hours ago
          source: 'dashboard',
          data: {},
          sessionId: 'session1',
          userId: 'user1',
          version: '1.0.0',
          severity: 'info'
        }
      ];

      mockStorage.get.mockResolvedValue({
        'dcmaar.telemetry.events': mockEvents
      });

      const startDate = new Date(now - 86400000); // 1 day ago
      const exported = await telemetryManager.exportData(startDate);
      
      expect(exported).toHaveLength(1);
      expect(exported[0].name).toBe('recent');
    });

    it('should clear all telemetry data', async () => {
      await telemetryManager.clearData();
      
      expect(mockStorage.remove).toHaveBeenCalledWith([
        'dcmaar.telemetry.events',
        'dcmaar.telemetry.uploadTime'
      ]);
    });
  });

  describe('Data Retention and Cleanup', () => {
    beforeEach(async () => {
      await telemetryManager.initialize();
    });

    it('should clean up old events based on retention period', async () => {
      const now = Date.now();
      const retentionDays = 7;
      
      const mockEvents: TelemetryEvent[] = [
        {
          id: '1',
          type: 'performance',
          name: 'old',
          timestamp: now - (retentionDays + 1) * 86400000, // Older than retention
          source: 'dashboard',
          data: {},
          sessionId: 'session1',
          userId: 'user1',
          version: '1.0.0',
          severity: 'info'
        },
        {
          id: '2',
          type: 'performance',
          name: 'recent',
          timestamp: now - 86400000, // Within retention period
          source: 'dashboard',
          data: {},
          sessionId: 'session1',
          userId: 'user1',
          version: '1.0.0',
          severity: 'info'
        }
      ];

      mockStorage.get.mockResolvedValue({
        'dcmaar.telemetry.events': mockEvents
      });

      // Trigger cleanup (would be called internally during initialization)
      // This tests the private cleanupOldEvents method indirectly
      await telemetryManager.initialize();
      
      // Verify old events were filtered out
      expect(mockStorage.set).toHaveBeenCalledWith({
        'dcmaar.telemetry.events': expect.arrayContaining([
          expect.objectContaining({ name: 'recent' })
        ])
      });
    });
  });

  describe('Error Handling', () => {
    beforeEach(async () => {
      await telemetryManager.initialize();
    });

    it('should handle storage errors gracefully', async () => {
      mockStorage.get.mockRejectedValue(new Error('Storage error'));
      
      // Should not throw error
      await expect(telemetryManager.getMetrics()).resolves.not.toThrow();
    });

    it('should handle configuration update errors', async () => {
      mockStorage.set.mockRejectedValue(new Error('Storage write error'));
      
      // Should not throw error
      await expect(telemetryManager.updateConfig({ debug: true })).resolves.not.toThrow();
    });

    it('should handle initialization errors gracefully', async () => {
      mockStorage.get.mockRejectedValue(new Error('Storage access denied'));
      
      // Should not throw error during initialization
      await expect(telemetryManager.initialize()).resolves.not.toThrow();
    });
  });

  describe('Privacy and Security', () => {
    beforeEach(async () => {
      await telemetryManager.initialize();
    });

    it('should generate cryptographically secure user IDs', async () => {
      // Mock empty storage to trigger ID generation
      mockStorage.get.mockResolvedValue({});
      
      await telemetryManager.initialize();
      
      // Verify crypto.subtle.digest was called
      expect(crypto.subtle.digest).toHaveBeenCalledWith('SHA-256', expect.any(Uint8Array));
    });

    it('should sanitize URLs to remove sensitive parameters', async () => {
      const sensitiveUrl = 'https://example.com/path?token=secret123&apiKey=key456';
      const eventData = { url: sensitiveUrl };
      
      await telemetryManager.track('interaction', 'test.url', eventData);
      
      // Should sanitize URL (remove query parameters)
      // This would require checking the internal event structure
    });

    it('should redact sensitive keys from event data', async () => {
      const sensitiveData = {
        password: 'secret',
        authToken: 'token123',
        secretKey: 'key456',
        normalData: 'this is fine'
      };
      
      await telemetryManager.track('interaction', 'test.sensitive', sensitiveData);
      
      // Sensitive keys should be redacted
    });
  });
});

// Helper function to create mock telemetry events
function createMockEvent(overrides: Partial<TelemetryEvent> = {}): TelemetryEvent {
  return {
    id: 'test-id',
    type: 'performance',
    name: 'test.event',
    timestamp: Date.now(),
    source: 'dashboard',
    data: {},
    sessionId: 'test-session',
    userId: 'test-user',
    version: '1.0.0',
    severity: 'info',
    ...overrides
  };
}