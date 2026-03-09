/**
 * Data Sampling Utilities
 * 
 * Efficient algorithms for downsampling large datasets while preserving visual fidelity.
 * Implements LTTB (Largest Triangle Three Buckets) algorithm for optimal sampling.
 */

import type { DataPoint } from '../types';

/**
 * Sample data using Largest Triangle Three Buckets (LTTB) algorithm
 * 
 * This algorithm preserves the visual characteristics of the data
 * while reducing the number of points.
 * 
 * @param data - Original data points
 * @param threshold - Target number of points
 * @returns Sampled data points
 */
export function sampleData(data: DataPoint[], threshold: number): DataPoint[] {
  if (data.length <= threshold) {
    return data;
  }

  const sampled: DataPoint[] = [];
  const bucketSize = (data.length - 2) / (threshold - 2);

  // Always include first point
  sampled.push(data[0]);

  let a = 0; // Initially a is the first point

  for (let i = 0; i < threshold - 2; i++) {
    // Calculate point average for next bucket
    let avgX = 0;
    let avgY = 0;
    const avgRangeStart = Math.floor((i + 1) * bucketSize) + 1;
    const avgRangeEnd = Math.floor((i + 2) * bucketSize) + 1;
    const avgRangeLength = avgRangeEnd - avgRangeStart;

    for (let j = avgRangeStart; j < avgRangeEnd; j++) {
      avgX += data[j].timestamp;
      avgY += data[j].value;
    }
    avgX /= avgRangeLength;
    avgY /= avgRangeLength;

    // Get the range for this bucket
    const rangeOffs = Math.floor(i * bucketSize) + 1;
    const rangeTo = Math.floor((i + 1) * bucketSize) + 1;

    // Point a
    const pointAX = data[a].timestamp;
    const pointAY = data[a].value;

    let maxArea = -1;
    let maxAreaPoint = 0;

    for (let j = rangeOffs; j < rangeTo; j++) {
      // Calculate triangle area
      const area = Math.abs(
        (pointAX - avgX) * (data[j].value - pointAY) -
        (pointAX - data[j].timestamp) * (avgY - pointAY)
      ) * 0.5;

      if (area > maxArea) {
        maxArea = area;
        maxAreaPoint = j;
      }
    }

    sampled.push(data[maxAreaPoint]);
    a = maxAreaPoint;
  }

  // Always include last point
  sampled.push(data[data.length - 1]);

  return sampled;
}

/**
 * Simple decimation sampling (every nth point)
 * Faster but less accurate than LTTB
 */
export function decimateSample(data: DataPoint[], threshold: number): DataPoint[] {
  if (data.length <= threshold) {
    return data;
  }

  const step = Math.ceil(data.length / threshold);
  const sampled: DataPoint[] = [];

  for (let i = 0; i < data.length; i += step) {
    sampled.push(data[i]);
  }

  // Always include last point
  if (sampled[sampled.length - 1] !== data[data.length - 1]) {
    sampled.push(data[data.length - 1]);
  }

  return sampled;
}

/**
 * Min-max sampling (preserves peaks and valleys)
 * Good for visualizing extreme values
 */
export function minMaxSample(data: DataPoint[], threshold: number): DataPoint[] {
  if (data.length <= threshold) {
    return data;
  }

  const sampled: DataPoint[] = [];
  const bucketSize = Math.ceil(data.length / threshold);

  for (let i = 0; i < data.length; i += bucketSize) {
    const bucket = data.slice(i, Math.min(i + bucketSize, data.length));
    
    // Find min and max in bucket
    let min = bucket[0];
    let max = bucket[0];

    for (const point of bucket) {
      if (point.value < min.value) min = point;
      if (point.value > max.value) max = point;
    }

    // Add both min and max (preserves peaks/valleys)
    if (min.timestamp < max.timestamp) {
      sampled.push(min, max);
    } else {
      sampled.push(max, min);
    }
  }

  return sampled;
}

/**
 * Adaptive sampling based on data variance
 * Samples more densely in areas with high variance
 */
export function adaptiveSample(data: DataPoint[], threshold: number): DataPoint[] {
  if (data.length <= threshold) {
    return data;
  }

  const sampled: DataPoint[] = [data[0]];
  const bucketSize = Math.ceil(data.length / threshold);

  for (let i = bucketSize; i < data.length; i += bucketSize) {
    const bucket = data.slice(i - bucketSize, Math.min(i, data.length));
    
    // Calculate variance
    const mean = bucket.reduce((sum, p) => sum + p.value, 0) / bucket.length;
    const variance = bucket.reduce((sum, p) => sum + Math.pow(p.value - mean, 2), 0) / bucket.length;

    // Sample more points in high-variance areas
    const sampleCount = variance > mean * 0.1 ? 2 : 1;
    const step = Math.ceil(bucket.length / sampleCount);

    for (let j = 0; j < bucket.length; j += step) {
      sampled.push(bucket[j]);
    }
  }

  // Always include last point
  if (sampled[sampled.length - 1] !== data[data.length - 1]) {
    sampled.push(data[data.length - 1]);
  }

  return sampled;
}
