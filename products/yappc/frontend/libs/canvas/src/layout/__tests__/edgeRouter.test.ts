/**
 * Edge Router Tests
 */

import { describe, it, expect } from 'vitest';

import {
  routeEdge,
  routeWithWaypoints,
  updateWaypoint,
  addWaypoint,
  removeWaypoint,
  getPointOnPath,
  type Waypoint,
  type EdgeRouterConfig,
} from '../edgeRouter';

import type { Point, Bounds } from '../../types/canvas-document';

describe('edgeRouter', () => {
  describe('routeEdge - straight', () => {
    it('should route straight line between two points', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 100, y: 100 };

      const route = routeEdge(source, target, [], { algorithm: 'straight' });

      expect(route.points).toHaveLength(2);
      expect(route.points[0]).toEqual(source);
      expect(route.points[1]).toEqual(target);
      expect(route.pathString).toBe('M 0 0 L 100 100');
      expect(route.length).toBeCloseTo(141.42, 1); // sqrt(100^2 + 100^2)
      expect(route.obstaclesAvoided).toBe(false);
    });

    it('should route horizontal line', () => {
      const source: Point = { x: 0, y: 50 };
      const target: Point = { x: 200, y: 50 };

      const route = routeEdge(source, target, [], { algorithm: 'straight' });

      expect(route.points).toHaveLength(2);
      expect(route.length).toBe(200);
    });

    it('should route vertical line', () => {
      const source: Point = { x: 50, y: 0 };
      const target: Point = { x: 50, y: 150 };

      const route = routeEdge(source, target, [], { algorithm: 'straight' });

      expect(route.points).toHaveLength(2);
      expect(route.length).toBe(150);
    });

    it('should use straight by default', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 50, y: 50 };

      const route = routeEdge(source, target);

      expect(route.points).toHaveLength(2);
      expect(route.pathString).toContain('M 0 0 L 50 50');
    });
  });

  describe('routeEdge - orthogonal', () => {
    it('should route orthogonal path without obstacles', () => {
      const source: Point = { x: 0, y: 50 };
      const target: Point = { x: 200, y: 150 };

      const route = routeEdge(source, target, [], { algorithm: 'orthogonal' });

      expect(route.points.length).toBeGreaterThanOrEqual(2);
      expect(route.points[0]).toEqual(source);
      expect(route.points[route.points.length - 1]).toEqual(target);
      expect(route.pathString).toContain('M 0 50');
      expect(route.pathString).toContain('L');
    });

    it('should create orthogonal segments', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 100, y: 100 };

      const route = routeEdge(source, target, [], { algorithm: 'orthogonal' });

      // Check that path has horizontal and vertical segments
      const points = route.points;
      expect(points.length).toBeGreaterThanOrEqual(3);

      // Verify orthogonal property - each segment should be horizontal or vertical
      for (let i = 1; i < points.length; i++) {
        const dx = Math.abs(points[i].x - points[i - 1].x);
        const dy = Math.abs(points[i].y - points[i - 1].y);
        // One of dx or dy should be 0 (or very close to 0 for floating point)
        expect(dx === 0 || dy === 0 || dx < 0.1 || dy < 0.1).toBe(true);
      }
    });

    it('should avoid obstacles when specified', () => {
      const source: Point = { x: 0, y: 50 };
      const target: Point = { x: 200, y: 50 };
      const obstacle: Bounds = { x: 80, y: 30, width: 40, height: 40 };

      const route = routeEdge(source, target, [obstacle], {
        algorithm: 'orthogonal',
        avoidObstacles: true,
        obstaclePadding: 10,
      });

      // Should attempt to route with more complexity than straight line
      expect(route.points.length).toBeGreaterThanOrEqual(2);
      expect(route.pathString).toContain('M 0 50');
      expect(route.pathString).toContain(' L ');
      expect(route.length).toBeGreaterThan(0);
    });

    it('should handle multiple obstacles', () => {
      const source: Point = { x: 0, y: 50 };
      const target: Point = { x: 300, y: 50 };
      const obstacles: Bounds[] = [
        { x: 50, y: 30, width: 40, height: 40 },
        { x: 150, y: 30, width: 40, height: 40 },
        { x: 250, y: 30, width: 40, height: 40 },
      ];

      const route = routeEdge(source, target, obstacles, {
        algorithm: 'orthogonal',
        avoidObstacles: true,
      });

      expect(route.points.length).toBeGreaterThan(2);
      // With multiple obstacles, path should be longer than straight line
      const straightDist = Math.sqrt((300 - 0) ** 2 + (50 - 50) ** 2);
      expect(route.length).toBeGreaterThanOrEqual(straightDist);
    });

    it('should respect obstacle padding configuration', () => {
      const source: Point = { x: 0, y: 50 };
      const target: Point = { x: 200, y: 50 };
      const obstacle: Bounds = { x: 90, y: 40, width: 20, height: 20 };

      const routeSmallPadding = routeEdge(source, target, [obstacle], {
        algorithm: 'orthogonal',
        avoidObstacles: true,
        obstaclePadding: 5,
      });

      const routeLargePadding = routeEdge(source, target, [obstacle], {
        algorithm: 'orthogonal',
        avoidObstacles: true,
        obstaclePadding: 30,
      });

      // Larger padding typically requires longer path
      expect(routeLargePadding.length).toBeGreaterThanOrEqual(routeSmallPadding.length);
    });

    it('should fallback to simple routing if pathfinding fails', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 50, y: 50 };

      // Create impossible scenario with max iterations = 0
      const route = routeEdge(source, target, [], {
        algorithm: 'orthogonal',
        maxIterations: 0,
      });

      // Should still return a valid route (fallback)
      expect(route.points.length).toBeGreaterThanOrEqual(2);
      expect(route.points[0]).toEqual(source);
      expect(route.points[route.points.length - 1]).toEqual(target);
    });
  });

  describe('routeEdge - bezier', () => {
    it('should create smooth bezier curve', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 200, y: 200 };

      const route = routeEdge(source, target, [], { algorithm: 'bezier' });

      expect(route.points.length).toBe(4); // source, cp1, cp2, target
      expect(route.pathString).toContain('M 0 0');
      expect(route.pathString).toContain('C'); // Cubic bezier
      expect(route.pathString).toContain('200 200');
      expect(route.length).toBeGreaterThan(0);
    });

    it('should respect curve tension', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 200, y: 200 };

      const looseCurve = routeEdge(source, target, [], {
        algorithm: 'bezier',
        curveTension: 0.3,
      });

      const tightCurve = routeEdge(source, target, [], {
        algorithm: 'bezier',
        curveTension: 0.7,
      });

      // Both should be valid routes
      expect(looseCurve.points.length).toBe(4);
      expect(tightCurve.points.length).toBe(4);

      // Control points should be different
      expect(looseCurve.points[1]).not.toEqual(tightCurve.points[1]);
    });

    it('should handle horizontal bezier curve', () => {
      const source: Point = { x: 0, y: 50 };
      const target: Point = { x: 200, y: 50 };

      const route = routeEdge(source, target, [], { algorithm: 'bezier' });

      expect(route.points[0]).toEqual(source);
      expect(route.points[3]).toEqual(target);
      // Control points should be on the same horizontal line for straight horizontal edge
      expect(route.points[1].y).toBe(50);
      expect(route.points[2].y).toBe(50);
    });

    it('should calculate correct path length', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 100, y: 0 };

      const route = routeEdge(source, target, [], { algorithm: 'bezier' });

      // Bezier curve length should be close to straight line for horizontal
      expect(route.length).toBeGreaterThan(90);
      expect(route.length).toBeLessThan(110);
    });
  });

  describe('routeWithWaypoints', () => {
    it('should route through multiple waypoints', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 100, y: 50 },
        { id: 'w3', x: 200, y: 100 },
      ];

      const route = routeWithWaypoints(waypoints);

      expect(route.points.length).toBeGreaterThanOrEqual(3);
      expect(route.points[0]).toEqual({ x: 0, y: 0 });
      expect(route.points[route.points.length - 1]).toEqual({ x: 200, y: 100 });
    });

    it('should throw error for less than 2 waypoints', () => {
      const waypoints: Waypoint[] = [{ id: 'w1', x: 0, y: 0 }];

      expect(() => routeWithWaypoints(waypoints)).toThrow('At least 2 waypoints required');
    });

    it('should support bezier routing through waypoints', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 100, y: 100 },
        { id: 'w3', x: 200, y: 0 },
      ];

      const route = routeWithWaypoints(waypoints, { algorithm: 'bezier' });

      expect(route.pathString).toContain('C'); // Should have cubic bezier curves
      expect(route.length).toBeGreaterThan(0);
    });

    it('should handle 2 waypoints', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 100, y: 100 },
      ];

      const route = routeWithWaypoints(waypoints);

      expect(route.points).toHaveLength(2);
    });

    it('should respect waypoint order', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 50, y: 100 },
        { id: 'w3', x: 100, y: 0 },
        { id: 'w4', x: 150, y: 100 },
      ];

      const route = routeWithWaypoints(waypoints);

      // Path should visit points in order
      expect(route.points[0]).toEqual({ x: 0, y: 0 });
      expect(route.points[route.points.length - 1]).toEqual({ x: 150, y: 100 });
    });
  });

  describe('updateWaypoint', () => {
    it('should update waypoint position', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 100, y: 100 },
        { id: 'w3', x: 200, y: 0 },
      ];

      const { waypoints: updated, route } = updateWaypoint(
        waypoints,
        'w2',
        { x: 150, y: 75 }
      );

      expect(updated).toHaveLength(3);
      expect(updated[1]).toEqual({ id: 'w2', x: 150, y: 75 });
      expect(route.points.length).toBeGreaterThanOrEqual(3);
    });

    it('should not modify other waypoints', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 100, y: 100 },
        { id: 'w3', x: 200, y: 0 },
      ];

      const { waypoints: updated } = updateWaypoint(
        waypoints,
        'w2',
        { x: 150, y: 75 }
      );

      expect(updated[0]).toEqual(waypoints[0]);
      expect(updated[2]).toEqual(waypoints[2]);
    });

    it('should recalculate route with new waypoint position', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 100, y: 0 },
        { id: 'w3', x: 200, y: 0 },
      ];

      const { route: route1 } = updateWaypoint(waypoints, 'w2', { x: 100, y: 0 });
      const { route: route2 } = updateWaypoint(waypoints, 'w2', { x: 100, y: 100 });

      // Moving waypoint should change route length
      expect(route2.length).toBeGreaterThan(route1.length);
    });

    it('should preserve waypoint properties', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0, draggable: true },
        { id: 'w2', x: 100, y: 100, draggable: false },
        { id: 'w3', x: 200, y: 0, draggable: true },
      ];

      const { waypoints: updated } = updateWaypoint(waypoints, 'w2', { x: 150, y: 75 });

      expect(updated[1].draggable).toBe(false);
    });
  });

  describe('addWaypoint', () => {
    it('should add waypoint at end by default', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 100, y: 100 },
      ];

      const updated = addWaypoint(waypoints, { x: 200, y: 0 });

      expect(updated).toHaveLength(3);
      expect(updated[2].x).toBe(200);
      expect(updated[2].y).toBe(0);
      expect(updated[2].id).toBeDefined();
      expect(updated[2].draggable).toBe(true);
    });

    it('should add waypoint at specified index', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 200, y: 0 },
      ];

      const updated = addWaypoint(waypoints, { x: 100, y: 50 }, 1);

      expect(updated).toHaveLength(3);
      expect(updated[1].x).toBe(100);
      expect(updated[1].y).toBe(50);
    });

    it('should generate unique waypoint ID', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
      ];

      const updated1 = addWaypoint(waypoints, { x: 50, y: 50 });
      const updated2 = addWaypoint(waypoints, { x: 100, y: 100 });

      expect(updated1[1].id).not.toBe(updated2[1].id);
    });

    it('should add at beginning', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 100, y: 100 },
      ];

      const updated = addWaypoint(waypoints, { x: 0, y: 0 }, 0);

      expect(updated).toHaveLength(2);
      expect(updated[0].x).toBe(0);
      expect(updated[0].y).toBe(0);
    });
  });

  describe('removeWaypoint', () => {
    it('should remove waypoint by ID', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 100, y: 100 },
        { id: 'w3', x: 200, y: 0 },
      ];

      const updated = removeWaypoint(waypoints, 'w2');

      expect(updated).toHaveLength(2);
      expect(updated[0].id).toBe('w1');
      expect(updated[1].id).toBe('w3');
    });

    it('should not modify array if ID not found', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 100, y: 100 },
      ];

      const updated = removeWaypoint(waypoints, 'w999');

      expect(updated).toHaveLength(2);
      expect(updated).toEqual(waypoints);
    });

    it('should handle removing all but one waypoint', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 100, y: 100 },
      ];

      const updated = removeWaypoint(waypoints, 'w2');

      expect(updated).toHaveLength(1);
      expect(updated[0].id).toBe('w1');
    });
  });

  describe('getPointOnPath', () => {
    it('should return start point for ratio 0', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 100, y: 100 };
      const route = routeEdge(source, target);

      const point = getPointOnPath(route, 0);

      expect(point.x).toBeCloseTo(0, 1);
      expect(point.y).toBeCloseTo(0, 1);
    });

    it('should return end point for ratio 1', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 100, y: 100 };
      const route = routeEdge(source, target);

      const point = getPointOnPath(route, 1);

      expect(point.x).toBeCloseTo(100, 1);
      expect(point.y).toBeCloseTo(100, 1);
    });

    it('should return midpoint for ratio 0.5', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 100, y: 0 };
      const route = routeEdge(source, target);

      const point = getPointOnPath(route, 0.5);

      expect(point.x).toBeCloseTo(50, 1);
      expect(point.y).toBeCloseTo(0, 1);
    });

    it('should handle ratio < 0', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 100, y: 100 };
      const route = routeEdge(source, target);

      const point = getPointOnPath(route, -0.5);

      // Should clamp to 0
      expect(point.x).toBeCloseTo(0, 1);
      expect(point.y).toBeCloseTo(0, 1);
    });

    it('should handle ratio > 1', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 100, y: 100 };
      const route = routeEdge(source, target);

      const point = getPointOnPath(route, 1.5);

      // Should clamp to 1
      expect(point.x).toBeCloseTo(100, 1);
      expect(point.y).toBeCloseTo(100, 1);
    });

    it('should work with complex paths', () => {
      const waypoints: Waypoint[] = [
        { id: 'w1', x: 0, y: 0 },
        { id: 'w2', x: 100, y: 0 },
        { id: 'w3', x: 100, y: 100 },
        { id: 'w4', x: 200, y: 100 },
      ];

      const route = routeWithWaypoints(waypoints);
      const point = getPointOnPath(route, 0.5);

      // Should be somewhere in the middle of the path
      expect(point.x).toBeGreaterThan(0);
      expect(point.x).toBeLessThan(200);
      expect(point.y).toBeGreaterThanOrEqual(0);
      expect(point.y).toBeLessThanOrEqual(100);
    });

    it('should interpolate correctly on orthogonal path', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 100, y: 100 };
      const route = routeEdge(source, target, [], { algorithm: 'orthogonal' });

      const point = getPointOnPath(route, 0.5);

      // Point should be on the path
      expect(point.x).toBeGreaterThanOrEqual(0);
      expect(point.x).toBeLessThanOrEqual(100);
      expect(point.y).toBeGreaterThanOrEqual(0);
      expect(point.y).toBeLessThanOrEqual(100);
    });
  });

  describe('Edge cases', () => {
    it('should handle zero-length edge', () => {
      const point: Point = { x: 50, y: 50 };
      const route = routeEdge(point, point);

      expect(route.points).toHaveLength(2);
      expect(route.length).toBe(0);
    });

    it('should handle very small distances', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 0.1, y: 0.1 };

      const route = routeEdge(source, target);

      expect(route.points).toHaveLength(2);
      expect(route.length).toBeGreaterThan(0);
      expect(route.length).toBeLessThan(1);
    });

    it('should handle very large coordinates', () => {
      const source: Point = { x: 0, y: 0 };
      const target: Point = { x: 10000, y: 10000 };

      const route = routeEdge(source, target);

      expect(route.points).toHaveLength(2);
      expect(route.length).toBeGreaterThan(14000);
    });

    it('should handle negative coordinates', () => {
      const source: Point = { x: -100, y: -100 };
      const target: Point = { x: 100, y: 100 };

      const route = routeEdge(source, target);

      expect(route.points).toHaveLength(2);
      expect(route.points[0]).toEqual(source);
      expect(route.points[1]).toEqual(target);
    });
  });
});
