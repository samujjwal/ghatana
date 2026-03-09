/**
 * Performance Test Utilities
 * Shared utilities for measuring and validating canvas performance
 */

import { Page } from '@playwright/test';

export interface PerformanceMetrics {
  renderTime: number;
  frameRate: number;
  memoryUsage: number;
  interactionLatency: number;
}

export interface PerformanceBudget {
  maxRenderTime: number;
  minFrameRate: number;
  maxMemoryIncrease: number;
  maxInteractionLatency: number;
}

export const DEFAULT_BUDGETS: PerformanceBudget = {
  maxRenderTime: 2000, // 2s
  minFrameRate: 30, // FPS
  maxMemoryIncrease: 100 * 1024 * 1024, // 100MB
  maxInteractionLatency: 100, // 100ms
};

/**
 * Measure canvas rendering performance
 */
export async function measureRenderPerformance(
  page: Page,
  action: () => Promise<void>
): Promise<{ renderTime: number; memoryIncrease: number }> {
  const initialMemory = await page.evaluate(() => 
    (performance as unknown).memory?.usedJSHeapSize || 0
  );
  
  const startTime = Date.now();
  await action();
  const renderTime = Date.now() - startTime;
  
  const finalMemory = await page.evaluate(() => 
    (performance as unknown).memory?.usedJSHeapSize || 0
  );
  
  return {
    renderTime,
    memoryIncrease: finalMemory - initialMemory,
  };
}

/**
 * Measure frame rate during canvas interaction
 */
export async function measureFrameRate(
  page: Page, 
  duration: number = 1000
): Promise<number> {
  return page.evaluate((ms) => {
    return new Promise<number>((resolve) => {
      let frames = 0;
      const startTime = performance.now();
      
      function countFrame() {
        frames++;
        if (performance.now() - startTime < ms) {
          requestAnimationFrame(countFrame);
        } else {
          const fps = (frames / ms) * 1000;
          resolve(fps);
        }
      }
      
      requestAnimationFrame(countFrame);
    });
  }, duration);
}

/**
 * Generate test canvas data with specified complexity
 */
export function generateCanvasData(nodeCount: number, connectionDensity: number = 0.1) {
  const elements = [];
  const connections = [];
  
  // Generate nodes in a grid layout
  const cols = Math.ceil(Math.sqrt(nodeCount));
  for (let i = 0; i < nodeCount; i++) {
    elements.push({
      id: `perf-node-${i}`,
      kind: 'node',
      type: i % 3 === 0 ? 'api' : i % 3 === 1 ? 'component' : 'data',
      position: {
        x: (i % cols) * 180,
        y: Math.floor(i / cols) * 120,
      },
      size: { width: 150, height: 80 },
      data: { label: `Node ${i}` },
      style: {},
    });
  }
  
  // Generate connections based on density
  const maxConnections = Math.floor(nodeCount * connectionDensity);
  for (let i = 0; i < maxConnections; i++) {
    const source = Math.floor(Math.random() * nodeCount);
    const target = Math.floor(Math.random() * nodeCount);
    
    if (source !== target) {
      connections.push({
        id: `perf-edge-${i}`,
        source: `perf-node-${source}`,
        target: `perf-node-${target}`,
      });
    }
  }
  
  return { elements, connections, sketches: [] };
}

/**
 * Seed canvas with performance test data
 */
export async function seedPerformanceData(
  page: Page, 
  nodeCount: number, 
  connectionDensity: number = 0.1
): Promise<void> {
  const data = generateCanvasData(nodeCount, connectionDensity);
  
  await page.evaluate((canvasData) => {
    localStorage.setItem('canvas-state', JSON.stringify(canvasData));
  }, data);
}

/**
 * Wait for canvas rendering to complete
 */
export async function waitForCanvasRender(
  page: Page, 
  expectedElements: number,
  timeout: number = 10000
): Promise<void> {
  await page.waitForFunction(
    (count) => {
      const nodes = document.querySelectorAll('.react-flow__node');
      return nodes.length >= Math.min(count, 50); // Visual viewport limit
    },
    expectedElements,
    { timeout }
  );
}

/**
 * Validate performance against budget
 */
export function validatePerformance(
  metrics: Partial<PerformanceMetrics>,
  budget: Partial<PerformanceBudget> = DEFAULT_BUDGETS
): { passed: boolean; violations: string[] } {
  const violations: string[] = [];
  
  if (metrics.renderTime && budget.maxRenderTime && metrics.renderTime > budget.maxRenderTime) {
    violations.push(`Render time ${metrics.renderTime}ms exceeds budget ${budget.maxRenderTime}ms`);
  }
  
  if (metrics.frameRate && budget.minFrameRate && metrics.frameRate < budget.minFrameRate) {
    violations.push(`Frame rate ${metrics.frameRate}fps below budget ${budget.minFrameRate}fps`);
  }
  
  if (metrics.memoryUsage && budget.maxMemoryIncrease && metrics.memoryUsage > budget.maxMemoryIncrease) {
    violations.push(`Memory increase ${Math.round(metrics.memoryUsage / 1024 / 1024)}MB exceeds budget ${Math.round(budget.maxMemoryIncrease / 1024 / 1024)}MB`);
  }
  
  if (metrics.interactionLatency && budget.maxInteractionLatency && metrics.interactionLatency > budget.maxInteractionLatency) {
    violations.push(`Interaction latency ${metrics.interactionLatency}ms exceeds budget ${budget.maxInteractionLatency}ms`);
  }
  
  return {
    passed: violations.length === 0,
    violations,
  };
}