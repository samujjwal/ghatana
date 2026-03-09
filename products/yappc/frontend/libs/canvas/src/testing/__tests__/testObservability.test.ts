/**
 * Test Observability - Test Suite
 * 
 * Comprehensive tests for test observability utilities.
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

import {
  createTestObservabilityManager,
  type ObservabilityConfig,
  type TestArtifact,
  type TestFailure,
  type PerformanceMetrics,
  type InvestigationGuide,

  TestObservabilityManager} from '../testObservability';

describe('TestObservabilityManager', () => {
  describe('Initialization', () => {
    it('should initialize with default configuration', () => {
      const manager = createTestObservabilityManager();
      const config = manager.getConfig();

      expect(config.debugEnabled).toBe(false);
      expect(config.logLevel).toBe('info');
      expect(config.artifactRetentionDays).toBe(30);
      expect(config.capturePerformance).toBe(true);
      expect(config.autoInvestigationGuides).toBe(true);
      expect(config.maxLogEntries).toBe(10000);
    });

    it('should accept custom configuration', () => {
      const customConfig: ObservabilityConfig = {
        debugEnabled: true,
        logLevel: 'debug',
        artifactRetentionDays: 7,
        capturePerformance: false,
        autoInvestigationGuides: false,
        maxLogEntries: 5000,
      };

      const manager = createTestObservabilityManager(customConfig);
      const config = manager.getConfig();

      expect(config.debugEnabled).toBe(true);
      expect(config.logLevel).toBe('debug');
      expect(config.artifactRetentionDays).toBe(7);
      expect(config.capturePerformance).toBe(false);
      expect(config.autoInvestigationGuides).toBe(false);
      expect(config.maxLogEntries).toBe(5000);
    });

    it('should update configuration', () => {
      const manager = createTestObservabilityManager();

      manager.updateConfig({
        debugEnabled: true,
        logLevel: 'warn',
      });

      const config = manager.getConfig();
      expect(config.debugEnabled).toBe(true);
      expect(config.logLevel).toBe('warn');
    });
  });

  describe('Logging', () => {
    let manager: TestObservabilityManager;

    beforeEach(() => {
      manager = createTestObservabilityManager({ debugEnabled: false });
    });

    it('should log messages at all levels', () => {
      manager.debug('Debug message');
      manager.info('Info message');
      manager.warn('Warn message');
      manager.error('Error message');

      const logs = manager.getLogs();
      expect(logs.some((log) => log.level === 'debug')).toBe(false); // filtered by log level
      expect(logs.some((log) => log.level === 'info')).toBe(true);
      expect(logs.some((log) => log.level === 'warn')).toBe(true);
      expect(logs.some((log) => log.level === 'error')).toBe(true);
    });

    it('should filter logs by level', () => {
      manager = createTestObservabilityManager({ logLevel: 'warn' });

      manager.debug('Debug message');
      manager.info('Info message');
      manager.warn('Warn message');
      manager.error('Error message');

      const logs = manager.getLogs();
      expect(logs.length).toBe(2); // only warn and error
      expect(logs.some((log) => log.level === 'debug')).toBe(false);
      expect(logs.some((log) => log.level === 'info')).toBe(false);
    });

    it('should include context in logs', () => {
      manager.info('Test message', {
        context: { userId: '123', action: 'click' },
        testName: 'my-test',
      });

      const logs = manager.getLogs();
      expect(logs[0].context).toEqual({ userId: '123', action: 'click' });
      expect(logs[0].testName).toBe('my-test');
    });

    it('should include stack trace', () => {
      const stack = 'Error: Test error\n  at test.ts:123';
      manager.error('Error occurred', { stack });

      const logs = manager.getLogs();
      expect(logs[0].stack).toBe(stack);
    });

    it('should get logs with filters', () => {
      manager.info('Test 1', { testName: 'test-1' });
      manager.warn('Test 2', { testName: 'test-2' });
      manager.error('Test 1 error', { testName: 'test-1' });

      const test1Logs = manager.getLogs({ testName: 'test-1' });
      expect(test1Logs).toHaveLength(2);

      const errorLogs = manager.getLogs({ level: 'error' });
      expect(errorLogs).toHaveLength(1);
    });

    it('should filter logs by timestamp', async () => {
      manager.info('Old message');

      // Wait a bit to ensure different timestamp
      await new Promise((resolve) => setTimeout(resolve, 10));
      const futureTime = Date.now();
      await new Promise((resolve) => setTimeout(resolve, 10));
      
      manager.info('New message');

      const recentLogs = manager.getLogs({ since: futureTime });
      expect(recentLogs).toHaveLength(1);
      expect(recentLogs[0].message).toBe('New message');
    });

    it('should trim logs when exceeding max entries', () => {
      manager = createTestObservabilityManager({ maxLogEntries: 10 });

      for (let i = 0; i < 15; i++) {
        manager.info(`Message ${i}`);
      }

      const logs = manager.getLogs();
      expect(logs.length).toBe(10);
      expect(logs[0].message).toBe('Message 5'); // oldest entries removed
    });
  });

  describe('Artifact Management', () => {
    let manager: TestObservabilityManager;

    beforeEach(() => {
      manager = createTestObservabilityManager();
    });

    it('should register artifacts', () => {
      const artifact: TestArtifact = {
        type: 'screenshot',
        path: '/path/to/screenshot.png',
        testName: 'my-test',
        size: 1024,
        timestamp: Date.now(),
      };

      manager.registerArtifact(artifact);

      const artifacts = manager.getArtifacts('my-test');
      expect(artifacts).toHaveLength(1);
      expect(artifacts[0]).toEqual(artifact);
    });

    it('should get all artifacts', () => {
      manager.registerArtifact({
        type: 'screenshot',
        path: '/path/1.png',
        testName: 'test-1',
        timestamp: Date.now(),
      });

      manager.registerArtifact({
        type: 'trace',
        path: '/path/2.zip',
        testName: 'test-2',
        timestamp: Date.now(),
      });

      const allArtifacts = manager.getArtifacts();
      expect(allArtifacts).toHaveLength(2);
    });

    it('should clean up old artifacts', () => {
      const oldTime = Date.now() - 60 * 24 * 60 * 60 * 1000; // 60 days ago
      const recentTime = Date.now();

      manager.registerArtifact({
        type: 'screenshot',
        path: '/path/old.png',
        testName: 'test-1',
        timestamp: oldTime,
      });

      manager.registerArtifact({
        type: 'screenshot',
        path: '/path/recent.png',
        testName: 'test-2',
        timestamp: recentTime,
      });

      const removed = manager.cleanupArtifacts(30);

      expect(removed).toBe(1);
      expect(manager.getArtifacts()).toHaveLength(1);
      expect(manager.getArtifacts()[0].path).toBe('/path/recent.png');
    });

    it('should cleanup with custom retention days', () => {
      const time45DaysAgo = Date.now() - 45 * 24 * 60 * 60 * 1000;
      const time15DaysAgo = Date.now() - 15 * 24 * 60 * 60 * 1000;

      manager.registerArtifact({
        type: 'screenshot',
        path: '/path/old.png',
        testName: 'test-1',
        timestamp: time45DaysAgo,
      });

      manager.registerArtifact({
        type: 'screenshot',
        path: '/path/recent.png',
        testName: 'test-2',
        timestamp: time15DaysAgo,
      });

      const removed = manager.cleanupArtifacts(20);

      expect(removed).toBe(1);
      expect(manager.getArtifacts()).toHaveLength(1);
    });
  });

  describe('Failure Recording', () => {
    let manager: TestObservabilityManager;

    beforeEach(() => {
      manager = createTestObservabilityManager();
    });

    it('should record test failure', () => {
      const failure: TestFailure = {
        testName: 'my-test',
        message: 'Test failed',
        stack: 'Error stack',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      };

      manager.recordFailure(failure);

      const recorded = manager.getFailure('my-test');
      expect(recorded).toEqual(failure);
    });

    it('should get all failures', () => {
      manager.recordFailure({
        testName: 'test-1',
        message: 'Failed 1',
        timestamp: Date.now(),
        duration: 500,
        artifacts: [],
      });

      manager.recordFailure({
        testName: 'test-2',
        message: 'Failed 2',
        timestamp: Date.now(),
        duration: 700,
        artifacts: [],
      });

      const failures = manager.getAllFailures();
      expect(failures).toHaveLength(2);
    });

    it('should auto-generate investigation guide', () => {
      const failure: TestFailure = {
        testName: 'timeout-test',
        message: 'Test timed out after 30 seconds',
        timestamp: Date.now(),
        duration: 30000,
        artifacts: [],
      };

      manager.recordFailure(failure);

      const recorded = manager.getFailure('timeout-test');
      expect(recorded?.investigationGuide).toBeDefined();
      expect(recorded?.investigationGuide?.some((line) => line.includes('Timeout Error'))).toBe(true);
    });

    it('should generate guide for element not found error', () => {
      const failure: TestFailure = {
        testName: 'selector-test',
        message: 'Cannot find element with selector .my-button',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      };

      manager.recordFailure(failure);

      const recorded = manager.getFailure('selector-test');
      expect(recorded?.investigationGuide).toBeDefined();
      expect(recorded?.investigationGuide?.some((line) => line.includes('Element Not Found'))).toBe(true);
    });

    it('should not generate guide when disabled', () => {
      manager = createTestObservabilityManager({ autoInvestigationGuides: false });

      const failure: TestFailure = {
        testName: 'timeout-test',
        message: 'Test timed out',
        timestamp: Date.now(),
        duration: 30000,
        artifacts: [],
      };

      manager.recordFailure(failure);

      const recorded = manager.getFailure('timeout-test');
      expect(recorded?.investigationGuide).toBeUndefined();
    });
  });

  describe('Performance Tracking', () => {
    let manager: TestObservabilityManager;

    beforeEach(() => {
      manager = createTestObservabilityManager();
    });

    it('should record performance metrics', () => {
      const metrics: PerformanceMetrics = {
        testName: 'my-test',
        duration: 1500,
        setupDuration: 100,
        teardownDuration: 50,
        timestamp: Date.now(),
      };

      manager.recordPerformance(metrics);

      const recorded = manager.getPerformance('my-test');
      expect(recorded).toHaveLength(1);
      expect(recorded[0]).toEqual(metrics);
    });

    it('should get all performance metrics', () => {
      manager.recordPerformance({
        testName: 'test-1',
        duration: 1000,
        timestamp: Date.now(),
      });

      manager.recordPerformance({
        testName: 'test-2',
        duration: 2000,
        timestamp: Date.now(),
      });

      const allMetrics = manager.getPerformance();
      expect(allMetrics).toHaveLength(2);
    });

    it('should warn about slow tests', () => {
      const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
      manager = createTestObservabilityManager({ debugEnabled: true });

      manager.recordPerformance({
        testName: 'slow-test',
        duration: 15000,
        timestamp: Date.now(),
      });

      const logs = manager.getLogs({ level: 'warn' });
      expect(logs.some((log) => log.message.includes('Slow test'))).toBe(true);

      consoleSpy.mockRestore();
    });

    it('should include memory usage', () => {
      const metrics: PerformanceMetrics = {
        testName: 'my-test',
        duration: 1000,
        memoryUsage: {
          heapUsed: 1024 * 1024,
          heapTotal: 2048 * 1024,
          external: 512 * 1024,
        },
        timestamp: Date.now(),
      };

      manager.recordPerformance(metrics);

      const recorded = manager.getPerformance('my-test');
      expect(recorded[0].memoryUsage).toEqual(metrics.memoryUsage);
    });

    it('should include custom metrics', () => {
      const metrics: PerformanceMetrics = {
        testName: 'my-test',
        duration: 1000,
        customMetrics: {
          apiCalls: 5,
          dbQueries: 3,
          cacheHits: 10,
        },
        timestamp: Date.now(),
      };

      manager.recordPerformance(metrics);

      const recorded = manager.getPerformance('my-test');
      expect(recorded[0].customMetrics).toEqual(metrics.customMetrics);
    });
  });

  describe('Investigation Guides', () => {
    let manager: TestObservabilityManager;

    beforeEach(() => {
      manager = createTestObservabilityManager();
    });

    it('should register custom investigation guide', () => {
      const guide: InvestigationGuide = {
        pattern: /custom error pattern/i,
        title: 'Custom Error',
        steps: ['Step 1', 'Step 2'],
      };

      manager.registerInvestigationGuide(guide);

      const failure: TestFailure = {
        testName: 'test',
        message: 'Custom error pattern detected',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      };

      const generated = manager.generateInvestigationGuide(failure);
      expect(generated).toBeDefined();
      expect(generated?.some((line) => line.includes('Custom Error'))).toBe(true);
    });

    it('should match guides by message', () => {
      const failure: TestFailure = {
        testName: 'test',
        message: 'Request timed out',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      };

      const guide = manager.generateInvestigationGuide(failure);
      expect(guide).toBeDefined();
      expect(guide?.some((line) => line.includes('Timeout Error'))).toBe(true);
    });

    it('should match guides by stack trace', () => {
      const failure: TestFailure = {
        testName: 'test',
        message: 'Generic error',
        stack: 'Network error: fetch failed',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      };

      const guide = manager.generateInvestigationGuide(failure);
      expect(guide).toBeDefined();
      expect(guide?.some((line) => line.includes('Network Error'))).toBe(true);
    });

    it('should include common causes in guide', () => {
      const failure: TestFailure = {
        testName: 'test',
        message: 'Assertion failed: expected 5 to equal 10',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      };

      const guide = manager.generateInvestigationGuide(failure);
      expect(guide?.some((line) => line.includes('Common Causes'))).toBe(true);
    });

    it('should return undefined for unknown errors', () => {
      const failure: TestFailure = {
        testName: 'test',
        message: 'Very specific custom error that has no guide',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      };

      const guide = manager.generateInvestigationGuide(failure);
      expect(guide).toBeUndefined();
    });
  });

  describe('Report Generation', () => {
    let manager: TestObservabilityManager;

    beforeEach(() => {
      manager = createTestObservabilityManager();
    });

    it('should export report as JSON', () => {
      const manager = createTestObservabilityManager(); // Fresh instance
      
      manager.info('Test log');
      manager.recordFailure({
        testName: 'test-1',
        message: 'Failed',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      });

      const json = manager.exportReportJSON();
      const report = JSON.parse(json);

      expect(report.logs.length).toBeGreaterThanOrEqual(1);
      expect(report.failures['test-1']).toBeDefined();
      expect(report.summary.totalLogs).toBeGreaterThanOrEqual(1);
      expect(report.summary.totalFailures).toBe(1);
    });

    it('should export report as Markdown', () => {
      manager.recordFailure({
        testName: 'test-1',
        message: 'Test failed',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      });

      manager.recordPerformance({
        testName: 'test-1',
        duration: 1000,
        timestamp: Date.now(),
      });

      const markdown = manager.exportReportMarkdown();

      expect(markdown).toContain('# Test Observability Report');
      expect(markdown).toContain('## Summary');
      expect(markdown).toContain('## Failures');
      expect(markdown).toContain('## Performance');
      expect(markdown).toContain('test-1');
    });

    it('should generate CI report', () => {
      manager.recordPerformance({
        testName: 'test-1',
        duration: 1000,
        timestamp: Date.now(),
      });

      manager.recordPerformance({
        testName: 'test-2',
        duration: 1500,
        timestamp: Date.now(),
      });

      manager.recordFailure({
        testName: 'test-2',
        message: 'Failed',
        timestamp: Date.now(),
        duration: 1500,
        artifacts: [],
      });

      const report = manager.generateCIReport();

      expect(report.exitCode).toBe(1); // has failures
      expect(report.summary).toContain('1/2 tests failed');
      expect(report.details).toContain('Passed: 1');
      expect(report.details).toContain('Failed: 1');
    });

    it('should return success CI report when all pass', () => {
      manager.recordPerformance({
        testName: 'test-1',
        duration: 1000,
        timestamp: Date.now(),
      });

      const report = manager.generateCIReport();

      expect(report.exitCode).toBe(0);
      expect(report.summary).toContain('All 1 tests passed');
    });
  });

  describe('Cleanup Operations', () => {
    let manager: TestObservabilityManager;

    beforeEach(() => {
      manager = createTestObservabilityManager();
    });

    it('should clear logs', () => {
      manager.info('Test log');
      manager.clearLogs();

      expect(manager.getLogs()).toHaveLength(0);
    });

    it('should clear specific test artifacts', () => {
      manager.registerArtifact({
        type: 'screenshot',
        path: '/path/1.png',
        testName: 'test-1',
        timestamp: Date.now(),
      });

      manager.registerArtifact({
        type: 'screenshot',
        path: '/path/2.png',
        testName: 'test-2',
        timestamp: Date.now(),
      });

      manager.clearArtifacts('test-1');

      expect(manager.getArtifacts('test-1')).toHaveLength(0);
      expect(manager.getArtifacts('test-2')).toHaveLength(1);
    });

    it('should clear all artifacts', () => {
      manager.registerArtifact({
        type: 'screenshot',
        path: '/path/1.png',
        testName: 'test-1',
        timestamp: Date.now(),
      });

      manager.clearArtifacts();

      expect(manager.getArtifacts()).toHaveLength(0);
    });

    it('should clear specific test failures', () => {
      manager.recordFailure({
        testName: 'test-1',
        message: 'Failed 1',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      });

      manager.recordFailure({
        testName: 'test-2',
        message: 'Failed 2',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      });

      manager.clearFailures('test-1');

      expect(manager.getFailure('test-1')).toBeUndefined();
      expect(manager.getFailure('test-2')).toBeDefined();
    });

    it('should clear all failures', () => {
      manager.recordFailure({
        testName: 'test-1',
        message: 'Failed',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      });

      manager.clearFailures();

      expect(manager.getAllFailures()).toHaveLength(0);
    });

    it('should clear specific test performance', () => {
      manager.recordPerformance({
        testName: 'test-1',
        duration: 1000,
        timestamp: Date.now(),
      });

      manager.recordPerformance({
        testName: 'test-2',
        duration: 2000,
        timestamp: Date.now(),
      });

      manager.clearPerformance('test-1');

      expect(manager.getPerformance('test-1')).toHaveLength(0);
      expect(manager.getPerformance('test-2')).toHaveLength(1);
    });

    it('should clear all performance metrics', () => {
      manager.recordPerformance({
        testName: 'test-1',
        duration: 1000,
        timestamp: Date.now(),
      });

      manager.clearPerformance();

      expect(manager.getPerformance()).toHaveLength(0);
    });

    it('should reset manager', () => {
      manager.info('Test log');
      manager.registerArtifact({
        type: 'screenshot',
        path: '/path/1.png',
        testName: 'test-1',
        timestamp: Date.now(),
      });
      manager.recordFailure({
        testName: 'test-1',
        message: 'Failed',
        timestamp: Date.now(),
        duration: 1000,
        artifacts: [],
      });
      manager.recordPerformance({
        testName: 'test-1',
        duration: 1000,
        timestamp: Date.now(),
      });

      manager.reset();

      expect(manager.getLogs()).toHaveLength(0);
      expect(manager.getArtifacts()).toHaveLength(0);
      expect(manager.getAllFailures()).toHaveLength(0);
      expect(manager.getPerformance()).toHaveLength(0);
    });

    it('should preserve config on reset', () => {
      manager = createTestObservabilityManager({
        debugEnabled: true,
        logLevel: 'debug',
      });

      manager.reset();

      const config = manager.getConfig();
      expect(config.debugEnabled).toBe(true);
      expect(config.logLevel).toBe('debug');
    });
  });
});
