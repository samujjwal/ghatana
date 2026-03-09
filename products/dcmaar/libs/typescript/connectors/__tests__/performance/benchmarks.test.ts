import { performance } from 'perf_hooks';
import { ConnectionPool } from '../../src/pooling/ConnectionPool';
import { BatchProcessor } from '../../src/batching/BatchProcessor';
import { CircuitBreaker } from '../../src/resilience/CircuitBreaker';
import { RateLimiter } from '../../src/security/RateLimiter';
import { MetricsCollector } from '../../src/monitoring/MetricsCollector';

interface BenchmarkResult {
  name: string;
  operations: number;
  duration: number;
  opsPerSecond: number;
  avgLatency: number;
  p50: number;
  p95: number;
  p99: number;
}

function calculatePercentile(values: number[], percentile: number): number {
  const sorted = [...values].sort((a, b) => a - b);
  const index = Math.ceil((percentile / 100) * sorted.length) - 1;
  return sorted[index];
}

async function runBenchmark(
  name: string,
  operations: number,
  fn: () => Promise<void>
): Promise<BenchmarkResult> {
  const latencies: number[] = [];
  const startTime = performance.now();

  for (let i = 0; i < operations; i++) {
    const opStart = performance.now();
    await fn();
    const opEnd = performance.now();
    latencies.push(opEnd - opStart);
  }

  const endTime = performance.now();
  const duration = endTime - startTime;

  return {
    name,
    operations,
    duration,
    opsPerSecond: (operations / duration) * 1000,
    avgLatency: latencies.reduce((a, b) => a + b, 0) / latencies.length,
    p50: calculatePercentile(latencies, 50),
    p95: calculatePercentile(latencies, 95),
    p99: calculatePercentile(latencies, 99),
  };
}

function printBenchmarkResult(result: BenchmarkResult): void {
  console.log(`\n${'='.repeat(60)}`);
  console.log(`Benchmark: ${result.name}`);
  console.log(`${'='.repeat(60)}`);
  console.log(`Operations:     ${result.operations.toLocaleString()}`);
  console.log(`Duration:       ${result.duration.toFixed(2)}ms`);
  console.log(`Ops/sec:        ${result.opsPerSecond.toFixed(2)}`);
  console.log(`Avg Latency:    ${result.avgLatency.toFixed(3)}ms`);
  console.log(`P50 Latency:    ${result.p50.toFixed(3)}ms`);
  console.log(`P95 Latency:    ${result.p95.toFixed(3)}ms`);
  console.log(`P99 Latency:    ${result.p99.toFixed(3)}ms`);
  console.log(`${'='.repeat(60)}\n`);
}

describe('Performance Benchmarks', () => {
  // Set longer timeout for benchmarks
  jest.setTimeout(60000);

  describe('ConnectionPool Benchmarks', () => {
    it('should benchmark connection pool acquire/release', async () => {
      interface MockConnection {
        id: number;
        query: () => Promise<void>;
      }

      let idCounter = 0;
      const pool = new ConnectionPool<MockConnection>({
        min: 10,
        max: 50,
        create: async () => ({
          id: ++idCounter,
          query: async () => {
            await new Promise(resolve => setTimeout(resolve, 1));
          },
        }),
      });

      // Warm up
      await new Promise(resolve => setTimeout(resolve, 100));

      const result = await runBenchmark(
        'ConnectionPool - Acquire/Release (10k ops)',
        10000,
        async () => {
          const conn = await pool.acquire();
          await pool.release(conn);
        }
      );

      printBenchmarkResult(result);

      // Performance assertions
      expect(result.opsPerSecond).toBeGreaterThan(1000); // At least 1k ops/sec
      expect(result.p95).toBeLessThan(50); // P95 latency < 50ms

      await pool.destroy();
    });

    it('should benchmark connection pool with concurrent operations', async () => {
      interface MockConnection {
        id: number;
        query: () => Promise<void>;
      }

      let idCounter = 0;
      const pool = new ConnectionPool<MockConnection>({
        min: 10,
        max: 50,
        create: async () => ({
          id: ++idCounter,
          query: async () => {
            await new Promise(resolve => setTimeout(resolve, 1));
          },
        }),
      });

      const concurrency = 100;
      const opsPerWorker = 100;

      const startTime = performance.now();

      await Promise.all(
        Array.from({ length: concurrency }, async () => {
          for (let i = 0; i < opsPerWorker; i++) {
            await pool.use(async (conn) => {
              await conn.query();
            });
          }
        })
      );

      const endTime = performance.now();
      const duration = endTime - startTime;
      const totalOps = concurrency * opsPerWorker;

      const result = {
        name: 'ConnectionPool - Concurrent Operations',
        operations: totalOps,
        duration,
        opsPerSecond: (totalOps / duration) * 1000,
        avgLatency: duration / totalOps,
        p50: 0,
        p95: 0,
        p99: 0,
      };

      printBenchmarkResult(result);

      expect(result.opsPerSecond).toBeGreaterThan(500);

      await pool.destroy();
    });
  });

  describe('BatchProcessor Benchmarks', () => {
    it('should benchmark batch processing throughput', async () => {
      const processedBatches: number[] = [];

      const batcher = new BatchProcessor({
        maxBatchSize: 100,
        maxWaitTime: 10,
        processBatch: async (items) => {
          processedBatches.push(items.length);
          return items;
        },
      });

      const result = await runBenchmark(
        'BatchProcessor - Throughput (10k items)',
        10000,
        async () => {
          await batcher.add({ data: 'test' });
        }
      );

      await batcher.flush();

      printBenchmarkResult(result);

      console.log(`Total batches processed: ${processedBatches.length}`);
      console.log(`Avg batch size: ${(10000 / processedBatches.length).toFixed(2)}`);

      expect(result.opsPerSecond).toBeGreaterThan(5000);

      await batcher.destroy();
    });

    it('should benchmark batch processing with concurrent adds', async () => {
      const batcher = new BatchProcessor({
        maxBatchSize: 100,
        maxWaitTime: 10,
        concurrency: 5,
        processBatch: async (items) => {
          await new Promise(resolve => setTimeout(resolve, 5));
          return items;
        },
      });

      const concurrency = 50;
      const itemsPerWorker = 200;

      const startTime = performance.now();

      await Promise.all(
        Array.from({ length: concurrency }, async () => {
          for (let i = 0; i < itemsPerWorker; i++) {
            await batcher.add({ data: `item-${i}` });
          }
        })
      );

      await batcher.flush();

      const endTime = performance.now();
      const duration = endTime - startTime;
      const totalItems = concurrency * itemsPerWorker;

      const result = {
        name: 'BatchProcessor - Concurrent Adds',
        operations: totalItems,
        duration,
        opsPerSecond: (totalItems / duration) * 1000,
        avgLatency: duration / totalItems,
        p50: 0,
        p95: 0,
        p99: 0,
      };

      printBenchmarkResult(result);

      expect(result.opsPerSecond).toBeGreaterThan(1000);

      await batcher.destroy();
    });
  });

  describe('CircuitBreaker Benchmarks', () => {
    it('should benchmark circuit breaker overhead', async () => {
      const breaker = new CircuitBreaker({
        failureThreshold: 5,
        timeout: 60000,
      });

      let counter = 0;
      const result = await runBenchmark(
        'CircuitBreaker - Successful Operations (10k ops)',
        10000,
        async () => {
          await breaker.execute(async () => {
            counter++;
            return counter;
          });
        }
      );

      printBenchmarkResult(result);

      expect(result.opsPerSecond).toBeGreaterThan(10000); // Minimal overhead
      expect(result.avgLatency).toBeLessThan(1); // < 1ms overhead

      breaker.reset();
    });

    it('should benchmark circuit breaker with failures', async () => {
      const breaker = new CircuitBreaker({
        failureThreshold: 100,
        timeout: 60000,
        volumeThreshold: 50,
      });

      let successCount = 0;
      let failureCount = 0;

      const startTime = performance.now();

      for (let i = 0; i < 10000; i++) {
        try {
          await breaker.execute(async () => {
            if (Math.random() < 0.1) { // 10% failure rate
              throw new Error('Random failure');
            }
            return 'success';
          });
          successCount++;
        } catch {
          failureCount++;
        }
      }

      const endTime = performance.now();
      const duration = endTime - startTime;

      const result = {
        name: 'CircuitBreaker - With 10% Failures',
        operations: 10000,
        duration,
        opsPerSecond: (10000 / duration) * 1000,
        avgLatency: duration / 10000,
        p50: 0,
        p95: 0,
        p99: 0,
      };

      printBenchmarkResult(result);

      console.log(`Success: ${successCount}, Failures: ${failureCount}`);
      console.log(`Circuit breaker stats:`, breaker.getStats());

      breaker.reset();
    });
  });

  describe('RateLimiter Benchmarks', () => {
    it('should benchmark rate limiter - fixed window', async () => {
      const limiter = new RateLimiter({
        maxRequests: 100000,
        windowMs: 60000,
        strategy: 'fixed-window',
      });

      const result = await runBenchmark(
        'RateLimiter - Fixed Window (10k ops)',
        10000,
        async () => {
          await limiter.consume();
        }
      );

      printBenchmarkResult(result);

      expect(result.opsPerSecond).toBeGreaterThan(50000);

      limiter.destroy();
    });

    it('should benchmark rate limiter - sliding window', async () => {
      const limiter = new RateLimiter({
        maxRequests: 100000,
        windowMs: 60000,
        strategy: 'sliding-window',
      });

      const result = await runBenchmark(
        'RateLimiter - Sliding Window (10k ops)',
        10000,
        async () => {
          await limiter.consume();
        }
      );

      printBenchmarkResult(result);

      expect(result.opsPerSecond).toBeGreaterThan(20000);

      limiter.destroy();
    });

    it('should benchmark rate limiter - token bucket', async () => {
      const limiter = new RateLimiter({
        maxRequests: 100000,
        windowMs: 60000,
        strategy: 'token-bucket',
      });

      const result = await runBenchmark(
        'RateLimiter - Token Bucket (10k ops)',
        10000,
        async () => {
          await limiter.consume();
        }
      );

      printBenchmarkResult(result);

      expect(result.opsPerSecond).toBeGreaterThan(30000);

      limiter.destroy();
    });
  });

  describe('MetricsCollector Benchmarks', () => {
    it('should benchmark metrics collection', async () => {
      const metrics = new MetricsCollector();

      const result = await runBenchmark(
        'MetricsCollector - Counter Increments (100k ops)',
        100000,
        async () => {
          metrics.incrementCounter('test.counter', 1, {
            label1: 'value1',
            label2: 'value2',
          });
        }
      );

      printBenchmarkResult(result);

      expect(result.opsPerSecond).toBeGreaterThan(100000); // Very fast
      expect(result.avgLatency).toBeLessThan(0.1);

      metrics.reset();
    });

    it('should benchmark histogram observations', async () => {
      const metrics = new MetricsCollector();

      const result = await runBenchmark(
        'MetricsCollector - Histogram Observations (50k ops)',
        50000,
        async () => {
          metrics.observeHistogram('test.histogram', Math.random() * 100, {
            endpoint: '/api/test',
          });
        }
      );

      printBenchmarkResult(result);

      expect(result.opsPerSecond).toBeGreaterThan(50000);

      metrics.reset();
    });

    it('should benchmark snapshot generation', async () => {
      const metrics = new MetricsCollector();

      // Populate with data
      for (let i = 0; i < 1000; i++) {
        metrics.incrementCounter(`counter.${i % 10}`, 1);
        metrics.setGauge(`gauge.${i % 10}`, Math.random() * 100);
        metrics.observeHistogram(`histogram.${i % 10}`, Math.random() * 100);
      }

      const latencies: number[] = [];

      for (let i = 0; i < 100; i++) {
        const start = performance.now();
        const snapshot = metrics.getSnapshot();
        const end = performance.now();
        latencies.push(end - start);
        expect(snapshot.length).toBeGreaterThan(0);
      }

      const avgLatency = latencies.reduce((a, b) => a + b, 0) / latencies.length;

      console.log(`\nMetricsCollector - Snapshot Generation (100 iterations)`);
      console.log(`Avg Latency: ${avgLatency.toFixed(3)}ms`);
      console.log(`P95 Latency: ${calculatePercentile(latencies, 95).toFixed(3)}ms`);
      console.log(`P99 Latency: ${calculatePercentile(latencies, 99).toFixed(3)}ms`);

      expect(avgLatency).toBeLessThan(10); // < 10ms for snapshot

      metrics.reset();
    });
  });

  describe('Memory Benchmarks', () => {
    it('should measure memory usage of connection pool', async () => {
      const initialMemory = process.memoryUsage().heapUsed;

      const pool = new ConnectionPool({
        min: 100,
        max: 1000,
        create: async () => ({
          id: Math.random(),
          data: Buffer.alloc(1024), // 1KB per connection
          query: async () => {},
        }),
      });

      await new Promise(resolve => setTimeout(resolve, 500));

      const afterCreateMemory = process.memoryUsage().heapUsed;
      const memoryIncrease = (afterCreateMemory - initialMemory) / 1024 / 1024;

      console.log(`\nMemory Usage - ConnectionPool (100 connections)`);
      console.log(`Memory Increase: ${memoryIncrease.toFixed(2)} MB`);
      console.log(`Per Connection: ${(memoryIncrease / 100).toFixed(3)} MB`);

      expect(memoryIncrease).toBeLessThan(50); // < 50MB for 100 connections

      await pool.destroy();
    });

    it('should measure memory usage of metrics collector', async () => {
      const initialMemory = process.memoryUsage().heapUsed;

      const metrics = new MetricsCollector();

      // Create 10k metrics
      for (let i = 0; i < 10000; i++) {
        metrics.incrementCounter(`counter.${i}`, 1);
        metrics.setGauge(`gauge.${i}`, Math.random() * 100);
        metrics.observeHistogram(`histogram.${i}`, Math.random() * 100);
      }

      const afterMetricsMemory = process.memoryUsage().heapUsed;
      const memoryIncrease = (afterMetricsMemory - initialMemory) / 1024 / 1024;

      console.log(`\nMemory Usage - MetricsCollector (10k metrics)`);
      console.log(`Memory Increase: ${memoryIncrease.toFixed(2)} MB`);
      console.log(`Per Metric: ${(memoryIncrease / 10000 * 1024).toFixed(3)} KB`);

      expect(memoryIncrease).toBeLessThan(100); // < 100MB for 10k metrics

      metrics.reset();
    });
  });
});
