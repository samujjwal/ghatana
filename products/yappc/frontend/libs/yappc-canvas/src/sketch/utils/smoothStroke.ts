/**
 * @ghatana/yappc-sketch - Smooth Stroke Utilities
 *
 * Production-grade stroke smoothing using perfect-freehand library.
 *
 * @doc.type module
 * @doc.purpose Stroke smoothing and simplification
 * @doc.layer shared
 * @doc.pattern Utility
 */

import getStroke from 'perfect-freehand';

/**
 * Options for smooth stroke generation
 */
export interface SmoothStrokeOptions {
  /** Base stroke size */
  size?: number;
  /** Thinning factor (0-1) */
  thinning?: number;
  /** Smoothing factor (0-1) */
  smoothing?: number;
  /** Streamline factor (0-1) */
  streamline?: number;
  /** Simulate pressure for non-pressure devices */
  simulatePressure?: boolean;
  /** Start cap style */
  start?: { cap?: boolean; taper?: number | boolean };
  /** End cap style */
  end?: { cap?: boolean; taper?: number | boolean };
}

/**
 * Default stroke options
 */
const DEFAULT_OPTIONS: SmoothStrokeOptions = {
  size: 4,
  thinning: 0.6,
  smoothing: 0.5,
  streamline: 0.5,
  simulatePressure: true,
  start: { cap: true, taper: 0 },
  end: { cap: true, taper: 0 },
};

/**
 * Convert flat points array to perfect-freehand input format
 *
 * @param points - Flat array [x1, y1, x2, y2, ...]
 * @returns Array of [x, y, pressure] tuples
 */
export function pointsToInputPoints(points: number[]): [number, number, number][] {
  const result: [number, number, number][] = [];
  for (let i = 0; i < points.length; i += 2) {
    if (i + 1 < points.length) {
      result.push([points[i], points[i + 1], 0.5]);
    }
  }
  return result;
}

/**
 * Generate smooth stroke SVG path using perfect-freehand
 *
 * @param points - Flat array of points
 * @param options - Stroke options
 * @returns SVG path string
 *
 * @example
 * ```ts
 * const path = getSmoothStrokePath([0, 0, 10, 10, 20, 5], { size: 4 });
 * // Returns: "M 0,0 L 10,10 L 20,5 Z"
 * ```
 */
export function getSmoothStrokePath(
  points: number[],
  options: SmoothStrokeOptions = {}
): string {
  if (points.length < 4) {
    return '';
  }

  const inputPoints = pointsToInputPoints(points);
  const mergedOptions = { ...DEFAULT_OPTIONS, ...options };

  const stroke = getStroke(inputPoints, mergedOptions);

  if (stroke.length === 0) {
    return '';
  }

  // Convert stroke outline to SVG path
  let pathData = `M ${stroke[0][0].toFixed(2)},${stroke[0][1].toFixed(2)}`;

  for (let i = 1; i < stroke.length; i++) {
    const [x, y] = stroke[i];
    pathData += ` L ${x.toFixed(2)},${y.toFixed(2)}`;
  }

  pathData += ' Z';

  return pathData;
}

/**
 * Simplify points array using Ramer-Douglas-Peucker algorithm
 *
 * @param points - Flat array of points
 * @param tolerance - Simplification tolerance
 * @returns Simplified points array
 */
export function simplifyPoints(points: number[], tolerance: number = 2): number[] {
  if (points.length < 6) {
    return points;
  }

  // Convert to point objects
  const pointObjects: Array<{ x: number; y: number }> = [];
  for (let i = 0; i < points.length; i += 2) {
    pointObjects.push({ x: points[i], y: points[i + 1] });
  }

  // Apply RDP algorithm
  const simplified = rdpSimplify(pointObjects, tolerance);

  // Convert back to flat array
  const result: number[] = [];
  for (const p of simplified) {
    result.push(p.x, p.y);
  }

  return result;
}

/**
 * Ramer-Douglas-Peucker simplification algorithm
 */
function rdpSimplify(
  points: Array<{ x: number; y: number }>,
  tolerance: number
): Array<{ x: number; y: number }> {
  if (points.length <= 2) {
    return points;
  }

  // Find point with maximum distance from line
  let maxDist = 0;
  let maxIndex = 0;
  const first = points[0];
  const last = points[points.length - 1];

  for (let i = 1; i < points.length - 1; i++) {
    const dist = perpendicularDistance(points[i], first, last);
    if (dist > maxDist) {
      maxDist = dist;
      maxIndex = i;
    }
  }

  // If max distance is greater than tolerance, recursively simplify
  if (maxDist > tolerance) {
    const left = rdpSimplify(points.slice(0, maxIndex + 1), tolerance);
    const right = rdpSimplify(points.slice(maxIndex), tolerance);
    return [...left.slice(0, -1), ...right];
  }

  return [first, last];
}

/**
 * Calculate perpendicular distance from point to line
 */
function perpendicularDistance(
  point: { x: number; y: number },
  lineStart: { x: number; y: number },
  lineEnd: { x: number; y: number }
): number {
  const dx = lineEnd.x - lineStart.x;
  const dy = lineEnd.y - lineStart.y;

  if (dx === 0 && dy === 0) {
    return Math.sqrt(
      Math.pow(point.x - lineStart.x, 2) + Math.pow(point.y - lineStart.y, 2)
    );
  }

  const t = Math.max(
    0,
    Math.min(
      1,
      ((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) /
        (dx * dx + dy * dy)
    )
  );

  const projX = lineStart.x + t * dx;
  const projY = lineStart.y + t * dy;

  return Math.sqrt(Math.pow(point.x - projX, 2) + Math.pow(point.y - projY, 2));
}

/**
 * Convert SVG path to flat points array
 *
 * @param pathData - SVG path string
 * @returns Flat array of points
 */
export function pathToPoints(pathData: string): number[] {
  const points: number[] = [];
  const commands = pathData.match(/[ML]\s*[\d.-]+,[\d.-]+/g);

  if (!commands) {
    return points;
  }

  for (const cmd of commands) {
    const coords = cmd.match(/[\d.-]+/g);
    if (coords && coords.length === 2) {
      points.push(parseFloat(coords[0]), parseFloat(coords[1]));
    }
  }

  return points;
}

/**
 * Calculate bounding box of points
 */
export function getPointsBounds(points: number[]): {
  x: number;
  y: number;
  width: number;
  height: number;
} {
  if (points.length < 2) {
    return { x: 0, y: 0, width: 0, height: 0 };
  }

  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;

  for (let i = 0; i < points.length; i += 2) {
    const x = points[i];
    const y = points[i + 1];
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    maxX = Math.max(maxX, x);
    maxY = Math.max(maxY, y);
  }

  return {
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY,
  };
}
