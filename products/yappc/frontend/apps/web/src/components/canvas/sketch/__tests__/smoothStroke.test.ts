import { describe, test, expect } from 'vitest';

// Import from shared library
import {
  pointsToInputPoints,
  getSmoothStrokePath,
  simplifyPoints,
} from '@ghatana/yappc-canvas/sketch';

describe('smoothStroke utilities', () => {
  describe('pointsToInputPoints', () => {
    test('should convert flat points array to input format', () => {
      const points = [10, 20, 30, 40, 50, 60];
      const result = pointsToInputPoints(points);

      expect(result).toEqual([
        [10, 20, 0.5],
        [30, 40, 0.5],
        [50, 60, 0.5],
      ]);
    });

    test('should handle odd-length arrays', () => {
      const points = [10, 20, 30];
      const result = pointsToInputPoints(points);

      expect(result).toEqual([[10, 20, 0.5]]);
    });

    test('should handle empty arrays', () => {
      const points: number[] = [];
      const result = pointsToInputPoints(points);

      expect(result).toEqual([]);
    });
  });

  describe('getSmoothStrokePath', () => {
    test('should generate SVG path for valid points', () => {
      const points = [0, 0, 10, 10, 20, 20, 30, 30];
      const path = getSmoothStrokePath(points);

      expect(path).toBeTruthy();
      expect(path).toContain('M');
      expect(path).toContain('L');
      expect(path).toContain('Z');
    });

    test('should return empty string for insufficient points', () => {
      const points = [0, 0];
      const path = getSmoothStrokePath(points);

      expect(path).toBe('');
    });

    test('should accept custom options', () => {
      const points = [0, 0, 10, 10, 20, 20, 30, 30];
      const path = getSmoothStrokePath(points, {
        size: 8,
        thinning: 0.8,
        smoothing: 0.7,
      });

      expect(path).toBeTruthy();
    });
  });

  describe('simplifyPoints', () => {
    test('should simplify points based on tolerance', () => {
      const points = [0, 0, 1, 1, 2, 2, 10, 10, 11, 11, 20, 20];
      const simplified = simplifyPoints(points, 5);

      expect(simplified.length).toBeLessThan(points.length);
      expect(simplified[0]).toBe(0); // First point preserved
      expect(simplified[1]).toBe(0);
      expect(simplified[simplified.length - 2]).toBe(20); // Last point preserved
      expect(simplified[simplified.length - 1]).toBe(20);
    });

    test('should preserve all points if tolerance is very small', () => {
      const points = [0, 0, 10, 10, 20, 20];
      const simplified = simplifyPoints(points, 0.1);

      expect(simplified.length).toBe(points.length);
    });

    test('should handle arrays with less than 3 points', () => {
      const points = [0, 0, 10, 10];
      const simplified = simplifyPoints(points);

      expect(simplified).toEqual(points);
    });

    test('should always include first and last points', () => {
      const points = [5, 5, 6, 6, 7, 7, 8, 8, 100, 100];
      const simplified = simplifyPoints(points, 2);

      expect(simplified[0]).toBe(5);
      expect(simplified[1]).toBe(5);
      expect(simplified[simplified.length - 2]).toBe(100);
      expect(simplified[simplified.length - 1]).toBe(100);
    });
  });

  describe('integration', () => {
    test('should handle full stroke workflow', () => {
      // Simulate drawing a stroke
      const rawPoints = [0, 0, 5, 5, 10, 10, 15, 15, 20, 20];

      // Simplify
      const simplified = simplifyPoints(rawPoints, 3);
      expect(simplified.length).toBeLessThanOrEqual(rawPoints.length);

      // Convert to input format
      const inputPoints = pointsToInputPoints(simplified);
      expect(inputPoints.length).toBeGreaterThan(0);

      // Generate smooth path
      const path = getSmoothStrokePath(simplified);
      expect(path).toBeTruthy();
    });
  });
});
