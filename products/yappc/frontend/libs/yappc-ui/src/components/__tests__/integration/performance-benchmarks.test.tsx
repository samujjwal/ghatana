// All tests skipped - incomplete feature
/**
 * Integration Tests: Performance Benchmarks
 *
 * Performance benchmarking for Phase 3 systems to establish baselines
 * and ensure systems meet performance requirements.
 */

import { renderHook, act, waitFor } from '@testing-library/react';
import { rest } from 'msw';
import { setupServer } from 'msw/node';

import { eventBus } from '../../core/event-bus';
import { useDataSource } from '../../hooks/useDataSource';
import { useForm } from '../../hooks/useForm';
import { validators } from '../../utils/validation';

// ============================================================================
// Mock Server Setup
// ============================================================================

const server = setupServer(
  rest.get('/api/users', (req, res, ctx) => {
    return res(
      ctx.json(
        Array.from({ length: 100 }, (_, i) => ({
          id: i + 1,
          name: `User ${i + 1}`,
          email: `user${i + 1}@example.com`,
        }))
      )
    );
  }),
  rest.get('/api/fast', (req, res, ctx) => {
    return res(ctx.json({ data: 'fast response' }));
  }),
  rest.get('/api/slow', (req, res, ctx) => {
    return res(ctx.delay(500), ctx.json({ data: 'slow response' }));
  })
);

beforeAll(() => server.listen());
afterEach(() => {
  server.resetHandlers();
  eventBus.removeAllListeners();
  eventBus.clearHistory();
});
afterAll(() => server.close());

// ============================================================================
// Performance Utilities
// ============================================================================

interface BenchmarkResult {
  name: string;
  duration: number;
  operations: number;
  opsPerSecond: number;
  averageTime: number;
}

function benchmark(
  name: string,
  fn: () => void | Promise<void>,
  iterations: number = 1000
): Promise<BenchmarkResult> {
  return new Promise(async (resolve) => {
    const start = performance.now();

    for (let i = 0; i < iterations; i++) {
      await fn();
    }

    const end = performance.now();
    const duration = end - start;

    resolve({
      name,
      duration,
      operations: iterations,
      opsPerSecond: (iterations / duration) * 1000,
      averageTime: duration / iterations,
    });
  });
}

function logBenchmark(result: BenchmarkResult) {
  console.log(`\n📊 ${result.name}`);
  console.log(`   Duration: ${result.duration.toFixed(2)}ms`);
  console.log(`   Operations: ${result.operations}`);
  console.log(`   Ops/sec: ${result.opsPerSecond.toFixed(2)}`);
  console.log(`   Avg time: ${result.averageTime.toFixed(4)}ms`);
}

// ============================================================================
// Performance Tests
// ============================================================================

describe('Performance Benchmarks', () => {
  describe('Event Bus Performance', () => {
    it('should emit events efficiently', async () => {
      const result = await benchmark(
        'Event Bus - Emit 1000 events',
        async () => {
          await eventBus.emit('test:event', { data: 'test' });
        },
        1000
      );

      logBenchmark(result);

      // Baseline: Should emit at least 10,000 events per second
      expect(result.opsPerSecond).toBeGreaterThan(10000);
    });

    it('should subscribe/unsubscribe efficiently', async () => {
      const result = await benchmark(
        'Event Bus - Subscribe/Unsubscribe 1000 times',
        () => {
          const subscription = eventBus.on('test:perf', () => {});
          subscription.unsubscribe();
        },
        1000
      );

      logBenchmark(result);

      // Baseline: Should handle at least 5,000 subscribe/unsubscribe per second
      expect(result.opsPerSecond).toBeGreaterThan(5000);
    });

    it('should handle multiple listeners efficiently', async () => {
      // Add 100 listeners
      const listeners = Array.from({ length: 100 }, () =>
        eventBus.on('test:multi', () => {})
      );

      const result = await benchmark(
        'Event Bus - Emit to 100 listeners',
        async () => {
          await eventBus.emit('test:multi', { data: 'test' });
        },
        100
      );

      logBenchmark(result);

      // Cleanup
      listeners.forEach((sub) => sub.unsubscribe());

      // Baseline: Should handle at least 1,000 emissions per second with 100 listeners
      expect(result.opsPerSecond).toBeGreaterThan(1000);
    });

    it('should handle middleware efficiently', async () => {
      // Add middleware
      const middleware1 = (
        eventName: string,
        payload: unknown,
        next: () => void
      ) => next();
      const middleware2 = (
        eventName: string,
        payload: unknown,
        next: () => void
      ) => next();
      const middleware3 = (
        eventName: string,
        payload: unknown,
        next: () => void
      ) => next();

      eventBus.use(middleware1);
      eventBus.use(middleware2);
      eventBus.use(middleware3);

      const result = await benchmark(
        'Event Bus - Emit through 3 middleware',
        async () => {
          await eventBus.emit('test:middleware', { data: 'test' });
        },
        1000
      );

      logBenchmark(result);

      // Cleanup
      eventBus.removeMiddleware(middleware1);
      eventBus.removeMiddleware(middleware2);
      eventBus.removeMiddleware(middleware3);

      // Baseline: Should handle at least 5,000 emissions per second with 3 middleware
      expect(result.opsPerSecond).toBeGreaterThan(5000);
    });
  });

  describe('DataSource Hook Performance', () => {
    it('should handle cache hits efficiently', async () => {
      const { result } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: '/api/fast',
          cache: true,
          cacheTTL: 60000,
        })
      );

      // Wait for initial fetch
      await waitFor(() => expect(result.current.isLoading).toBe(false));

      const start = performance.now();

      // Refetch 100 times (should use cache)
      for (let i = 0; i < 100; i++) {
        await result.current.refetch();
      }

      const end = performance.now();
      const duration = end - start;
      const opsPerSecond = (100 / duration) * 1000;

      console.log(`\n📊 DataSource - 100 cached refetches`);
      console.log(`   Duration: ${duration.toFixed(2)}ms`);
      console.log(`   Ops/sec: ${opsPerSecond.toFixed(2)}`);

      // Baseline: Should handle at least 1,000 cache hits per second
      expect(opsPerSecond).toBeGreaterThan(1000);
    });

    it('should deduplicate concurrent requests', async () => {
      let fetchCount = 0;

      server.use(
        rest.get('/api/dedupe-test', (req, res, ctx) => {
          fetchCount++;
          return res(ctx.delay(100), ctx.json({ data: 'test' }));
        })
      );

      const start = performance.now();

      // Create 10 concurrent requests
      const hooks = Array.from({ length: 10 }, () =>
        renderHook(() =>
          useDataSource({
            type: 'rest',
            url: '/api/dedupe-test',
            dedupe: true,
          })
        )
      );

      // Wait for all to complete
      await waitFor(() => {
        return hooks.every((hook) => !hook.result.current.isLoading);
      });

      const end = performance.now();
      const duration = end - start;

      console.log(`\n📊 DataSource - Request Deduplication`);
      console.log(`   Duration: ${duration.toFixed(2)}ms`);
      console.log(`   Fetch count: ${fetchCount}`);
      console.log(`   Requests made: 10`);

      // Should only make 1 actual fetch
      expect(fetchCount).toBe(1);

      // Should complete in reasonable time (< 200ms)
      expect(duration).toBeLessThan(200);
    });
  });

  describe('Form Validation Performance', () => {
    it('should validate simple fields efficiently', async () => {
      const { result } = renderHook(() =>
        useForm({
          initialValues: { email: '' },
          validationRules: {
            email: [validators.required(), validators.email()],
          },
          onSubmit: jest.fn(),
          validateOnChange: true,
        })
      );

      const validationResult = await benchmark(
        'Form Validation - 1000 email validations',
        () => {
          act(() => {
            result.current.setFieldValue('email', 'test@example.com');
          });
        },
        1000
      );

      logBenchmark(validationResult);

      // Baseline: Should validate at least 5,000 fields per second
      expect(validationResult.opsPerSecond).toBeGreaterThan(5000);
    });

    it('should validate complex forms efficiently', async () => {
      const { result } = renderHook(() =>
        useForm({
          initialValues: {
            username: '',
            email: '',
            password: '',
            confirmPassword: '',
            age: 0,
            phone: '',
            website: '',
          },
          validationRules: {
            username: [
              validators.required(),
              validators.minLength(3),
              validators.maxLength(20),
              validators.pattern(/^[a-zA-Z0-9_]+$/),
            ],
            email: [validators.required(), validators.email()],
            password: [validators.required(), validators.minLength(8)],
            confirmPassword: [
              validators.required(),
              validators.match(
                'password',
                (name) => result.current.values.password
              ),
            ],
            age: [
              validators.required(),
              validators.min(18),
              validators.max(120),
            ],
            phone: [validators.required(), validators.phone()],
            website: [validators.url()],
          },
          onSubmit: jest.fn(),
          validateOnChange: false,
        })
      );

      // Set valid values
      act(() => {
        result.current.setFieldValue('username', 'testuser');
        result.current.setFieldValue('email', 'test@example.com');
        result.current.setFieldValue('password', 'password123');
        result.current.setFieldValue('confirmPassword', 'password123');
        result.current.setFieldValue('age', 25);
        result.current.setFieldValue('phone', '1234567890');
        result.current.setFieldValue('website', 'https://example.com');
      });

      const validationResult = await benchmark(
        'Form Validation - 1000 full form validations',
        () => {
          result.current.validateForm();
        },
        1000
      );

      logBenchmark(validationResult);

      // Baseline: Should validate at least 1,000 complete forms per second
      expect(validationResult.opsPerSecond).toBeGreaterThan(1000);
    });
  });

  describe('Integrated System Performance', () => {
    it('should handle complete workflow efficiently', async () => {
      const workflowTimes: number[] = [];

      for (let i = 0; i < 10; i++) {
        const start = performance.now();

        const { result } = renderHook(() => {
          const dataSource = useDataSource({
            type: 'rest',
            url: '/api/fast',
            cache: false,
          });

          const form = useForm({
            initialValues: { name: '', email: '' },
            validationRules: {
              name: [validators.required()],
              email: [validators.required(), validators.email()],
            },
            onSubmit: async (values) => {
              await eventBus.emit('form:submit', { data: values });
            },
          });

          return { dataSource, form };
        });

        // Wait for data load
        await waitFor(() =>
          expect(result.current.dataSource.isLoading).toBe(false)
        );

        // Fill form
        act(() => {
          result.current.form.setFieldValue('name', 'Test User');
          result.current.form.setFieldValue('email', 'test@example.com');
        });

        // Validate
        act(() => {
          result.current.form.validateForm();
        });

        // Submit
        await act(async () => {
          const mockEvent = { preventDefault: jest.fn() } as unknown;
          await result.current.form.handleSubmit(mockEvent);
        });

        const end = performance.now();
        workflowTimes.push(end - start);
      }

      const avgTime =
        workflowTimes.reduce((a, b) => a + b, 0) / workflowTimes.length;
      const minTime = Math.min(...workflowTimes);
      const maxTime = Math.max(...workflowTimes);

      console.log(`\n📊 Complete Workflow - 10 iterations`);
      console.log(`   Avg time: ${avgTime.toFixed(2)}ms`);
      console.log(`   Min time: ${minTime.toFixed(2)}ms`);
      console.log(`   Max time: ${maxTime.toFixed(2)}ms`);

      // Baseline: Average workflow should complete in under 100ms
      expect(avgTime).toBeLessThan(100);
    });

    it('should handle concurrent workflows efficiently', async () => {
      const start = performance.now();

      // Create 20 concurrent workflows
      const workflows = Array.from({ length: 20 }, () =>
        renderHook(() => {
          const dataSource = useDataSource({
            type: 'rest',
            url: '/api/fast',
          });

          const form = useForm({
            initialValues: { email: '' },
            validationRules: {
              email: [validators.required(), validators.email()],
            },
            onSubmit: jest.fn(),
          });

          return { dataSource, form };
        })
      );

      // Wait for all data sources to load
      await waitFor(() => {
        return workflows.every((w) => !w.result.current.dataSource.isLoading);
      });

      const end = performance.now();
      const duration = end - start;

      console.log(`\n📊 Concurrent Workflows - 20 parallel workflows`);
      console.log(`   Duration: ${duration.toFixed(2)}ms`);
      console.log(`   Time per workflow: ${(duration / 20).toFixed(2)}ms`);

      // Baseline: Should handle 20 concurrent workflows in under 500ms
      expect(duration).toBeLessThan(500);
    });
  });

  describe('Memory Performance', () => {
    it('should not leak memory with event subscriptions', () => {
      const initialListenerCount = eventBus.listenerCount('memory:test');

      // Create and cleanup 100 subscriptions
      for (let i = 0; i < 100; i++) {
        const subscription = eventBus.on('memory:test', () => {});
        subscription.unsubscribe();
      }

      const finalListenerCount = eventBus.listenerCount('memory:test');

      // Should have same number of listeners as start
      expect(finalListenerCount).toBe(initialListenerCount);
    });

    it('should clear cache efficiently', async () => {
      const { result } = renderHook(() =>
        useDataSource({
          type: 'rest',
          url: '/api/fast',
          cache: true,
        })
      );

      await waitFor(() => expect(result.current.isLoading).toBe(false));

      const start = performance.now();

      // Clear cache 1000 times
      for (let i = 0; i < 1000; i++) {
        result.current.clearCache();
      }

      const end = performance.now();
      const duration = end - start;
      const opsPerSecond = (1000 / duration) * 1000;

      console.log(`\n📊 Cache Clear - 1000 operations`);
      console.log(`   Duration: ${duration.toFixed(2)}ms`);
      console.log(`   Ops/sec: ${opsPerSecond.toFixed(2)}`);

      // Should handle at least 10,000 cache clears per second
      expect(opsPerSecond).toBeGreaterThan(10000);
    });
  });

  describe('Canvas Rendering Performance', () => {
    it('should render simple canvas efficiently', async () => {
      const canvas = document.createElement('canvas');
      canvas.width = 800;
      canvas.height = 600;
      const ctx = canvas.getContext('2d');

      if (!ctx) {
        throw new Error('Could not get canvas context');
      }

      const result = await benchmark(
        'Canvas - Render simple shapes',
        () => {
          ctx.clearRect(0, 0, 800, 600);
          ctx.fillStyle = '#ffffff';
          ctx.fillRect(0, 0, 800, 600);
          ctx.fillStyle = '#3b82f6';
          ctx.fillRect(100, 100, 200, 150);
          ctx.strokeStyle = '#ef4444';
          ctx.lineWidth = 2;
          ctx.strokeRect(350, 100, 200, 150);
          ctx.fillStyle = '#10b981';
          ctx.beginPath();
          ctx.arc(600, 175, 75, 0, Math.PI * 2);
          ctx.fill();
        },
        100
      );

      logBenchmark(result);

      // Baseline: Should render at least 60 frames per second (1000ms / 60 = ~16.67ms per frame)
      expect(result.averageTime).toBeLessThan(20);
    });

    it('should render complex canvas with multiple nodes efficiently', async () => {
      const canvas = document.createElement('canvas');
      canvas.width = 1200;
      canvas.height = 800;
      const ctx = canvas.getContext('2d');

      if (!ctx) {
        throw new Error('Could not get canvas context');
      }

      // Simulate rendering workflow nodes
      const nodes = Array.from({ length: 50 }, (_, i) => ({
        x: 50 + (i % 10) * 110,
        y: 50 + Math.floor(i / 10) * 120,
        width: 100,
        height: 100,
        color: `hsl(${(i * 7) % 360}, 70%, 60%)`,
      }));

      const result = await benchmark(
        'Canvas - Render 50 workflow nodes',
        () => {
          ctx.clearRect(0, 0, 1200, 800);
          ctx.fillStyle = '#f8fafc';
          ctx.fillRect(0, 0, 1200, 800);

          // Draw grid
          ctx.strokeStyle = '#e2e8f0';
          ctx.lineWidth = 1;
          for (let x = 0; x < 1200; x += 50) {
            ctx.beginPath();
            ctx.moveTo(x, 0);
            ctx.lineTo(x, 800);
            ctx.stroke();
          }
          for (let y = 0; y < 800; y += 50) {
            ctx.beginPath();
            ctx.moveTo(0, y);
            ctx.lineTo(1200, y);
            ctx.stroke();
          }

          // Draw nodes
          nodes.forEach((node) => {
            ctx.fillStyle = node.color;
            ctx.fillRect(node.x, node.y, node.width, node.height);
            ctx.strokeStyle = '#1e293b';
            ctx.lineWidth = 2;
            ctx.strokeRect(node.x, node.y, node.width, node.height);
            
            // Draw node label
            ctx.fillStyle = '#1e293b';
            ctx.font = '12px sans-serif';
            ctx.fillText(`Node ${nodes.indexOf(node) + 1}`, node.x + 10, node.y + 20);
          });

          // Draw connections (simplified)
          ctx.strokeStyle = '#64748b';
          ctx.lineWidth = 2;
          for (let i = 0; i < nodes.length - 1; i++) {
            ctx.beginPath();
            ctx.moveTo(nodes[i].x + nodes[i].width / 2, nodes[i].y + nodes[i].height);
            ctx.lineTo(nodes[i + 1].x + nodes[i + 1].width / 2, nodes[i + 1].y);
            ctx.stroke();
          }
        },
        50
      );

      logBenchmark(result);

      // Baseline: Should render complex canvas at least 30 frames per second
      expect(result.averageTime).toBeLessThan(33);
    });

    it('should handle canvas zoom and pan efficiently', async () => {
      const canvas = document.createElement('canvas');
      canvas.width = 1200;
      canvas.height = 800;
      const ctx = canvas.getContext('2d');

      if (!ctx) {
        throw new Error('Could not get canvas context');
      }

      let zoom = 1;
      let panX = 0;
      let panY = 0;

      const result = await benchmark(
        'Canvas - Zoom and pan operations',
        () => {
          ctx.save();
          ctx.clearRect(0, 0, 1200, 800);
          ctx.translate(panX, panY);
          ctx.scale(zoom, zoom);
          
          // Draw content
          ctx.fillStyle = '#ffffff';
          ctx.fillRect(0, 0, 800, 600);
          ctx.fillStyle = '#3b82f6';
          ctx.fillRect(100, 100, 200, 150);
          
          ctx.restore();
          
          // Update zoom/pan for next iteration
          zoom = 0.8 + (zoom % 3) * 0.2;
          panX = (panX + 10) % 100;
          panY = (panY + 5) % 50;
        },
        100
      );

      logBenchmark(result);

      // Baseline: Should handle zoom/pan at least 30 frames per second
      expect(result.averageTime).toBeLessThan(33);
    });

    it('should render workflow with connections efficiently', async () => {
      const canvas = document.createElement('canvas');
      canvas.width = 1600;
      canvas.height = 1200;
      const ctx = canvas.getContext('2d');

      if (!ctx) {
        throw new Error('Could not get canvas context');
      }

      // Simulate workflow with nodes and connections
      const nodes = Array.from({ length: 30 }, (_, i) => ({
        id: i,
        x: 100 + (i % 6) * 200,
        y: 100 + Math.floor(i / 6) * 150,
        width: 150,
        height: 100,
      }));

      const connections = Array.from({ length: 45 }, (_, i) => ({
        from: i % 30,
        to: (i + 1) % 30,
      }));

      const result = await benchmark(
        'Canvas - Render workflow with 30 nodes and 45 connections',
        () => {
          ctx.clearRect(0, 0, 1600, 1200);
          ctx.fillStyle = '#f8fafc';
          ctx.fillRect(0, 0, 1600, 1200);

          // Draw connections first (behind nodes)
          ctx.strokeStyle = '#94a3b8';
          ctx.lineWidth = 2;
          connections.forEach((conn) => {
            const fromNode = nodes[conn.from];
            const toNode = nodes[conn.to];
            ctx.beginPath();
            ctx.moveTo(fromNode.x + fromNode.width / 2, fromNode.y + fromNode.height);
            ctx.lineTo(toNode.x + toNode.width / 2, toNode.y);
            ctx.stroke();
          });

          // Draw nodes
          nodes.forEach((node) => {
            ctx.fillStyle = '#3b82f6';
            ctx.fillRect(node.x, node.y, node.width, node.height);
            ctx.strokeStyle = '#1e40af';
            ctx.lineWidth = 2;
            ctx.strokeRect(node.x, node.y, node.width, node.height);
            
            ctx.fillStyle = '#ffffff';
            ctx.font = '14px sans-serif';
            ctx.fillText(`Node ${node.id}`, node.x + 10, node.y + 30);
          });
        },
        30
      );

      logBenchmark(result);

      // Baseline: Should render workflow at least 20 frames per second
      expect(result.averageTime).toBeLessThan(50);
    });
  });

  describe('Performance Baselines Summary', () => {
    it('should log all performance baselines', () => {
      const logSpy = jest.spyOn(console, 'log').mockImplementation(() => undefined);
      console.log(`
╔════════════════════════════════════════════════════════════╗
║         PERFORMANCE BASELINES - PHASE 3 SYSTEMS           ║
╠════════════════════════════════════════════════════════════╣
║                                                            ║
║  Event Bus:                                                ║
║    • Event emission: > 10,000 ops/sec                     ║
║    • Subscribe/unsubscribe: > 5,000 ops/sec               ║
║    • Multi-listener (100): > 1,000 ops/sec                ║
║    • With middleware (3): > 5,000 ops/sec                 ║
║                                                            ║
║  DataSource:                                               ║
║    • Cache hits: > 1,000 ops/sec                          ║
║    • Request deduplication: 1 fetch for N concurrent      ║
║    • Cache clear: > 10,000 ops/sec                        ║
║                                                            ║
║  Form Validation:                                          ║
║    • Simple field: > 5,000 ops/sec                        ║
║    • Complex form: > 1,000 ops/sec                        ║
║                                                            ║
║  Canvas Rendering:                                         ║
║    • Simple shapes: > 60 fps (< 20ms/frame)              ║
║    • 50 workflow nodes: > 30 fps (< 33ms/frame)           ║
║    • Zoom/pan operations: > 30 fps (< 33ms/frame)         ║
║    • Workflow with connections: > 20 fps (< 50ms/frame)    ║
║                                                            ║
║  Integrated Workflows:                                     ║
║    • Single workflow: < 100ms                             ║
║    • 20 concurrent workflows: < 500ms total               ║
║                                                            ║
║  Memory:                                                   ║
║    • No subscription leaks                                ║
║    • Efficient cache management                           ║
║                                                            ║
╚════════════════════════════════════════════════════════════╝
      `);

      expect(logSpy).toHaveBeenCalled();
      logSpy.mockRestore();
    });
  });
});
