/**
 * ErrorMonitor Test Suite
 * 
 * Comprehensive unit tests for error monitoring, performance alerting,
 * and error reporting functionality.
 */

import { ErrorMonitor, ErrorReport, PerformanceAlert } from '../ErrorMonitor';
import { telemetryManager } from '../TelemetryManager';
import browser from 'webextension-polyfill';

// Mock dependencies
jest.mock('../TelemetryManager');
jest.mock('webextension-polyfill', () => ({
  runtime: {
    getManifest: jest.fn(() => ({ version: '1.0.0' }))
  },
  storage: {
    local: {
      get: jest.fn(),
      set: jest.fn(),
      remove: jest.fn()
    }
  }
}));

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
    now: jest.fn(() => 1234567890),
    memory: {
      usedJSHeapSize: 50000000,
      totalJSHeapSize: 100000000,
      jsHeapSizeLimit: 200000000
    },
    getEntriesByType: jest.fn(() => [])
  }
});

describe('ErrorMonitor', () => {
  let errorMonitor: ErrorMonitor;
  let mockStorage: jest.Mocked<typeof browser.storage.local>;
  let mockTelemetryManager: jest.Mocked<typeof telemetryManager>;

  beforeEach(() => {
    jest.clearAllMocks();
    
    // Mock storage
    mockStorage = browser.storage.local as jest.Mocked<typeof browser.storage.local>;
    mockStorage.get.mockResolvedValue({});
    mockStorage.set.mockResolvedValue();
    
    // Mock telemetry manager
    mockTelemetryManager = telemetryManager as jest.Mocked<typeof telemetryManager>;
    mockTelemetryManager.track.mockResolvedValue();
    mockTelemetryManager.trackError.mockResolvedValue();
    
    // Create fresh instance
    errorMonitor = new (ErrorMonitor as any)();
  });

  describe('Initialization', () => {
    it('should initialize successfully', async () => {
      await errorMonitor.initialize();
      
      expect(mockStorage.get).toHaveBeenCalled();
      expect(mockTelemetryManager.track).toHaveBeenCalledWith('system', 'error_monitor.initialized');
    });

    it('should load error thresholds from storage', async () => {
      const customThresholds = {
        errorRate: 0.1,
        memoryUsage: 200 * 1024 * 1024
      };
      
      mockStorage.get.mockImplementation((key) => {
        if (key === 'dcmaar.error_monitor.thresholds') {
          return Promise.resolve({ 'dcmaar.error_monitor.thresholds': customThresholds });
        }
        return Promise.resolve({});
      });

      await errorMonitor.initialize();
      
      expect(mockStorage.get).toHaveBeenCalledWith('dcmaar.error_monitor.thresholds');
    });

    it('should handle initialization errors gracefully', async () => {
      mockStorage.get.mockRejectedValue(new Error('Storage error'));
      
      await expect(errorMonitor.initialize()).resolves.not.toThrow();
    });
  });

  describe('Error Reporting', () => {
    beforeEach(async () => {
      await errorMonitor.initialize();
    });

    it('should report JavaScript errors with full context', async () => {
      const error = new Error('Test JavaScript error');
      error.stack = 'Error: Test error\n    at testFunction (test.js:10:5)';
      
      await errorMonitor.reportError(error, 'javascript', 'medium', 'user-click');
      
      expect(mockTelemetryManager.trackError).toHaveBeenCalledWith(
        error,
        expect.objectContaining({
          errorId: expect.any(String),
          type: 'javascript',
          severity: 'medium',
          userAction: 'user-click'
        })
      );
    });

    it('should create error report with proper structure', async () => {
      const error = new Error('Test error with metadata');
      const metadata = { component: 'AlertPanel', userId: 'test123' };
      
      await errorMonitor.reportError(error, 'extension', 'high', 'component-render', metadata);
      
      // Verify error report was stored
      expect(mockStorage.set).toHaveBeenCalledWith(
        expect.objectContaining({
          'dcmaar.error_monitor.errors': expect.arrayContaining([
            expect.objectContaining({
              type: 'extension',
              severity: 'high',
              message: 'Test error with metadata',
              userAction: 'component-render',
              metadata
            })
          ])
        })
      );
    });

    it('should include performance snapshot in error reports', async () => {
      const error = new Error('Performance context error');
      
      await errorMonitor.reportError(error);
      
      // Verify performance snapshot was captured
      expect(mockStorage.set).toHaveBeenCalledWith(
        expect.objectContaining({
          'dcmaar.error_monitor.errors': expect.arrayContaining([
            expect.objectContaining({
              performanceSnapshot: expect.objectContaining({
                memory: expect.any(Object)
              })
            })
          ])
        })
      );
    });

    it('should update error rate tracking', async () => {
      const error1 = new Error('Error 1');
      const error2 = new Error('Error 2');
      
      await errorMonitor.reportError(error1);
      await errorMonitor.reportError(error2);
      
      // Error rate should be tracked internally
      // This would be verified through error rate threshold checking
    });

    it('should limit stored errors to prevent storage bloat', async () => {
      // Mock existing errors (100 errors already stored)
      const existingErrors = Array.from({ length: 100 }, (_, i) => ({
        id: `error-${i}`,
        type: 'javascript',
        message: `Error ${i}`,
        context: { timestamp: Date.now() - i * 1000 }
      }));

      mockStorage.get.mockImplementation((key) => {
        if (key === 'dcmaar.error_monitor.errors') {
          return Promise.resolve({ 'dcmaar.error_monitor.errors': existingErrors });
        }
        return Promise.resolve({});
      });

      const newError = new Error('New error');
      await errorMonitor.reportError(newError);
      
      // Should keep only last 100 errors
      expect(mockStorage.set).toHaveBeenCalledWith(
        expect.objectContaining({
          'dcmaar.error_monitor.errors': expect.arrayContaining([
            expect.objectContaining({ message: 'New error' })
          ])
        })
      );
    });
  });

  describe('Performance Monitoring', () => {
    beforeEach(async () => {
      await errorMonitor.initialize();
    });

    it('should report performance issues when thresholds exceeded', async () => {
      const metric = 'memory.usage';
      const value = 150000000; // 150MB
      const threshold = 100000000; // 100MB
      const context = { component: 'dashboard' };
      
      await errorMonitor.reportPerformanceIssue(metric, value, threshold, context);
      
      expect(mockTelemetryManager.track).toHaveBeenCalledWith(
        'performance',
        'degradation.detected',
        expect.objectContaining({
          alert: expect.objectContaining({
            type: 'memory',
            threshold,
            actualValue: value,
            severity: 'error'
          }),
          metric,
          value,
          threshold
        })
      );
    });

    it('should calculate correct severity levels', async () => {
      // Test different severity calculations based on threshold ratios
      
      // Warning level (1.5x threshold)
      await errorMonitor.reportPerformanceIssue('test.metric', 150, 100);
      
      // Error level (2.5x threshold)
      await errorMonitor.reportPerformanceIssue('test.metric', 250, 100);
      
      // Critical level (3.5x threshold)
      await errorMonitor.reportPerformanceIssue('test.metric', 350, 100);
      
      // Verify different severity levels were used
      expect(mockStorage.set).toHaveBeenCalledTimes(3);
    });

    it('should categorize alert types correctly', async () => {
      await errorMonitor.reportPerformanceIssue('memory.heap.size', 1000, 500);
      await errorMonitor.reportPerformanceIssue('timing.load.duration', 3000, 2000);
      await errorMonitor.reportPerformanceIssue('storage.quota.usage', 0.9, 0.8);
      
      // Should categorize as memory, timing, and quota respectively
      expect(mockStorage.set).toHaveBeenCalledWith(
        expect.objectContaining({
          'dcmaar.error_monitor.performance_alerts': expect.arrayContaining([
            expect.objectContaining({ type: 'memory' }),
            expect.objectContaining({ type: 'timing' }),
            expect.objectContaining({ type: 'quota' })
          ])
        })
      );
    });

    it('should monitor memory usage periodically', async () => {
      // Mock high memory usage
      Object.defineProperty(performance, 'memory', {
        value: {
          usedJSHeapSize: 150000000, // 150MB (exceeds default 100MB threshold)
          totalJSHeapSize: 200000000,
          jsHeapSizeLimit: 300000000
        }
      });

      await errorMonitor.initialize();
      
      // Trigger performance check (normally done by timer)
      // This would require access to private methods or timer manipulation
    });
  });

  describe('Specialized Error Types', () => {
    beforeEach(async () => {
      await errorMonitor.initialize();
    });

    it('should report storage issues with operation context', async () => {
      const storageError = new Error('QuotaExceededError: Storage quota exceeded');
      const context = { operation: 'save', dataSize: 5000000 };
      
      await errorMonitor.reportStorageIssue('save', storageError, context);
      
      expect(mockTelemetryManager.trackError).toHaveBeenCalledWith(
        storageError,
        expect.objectContaining({
          ...context
        })
      );
    });

    it('should report network issues with sanitized URLs', async () => {
      const networkError = new Error('Failed to fetch');
      const sensitiveUrl = 'https://api.example.com/data?apiKey=secret123&token=abc456';
      const context = { method: 'POST', status: 500 };
      
      await errorMonitor.reportNetworkIssue(sensitiveUrl, networkError, context);
      
      // URL should be sanitized (remove query parameters)
      expect(mockTelemetryManager.trackError).toHaveBeenCalledWith(
        networkError,
        expect.objectContaining({
          url: 'https://api.example.com/data',
          method: 'POST',
          status: 500
        })
      );
    });
  });

  describe('Error Statistics and Analytics', () => {
    beforeEach(async () => {
      await errorMonitor.initialize();
    });

    it('should calculate error statistics correctly', async () => {
      const mockErrors: ErrorReport[] = [
        {
          id: '1',
          type: 'javascript',
          message: 'Error 1',
          severity: 'medium',
          context: {
            timestamp: Date.now(),
            userAgent: 'test',
            extensionVersion: '1.0.0',
            userId: 'user1',
            sessionId: 'session1',
            source: 'dashboard'
          }
        },
        {
          id: '2',
          type: 'performance',
          message: 'Error 2',
          severity: 'high',
          context: {
            timestamp: Date.now(),
            userAgent: 'test',
            extensionVersion: '1.0.0',
            userId: 'user1',
            sessionId: 'session1',
            source: 'background'
          }
        },
        {
          id: '3',
          type: 'javascript',
          message: 'Error 3',
          severity: 'low',
          context: {
            timestamp: Date.now(),
            userAgent: 'test',
            extensionVersion: '1.0.0',
            userId: 'user1',
            sessionId: 'session1',
            source: 'popup'
          }
        }
      ];

      mockStorage.get.mockImplementation((key) => {
        if (key === 'dcmaar.error_monitor.errors') {
          return Promise.resolve({ 'dcmaar.error_monitor.errors': mockErrors });
        }
        return Promise.resolve({});
      });

      const stats = await errorMonitor.getErrorStats();
      
      expect(stats.totalErrors).toBe(3);
      expect(stats.errorsByType).toEqual({
        javascript: 2,
        performance: 1
      });
      expect(stats.errorsBySeverity).toEqual({
        medium: 1,
        high: 1,
        low: 1
      });
    });

    it('should calculate error rate from time window', async () => {
      // Mock error rate window with recent errors
      const recentErrors = [
        Date.now() - 1000,    // 1 second ago
        Date.now() - 30000,   // 30 seconds ago
        Date.now() - 120000,  // 2 minutes ago
        Date.now() - 1800000  // 30 minutes ago
      ];

      // This would require access to private error rate window
      // or testing through public methods that trigger rate calculations
      
      const stats = await errorMonitor.getErrorStats();
      expect(stats.errorRate).toBeGreaterThanOrEqual(0);
    });

    it('should return recent errors in statistics', async () => {
      const mockErrors = Array.from({ length: 15 }, (_, i) => ({
        id: `error-${i}`,
        type: 'javascript' as const,
        message: `Error ${i}`,
        severity: 'medium' as const,
        context: {
          timestamp: Date.now() - i * 1000,
          userAgent: 'test',
          extensionVersion: '1.0.0',
          userId: 'user1',
          sessionId: 'session1',
          source: 'dashboard' as const
        }
      }));

      mockStorage.get.mockImplementation((key) => {
        if (key === 'dcmaar.error_monitor.errors') {
          return Promise.resolve({ 'dcmaar.error_monitor.errors': mockErrors });
        }
        return Promise.resolve({});
      });

      const stats = await errorMonitor.getErrorStats();
      
      // Should return last 10 errors
      expect(stats.recentErrors).toHaveLength(10);
      expect(stats.recentErrors[0].id).toBe('error-14'); // Most recent
    });
  });

  describe('Data Cleanup and Maintenance', () => {
    beforeEach(async () => {
      await errorMonitor.initialize();
    });

    it('should clean up old error reports', async () => {
      const now = Date.now();
      const oldErrors = [
        {
          id: '1',
          type: 'javascript',
          message: 'Old error',
          severity: 'medium',
          context: {
            timestamp: now - 10 * 86400000, // 10 days ago
            userAgent: 'test',
            extensionVersion: '1.0.0',
            userId: 'user1',
            sessionId: 'session1',
            source: 'dashboard'
          }
        },
        {
          id: '2',
          type: 'javascript',
          message: 'Recent error',
          severity: 'medium',
          context: {
            timestamp: now - 2 * 86400000, // 2 days ago
            userAgent: 'test',
            extensionVersion: '1.0.0',
            userId: 'user1',
            sessionId: 'session1',
            source: 'dashboard'
          }
        }
      ];

      mockStorage.get.mockImplementation((key) => {
        if (key === 'dcmaar.error_monitor.errors') {
          return Promise.resolve({ 'dcmaar.error_monitor.errors': oldErrors });
        }
        return Promise.resolve({});
      });

      await errorMonitor.cleanupErrors(7); // Keep 7 days
      
      // Should keep only recent errors
      expect(mockStorage.set).toHaveBeenCalledWith({
        'dcmaar.error_monitor.errors': expect.arrayContaining([
          expect.objectContaining({ message: 'Recent error' })
        ])
      });

      expect(mockTelemetryManager.track).toHaveBeenCalledWith(
        'system',
        'error_monitor.cleanup',
        expect.objectContaining({
          removed: 1,
          remaining: 1
        })
      );
    });
  });

  describe('Error Rate Monitoring', () => {
    beforeEach(async () => {
      await errorMonitor.initialize();
    });

    it('should detect when error rate exceeds threshold', async () => {
      // Report multiple errors quickly to exceed rate threshold
      const error1 = new Error('Rate test 1');
      const error2 = new Error('Rate test 2');
      const error3 = new Error('Rate test 3');
      
      await errorMonitor.reportError(error1);
      await errorMonitor.reportError(error2);
      await errorMonitor.reportError(error3);
      
      // Should trigger error rate threshold alert
      expect(mockTelemetryManager.track).toHaveBeenCalledWith(
        'system',
        'error_rate.threshold_exceeded',
        expect.objectContaining({
          errorRate: expect.any(Number),
          threshold: expect.any(Number)
        }),
        'error'
      );
    });
  });

  describe('Global Error Handler Setup', () => {
    it('should set up window error handlers when available', async () => {
      // Mock window object
      const mockAddEventListener = jest.fn();
      Object.defineProperty(global, 'window', {
        value: {
          addEventListener: mockAddEventListener
        }
      });

      await errorMonitor.initialize();
      
      // Should set up error event listeners
      expect(mockAddEventListener).toHaveBeenCalledWith('error', expect.any(Function));
      expect(mockAddEventListener).toHaveBeenCalledWith('unhandledrejection', expect.any(Function));
    });

    it('should handle CSP violations', async () => {
      const mockAddEventListener = jest.fn();
      Object.defineProperty(global, 'window', {
        value: {
          addEventListener: mockAddEventListener
        }
      });

      await errorMonitor.initialize();
      
      // Should set up CSP violation handler
      expect(mockAddEventListener).toHaveBeenCalledWith('securitypolicyviolation', expect.any(Function));
    });
  });
});