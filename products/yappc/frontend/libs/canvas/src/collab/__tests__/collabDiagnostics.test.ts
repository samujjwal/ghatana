/**
 * Tests for Collaboration Diagnostics Manager
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  CollabDiagnosticsManager,
  type SessionInfo,
  type ErrorEntry,
  type PerformanceMetric,
  type ConnectionQualityMetrics,
  type HealthCheckResult,
} from '../collabDiagnostics';

describe('CollabDiagnosticsManager', () => {
  let manager: CollabDiagnosticsManager;

  beforeEach(() => {
    manager = new CollabDiagnosticsManager();
  });

  describe('initialization', () => {
    it('should initialize with default configuration', () => {
      const config = manager.getConfig();
      expect(config.enabled).toBe(true);
      expect(config.maxErrorHistory).toBe(1000);
      expect(config.maxMetricHistory).toBe(5000);
      expect(config.errorRateThreshold).toBe(10);
      expect(config.latencyThreshold).toBe(200);
      expect(config.packetLossThreshold).toBe(5);
      expect(config.healthCheckInterval).toBe(30000);
    });

    it('should initialize with custom configuration', () => {
      const customManager = new CollabDiagnosticsManager({
        enabled: false,
        maxErrorHistory: 500,
        errorRateThreshold: 20,
      });

      const config = customManager.getConfig();
      expect(config.enabled).toBe(false);
      expect(config.maxErrorHistory).toBe(500);
      expect(config.errorRateThreshold).toBe(20);
      // Other values should use defaults
      expect(config.maxMetricHistory).toBe(5000);
    });
  });

  describe('session management', () => {
    it('should register new session', () => {
      const session = manager.registerSession(
        'session-1',
        'user-1',
        'alice',
        'websocket',
        'room-1'
      );

      expect(session.sessionId).toBe('session-1');
      expect(session.userId).toBe('user-1');
      expect(session.username).toBe('alice');
      expect(session.provider).toBe('websocket');
      expect(session.roomId).toBe('room-1');
      expect(session.status).toBe('connected');
      expect(session.connectedAt).toBeGreaterThan(0);
      expect(session.lastActivityAt).toEqual(session.connectedAt);
    });

    it('should register session with metadata', () => {
      const metadata = { clientVersion: '1.0.0', deviceType: 'desktop' };
      const session = manager.registerSession(
        'session-1',
        'user-1',
        'alice',
        'websocket',
        'room-1',
        metadata
      );

      expect(session.metadata).toEqual(metadata);
    });

    it('should throw error when registering session with diagnostics disabled', () => {
      const disabledManager = new CollabDiagnosticsManager({ enabled: false });

      expect(() => {
        disabledManager.registerSession(
          'session-1',
          'user-1',
          'alice',
          'websocket',
          'room-1'
        );
      }).toThrow('Diagnostics collection is disabled');
    });

    it('should get registered session', () => {
      manager.registerSession('session-1', 'user-1', 'alice', 'websocket', 'room-1');
      const session = manager.getSession('session-1');

      expect(session).toBeDefined();
      expect(session?.sessionId).toBe('session-1');
    });

    it('should return undefined for non-existent session', () => {
      const session = manager.getSession('non-existent');
      expect(session).toBeUndefined();
    });

    it('should update session status', () => {
      manager.registerSession('session-1', 'user-1', 'alice', 'websocket', 'room-1');
      manager.updateSessionStatus('session-1', 'reconnecting');

      const session = manager.getSession('session-1');
      expect(session?.status).toBe('reconnecting');
    });

    it('should increment reconnection count on reconnecting status', () => {
      manager.registerSession('session-1', 'user-1', 'alice', 'websocket', 'room-1');
      manager.updateSessionStatus('session-1', 'reconnecting');

      const stats = manager.getStatistics();
      expect(stats.reconnectionCount).toBe(1);

      manager.updateSessionStatus('session-1', 'reconnecting');
      const stats2 = manager.getStatistics();
      expect(stats2.reconnectionCount).toBe(2);
    });

    it('should throw error when updating status for non-existent session', () => {
      expect(() => {
        manager.updateSessionStatus('non-existent', 'disconnected');
      }).toThrow('Session not found: non-existent');
    });

    it('should update session activity timestamp', () => {
      manager.registerSession('session-1', 'user-1', 'alice', 'websocket', 'room-1');
      const session1 = manager.getSession('session-1');
      const initialActivity = session1!.lastActivityAt;

      // Wait a bit
      const startTime = Date.now();
      while (Date.now() - startTime < 10) {
        // Busy wait
      }

      manager.updateSessionActivity('session-1');
      const session2 = manager.getSession('session-1');

      expect(session2!.lastActivityAt).toBeGreaterThan(initialActivity);
    });

    it('should throw error when updating activity for non-existent session', () => {
      expect(() => {
        manager.updateSessionActivity('non-existent');
      }).toThrow('Session not found: non-existent');
    });

    it('should unregister session', () => {
      manager.registerSession('session-1', 'user-1', 'alice', 'websocket', 'room-1');
      const result = manager.unregisterSession('session-1');

      expect(result).toBe(true);
      const session = manager.getSession('session-1');
      expect(session?.status).toBe('disconnected');
    });

    it('should return false when unregistering non-existent session', () => {
      const result = manager.unregisterSession('non-existent');
      expect(result).toBe(false);
    });

    it('should get all active sessions', () => {
      manager.registerSession('session-1', 'user-1', 'alice', 'websocket', 'room-1');
      manager.registerSession('session-2', 'user-2', 'bob', 'webrtc', 'room-1');
      manager.registerSession('session-3', 'user-3', 'charlie', 'websocket', 'room-2');
      manager.updateSessionStatus('session-3', 'disconnected');

      const active = manager.getActiveSessions();
      expect(active.length).toBe(2);
      expect(active.map((s) => s.sessionId).sort()).toEqual(['session-1', 'session-2']);
    });

    it('should include reconnecting sessions in active list', () => {
      manager.registerSession('session-1', 'user-1', 'alice', 'websocket', 'room-1');
      manager.updateSessionStatus('session-1', 'reconnecting');

      const active = manager.getActiveSessions();
      expect(active.length).toBe(1);
      expect(active[0].status).toBe('reconnecting');
    });

    it('should get sessions by user', () => {
      manager.registerSession('session-1', 'user-1', 'alice', 'websocket', 'room-1');
      manager.registerSession('session-2', 'user-1', 'alice', 'webrtc', 'room-2');
      manager.registerSession('session-3', 'user-2', 'bob', 'websocket', 'room-1');

      const userSessions = manager.getSessionsByUser('user-1');
      expect(userSessions.length).toBe(2);
      expect(userSessions.every((s) => s.userId === 'user-1')).toBe(true);
    });

    it('should get sessions by room', () => {
      manager.registerSession('session-1', 'user-1', 'alice', 'websocket', 'room-1');
      manager.registerSession('session-2', 'user-2', 'bob', 'webrtc', 'room-1');
      manager.registerSession('session-3', 'user-3', 'charlie', 'websocket', 'room-2');

      const roomSessions = manager.getSessionsByRoom('room-1');
      expect(roomSessions.length).toBe(2);
      expect(roomSessions.every((s) => s.roomId === 'room-1')).toBe(true);
    });
  });

  describe('error tracking', () => {
    it('should log error', () => {
      const error = manager.logError(
        'CONNECTION_ERROR',
        'Failed to connect to server',
        'high'
      );

      expect(error.id).toBeDefined();
      expect(error.type).toBe('CONNECTION_ERROR');
      expect(error.message).toBe('Failed to connect to server');
      expect(error.severity).toBe('high');
      expect(error.timestamp).toBeGreaterThan(0);
    });

    it('should log error with session and user context', () => {
      const error = manager.logError(
        'SYNC_ERROR',
        'Synchronization failed',
        'medium',
        'user-1',
        'session-1'
      );

      expect(error.userId).toBe('user-1');
      expect(error.sessionId).toBe('session-1');
    });

    it('should log error with stack trace and context', () => {
      const error = manager.logError(
        'RUNTIME_ERROR',
        'Unexpected error',
        'critical',
        undefined,
        undefined,
        'Error: at line 42',
        { operation: 'load', resource: 'canvas' }
      );

      expect(error.stack).toBe('Error: at line 42');
      expect(error.context).toEqual({ operation: 'load', resource: 'canvas' });
    });

    it('should throw error when logging with diagnostics disabled', () => {
      const disabledManager = new CollabDiagnosticsManager({ enabled: false });

      expect(() => {
        disabledManager.logError('ERROR', 'Test error', 'low');
      }).toThrow('Diagnostics collection is disabled');
    });

    it('should get all errors', () => {
      manager.logError('ERROR_1', 'Error 1', 'low');
      manager.logError('ERROR_2', 'Error 2', 'high');
      manager.logError('ERROR_3', 'Error 3', 'medium');

      const errors = manager.getErrors();
      expect(errors.length).toBe(3);
    });

    it('should get errors by severity', () => {
      manager.logError('ERROR_1', 'Error 1', 'low');
      manager.logError('ERROR_2', 'Error 2', 'high');
      manager.logError('ERROR_3', 'Error 3', 'high');

      const highErrors = manager.getErrorsBySeverity('high');
      expect(highErrors.length).toBe(2);
      expect(highErrors.every((e) => e.severity === 'high')).toBe(true);
    });

    it('should get errors by session', () => {
      manager.logError('ERROR_1', 'Error 1', 'low', undefined, 'session-1');
      manager.logError('ERROR_2', 'Error 2', 'high', undefined, 'session-1');
      manager.logError('ERROR_3', 'Error 3', 'medium', undefined, 'session-2');

      const sessionErrors = manager.getErrorsBySession('session-1');
      expect(sessionErrors.length).toBe(2);
      expect(sessionErrors.every((e) => e.sessionId === 'session-1')).toBe(true);
    });

    it('should get errors by time range', () => {
      const startTime = Date.now();
      manager.logError('ERROR_1', 'Error 1', 'low');

      // Wait a bit
      const waitStart = Date.now();
      while (Date.now() - waitStart < 10) {
        // Busy wait
      }

      const midTime = Date.now();
      manager.logError('ERROR_2', 'Error 2', 'high');

      // Wait a bit more
      const waitStart2 = Date.now();
      while (Date.now() - waitStart2 < 10) {
        // Busy wait
      }

      const endTime = Date.now();

      const rangeErrors = manager.getErrorsByTimeRange(midTime, endTime);
      expect(rangeErrors.length).toBe(1);
      expect(rangeErrors[0].type).toBe('ERROR_2');
    });

    it('should clear error history', () => {
      manager.logError('ERROR_1', 'Error 1', 'low');
      manager.logError('ERROR_2', 'Error 2', 'high');

      const cleared = manager.clearErrors();
      expect(cleared).toBe(2);

      const errors = manager.getErrors();
      expect(errors.length).toBe(0);
    });

    it('should enforce maximum error history', () => {
      const smallHistoryManager = new CollabDiagnosticsManager({ maxErrorHistory: 3 });

      for (let i = 0; i < 5; i++) {
        smallHistoryManager.logError(`ERROR_${i}`, `Error ${i}`, 'low');
      }

      const errors = smallHistoryManager.getErrors();
      expect(errors.length).toBe(3);
      // Should keep the last 3 errors (2, 3, 4)
      expect(errors[0].type).toBe('ERROR_2');
      expect(errors[2].type).toBe('ERROR_4');
    });

    it('should detect error rate exceeded', () => {
      const customManager = new CollabDiagnosticsManager({ errorRateThreshold: 2 });

      // Log 3 errors (exceeds threshold of 2)
      customManager.logError('ERROR_1', 'Error 1', 'low');
      customManager.logError('ERROR_2', 'Error 2', 'low');
      customManager.logError('ERROR_3', 'Error 3', 'low');

      expect(customManager.isErrorRateExceeded()).toBe(true);
    });

    it('should not detect error rate exceeded when below threshold', () => {
      const customManager = new CollabDiagnosticsManager({ errorRateThreshold: 5 });

      customManager.logError('ERROR_1', 'Error 1', 'low');
      customManager.logError('ERROR_2', 'Error 2', 'low');

      expect(customManager.isErrorRateExceeded()).toBe(false);
    });
  });

  describe('performance metrics', () => {
    it('should record performance metric', () => {
      const metric = manager.recordMetric('sync_latency', 150, 'ms');

      expect(metric.name).toBe('sync_latency');
      expect(metric.value).toBe(150);
      expect(metric.unit).toBe('ms');
      expect(metric.timestamp).toBeGreaterThan(0);
    });

    it('should record metric with session and tags', () => {
      const metric = manager.recordMetric('bandwidth', 1024, 'kbps', 'session-1', {
        provider: 'websocket',
      });

      expect(metric.sessionId).toBe('session-1');
      expect(metric.tags).toEqual({ provider: 'websocket' });
    });

    it('should throw error when recording metric with diagnostics disabled', () => {
      const disabledManager = new CollabDiagnosticsManager({ enabled: false });

      expect(() => {
        disabledManager.recordMetric('latency', 100, 'ms');
      }).toThrow('Diagnostics collection is disabled');
    });

    it('should get metrics by name', () => {
      manager.recordMetric('latency', 100, 'ms');
      manager.recordMetric('latency', 150, 'ms');
      manager.recordMetric('bandwidth', 512, 'kbps');

      const latencyMetrics = manager.getMetricsByName('latency');
      expect(latencyMetrics.length).toBe(2);
      expect(latencyMetrics.every((m) => m.name === 'latency')).toBe(true);
    });

    it('should get metrics by session', () => {
      manager.recordMetric('latency', 100, 'ms', 'session-1');
      manager.recordMetric('latency', 150, 'ms', 'session-1');
      manager.recordMetric('latency', 120, 'ms', 'session-2');

      const sessionMetrics = manager.getMetricsBySession('session-1');
      expect(sessionMetrics.length).toBe(2);
      expect(sessionMetrics.every((m) => m.sessionId === 'session-1')).toBe(true);
    });

    it('should calculate average metric value', () => {
      manager.recordMetric('latency', 100, 'ms');
      manager.recordMetric('latency', 200, 'ms');
      manager.recordMetric('latency', 150, 'ms');

      const average = manager.getAverageMetric('latency');
      expect(average).toBe(150);
    });

    it('should return 0 for average when no metrics exist', () => {
      const average = manager.getAverageMetric('non-existent');
      expect(average).toBe(0);
    });

    it('should clear metrics history', () => {
      manager.recordMetric('latency', 100, 'ms');
      manager.recordMetric('bandwidth', 512, 'kbps');

      const cleared = manager.clearMetrics();
      expect(cleared).toBe(2);

      const metrics = manager.getMetricsByName('latency');
      expect(metrics.length).toBe(0);
    });

    it('should enforce maximum metric history', () => {
      const smallHistoryManager = new CollabDiagnosticsManager({ maxMetricHistory: 3 });

      for (let i = 0; i < 5; i++) {
        smallHistoryManager.recordMetric('latency', 100 + i, 'ms');
      }

      const metrics = smallHistoryManager.getMetricsByName('latency');
      expect(metrics.length).toBe(3);
      // Should keep the last 3 metrics
      expect(metrics[0].value).toBe(102);
      expect(metrics[2].value).toBe(104);
    });
  });

  describe('connection quality', () => {
    it('should update connection quality metrics', () => {
      const quality = manager.updateConnectionQuality('session-1', 80, 2, 5, 1024);

      expect(quality.sessionId).toBe('session-1');
      expect(quality.quality).toBe('good');
      expect(quality.latency).toBe(80);
      expect(quality.packetLoss).toBe(2);
      expect(quality.jitter).toBe(5);
      expect(quality.bandwidth).toBe(1024);
      expect(quality.timestamp).toBeGreaterThan(0);
    });

    it('should rate connection as excellent', () => {
      const quality = manager.updateConnectionQuality('session-1', 30, 0.5, 2, 2048);
      expect(quality.quality).toBe('excellent');
    });

    it('should rate connection as good', () => {
      const quality = manager.updateConnectionQuality('session-1', 80, 2, 5, 1024);
      expect(quality.quality).toBe('good');
    });

    it('should rate connection as fair', () => {
      const quality = manager.updateConnectionQuality('session-1', 150, 4, 10, 512);
      expect(quality.quality).toBe('fair');
    });

    it('should rate connection as poor', () => {
      const quality = manager.updateConnectionQuality('session-1', 300, 8, 20, 256);
      expect(quality.quality).toBe('poor');
    });

    it('should get connection quality for session', () => {
      manager.updateConnectionQuality('session-1', 80, 2, 5, 1024);
      const quality = manager.getConnectionQuality('session-1');

      expect(quality).toBeDefined();
      expect(quality?.sessionId).toBe('session-1');
    });

    it('should return undefined for non-existent session quality', () => {
      const quality = manager.getConnectionQuality('non-existent');
      expect(quality).toBeUndefined();
    });

    it('should get all connection quality metrics', () => {
      manager.updateConnectionQuality('session-1', 80, 2, 5, 1024);
      manager.updateConnectionQuality('session-2', 120, 3, 8, 768);

      const allQuality = manager.getAllConnectionQuality();
      expect(allQuality.length).toBe(2);
    });

    it('should get poor quality connections', () => {
      manager.updateConnectionQuality('session-1', 30, 0.5, 2, 2048); // excellent
      manager.updateConnectionQuality('session-2', 150, 4, 10, 512); // fair
      manager.updateConnectionQuality('session-3', 300, 8, 20, 256); // poor

      const poorConnections = manager.getPoorQualityConnections();
      expect(poorConnections.length).toBe(2);
      expect(poorConnections.map((c) => c.sessionId).sort()).toEqual([
        'session-2',
        'session-3',
      ]);
    });
  });

  describe('health checks', () => {
    it('should record health check result', () => {
      const result = manager.recordHealthCheck('yjs-server', 'healthy', 45, {
        uptime: 3600,
      });

      expect(result.service).toBe('yjs-server');
      expect(result.status).toBe('healthy');
      expect(result.responseTime).toBe(45);
      expect(result.details).toEqual({ uptime: 3600 });
      expect(result.timestamp).toBeGreaterThan(0);
    });

    it('should record unhealthy check with error', () => {
      const result = manager.recordHealthCheck(
        'websocket-gateway',
        'unhealthy',
        5000,
        undefined,
        'Connection timeout'
      );

      expect(result.status).toBe('unhealthy');
      expect(result.error).toBe('Connection timeout');
    });

    it('should get health check for service', () => {
      manager.recordHealthCheck('yjs-server', 'healthy', 45);
      const check = manager.getHealthCheck('yjs-server');

      expect(check).toBeDefined();
      expect(check?.service).toBe('yjs-server');
    });

    it('should return undefined for non-existent service', () => {
      const check = manager.getHealthCheck('non-existent');
      expect(check).toBeUndefined();
    });

    it('should get all health checks', () => {
      manager.recordHealthCheck('yjs-server', 'healthy', 45);
      manager.recordHealthCheck('websocket-gateway', 'degraded', 150);

      const checks = manager.getAllHealthChecks();
      expect(checks.length).toBe(2);
    });

    it('should get unhealthy services', () => {
      manager.recordHealthCheck('service-1', 'healthy', 30);
      manager.recordHealthCheck('service-2', 'degraded', 200);
      manager.recordHealthCheck('service-3', 'unhealthy', 5000);

      const unhealthy = manager.getUnhealthyServices();
      expect(unhealthy.length).toBe(2);
      expect(unhealthy.map((s) => s.service).sort()).toEqual(['service-2', 'service-3']);
    });
  });

  describe('statistics', () => {
    it('should calculate diagnostic statistics', () => {
      // Setup sessions
      manager.registerSession('session-1', 'user-1', 'alice', 'websocket', 'room-1');
      manager.registerSession('session-2', 'user-2', 'bob', 'webrtc', 'room-1');
      manager.updateSessionStatus('session-1', 'reconnecting');

      // Setup errors
      manager.logError('ERROR_1', 'Error 1', 'low');
      manager.logError('ERROR_2', 'Error 2', 'high');
      manager.logError('ERROR_3', 'Error 3', 'high');

      // Setup connection quality
      manager.updateConnectionQuality('session-1', 100, 3, 5, 1024);
      manager.updateConnectionQuality('session-2', 80, 2, 4, 2048);

      // Setup health checks
      manager.recordHealthCheck('yjs-server', 'healthy', 30);
      manager.recordHealthCheck('websocket-gateway', 'degraded', 150);

      const stats = manager.getStatistics();

      expect(stats.activeSessions).toBe(2);
      expect(stats.totalErrors).toBe(3);
      expect(stats.errorsBySeverity.low).toBe(1);
      expect(stats.errorsBySeverity.high).toBe(2);
      expect(stats.averageLatency).toBe(90);
      expect(stats.averagePacketLoss).toBe(2.5);
      expect(stats.reconnectionCount).toBe(1);
      expect(stats.healthSummary).toEqual({
        'yjs-server': 'healthy',
        'websocket-gateway': 'degraded',
      });
    });

    it('should return zero averages when no quality metrics exist', () => {
      const stats = manager.getStatistics();

      expect(stats.averageLatency).toBe(0);
      expect(stats.averagePacketLoss).toBe(0);
    });

    it('should count errors by severity correctly', () => {
      manager.logError('ERROR_1', 'Error 1', 'low');
      manager.logError('ERROR_2', 'Error 2', 'low');
      manager.logError('ERROR_3', 'Error 3', 'medium');
      manager.logError('ERROR_4', 'Error 4', 'high');
      manager.logError('ERROR_5', 'Error 5', 'critical');
      manager.logError('ERROR_6', 'Error 6', 'critical');

      const stats = manager.getStatistics();

      expect(stats.errorsBySeverity).toEqual({
        low: 2,
        medium: 1,
        high: 1,
        critical: 2,
      });
    });
  });

  describe('configuration management', () => {
    it('should get current configuration', () => {
      const config = manager.getConfig();

      expect(config).toEqual({
        enabled: true,
        maxErrorHistory: 1000,
        maxMetricHistory: 5000,
        errorRateThreshold: 10,
        latencyThreshold: 200,
        packetLossThreshold: 5,
        healthCheckInterval: 30000,
      });
    });

    it('should update configuration', () => {
      manager.updateConfig({
        errorRateThreshold: 20,
        latencyThreshold: 300,
      });

      const config = manager.getConfig();
      expect(config.errorRateThreshold).toBe(20);
      expect(config.latencyThreshold).toBe(300);
      // Other values unchanged
      expect(config.maxErrorHistory).toBe(1000);
    });

    it('should update enabled status', () => {
      manager.updateConfig({ enabled: false });

      const config = manager.getConfig();
      expect(config.enabled).toBe(false);
    });
  });

  describe('reset', () => {
    it('should reset all diagnostics data', () => {
      // Setup various data
      manager.registerSession('session-1', 'user-1', 'alice', 'websocket', 'room-1');
      manager.logError('ERROR_1', 'Error 1', 'low');
      manager.recordMetric('latency', 100, 'ms');
      manager.updateConnectionQuality('session-1', 80, 2, 5, 1024);
      manager.recordHealthCheck('yjs-server', 'healthy', 30);
      manager.updateSessionStatus('session-1', 'reconnecting');

      manager.reset();

      // Verify all data cleared
      expect(manager.getActiveSessions().length).toBe(0);
      expect(manager.getErrors().length).toBe(0);
      expect(manager.getMetricsByName('latency').length).toBe(0);
      expect(manager.getAllConnectionQuality().length).toBe(0);
      expect(manager.getAllHealthChecks().length).toBe(0);

      const stats = manager.getStatistics();
      expect(stats.activeSessions).toBe(0);
      expect(stats.totalErrors).toBe(0);
      expect(stats.reconnectionCount).toBe(0);
    });

    it('should preserve configuration after reset', () => {
      manager.updateConfig({ errorRateThreshold: 20 });
      manager.reset();

      const config = manager.getConfig();
      expect(config.errorRateThreshold).toBe(20);
    });
  });
});
