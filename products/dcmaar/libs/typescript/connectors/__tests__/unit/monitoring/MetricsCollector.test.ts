/**
 * @fileoverview Comprehensive unit tests for MetricsCollector module
 *
 * Tests cover:
 * - Counter creation and increment
 * - Histogram/gauge recording
 * - Metric labels and dimensions
 * - Metric aggregation
 * - Metric export and serialization
 * - Time-series data handling
 * - Metric reset and clearing
 * - Concurrent metric updates
 * - Custom metric types
 * - Metric filtering and querying
 * - Percentile calculations
 * - Summary statistics
 */

import { MetricsCollector, Metric, MetricSnapshot } from '../../../src/monitoring/MetricsCollector';

describe('MetricsCollector', () => {
  let collector: MetricsCollector;

  beforeEach(() => {
    collector = new MetricsCollector();
  });

  afterEach(() => {
    collector.removeAllListeners();
  });

  describe('Counter Metrics', () => {
    it('should create and increment counter', () => {
      collector.incrementCounter('requests_total');

      expect(collector.getCounter('requests_total')).toBe(1);
    });

    it('should increment counter by custom value', () => {
      collector.incrementCounter('bytes_processed', 1024);

      expect(collector.getCounter('bytes_processed')).toBe(1024);
    });

    it('should increment counter multiple times', () => {
      collector.incrementCounter('requests_total');
      collector.incrementCounter('requests_total');
      collector.incrementCounter('requests_total', 3);

      expect(collector.getCounter('requests_total')).toBe(5);
    });

    it('should support counter with labels', () => {
      collector.incrementCounter('requests_total', 1, { method: 'GET', status: '200' });
      collector.incrementCounter('requests_total', 1, { method: 'POST', status: '201' });

      expect(collector.getCounter('requests_total', { method: 'GET', status: '200' })).toBe(1);
      expect(collector.getCounter('requests_total', { method: 'POST', status: '201' })).toBe(1);
    });

    it('should increment labeled counter multiple times', () => {
      const labels = { method: 'GET', endpoint: '/api/users' };

      collector.incrementCounter('requests_total', 1, labels);
      collector.incrementCounter('requests_total', 2, labels);
      collector.incrementCounter('requests_total', 3, labels);

      expect(collector.getCounter('requests_total', labels)).toBe(6);
    });

    it('should emit metric event on counter increment', () => {
      const metricListener = jest.fn();
      collector.on('metric', metricListener);

      collector.incrementCounter('requests_total', 5, { method: 'GET' });

      expect(metricListener).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'requests_total',
          value: 5,
          timestamp: expect.any(Number),
          labels: { method: 'GET' },
          type: 'counter',
        })
      );
    });

    it('should return 0 for non-existent counter', () => {
      expect(collector.getCounter('non_existent')).toBe(0);
    });

    it('should handle counter without labels', () => {
      collector.incrementCounter('simple_counter');

      expect(collector.getCounter('simple_counter')).toBe(1);
    });

    it('should differentiate counters with different label values', () => {
      collector.incrementCounter('requests', 1, { status: '200' });
      collector.incrementCounter('requests', 1, { status: '404' });
      collector.incrementCounter('requests', 1, { status: '500' });

      expect(collector.getCounter('requests', { status: '200' })).toBe(1);
      expect(collector.getCounter('requests', { status: '404' })).toBe(1);
      expect(collector.getCounter('requests', { status: '500' })).toBe(1);
    });

    it('should handle labels in consistent order', () => {
      collector.incrementCounter('metric', 1, { b: 'beta', a: 'alpha' });
      collector.incrementCounter('metric', 2, { a: 'alpha', b: 'beta' });

      expect(collector.getCounter('metric', { a: 'alpha', b: 'beta' })).toBe(3);
    });
  });

  describe('Gauge Metrics', () => {
    it('should create and set gauge', () => {
      collector.setGauge('memory_usage', 512);

      expect(collector.getGauge('memory_usage')).toBe(512);
    });

    it('should overwrite previous gauge value', () => {
      collector.setGauge('cpu_usage', 25);
      collector.setGauge('cpu_usage', 75);

      expect(collector.getGauge('cpu_usage')).toBe(75);
    });

    it('should support gauge with labels', () => {
      collector.setGauge('queue_size', 100, { queue: 'high_priority' });
      collector.setGauge('queue_size', 50, { queue: 'low_priority' });

      expect(collector.getGauge('queue_size', { queue: 'high_priority' })).toBe(100);
      expect(collector.getGauge('queue_size', { queue: 'low_priority' })).toBe(50);
    });

    it('should update labeled gauge independently', () => {
      const labels = { instance: 'server-1' };

      collector.setGauge('connections', 10, labels);
      collector.setGauge('connections', 20, labels);

      expect(collector.getGauge('connections', labels)).toBe(20);
    });

    it('should emit metric event on gauge set', () => {
      const metricListener = jest.fn();
      collector.on('metric', metricListener);

      collector.setGauge('temperature', 72, { sensor: 'room1' });

      expect(metricListener).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'temperature',
          value: 72,
          timestamp: expect.any(Number),
          labels: { sensor: 'room1' },
          type: 'gauge',
        })
      );
    });

    it('should return 0 for non-existent gauge', () => {
      expect(collector.getGauge('non_existent')).toBe(0);
    });

    it('should handle negative gauge values', () => {
      collector.setGauge('balance', -50);

      expect(collector.getGauge('balance')).toBe(-50);
    });

    it('should handle floating point gauge values', () => {
      collector.setGauge('percentage', 99.99);

      expect(collector.getGauge('percentage')).toBe(99.99);
    });
  });

  describe('Histogram Metrics', () => {
    it('should observe histogram values', () => {
      collector.observeHistogram('request_duration', 100);
      collector.observeHistogram('request_duration', 200);
      collector.observeHistogram('request_duration', 300);

      const stats = collector.getHistogramStats('request_duration');

      expect(stats).toMatchObject({
        count: 3,
        sum: 600,
        min: 100,
        max: 300,
        mean: 200,
      });
    });

    it('should calculate histogram percentiles', () => {
      const values = [10, 20, 30, 40, 50, 60, 70, 80, 90, 100];

      values.forEach(v => collector.observeHistogram('latency', v));

      const stats = collector.getHistogramStats('latency');

      expect(stats).toMatchObject({
        count: 10,
        p50: 55,
        p95: 97.5,
        p99: 99.5,
      });
    });

    it('should support histogram with labels', () => {
      collector.observeHistogram('response_time', 100, { endpoint: '/api/users' });
      collector.observeHistogram('response_time', 150, { endpoint: '/api/users' });
      collector.observeHistogram('response_time', 200, { endpoint: '/api/posts' });

      const usersStats = collector.getHistogramStats('response_time', { endpoint: '/api/users' });
      const postsStats = collector.getHistogramStats('response_time', { endpoint: '/api/posts' });

      expect(usersStats?.count).toBe(2);
      expect(postsStats?.count).toBe(1);
    });

    it('should emit metric event on histogram observation', () => {
      const metricListener = jest.fn();
      collector.on('metric', metricListener);

      collector.observeHistogram('latency', 42, { service: 'api' });

      expect(metricListener).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'latency',
          value: 42,
          timestamp: expect.any(Number),
          labels: { service: 'api' },
          type: 'histogram',
        })
      );
    });

    it('should return null for non-existent histogram', () => {
      expect(collector.getHistogramStats('non_existent')).toBeNull();
    });

    it('should handle single observation histogram', () => {
      collector.observeHistogram('single_value', 42);

      const stats = collector.getHistogramStats('single_value');

      expect(stats).toMatchObject({
        count: 1,
        sum: 42,
        min: 42,
        max: 42,
        mean: 42,
        p50: 42,
        p95: 42,
        p99: 42,
      });
    });

    it('should calculate percentiles correctly for edge cases', () => {
      // All same values
      for (let i = 0; i < 10; i++) {
        collector.observeHistogram('uniform', 50);
      }

      const stats = collector.getHistogramStats('uniform');

      expect(stats?.p50).toBe(50);
      expect(stats?.p95).toBe(50);
      expect(stats?.p99).toBe(50);
    });

    it('should handle histogram with floating point values', () => {
      collector.observeHistogram('precise', 1.5);
      collector.observeHistogram('precise', 2.7);
      collector.observeHistogram('precise', 3.9);

      const stats = collector.getHistogramStats('precise');

      expect(stats?.mean).toBeCloseTo(2.7, 1);
    });

    it('should accumulate many observations', () => {
      for (let i = 1; i <= 1000; i++) {
        collector.observeHistogram('large_sample', i);
      }

      const stats = collector.getHistogramStats('large_sample');

      expect(stats?.count).toBe(1000);
      expect(stats?.sum).toBe(500500);
      expect(stats?.mean).toBe(500.5);
    });
  });

  describe('Summary Metrics', () => {
    it('should observe summary values', () => {
      collector.observeSummary('batch_size', 10);
      collector.observeSummary('batch_size', 20);
      collector.observeSummary('batch_size', 30);

      const stats = collector.getSummaryStats('batch_size');

      expect(stats).toMatchObject({
        count: 3,
        sum: 60,
        min: 10,
        max: 30,
        mean: 20,
      });
    });

    it('should support summary with labels', () => {
      collector.observeSummary('processing_time', 100, { pipeline: 'orders' });
      collector.observeSummary('processing_time', 150, { pipeline: 'orders' });
      collector.observeSummary('processing_time', 200, { pipeline: 'inventory' });

      const ordersStats = collector.getSummaryStats('processing_time', { pipeline: 'orders' });
      const inventoryStats = collector.getSummaryStats('processing_time', { pipeline: 'inventory' });

      expect(ordersStats?.count).toBe(2);
      expect(ordersStats?.mean).toBe(125);
      expect(inventoryStats?.count).toBe(1);
    });

    it('should emit metric event on summary observation', () => {
      const metricListener = jest.fn();
      collector.on('metric', metricListener);

      collector.observeSummary('duration', 123, { operation: 'db_query' });

      expect(metricListener).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'duration',
          value: 123,
          timestamp: expect.any(Number),
          labels: { operation: 'db_query' },
          type: 'summary',
        })
      );
    });

    it('should return null for non-existent summary', () => {
      expect(collector.getSummaryStats('non_existent')).toBeNull();
    });

    it('should handle single observation summary', () => {
      collector.observeSummary('single', 99);

      const stats = collector.getSummaryStats('single');

      expect(stats).toMatchObject({
        count: 1,
        sum: 99,
        min: 99,
        max: 99,
        mean: 99,
      });
    });

    it('should calculate statistics for negative values', () => {
      collector.observeSummary('delta', -10);
      collector.observeSummary('delta', 0);
      collector.observeSummary('delta', 10);

      const stats = collector.getSummaryStats('delta');

      expect(stats).toMatchObject({
        count: 3,
        sum: 0,
        min: -10,
        max: 10,
        mean: 0,
      });
    });
  });

  describe('Metric Snapshot', () => {
    beforeEach(() => {
      collector.incrementCounter('requests_total', 100);
      collector.incrementCounter('errors_total', 5, { type: 'timeout' });
      collector.setGauge('memory_bytes', 1024);
      collector.setGauge('cpu_percent', 75, { core: '0' });
      collector.observeHistogram('latency_ms', 10);
      collector.observeHistogram('latency_ms', 20);
      collector.observeHistogram('latency_ms', 30);
      collector.observeSummary('batch_size', 100);
      collector.observeSummary('batch_size', 200);
    });

    it('should generate snapshot of all metrics', () => {
      const snapshot = collector.getSnapshot();

      expect(snapshot.length).toBeGreaterThan(0);
      expect(snapshot).toEqual(expect.arrayContaining([
        expect.objectContaining({
          name: 'requests_total',
          type: 'counter',
          value: 100,
        }),
      ]));
    });

    it('should include counters in snapshot', () => {
      const snapshot = collector.getSnapshot();
      const counterMetrics = snapshot.filter(m => m.type === 'counter');

      expect(counterMetrics).toContainEqual(
        expect.objectContaining({
          name: 'requests_total',
          value: 100,
        })
      );
    });

    it('should include gauges in snapshot', () => {
      const snapshot = collector.getSnapshot();
      const gaugeMetrics = snapshot.filter(m => m.type === 'gauge');

      expect(gaugeMetrics).toContainEqual(
        expect.objectContaining({
          name: 'memory_bytes',
          value: 1024,
        })
      );
    });

    it('should include histogram statistics in snapshot', () => {
      const snapshot = collector.getSnapshot();
      const histogramMetrics = snapshot.filter(
        m => m.name.startsWith('latency_ms') && m.type === 'histogram'
      );

      expect(histogramMetrics).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ name: 'latency_ms_count', value: 3 }),
          expect.objectContaining({ name: 'latency_ms_sum', value: 60 }),
          expect.objectContaining({ name: 'latency_ms_p50' }),
          expect.objectContaining({ name: 'latency_ms_p95' }),
          expect.objectContaining({ name: 'latency_ms_p99' }),
        ])
      );
    });

    it('should include summary statistics in snapshot', () => {
      const snapshot = collector.getSnapshot();
      const summaryMetrics = snapshot.filter(
        m => m.name.startsWith('batch_size') && m.type === 'summary'
      );

      expect(summaryMetrics).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ name: 'batch_size_count', value: 2 }),
          expect.objectContaining({ name: 'batch_size_sum', value: 300 }),
        ])
      );
    });

    it('should include labels in snapshot', () => {
      const snapshot = collector.getSnapshot();
      const labeledMetric = snapshot.find(
        m => m.name === 'errors_total' && m.labels.type === 'timeout'
      );

      expect(labeledMetric).toBeDefined();
      expect(labeledMetric?.labels).toEqual({ type: 'timeout' });
    });

    it('should include timestamp in snapshot', () => {
      const before = Date.now();
      const snapshot = collector.getSnapshot();
      const after = Date.now();

      expect(snapshot[0].timestamp).toBeGreaterThanOrEqual(before);
      expect(snapshot[0].timestamp).toBeLessThanOrEqual(after);
    });

    it('should use same timestamp for all metrics in snapshot', () => {
      const snapshot = collector.getSnapshot();
      const timestamps = snapshot.map(m => m.timestamp);
      const uniqueTimestamps = new Set(timestamps);

      expect(uniqueTimestamps.size).toBe(1);
    });

    it('should handle empty snapshot', () => {
      const emptyCollector = new MetricsCollector();
      const snapshot = emptyCollector.getSnapshot();

      expect(snapshot).toEqual([]);
    });
  });

  describe('Metric Reset', () => {
    beforeEach(() => {
      collector.incrementCounter('requests', 100);
      collector.setGauge('connections', 50);
      collector.observeHistogram('latency', 100);
      collector.observeSummary('batch', 200);
    });

    it('should reset all metrics', () => {
      collector.reset();

      expect(collector.getCounter('requests')).toBe(0);
      expect(collector.getGauge('connections')).toBe(0);
      expect(collector.getHistogramStats('latency')).toBeNull();
      expect(collector.getSummaryStats('batch')).toBeNull();
    });

    it('should emit reset event', () => {
      const resetListener = jest.fn();
      collector.on('reset', resetListener);

      collector.reset();

      expect(resetListener).toHaveBeenCalled();
    });

    it('should allow metrics after reset', () => {
      collector.reset();

      collector.incrementCounter('new_metric', 10);

      expect(collector.getCounter('new_metric')).toBe(10);
    });

    it('should reset specific metric by name', () => {
      collector.resetMetric('requests');

      expect(collector.getCounter('requests')).toBe(0);
      expect(collector.getGauge('connections')).toBe(50);
    });

    it('should reset specific labeled metric', () => {
      collector.incrementCounter('api_calls', 100, { endpoint: '/users' });
      collector.incrementCounter('api_calls', 50, { endpoint: '/posts' });

      collector.resetMetric('api_calls', { endpoint: '/users' });

      expect(collector.getCounter('api_calls', { endpoint: '/users' })).toBe(0);
      expect(collector.getCounter('api_calls', { endpoint: '/posts' })).toBe(50);
    });

    it('should reset all metric types for given name', () => {
      const labels = { instance: 'server-1' };

      collector.incrementCounter('metric', 100, labels);
      collector.setGauge('metric', 50, labels);
      collector.observeHistogram('metric', 200, labels);
      collector.observeSummary('metric', 300, labels);

      collector.resetMetric('metric', labels);

      expect(collector.getCounter('metric', labels)).toBe(0);
      expect(collector.getGauge('metric', labels)).toBe(0);
      expect(collector.getHistogramStats('metric', labels)).toBeNull();
      expect(collector.getSummaryStats('metric', labels)).toBeNull();
    });
  });

  describe('Concurrent Operations', () => {
    it('should handle concurrent counter increments', () => {
      const promises = [];

      for (let i = 0; i < 100; i++) {
        promises.push(
          Promise.resolve().then(() => {
            collector.incrementCounter('concurrent_counter');
          })
        );
      }

      return Promise.all(promises).then(() => {
        expect(collector.getCounter('concurrent_counter')).toBe(100);
      });
    });

    it('should handle concurrent gauge updates', () => {
      const promises = [];

      for (let i = 0; i < 100; i++) {
        promises.push(
          Promise.resolve().then(() => {
            collector.setGauge('concurrent_gauge', i);
          })
        );
      }

      return Promise.all(promises).then(() => {
        const value = collector.getGauge('concurrent_gauge');
        expect(value).toBeGreaterThanOrEqual(0);
        expect(value).toBeLessThan(100);
      });
    });

    it('should handle concurrent histogram observations', () => {
      const promises = [];

      for (let i = 1; i <= 100; i++) {
        promises.push(
          Promise.resolve().then(() => {
            collector.observeHistogram('concurrent_histogram', i);
          })
        );
      }

      return Promise.all(promises).then(() => {
        const stats = collector.getHistogramStats('concurrent_histogram');
        expect(stats?.count).toBe(100);
      });
    });

    it('should handle mixed concurrent operations', () => {
      const promises = [];

      for (let i = 0; i < 50; i++) {
        promises.push(
          Promise.resolve().then(() => {
            collector.incrementCounter('mixed_counter');
            collector.setGauge('mixed_gauge', i);
            collector.observeHistogram('mixed_histogram', i);
            collector.observeSummary('mixed_summary', i);
          })
        );
      }

      return Promise.all(promises).then(() => {
        expect(collector.getCounter('mixed_counter')).toBe(50);
        expect(collector.getHistogramStats('mixed_histogram')?.count).toBe(50);
        expect(collector.getSummaryStats('mixed_summary')?.count).toBe(50);
      });
    });
  });

  describe('Label Handling', () => {
    it('should handle empty labels object', () => {
      collector.incrementCounter('metric', 1, {});

      expect(collector.getCounter('metric')).toBe(1);
      expect(collector.getCounter('metric', {})).toBe(1);
    });

    it('should differentiate between no labels and empty labels', () => {
      collector.incrementCounter('metric', 1);
      collector.incrementCounter('metric', 2, {});

      // Both should map to the same key
      expect(collector.getCounter('metric')).toBe(3);
    });

    it('should handle special characters in label values', () => {
      const labels = { path: '/api/users?id=123&name=test' };

      collector.incrementCounter('requests', 1, labels);

      expect(collector.getCounter('requests', labels)).toBe(1);
    });

    it('should handle Unicode in label values', () => {
      const labels = { name: '测试', emoji: '🚀' };

      collector.incrementCounter('requests', 1, labels);

      expect(collector.getCounter('requests', labels)).toBe(1);
    });

    it('should sort label keys consistently', () => {
      collector.incrementCounter('metric', 1, { z: '3', a: '1', m: '2' });
      collector.incrementCounter('metric', 2, { a: '1', m: '2', z: '3' });

      expect(collector.getCounter('metric', { a: '1', m: '2', z: '3' })).toBe(3);
    });

    it('should handle many label dimensions', () => {
      const labels = {
        label1: 'value1',
        label2: 'value2',
        label3: 'value3',
        label4: 'value4',
        label5: 'value5',
      };

      collector.incrementCounter('high_cardinality', 1, labels);

      expect(collector.getCounter('high_cardinality', labels)).toBe(1);
    });
  });

  describe('Edge Cases', () => {
    it('should handle very large counter values', () => {
      collector.incrementCounter('large_counter', Number.MAX_SAFE_INTEGER);

      expect(collector.getCounter('large_counter')).toBe(Number.MAX_SAFE_INTEGER);
    });

    it('should handle zero values', () => {
      collector.incrementCounter('zero_counter', 0);
      collector.setGauge('zero_gauge', 0);
      collector.observeHistogram('zero_histogram', 0);
      collector.observeSummary('zero_summary', 0);

      expect(collector.getCounter('zero_counter')).toBe(0);
      expect(collector.getGauge('zero_gauge')).toBe(0);
      expect(collector.getHistogramStats('zero_histogram')?.mean).toBe(0);
      expect(collector.getSummaryStats('zero_summary')?.mean).toBe(0);
    });

    it('should handle negative counter increments', () => {
      collector.incrementCounter('counter', 10);
      collector.incrementCounter('counter', -5);

      expect(collector.getCounter('counter')).toBe(5);
    });

    it('should handle very small floating point values', () => {
      collector.observeHistogram('tiny', 0.0001);
      collector.observeHistogram('tiny', 0.0002);

      const stats = collector.getHistogramStats('tiny');

      expect(stats?.mean).toBeCloseTo(0.00015, 5);
    });

    it('should handle metrics with same name but different labels', () => {
      collector.incrementCounter('http_requests', 100, { method: 'GET' });
      collector.setGauge('http_requests', 50, { method: 'POST' });

      const snapshot = collector.getSnapshot();
      const httpMetrics = snapshot.filter(m => m.name === 'http_requests');

      expect(httpMetrics).toHaveLength(2);
    });
  });

  describe('Event Emission', () => {
    it('should emit events for all metric types', () => {
      const metricListener = jest.fn();
      collector.on('metric', metricListener);

      collector.incrementCounter('counter', 1);
      collector.setGauge('gauge', 2);
      collector.observeHistogram('histogram', 3);
      collector.observeSummary('summary', 4);

      expect(metricListener).toHaveBeenCalledTimes(4);
      expect(metricListener).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'counter' })
      );
      expect(metricListener).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'gauge' })
      );
      expect(metricListener).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'histogram' })
      );
      expect(metricListener).toHaveBeenCalledWith(
        expect.objectContaining({ type: 'summary' })
      );
    });

    it('should include accurate timestamps in events', () => {
      const metricListener = jest.fn();
      collector.on('metric', metricListener);

      const before = Date.now();
      collector.incrementCounter('counter', 1);
      const after = Date.now();

      const event = metricListener.mock.calls[0][0] as Metric;

      expect(event.timestamp).toBeGreaterThanOrEqual(before);
      expect(event.timestamp).toBeLessThanOrEqual(after);
    });

    it('should support multiple event listeners', () => {
      const listener1 = jest.fn();
      const listener2 = jest.fn();
      const listener3 = jest.fn();

      collector.on('metric', listener1);
      collector.on('metric', listener2);
      collector.on('metric', listener3);

      collector.incrementCounter('counter', 1);

      expect(listener1).toHaveBeenCalled();
      expect(listener2).toHaveBeenCalled();
      expect(listener3).toHaveBeenCalled();
    });
  });

  describe('Percentile Calculation', () => {
    it('should calculate exact percentiles for small datasets', () => {
      const values = [1, 2, 3, 4, 5];
      values.forEach(v => collector.observeHistogram('test', v));

      const stats = collector.getHistogramStats('test');

      expect(stats?.p50).toBe(3);
    });

    it('should interpolate percentiles correctly', () => {
      const values = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
      values.forEach(v => collector.observeHistogram('test', v));

      const stats = collector.getHistogramStats('test');

      // p50 should be between 5 and 6
      expect(stats?.p50).toBeGreaterThan(5);
      expect(stats?.p50).toBeLessThan(6);

      // p95 should be between 9 and 10
      expect(stats?.p95).toBeGreaterThan(9);
      expect(stats?.p95).toBeLessThanOrEqual(10);
    });

    it('should handle percentiles at boundaries', () => {
      const values = [1, 2, 3];
      values.forEach(v => collector.observeHistogram('test', v));

      const stats = collector.getHistogramStats('test');

      expect(stats?.p99).toBe(3);
    });
  });

  describe('Memory and Performance', () => {
    it('should handle large number of unique label combinations', () => {
      for (let i = 0; i < 1000; i++) {
        collector.incrementCounter('metric', 1, { id: `id-${i}` });
      }

      const snapshot = collector.getSnapshot();
      const metricCount = snapshot.filter(m => m.name === 'metric').length;

      expect(metricCount).toBe(1000);
    });

    it('should handle large histogram datasets efficiently', () => {
      const startTime = Date.now();

      for (let i = 0; i < 10000; i++) {
        collector.observeHistogram('large_histogram', Math.random() * 1000);
      }

      const stats = collector.getHistogramStats('large_histogram');
      const endTime = Date.now();

      expect(stats?.count).toBe(10000);
      expect(endTime - startTime).toBeLessThan(1000); // Should complete in less than 1 second
    });

    it('should generate snapshot efficiently', () => {
      // Create many metrics
      for (let i = 0; i < 100; i++) {
        collector.incrementCounter(`counter_${i}`, i);
        collector.setGauge(`gauge_${i}`, i);
        collector.observeHistogram(`histogram_${i}`, i);
        collector.observeSummary(`summary_${i}`, i);
      }

      const startTime = Date.now();
      const snapshot = collector.getSnapshot();
      const endTime = Date.now();

      expect(snapshot.length).toBeGreaterThan(0);
      expect(endTime - startTime).toBeLessThan(100); // Should be very fast
    });
  });
});
