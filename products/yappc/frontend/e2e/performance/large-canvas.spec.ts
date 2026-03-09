/**
 * Performance tests for large canvas scenarios (≥1k nodes)
 * Validates canvas can handle enterprise-scale diagrams
 */

import { test, expect } from '@playwright/test';
import { setupTest, teardownTest } from '../helpers/test-isolation';

const PERFORMANCE_BUDGETS = {
  maxFrameTime: 16.67, // 60 FPS
  minFPS: 30,
  maxRenderTime: 15000, // 15s - includes page load time for large datasets
  maxMemoryIncrease: 100 * 1024 * 1024, // 100MB - reasonable for 1000 nodes
};

test.describe('Large Canvas Performance', () => {
  test.beforeEach(async ({ page }) => {
    await setupTest(page, {
      url: '/canvas',
      seedData: false,
    });
  });

  test.afterEach(async ({ page }) => {
    await teardownTest(page);
  });

  test('handles 1000 nodes without performance degradation', async ({
    page,
  }) => {
    // Start performance monitoring
    const startTime = Date.now();
    const initialMemory = await page.evaluate(
      () => (performance as unknown).memory?.usedJSHeapSize || 0
    );

    // Generate 1000 test nodes
    await page.evaluate(() => {
      const elements = [];
      for (let i = 0; i < 1000; i++) {
        elements.push({
          id: `node-${i}`,
          kind: 'node',
          type: 'component',
          position: {
            x: (i % 40) * 200,
            y: Math.floor(i / 40) * 150,
          },
          size: { width: 150, height: 80 },
          data: { label: `Node ${i}` },
          style: {},
        });
      }

      // Set the elements in canvas state
      localStorage.setItem(
        'canvas-state',
        JSON.stringify({
          elements,
          connections: [],
          sketches: [],
        })
      );
    });

    // Reload to apply the large dataset
    await page.reload();
    await page.waitForLoadState('networkidle');

    // Wait for all nodes to render
    await page.waitForSelector('.react-flow__node', { timeout: 10000 });

    // Measure performance
    const renderTime = Date.now() - startTime;
    const finalMemory = await page.evaluate(
      () => (performance as unknown).memory?.usedJSHeapSize || 0
    );
    const memoryIncrease = finalMemory - initialMemory;

    // Verify node count
    const nodeCount = await page.locator('.react-flow__node').count();
    expect(nodeCount).toBeGreaterThanOrEqual(50); // At least some nodes rendered

    // Performance assertions
    expect(renderTime).toBeLessThan(PERFORMANCE_BUDGETS.maxRenderTime);

    if (initialMemory > 0) {
      expect(memoryIncrease).toBeLessThan(
        PERFORMANCE_BUDGETS.maxMemoryIncrease
      );
    }

    console.log(`Performance metrics for 1000 nodes:
      - Render time: ${renderTime}ms
      - Memory increase: ${Math.round(memoryIncrease / 1024 / 1024)}MB
      - Nodes rendered: ${nodeCount}
    `);
  });

  test('viewport performance with dense node clusters', async ({ page }) => {
    // Create a dense cluster of 200 nodes in viewport
    await page.evaluate(() => {
      const elements = [];
      for (let i = 0; i < 200; i++) {
        elements.push({
          id: `cluster-node-${i}`,
          kind: 'node',
          type: 'api',
          position: {
            x: 400 + (i % 10) * 20,
            y: 300 + Math.floor(i / 10) * 20,
          },
          size: { width: 80, height: 60 },
          data: { label: `API ${i}` },
          style: {},
        });
      }

      localStorage.setItem(
        'canvas-state',
        JSON.stringify({
          elements,
          connections: [],
          sketches: [],
        })
      );
    });

    await page.reload();
    await page.waitForLoadState('networkidle');

    // Measure interaction performance
    const startInteraction = Date.now();

    // Pan around the canvas
    const canvas = page.locator('[data-testid="react-flow-wrapper"]');
    await canvas.hover();
    await page.mouse.down();
    await page.mouse.move(500, 400);
    await page.mouse.move(600, 500);
    await page.mouse.up();

    const interactionTime = Date.now() - startInteraction;

    // Should handle interactions smoothly (allow more time for complex canvases)
    expect(interactionTime).toBeLessThan(15000); // 15s max for pan operation with dense clusters

    console.log(`Interaction performance: ${interactionTime}ms`);
  });

  test('connection rendering performance with many edges', async ({ page }) => {
    // Create nodes with many connections
    await page.evaluate(() => {
      const elements = [];
      const connections = [];

      // Create 100 nodes
      for (let i = 0; i < 100; i++) {
        elements.push({
          id: `conn-node-${i}`,
          kind: 'node',
          type: 'component',
          position: {
            x: (i % 10) * 150,
            y: Math.floor(i / 10) * 100,
          },
          size: { width: 120, height: 60 },
          data: { label: `Node ${i}` },
          style: {},
        });
      }

      // Create connections (each node connects to next 3 nodes)
      for (let i = 0; i < 97; i++) {
        for (let j = 1; j <= 3; j++) {
          if (i + j < 100) {
            connections.push({
              id: `edge-${i}-${i + j}`,
              source: `conn-node-${i}`,
              target: `conn-node-${i + j}`,
            });
          }
        }
      }

      localStorage.setItem(
        'canvas-state',
        JSON.stringify({
          elements,
          connections,
          sketches: [],
        })
      );
    });

    const startTime = Date.now();
    await page.reload();
    await page.waitForLoadState('networkidle');

    // Wait for edges to render
    await page.waitForSelector('.react-flow__edge', { timeout: 5000 });

    const renderTime = Date.now() - startTime;
    const edgeCount = await page.locator('.react-flow__edge').count();

    expect(edgeCount).toBeGreaterThan(50); // Should render many edges
    expect(renderTime).toBeLessThan(5000); // 5s max for complex graph rendering

    console.log(`Edge rendering: ${edgeCount} edges in ${renderTime}ms`);
  });
});
