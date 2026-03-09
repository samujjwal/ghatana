/**
 * Performance testing utilities for UI components
 */

import { performance } from 'perf_hooks';

import { render } from '@testing-library/react';
import React from 'react';

import type { RenderResult } from '@testing-library/react';

/**
 *
 */
export interface PerformanceResult {
  /** Component name */
  component: string;
  /** Average render time in milliseconds */
  averageRenderTime: number;
  /** Minimum render time in milliseconds */
  minRenderTime: number;
  /** Maximum render time in milliseconds */
  maxRenderTime: number;
  /** Standard deviation of render times */
  stdDeviation: number;
  /** Number of render iterations */
  iterations: number;
  /** Total render time in milliseconds */
  totalTime: number;
  /** Individual render times */
  renderTimes: number[];
  /** Memory usage in bytes (if available) */
  memoryUsage?: number;
}

/**
 *
 */
export interface PerformanceOptions {
  /** Number of render iterations (default: 100) */
  iterations?: number;
  /** Whether to warm up the component before measuring (default: true) */
  warmup?: boolean;
  /** Number of warmup iterations (default: 5) */
  warmupIterations?: number;
  /** Whether to measure memory usage (default: false) */
  measureMemory?: boolean;
  /** Whether to log results to console (default: false) */
  logResults?: boolean;
  /** Threshold for average render time in milliseconds (test fails if exceeded) */
  renderTimeThreshold?: number;
}

/**
 * Measure render performance of a React component
 *
 * @param componentName - Name of the component being tested
 * @param renderFn - Function that renders the component
 * @param options - Performance testing options
 * @returns Performance test results
 */
export async function measureRenderPerformance(
  componentName: string,
  renderFn: () => RenderResult,
  options: PerformanceOptions = {}
): Promise<PerformanceResult> {
  const {
    iterations = 100,
    warmup = true,
    warmupIterations = 5,
    measureMemory = false,
    logResults = false,
    renderTimeThreshold,
  } = options;

  // Warm up the component to avoid first-render penalties
  if (warmup) {
    for (let i = 0; i < warmupIterations; i++) {
      const result = renderFn();
      result.unmount();
    }
  }

  // Measure render times
  const renderTimes: number[] = [];
  let memoryBefore: number | undefined;
  let memoryAfter: number | undefined;

  if (measureMemory && typeof global.gc === 'function') {
    global.gc();
    memoryBefore = process.memoryUsage().heapUsed;
  }

  for (let i = 0; i < iterations; i++) {
    const startTime = performance.now();
    const result = renderFn();
    const endTime = performance.now();
    renderTimes.push(endTime - startTime);
    result.unmount();
  }

  if (measureMemory && typeof global.gc === 'function') {
    global.gc();
    memoryAfter = process.memoryUsage().heapUsed;
  }

  // Calculate statistics
  const totalTime = renderTimes.reduce((sum, time) => sum + time, 0);
  const averageRenderTime = totalTime / iterations;
  const minRenderTime = Math.min(...renderTimes);
  const maxRenderTime = Math.max(...renderTimes);

  // Calculate standard deviation
  const squaredDifferences = renderTimes.map((time) => {
    const diff = time - averageRenderTime;
    return diff * diff;
  });
  const variance =
    squaredDifferences.reduce((sum, sqDiff) => sum + sqDiff, 0) /
    renderTimes.length;
  const stdDeviation = Math.sqrt(variance);

  const result: PerformanceResult = {
    component: componentName,
    averageRenderTime,
    minRenderTime,
    maxRenderTime,
    stdDeviation,
    iterations,
    totalTime,
    renderTimes,
    memoryUsage:
      memoryAfter && memoryBefore ? memoryAfter - memoryBefore : undefined,
  };

  if (logResults) {
    console.log(`Performance test results for ${componentName}:`);
    console.log(`  Average render time: ${averageRenderTime.toFixed(3)}ms`);
    console.log(`  Min render time: ${minRenderTime.toFixed(3)}ms`);
    console.log(`  Max render time: ${maxRenderTime.toFixed(3)}ms`);
    console.log(`  Standard deviation: ${stdDeviation.toFixed(3)}ms`);
    console.log(
      `  Total time: ${totalTime.toFixed(3)}ms for ${iterations} iterations`
    );
    if (result.memoryUsage !== undefined) {
      console.log(
        `  Memory usage: ${(result.memoryUsage / 1024).toFixed(2)}KB`
      );
    }
  }

  // Check against threshold if provided
  if (
    renderTimeThreshold !== undefined &&
    averageRenderTime > renderTimeThreshold
  ) {
    throw new Error(
      `Performance threshold exceeded for ${componentName}: ` +
        `${averageRenderTime.toFixed(3)}ms > ${renderTimeThreshold.toFixed(3)}ms`
    );
  }

  return result;
}

/**
 * Create a performance test for a React component
 *
 * @param componentName - Name of the component being tested
 * @param component - React component to test
 * @param props - Props to pass to the component
 * @param options - Performance testing options
 * @returns Performance test function
 */
export function createPerformanceTest<P = {}>(
  componentName: string,
  component: React.ComponentType<P>,
  props: P,
  options: PerformanceOptions = {}
) {
  const Component = component;
  return async () => {
    return measureRenderPerformance(
      componentName,
      () => render(React.createElement(Component as unknown, props as unknown)),
      options
    );
  };
}

/**
 * Compare performance between two component implementations
 *
 * @param name - Name of the comparison test
 * @param baselineRenderFn - Function that renders the baseline component
 * @param candidateRenderFn - Function that renders the candidate component
 * @param options - Performance testing options
 * @returns Comparison results
 */
export async function compareRenderPerformance(
  name: string,
  baselineRenderFn: () => RenderResult,
  candidateRenderFn: () => RenderResult,
  options: PerformanceOptions = {}
): Promise<{
  baseline: PerformanceResult;
  candidate: PerformanceResult;
  improvement: number;
  improvementPercentage: number;
}> {
  const baselineResult = await measureRenderPerformance(
    `${name} (baseline)`,
    baselineRenderFn,
    options
  );
  const candidateResult = await measureRenderPerformance(
    `${name} (candidate)`,
    candidateRenderFn,
    options
  );

  const improvement =
    baselineResult.averageRenderTime - candidateResult.averageRenderTime;
  const improvementPercentage =
    (improvement / baselineResult.averageRenderTime) * 100;

  if (options.logResults) {
    console.log(`Performance comparison for ${name}:`);
    console.log(`  Baseline: ${baselineResult.averageRenderTime.toFixed(3)}ms`);
    console.log(
      `  Candidate: ${candidateResult.averageRenderTime.toFixed(3)}ms`
    );
    console.log(
      `  Improvement: ${improvement.toFixed(3)}ms (${improvementPercentage.toFixed(2)}%)`
    );
  }

  return {
    baseline: baselineResult,
    candidate: candidateResult,
    improvement,
    improvementPercentage,
  };
}
