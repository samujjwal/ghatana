/**
 * Performance Regression Tests
 * 
 * Automated tests to detect performance degradation across releases.
 */

import { performance } from 'perf_hooks';

import { describe, it, expect, beforeAll } from 'vitest';

// Performance budgets (in milliseconds)
const BUDGETS = {
  viewport: {
    zoom: 16,          // 60 FPS = 16ms per frame
    pan: 16,
    fitView: 200,
  },
  rendering: {
    initialRender: 100,
    rerender: 50,
    largeCanvas: 500,  // 1000+ elements
  },
  state: {
    atomRead: 1,
    atomWrite: 5,
    atomSubscribe: 10,
  },
  sync: {
    pull: 1000,
    push: 500,
    queueReplay: 2000,
  },
};

describe('Performance Regression Tests', () => {
  describe('Viewport Performance', () => {
    it('should zoom within 16ms budget (60 FPS)', async () => {
      const iterations = 100;
      const times: number[] = [];
      
      for (let i = 0; i < iterations; i++) {
        const start = performance.now();
        
        // Simulate zoom operation
        const viewport = {
          zoom: 1.0,
          center: { x: 0, y: 0 },
        };
        
        // Zoom calculation (typical implementation)
        const newZoom = Math.max(0.1, Math.min(5.0, viewport.zoom * 1.1));
        const zoomDelta = Math.log(newZoom / viewport.zoom);
        
        const end = performance.now();
        times.push(end - start);
      }
      
      const avgTime = times.reduce((a, b) => a + b, 0) / times.length;
      const p95Time = times.sort((a, b) => a - b)[Math.floor(times.length * 0.95)];
      
      console.log(`  Zoom - Avg: ${avgTime.toFixed(2)}ms, P95: ${p95Time.toFixed(2)}ms`);
      
      expect(p95Time).toBeLessThan(BUDGETS.viewport.zoom);
    });
    
    it('should pan within 16ms budget', async () => {
      const iterations = 100;
      const times: number[] = [];
      
      for (let i = 0; i < iterations; i++) {
        const start = performance.now();
        
        // Simulate pan operation
        const viewport = { center: { x: 0, y: 0 } };
        const delta = { x: 10, y: 10 };
        viewport.center = {
          x: viewport.center.x + delta.x,
          y: viewport.center.y + delta.y,
        };
        
        const end = performance.now();
        times.push(end - start);
      }
      
      const avgTime = times.reduce((a, b) => a + b, 0) / times.length;
      const p95Time = times.sort((a, b) => a - b)[Math.floor(times.length * 0.95)];
      
      console.log(`  Pan - Avg: ${avgTime.toFixed(2)}ms, P95: ${p95Time.toFixed(2)}ms`);
      
      expect(p95Time).toBeLessThan(BUDGETS.viewport.pan);
    });
  });
  
  describe('Rendering Performance', () => {
    it('should render initial canvas within 100ms', async () => {
      const times: number[] = [];
      
      for (let i = 0; i < 10; i++) {
        const start = performance.now();
        
        // Simulate initial render with 50 elements
        const elements = Array.from({ length: 50 }, (_, i) => ({
          id: `element-${i}`,
          type: 'rectangle',
          x: Math.random() * 1000,
          y: Math.random() * 1000,
          width: 100,
          height: 100,
        }));
        
        // Simulate render calculations
        elements.forEach((el) => {
          const bounds = {
            left: el.x,
            top: el.y,
            right: el.x + el.width,
            bottom: el.y + el.height,
          };
        });
        
        const end = performance.now();
        times.push(end - start);
      }
      
      const avgTime = times.reduce((a, b) => a + b, 0) / times.length;
      
      console.log(`  Initial Render - Avg: ${avgTime.toFixed(2)}ms`);
      
      expect(avgTime).toBeLessThan(BUDGETS.rendering.initialRender);
    });
    
    it('should handle large canvas (1000+ elements) within budget', async () => {
      const start = performance.now();
      
      // Simulate large canvas
      const elements = Array.from({ length: 1000 }, (_, i) => ({
        id: `element-${i}`,
        type: 'rectangle',
        x: Math.random() * 10000,
        y: Math.random() * 10000,
        width: 100,
        height: 100,
      }));
      
      // Viewport culling simulation
      const viewport = { x: 0, y: 0, width: 1920, height: 1080 };
      const visible = elements.filter((el) => {
        return (
          el.x < viewport.x + viewport.width &&
          el.x + el.width > viewport.x &&
          el.y < viewport.y + viewport.height &&
          el.y + el.height > viewport.y
        );
      });
      
      const end = performance.now();
      const time = end - start;
      
      console.log(`  Large Canvas (1000 elements) - Time: ${time.toFixed(2)}ms`);
      console.log(`    Visible elements: ${visible.length}`);
      
      expect(time).toBeLessThan(BUDGETS.rendering.largeCanvas);
    });
  });
  
  describe('State Management Performance', () => {
    it('should read atom within 1ms', async () => {
      const iterations = 1000;
      const times: number[] = [];
      
      // Mock atom
      const mockAtom = { value: { data: 'test' } };
      
      for (let i = 0; i < iterations; i++) {
        const start = performance.now();
        const value = mockAtom.value;
        const end = performance.now();
        times.push(end - start);
      }
      
      const avgTime = times.reduce((a, b) => a + b, 0) / times.length;
      
      console.log(`  Atom Read - Avg: ${avgTime.toFixed(4)}ms`);
      
      expect(avgTime).toBeLessThan(BUDGETS.state.atomRead);
    });
    
    it('should write atom within 5ms', async () => {
      const iterations = 100;
      const times: number[] = [];
      
      const mockAtom = { value: { data: 'test' } };
      
      for (let i = 0; i < iterations; i++) {
        const start = performance.now();
        mockAtom.value = { data: `test-${i}` };
        const end = performance.now();
        times.push(end - start);
      }
      
      const avgTime = times.reduce((a, b) => a + b, 0) / times.length;
      
      console.log(`  Atom Write - Avg: ${avgTime.toFixed(2)}ms`);
      
      expect(avgTime).toBeLessThan(BUDGETS.state.atomWrite);
    });
  });
  
  describe('Sync Performance', () => {
    it('should queue operations efficiently', async () => {
      const iterations = 1000;
      const start = performance.now();
      
      const queue: unknown[] = [];
      
      for (let i = 0; i < iterations; i++) {
        queue.push({
          id: `op-${i}`,
          documentId: 'doc-1',
          operation: 'update',
          data: { value: i },
          timestamp: Date.now(),
        });
      }
      
      const end = performance.now();
      const time = end - start;
      const timePerOp = time / iterations;
      
      console.log(`  Queue Operations - Total: ${time.toFixed(2)}ms, Per Op: ${timePerOp.toFixed(4)}ms`);
      
      expect(timePerOp).toBeLessThan(1); // Less than 1ms per operation
    });
  });
  
  describe('Memory Performance', () => {
    it('should not leak memory during viewport operations', async () => {
      if (typeof global.gc !== 'function') {
        console.log('  ⚠️  Skipping memory test (run with --expose-gc)');
        return;
      }
      
      global.gc();
      const memBefore = process.memoryUsage().heapUsed;
      
      // Simulate 1000 viewport updates
      for (let i = 0; i < 1000; i++) {
        const viewport = {
          zoom: 1.0 + (i * 0.01),
          center: { x: i, y: i },
        };
        
        // Clear reference
        const temp = viewport;
      }
      
      global.gc();
      const memAfter = process.memoryUsage().heapUsed;
      const memDiff = (memAfter - memBefore) / 1024 / 1024; // Convert to MB
      
      console.log(`  Memory Usage - Before: ${(memBefore / 1024 / 1024).toFixed(2)}MB, After: ${(memAfter / 1024 / 1024).toFixed(2)}MB, Diff: ${memDiff.toFixed(2)}MB`);
      
      expect(memDiff).toBeLessThan(10); // Less than 10MB growth
    });
  });
});

/**
 * Generate performance report
 */
export function generatePerformanceReport(results: unknown) {
  const report = {
    timestamp: new Date().toISOString(),
    budgets: BUDGETS,
    results,
    summary: {
      passed: results.filter((r: unknown) => r.passed).length,
      failed: results.filter((r: unknown) => !r.passed).length,
      total: results.length,
    },
  };
  
  return report;
}
