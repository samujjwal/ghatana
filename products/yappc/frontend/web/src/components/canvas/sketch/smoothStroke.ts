import getStroke from 'perfect-freehand';

export interface SmoothStrokeOptions {
  size?: number;
  thinning?: number;
  smoothing?: number;
  streamline?: number;
  simulatePressure?: boolean;
  start?: { cap?: boolean; taper?: number | boolean };
  end?: { cap?: boolean; taper?: number | boolean };
}

const DEFAULT_OPTIONS: SmoothStrokeOptions = {
  size: 4,
  thinning: 0.6,
  smoothing: 0.5,
  streamline: 0.5,
  simulatePressure: true,
  start: { cap: true, taper: 0 },
  end: { cap: true, taper: 0 },
};

export function pointsToInputPoints(
  points: number[]
): [number, number, number][] {
  const result: [number, number, number][] = [];
  for (let index = 0; index < points.length; index += 2) {
    if (index + 1 < points.length) {
      result.push([points[index], points[index + 1], 0.5]);
    }
  }
  return result;
}

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

  let pathData = `M ${stroke[0][0].toFixed(2)},${stroke[0][1].toFixed(2)}`;
  for (let index = 1; index < stroke.length; index += 1) {
    const [x, y] = stroke[index];
    pathData += ` L ${x.toFixed(2)},${y.toFixed(2)}`;
  }
  pathData += ' Z';

  return pathData;
}

export function simplifyPoints(
  points: number[],
  tolerance: number = 2
): number[] {
  if (points.length < 6) {
    return points;
  }

  const pointObjects: Array<{ x: number; y: number }> = [];
  for (let index = 0; index < points.length; index += 2) {
    pointObjects.push({ x: points[index], y: points[index + 1] });
  }

  const simplified = rdpSimplify(pointObjects, tolerance);
  const result: number[] = [];
  for (const point of simplified) {
    result.push(point.x, point.y);
  }

  return result;
}

function rdpSimplify(
  points: Array<{ x: number; y: number }>,
  tolerance: number
): Array<{ x: number; y: number }> {
  if (points.length <= 2) {
    return points;
  }

  let maxDistance = 0;
  let maxIndex = 0;
  const firstPoint = points[0];
  const lastPoint = points[points.length - 1];

  for (let index = 1; index < points.length - 1; index += 1) {
    const distance = perpendicularDistance(points[index], firstPoint, lastPoint);
    if (distance > maxDistance) {
      maxDistance = distance;
      maxIndex = index;
    }
  }

  if (maxDistance > tolerance) {
    const left = rdpSimplify(points.slice(0, maxIndex + 1), tolerance);
    const right = rdpSimplify(points.slice(maxIndex), tolerance);
    return [...left.slice(0, -1), ...right];
  }

  return [firstPoint, lastPoint];
}

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

  const projectedX = lineStart.x + t * dx;
  const projectedY = lineStart.y + t * dy;

  return Math.sqrt(
    Math.pow(point.x - projectedX, 2) + Math.pow(point.y - projectedY, 2)
  );
}