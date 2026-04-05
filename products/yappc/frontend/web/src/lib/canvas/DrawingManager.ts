/**
 * Drawing Manager - Freehand Drawing and Sketching
 *
 * Handles pen/pencil drawing with stroke smoothing
 *
 * @doc.type manager
 * @doc.purpose Freehand drawing functionality
 * @doc.layer core
 * @doc.pattern Manager
 */

export interface Point {
  x: number;
  y: number;
  pressure?: number;
}

export interface DrawingStroke {
  id: string;
  points: Point[];
  color: string;
  width: number;
  tool: 'pen' | 'pencil' | 'marker' | 'highlighter' | 'eraser';
  opacity?: number;
  timestamp: number;
}

export interface DrawingNode {
  id: string;
  type: 'drawing';
  strokes: DrawingStroke[];
  bounds: { x: number; y: number; width: number; height: number };
}

export class DrawingManager {
  private currentStroke: DrawingStroke | null = null;

  constructor() {}

  /**
   * Start new stroke
   */
  startStroke(
    point: Point,
    tool: DrawingStroke['tool'] = 'pen',
    color: string = '#000000',
    width: number = 2,
    opacity: number = 1
  ): DrawingStroke {
    this.currentStroke = {
      id: `stroke_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      points: [point],
      color,
      width,
      tool,
      opacity,
      timestamp: Date.now(),
    };
    return this.currentStroke;
  }

  /**
   * Add point to current stroke
   */
  addPoint(point: Point): void {
    if (!this.currentStroke) return;
    this.currentStroke.points.push(point);
  }

  /**
   * End current stroke
   */
  endStroke(): DrawingStroke | null {
    const stroke = this.currentStroke;
    this.currentStroke = null;

    // Smooth the stroke
    if (stroke && stroke.points.length > 2) {
      stroke.points = this.smoothPoints(stroke.points);
    }

    return stroke;
  }

  /**
   * Smooth points using Catmull-Rom spline with higher quality interpolation
   */
  private smoothPoints(points: Point[]): Point[] {
    if (points.length < 3) return points;

    // First pass: reduce noise with simple averaging
    const denoised = this.denoisePoints(points);

    const smoothed: Point[] = [denoised[0]];

    for (let i = 0; i < denoised.length - 1; i++) {
      const p0 = i > 0 ? denoised[i - 1] : denoised[i];
      const p1 = denoised[i];
      const p2 = denoised[i + 1];
      const p3 = i < denoised.length - 2 ? denoised[i + 2] : p2;

      // Add interpolated points using higher density
      for (let t = 0; t < 1; t += 0.1) {
        const t2 = t * t;
        const t3 = t2 * t;

        const x =
          0.5 *
          (2 * p1.x +
            (-p0.x + p2.x) * t +
            (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
            (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);

        const y =
          0.5 *
          (2 * p1.y +
            (-p0.y + p2.y) * t +
            (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
            (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);

        smoothed.push({ x, y, pressure: p1.pressure });
      }
    }

    smoothed.push(denoised[denoised.length - 1]);
    return smoothed;
  }

  /**
   * Denoise points by averaging nearby points
   */
  private denoisePoints(points: Point[], window: number = 3): Point[] {
    if (points.length <= window) return points;

    const denoised: Point[] = [];
    const half = Math.floor(window / 2);

    for (let i = 0; i < points.length; i++) {
      const start = Math.max(0, i - half);
      const end = Math.min(points.length, i + half + 1);
      let sumX = 0,
        sumY = 0;

      for (let j = start; j < end; j++) {
        sumX += points[j].x;
        sumY += points[j].y;
      }

      const count = end - start;
      denoised.push({
        x: sumX / count,
        y: sumY / count,
        pressure: points[i].pressure,
      });
    }

    return denoised;
  }

  /**
   * Calculate bounds of strokes
   */
  calculateBounds(strokes: DrawingStroke[]): {
    x: number;
    y: number;
    width: number;
    height: number;
  } {
    if (strokes.length === 0) {
      return { x: 0, y: 0, width: 100, height: 100 };
    }

    let minX = Infinity,
      minY = Infinity;
    let maxX = -Infinity,
      maxY = -Infinity;

    for (const stroke of strokes) {
      for (const point of stroke.points) {
        minX = Math.min(minX, point.x);
        minY = Math.min(minY, point.y);
        maxX = Math.max(maxX, point.x);
        maxY = Math.max(maxY, point.y);
      }
    }

    return {
      x: minX - 10,
      y: minY - 10,
      width: maxX - minX + 20,
      height: maxY - minY + 20,
    };
  }

  /**
   * Convert strokes to SVG path
   */
  strokesToSVG(strokes: DrawingStroke[]): string {
    return strokes.map((stroke) => this.strokeToSVGPath(stroke)).join('\n');
  }

  /**
   * Convert single stroke to SVG path
   */
  private strokeToSVGPath(stroke: DrawingStroke): string {
    if (stroke.points.length === 0) return '';

    let path = `M ${stroke.points[0].x} ${stroke.points[0].y}`;

    for (let i = 1; i < stroke.points.length; i++) {
      path += ` L ${stroke.points[i].x} ${stroke.points[i].y}`;
    }

    return `<path d="${path}" stroke="${stroke.color}" stroke-width="${stroke.width}" stroke-opacity="${stroke.opacity ?? 1}" fill="none" stroke-linecap="round" stroke-linejoin="round" />`;
  }

  /**
   * Simplify stroke (reduce points)
   */
  simplifyStroke(stroke: DrawingStroke, tolerance: number = 2): DrawingStroke {
    if (stroke.points.length <= 2) return stroke;

    const simplified = this.douglasPeucker(stroke.points, tolerance);
    return { ...stroke, points: simplified };
  }

  /**
   * Douglas-Peucker algorithm for point reduction
   */
  private douglasPeucker(points: Point[], tolerance: number): Point[] {
    if (points.length <= 2) return points;

    let maxDistance = 0;
    let maxIndex = 0;

    const first = points[0];
    const last = points[points.length - 1];

    for (let i = 1; i < points.length - 1; i++) {
      const distance = this.perpendicularDistance(points[i], first, last);
      if (distance > maxDistance) {
        maxDistance = distance;
        maxIndex = i;
      }
    }

    if (maxDistance > tolerance) {
      const left = this.douglasPeucker(
        points.slice(0, maxIndex + 1),
        tolerance
      );
      const right = this.douglasPeucker(points.slice(maxIndex), tolerance);
      return left.slice(0, -1).concat(right);
    } else {
      return [first, last];
    }
  }

  /**
   * Calculate perpendicular distance from point to line
   */
  private perpendicularDistance(
    point: Point,
    lineStart: Point,
    lineEnd: Point
  ): number {
    const dx = lineEnd.x - lineStart.x;
    const dy = lineEnd.y - lineStart.y;
    const mag = Math.sqrt(dx * dx + dy * dy);

    if (mag === 0)
      return Math.sqrt(
        Math.pow(point.x - lineStart.x, 2) + Math.pow(point.y - lineStart.y, 2)
      );

    const u =
      ((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) /
      (mag * mag);
    const closestX = lineStart.x + u * dx;
    const closestY = lineStart.y + u * dy;

    return Math.sqrt(
      Math.pow(point.x - closestX, 2) + Math.pow(point.y - closestY, 2)
    );
  }
}
