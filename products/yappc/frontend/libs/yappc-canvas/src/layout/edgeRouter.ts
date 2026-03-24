/**
 * Edge Router - Advanced edge routing algorithms
 *
 * Provides multiple routing strategies for canvas edges:
 * - Orthogonal (Manhattan) routing with obstacle avoidance
 * - Bezier spline routing for smooth curves
 * - Straight line routing (default)
 * - Waypoint-based custom routing
 *
 * @module canvas/layout/edgeRouter
 */

import type { Point, Bounds } from '../types/canvas-document';

// Routing algorithm types
/**
 *
 */
export type RoutingAlgorithm = 'straight' | 'orthogonal' | 'bezier' | 'waypoint';

// Edge routing configuration
/**
 *
 */
export interface EdgeRouterConfig {
  /**
   * Routing algorithm to use
   * @default 'straight'
   */
  algorithm?: RoutingAlgorithm;

  /**
   * Padding around obstacles (nodes) in pixels
   * @default 20
   */
  obstaclePadding?: number;

  /**
   * Minimum distance between edge segments in orthogonal routing
   * @default 10
   */
  segmentSpacing?: number;

  /**
   * Bezier curve tension (0-1, higher = tighter curves)
   * @default 0.5
   */
  curveTension?: number;

  /**
   * Enable obstacle avoidance
   * @default true
   */
  avoidObstacles?: boolean;

  /**
   * Maximum iterations for pathfinding
   * @default 100
   */
  maxIterations?: number;
}

// Waypoint for custom edge paths
/**
 *
 */
export interface Waypoint extends Point {
  /**
   * Unique waypoint identifier
   */
  id: string;

  /**
   * Whether this waypoint is draggable
   * @default true
   */
  draggable?: boolean;
}

// Edge routing result
/**
 *
 */
export interface EdgeRoute {
  /**
   * Points along the edge path
   */
  points: readonly Point[];

  /**
   * SVG path string representation
   */
  pathString: string;

  /**
   * Total path length in pixels
   */
  length: number;

  /**
   * Whether obstacles were avoided
   */
  obstaclesAvoided: boolean;
}

// Default configuration
const DEFAULT_CONFIG: Required<EdgeRouterConfig> = {
  algorithm: 'straight',
  obstaclePadding: 20,
  segmentSpacing: 10,
  curveTension: 0.5,
  avoidObstacles: true,
  maxIterations: 100,
};

/**
 * Route an edge between two points using specified algorithm
 *
 * @param source - Start point
 * @param target - End point
 * @param obstacles - Array of obstacle bounds to avoid
 * @param config - Routing configuration
 * @returns Edge route with points and path string
 *
 * @example
 * ```typescript
 * const route = routeEdge(
 *   { x: 0, y: 0 },
 *   { x: 200, y: 200 },
 *   [{ x: 50, y: 50, width: 100, height: 100 }],
 *   { algorithm: 'orthogonal', avoidObstacles: true }
 * );
 * ```
 */
export function routeEdge(
  source: Point,
  target: Point,
  obstacles: readonly Bounds[] = [],
  config: EdgeRouterConfig = {}
): EdgeRoute {
  const cfg = { ...DEFAULT_CONFIG, ...config };

  switch (cfg.algorithm) {
    case 'orthogonal':
      return routeOrthogonal(source, target, obstacles, cfg);
    case 'bezier':
      return routeBezier(source, target, obstacles, cfg);
    case 'waypoint':
      return routeStraight(source, target); // Waypoints handled separately
    case 'straight':
    default:
      return routeStraight(source, target);
  }
}

/**
 * Route edge with custom waypoints
 *
 * @param waypoints - Array of waypoints defining the path
 * @param config - Routing configuration
 * @returns Edge route through waypoints
 *
 * @example
 * ```typescript
 * const route = routeWithWaypoints([
 *   { id: 'w1', x: 0, y: 0 },
 *   { id: 'w2', x: 100, y: 50 },
 *   { id: 'w3', x: 200, y: 100 }
 * ]);
 * ```
 */
export function routeWithWaypoints(
  waypoints: readonly Waypoint[],
  config: EdgeRouterConfig = {}
): EdgeRoute {
  if (waypoints.length < 2) {
    throw new Error('At least 2 waypoints required');
  }

  const cfg = { ...DEFAULT_CONFIG, ...config };
  const points = waypoints.map(({ x, y }) => ({ x, y }));

  if (cfg.algorithm === 'bezier') {
    return routeBezierThroughPoints(points, cfg);
  }

  return routeStraightThroughPoints(points);
}

// Straight line routing
/**
 *
 */
function routeStraight(source: Point, target: Point): EdgeRoute {
  const points = [source, target];
  const pathString = `M ${source.x} ${source.y} L ${target.x} ${target.y}`;
  const length = distance(source, target);

  return {
    points,
    pathString,
    length,
    obstaclesAvoided: false,
  };
}

// Straight lines through multiple points
/**
 *
 */
function routeStraightThroughPoints(points: readonly Point[]): EdgeRoute {
  const pathParts = [`M ${points[0].x} ${points[0].y}`];
  let totalLength = 0;

  for (let i = 1; i < points.length; i++) {
    pathParts.push(`L ${points[i].x} ${points[i].y}`);
    totalLength += distance(points[i - 1], points[i]);
  }

  return {
    points,
    pathString: pathParts.join(' '),
    length: totalLength,
    obstaclesAvoided: false,
  };
}

// Orthogonal (Manhattan) routing with obstacle avoidance
/**
 *
 */
function routeOrthogonal(
  source: Point,
  target: Point,
  obstacles: readonly Bounds[],
  config: Required<EdgeRouterConfig>
): EdgeRoute {
  if (!config.avoidObstacles || obstacles.length === 0) {
    return routeOrthogonalSimple(source, target);
  }

  // A* pathfinding with orthogonal constraints
  const path = findOrthogonalPath(source, target, obstacles, config);

  if (path.length === 0) {
    // Fallback to simple routing if pathfinding fails
    return routeOrthogonalSimple(source, target);
  }

  const points = path;
  const pathString = pointsToOrthogonalPath(points);
  const length = calculatePathLength(points);

  // Check if path actually avoided obstacles (more than simple 3-point path)
  const obstaclesAvoided = path.length > 3 && obstacles.length > 0;

  return {
    points,
    pathString,
    length,
    obstaclesAvoided,
  };
}

// Simple orthogonal routing without obstacle avoidance
/**
 *
 */
function routeOrthogonalSimple(source: Point, target: Point): EdgeRoute {
  const midX = (source.x + target.x) / 2;

  const points = [
    source,
    { x: midX, y: source.y },
    { x: midX, y: target.y },
    target,
  ];

  const pathString = pointsToOrthogonalPath(points);
  const length = calculatePathLength(points);

  return {
    points,
    pathString,
    length,
    obstaclesAvoided: false,
  };
}

// Find orthogonal path using A* algorithm
/**
 *
 */
function findOrthogonalPath(
  source: Point,
  target: Point,
  obstacles: readonly Bounds[],
  config: Required<EdgeRouterConfig>
): Point[] {
  // Grid-based A* pathfinding
  const gridSize = config.segmentSpacing;
  const padding = config.obstaclePadding;

  // Snap points to grid
  const start = snapToGrid(source, gridSize);
  const end = snapToGrid(target, gridSize);

  // If start and end are the same, return direct path
  if (start.x === end.x && start.y === end.y) {
    return [source, target];
  }

  // Build obstacle grid
  const obstacleGrid = buildObstacleGrid(obstacles, padding, gridSize);

  // A* search
  const openSet = new Set<string>([pointKey(start)]);
  const cameFrom = new Map<string, Point>();
  const gScore = new Map<string, number>([[pointKey(start), 0]]);
  const fScore = new Map<string, number>([[pointKey(start), heuristic(start, end)]]);

  let iterations = 0;

  while (openSet.size > 0 && iterations < config.maxIterations) {
    iterations++;

    // Find node with lowest fScore
    let current: Point | null = null;
    let currentKey = '';
    let lowestF = Infinity;

    for (const key of openSet) {
      const f = fScore.get(key) ?? Infinity;
      if (f < lowestF) {
        lowestF = f;
        currentKey = key;
        current = keyToPoint(key);
      }
    }

    if (!current) break;

    // Check if we reached the target
    if (Math.abs(current.x - end.x) < gridSize && Math.abs(current.y - end.y) < gridSize) {
      return reconstructPath(cameFrom, current, source, target);
    }

    openSet.delete(currentKey);

    // Explore orthogonal neighbors
    const neighbors = getOrthogonalNeighbors(current, gridSize);

    for (const neighbor of neighbors) {
      const neighborKey = pointKey(neighbor);

      // Skip if obstacle
      if (isObstacle(neighbor, obstacleGrid)) {
        continue;
      }

      const tentativeG = (gScore.get(currentKey) ?? Infinity) + distance(current, neighbor);

      if (tentativeG < (gScore.get(neighborKey) ?? Infinity)) {
        cameFrom.set(neighborKey, current);
        gScore.set(neighborKey, tentativeG);
        fScore.set(neighborKey, tentativeG + heuristic(neighbor, end));

        if (!openSet.has(neighborKey)) {
          openSet.add(neighborKey);
        }
      }
    }
  }

  // No path found, return empty array
  return [];
}

// Bezier spline routing
/**
 *
 */
function routeBezier(
  source: Point,
  target: Point,
  obstacles: readonly Bounds[],
  config: Required<EdgeRouterConfig>
): EdgeRoute {
  const tension = config.curveTension;

  // Calculate control points for cubic Bezier curve
  const dx = target.x - source.x;

  // Control points at 1/3 and 2/3 of the distance
  const cp1 = {
    x: source.x + (dx * tension),
    y: source.y,
  };

  const cp2 = {
    x: target.x - (dx * tension),
    y: target.y,
  };

  const pathString = `M ${source.x} ${source.y} C ${cp1.x} ${cp1.y}, ${cp2.x} ${cp2.y}, ${target.x} ${target.y}`;

  // Sample points along curve for length calculation
  const samples = 20;
  const points: Point[] = [source];

  for (let i = 1; i < samples; i++) {
    const t = i / samples;
    points.push(cubicBezierPoint(source, cp1, cp2, target, t));
  }

  points.push(target);

  const length = calculatePathLength(points);

  return {
    points: [source, cp1, cp2, target],
    pathString,
    length,
    obstaclesAvoided: false,
  };
}

// Bezier curve through multiple points
/**
 *
 */
function routeBezierThroughPoints(
  points: readonly Point[],
  config: Required<EdgeRouterConfig>
): EdgeRoute {
  if (points.length < 2) {
    throw new Error('At least 2 points required');
  }

  if (points.length === 2) {
    return routeBezier(points[0], points[1], [], config);
  }

  // Catmull-Rom to Bezier conversion for smooth curve through all points
  const pathParts: string[] = [`M ${points[0].x} ${points[0].y}`];
  let totalLength = 0;
  const allPoints: Point[] = [points[0]];

  for (let i = 0; i < points.length - 1; i++) {
    const p0 = i > 0 ? points[i - 1] : points[i];
    const p1 = points[i];
    const p2 = points[i + 1];
    const p3 = i < points.length - 2 ? points[i + 2] : p2;

    // Catmull-Rom to cubic Bezier control points
    const cp1 = {
      x: p1.x + (p2.x - p0.x) / 6,
      y: p1.y + (p2.y - p0.y) / 6,
    };

    const cp2 = {
      x: p2.x - (p3.x - p1.x) / 6,
      y: p2.y - (p3.y - p1.y) / 6,
    };

    pathParts.push(`C ${cp1.x} ${cp1.y}, ${cp2.x} ${cp2.y}, ${p2.x} ${p2.y}`);

    // Sample for length
    for (let t = 0.1; t <= 1; t += 0.1) {
      const pt = cubicBezierPoint(p1, cp1, cp2, p2, t);
      allPoints.push(pt);
      totalLength += distance(allPoints[allPoints.length - 2], pt);
    }
  }

  return {
    points: allPoints,
    pathString: pathParts.join(' '),
    length: totalLength,
    obstaclesAvoided: false,
  };
}

// Helper functions

/**
 *
 */
function distance(p1: Point, p2: Point): number {
  const dx = p2.x - p1.x;
  const dy = p2.y - p1.y;
  return Math.sqrt(dx * dx + dy * dy);
}

/**
 *
 */
function snapToGrid(point: Point, gridSize: number): Point {
  return {
    x: Math.round(point.x / gridSize) * gridSize,
    y: Math.round(point.y / gridSize) * gridSize,
  };
}

/**
 *
 */
function pointKey(point: Point): string {
  return `${point.x},${point.y}`;
}

/**
 *
 */
function keyToPoint(key: string): Point {
  const [x, y] = key.split(',').map(Number);
  return { x, y };
}

/**
 *
 */
function heuristic(a: Point, b: Point): number {
  // Manhattan distance for orthogonal routing
  return Math.abs(b.x - a.x) + Math.abs(b.y - a.y);
}

/**
 *
 */
function getOrthogonalNeighbors(point: Point, gridSize: number): Point[] {
  return [
    { x: point.x + gridSize, y: point.y }, // Right
    { x: point.x - gridSize, y: point.y }, // Left
    { x: point.x, y: point.y + gridSize }, // Down
    { x: point.x, y: point.y - gridSize }, // Up
  ];
}

/**
 *
 */
function buildObstacleGrid(
  obstacles: readonly Bounds[],
  padding: number,
  gridSize: number
): Set<string> {
  const grid = new Set<string>();

  for (const obstacle of obstacles) {
    const minX = Math.floor((obstacle.x - padding) / gridSize) * gridSize;
    const maxX = Math.ceil((obstacle.x + obstacle.width + padding) / gridSize) * gridSize;
    const minY = Math.floor((obstacle.y - padding) / gridSize) * gridSize;
    const maxY = Math.ceil((obstacle.y + obstacle.height + padding) / gridSize) * gridSize;

    for (let x = minX; x <= maxX; x += gridSize) {
      for (let y = minY; y <= maxY; y += gridSize) {
        grid.add(pointKey({ x, y }));
      }
    }
  }

  return grid;
}

/**
 *
 */
function isObstacle(point: Point, obstacleGrid: Set<string>): boolean {
  return obstacleGrid.has(pointKey(point));
}

/**
 *
 */
function reconstructPath(
  cameFrom: Map<string, Point>,
  current: Point,
  source: Point,
  target: Point
): Point[] {
  const path: Point[] = [target];
  let currentKey = pointKey(current);

  while (cameFrom.has(currentKey)) {
    current = cameFrom.get(currentKey)!;
    path.unshift(current);
    currentKey = pointKey(current);
  }

  path.unshift(source);
  return path;
}

/**
 *
 */
function pointsToOrthogonalPath(points: readonly Point[]): string {
  if (points.length === 0) return '';

  const parts = [`M ${points[0].x} ${points[0].y}`];

  for (let i = 1; i < points.length; i++) {
    parts.push(`L ${points[i].x} ${points[i].y}`);
  }

  return parts.join(' ');
}

/**
 *
 */
function calculatePathLength(points: readonly Point[]): number {
  let length = 0;
  for (let i = 1; i < points.length; i++) {
    length += distance(points[i - 1], points[i]);
  }
  return length;
}

/**
 *
 */
function cubicBezierPoint(p0: Point, p1: Point, p2: Point, p3: Point, t: number): Point {
  const mt = 1 - t;
  const mt2 = mt * mt;
  const mt3 = mt2 * mt;
  const t2 = t * t;
  const t3 = t2 * t;

  return {
    x: mt3 * p0.x + 3 * mt2 * t * p1.x + 3 * mt * t2 * p2.x + t3 * p3.x,
    y: mt3 * p0.y + 3 * mt2 * t * p1.y + 3 * mt * t2 * p2.y + t3 * p3.y,
  };
}

/**
 * Update waypoint position and recalculate edge route
 *
 * @param waypoints - Current waypoints
 * @param waypointId - ID of waypoint to update
 * @param newPosition - New position for the waypoint
 * @param config - Routing configuration
 * @returns Updated waypoints and new route
 *
 * @example
 * ```typescript
 * const { waypoints, route } = updateWaypoint(
 *   currentWaypoints,
 *   'w2',
 *   { x: 150, y: 75 },
 *   { algorithm: 'bezier' }
 * );
 * ```
 */
export function updateWaypoint(
  waypoints: readonly Waypoint[],
  waypointId: string,
  newPosition: Point,
  config: EdgeRouterConfig = {}
): { waypoints: Waypoint[]; route: EdgeRoute } {
  const updatedWaypoints = waypoints.map((wp) =>
    wp.id === waypointId ? { ...wp, ...newPosition } : wp
  );

  const route = routeWithWaypoints(updatedWaypoints, config);

  return { waypoints: updatedWaypoints, route };
}

/**
 * Add a waypoint to an existing edge
 *
 * @param waypoints - Current waypoints
 * @param position - Position for new waypoint
 * @param insertIndex - Index to insert waypoint (default: end)
 * @returns Updated waypoints array
 *
 * @example
 * ```typescript
 * const waypoints = addWaypoint(
 *   currentWaypoints,
 *   { x: 100, y: 50 },
 *   1 // Insert after first waypoint
 * );
 * ```
 */
export function addWaypoint(
  waypoints: readonly Waypoint[],
  position: Point,
  insertIndex?: number
): Waypoint[] {
  const newWaypoint: Waypoint = {
    id: `waypoint-${Date.now()}-${Math.random()}`,
    ...position,
    draggable: true,
  };

  const index = insertIndex ?? waypoints.length;
  const result = [...waypoints];
  result.splice(index, 0, newWaypoint);

  return result;
}

/**
 * Remove a waypoint from an edge
 *
 * @param waypoints - Current waypoints
 * @param waypointId - ID of waypoint to remove
 * @returns Updated waypoints array
 *
 * @example
 * ```typescript
 * const waypoints = removeWaypoint(currentWaypoints, 'w2');
 * ```
 */
export function removeWaypoint(
  waypoints: readonly Waypoint[],
  waypointId: string
): Waypoint[] {
  return waypoints.filter((wp) => wp.id !== waypointId);
}

/**
 * Find nearest point on edge path for label placement
 *
 * @param route - Edge route
 * @param ratio - Position along path (0-1)
 * @returns Point at specified ratio along path
 *
 * @example
 * ```typescript
 * const labelPos = getPointOnPath(route, 0.5); // Midpoint
 * ```
 */
export function getPointOnPath(route: EdgeRoute, ratio: number): Point {
  const clampedRatio = Math.max(0, Math.min(1, ratio));
  const targetLength = route.length * clampedRatio;

  let accumulatedLength = 0;
  const points = route.points;

  for (let i = 1; i < points.length; i++) {
    const segmentLength = distance(points[i - 1], points[i]);

    if (accumulatedLength + segmentLength >= targetLength) {
      const remainingLength = targetLength - accumulatedLength;
      const t = remainingLength / segmentLength;

      return {
        x: points[i - 1].x + (points[i].x - points[i - 1].x) * t,
        y: points[i - 1].y + (points[i].y - points[i - 1].y) * t,
      };
    }

    accumulatedLength += segmentLength;
  }

  return points[points.length - 1];
}
