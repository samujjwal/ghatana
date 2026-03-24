/**
 * Tests for Feature 2.8: Grouping & Constraints
 * Comprehensive test coverage for the constraint engine
 */

import { describe, it, expect } from 'vitest';

import {
  createConstraintEngine,
  addConstraint,
  removeConstraint,
  updateConstraint,
  getConstraintsForElement,
  setElement,
  removeElement,
  isPointInRect,
  isRectContained,
  rectsOverlap,
  clampRectToBounds,
  validateContainerConstraint,
  validateStickyConstraint,
  validateSizeConstraint,
  validatePositionConstraint,
  validateElement,
  applyConstraints,
  validateAll,
  getViolationsForElement,
  getViolationsBySeverity,
  clearViolations,
  createContainerConstraint,
  createStickyConstraint,
  createSizeConstraint,
  createPositionConstraint,
  updateStickyForAnchorMove,
  getConstraintStatistics,
  ConstraintType,
  ViolationSeverity,
} from '../constraintEngine';

import type {
  CanvasElement,
  ConstraintEngineState,
  ContainerConstraint,
  StickyConstraint,
  SizeConstraint,
  PositionConstraint,
  Point,
  Rect,
  ConstraintViolation,
} from '../constraintEngine';

describe('Feature 2.8: Grouping & Constraints - constraintEngine', () => {
  // Helper to create test elements
  const createElement = (
    id: string,
    x: number,
    y: number,
    width: number,
    height: number
  ): CanvasElement => ({
    id,
    x,
    y,
    width,
    height,
  });

  describe('Engine Creation', () => {
    it('should create engine with default options', () => {
      const engine = createConstraintEngine();

      expect(engine.constraints.size).toBe(0);
      expect(engine.elements.size).toBe(0);
      expect(engine.violations).toEqual([]);
      expect(engine.autoFix).toBe(true);
      expect(engine.validateOnMove).toBe(true);
    });

    it('should create engine with custom options', () => {
      const engine = createConstraintEngine({
        autoFix: false,
        validateOnMove: false,
      });

      expect(engine.autoFix).toBe(false);
      expect(engine.validateOnMove).toBe(false);
    });
  });

  describe('Constraint Management', () => {
    it('should add constraint to engine', () => {
      const engine = createConstraintEngine();
      const constraint = createContainerConstraint('c1', 'child', 'parent');

      addConstraint(engine, constraint);

      expect(engine.constraints.size).toBe(1);
      expect(engine.constraints.get('c1')).toEqual(constraint);
    });

    it('should remove constraint from engine', () => {
      const engine = createConstraintEngine();
      const constraint = createContainerConstraint('c1', 'child', 'parent');

      addConstraint(engine, constraint);
      removeConstraint(engine, 'c1');

      expect(engine.constraints.size).toBe(0);
    });

    it('should update constraint', () => {
      const engine = createConstraintEngine();
      const constraint = createContainerConstraint('c1', 'child', 'parent');

      addConstraint(engine, constraint);
      updateConstraint(engine, 'c1', { enabled: false, priority: 50 });

      const updated = engine.constraints.get('c1');
      expect(updated?.enabled).toBe(false);
      expect(updated?.priority).toBe(50);
    });

    it('should get constraints for element', () => {
      const engine = createConstraintEngine();
      const c1 = createContainerConstraint('c1', 'child', 'parent', { priority: 100 });
      const c2 = createSizeConstraint('c2', 'child', { priority: 50 });
      const c3 = createContainerConstraint('c3', 'other', 'parent');

      addConstraint(engine, c1);
      addConstraint(engine, c2);
      addConstraint(engine, c3);

      const constraints = getConstraintsForElement(engine, 'child');

      expect(constraints).toHaveLength(2);
      expect(constraints[0].id).toBe('c1'); // Higher priority first
      expect(constraints[1].id).toBe('c2');
    });

    it('should filter disabled constraints', () => {
      const engine = createConstraintEngine();
      const c1 = createContainerConstraint('c1', 'child', 'parent');
      c1.enabled = false;

      addConstraint(engine, c1);

      const constraints = getConstraintsForElement(engine, 'child');

      expect(constraints).toHaveLength(0);
    });
  });

  describe('Element Management', () => {
    it('should add element to engine', () => {
      const engine = createConstraintEngine();
      const element = createElement('e1', 100, 100, 50, 50);

      setElement(engine, element);

      expect(engine.elements.size).toBe(1);
      expect(engine.elements.get('e1')).toEqual(element);
    });

    it('should update element in engine', () => {
      const engine = createConstraintEngine();
      const element = createElement('e1', 100, 100, 50, 50);

      setElement(engine, element);
      setElement(engine, { ...element, x: 200 });

      expect(engine.elements.get('e1')?.x).toBe(200);
    });

    it('should remove element from engine', () => {
      const engine = createConstraintEngine();
      const element = createElement('e1', 100, 100, 50, 50);
      const constraint = createContainerConstraint('c1', 'e1', 'parent');

      setElement(engine, element);
      addConstraint(engine, constraint);

      removeElement(engine, 'e1');

      expect(engine.elements.size).toBe(0);
      expect(engine.constraints.size).toBe(0);
    });
  });

  describe('Geometric Utilities', () => {
    it('should check if point is in rectangle', () => {
      const rect: Rect = { x: 0, y: 0, width: 100, height: 100 };

      expect(isPointInRect({ x: 50, y: 50 }, rect)).toBe(true);
      expect(isPointInRect({ x: 0, y: 0 }, rect)).toBe(true);
      expect(isPointInRect({ x: 100, y: 100 }, rect)).toBe(true);
      expect(isPointInRect({ x: 101, y: 50 }, rect)).toBe(false);
      expect(isPointInRect({ x: -1, y: 50 }, rect)).toBe(false);
    });

    it('should check if rectangle is contained', () => {
      const outer: Rect = { x: 0, y: 0, width: 100, height: 100 };
      const inner: Rect = { x: 10, y: 10, width: 80, height: 80 };
      const overlapping: Rect = { x: 50, y: 50, width: 100, height: 100 };

      expect(isRectContained(inner, outer)).toBe(true);
      expect(isRectContained(overlapping, outer)).toBe(false);
    });

    it('should respect padding in containment check', () => {
      const outer: Rect = { x: 0, y: 0, width: 100, height: 100 };
      const inner: Rect = { x: 5, y: 5, width: 90, height: 90 };

      expect(isRectContained(inner, outer, 0)).toBe(true);
      expect(isRectContained(inner, outer, 5)).toBe(true);
      expect(isRectContained(inner, outer, 6)).toBe(false);
    });

    it('should check if rectangles overlap', () => {
      const rect1: Rect = { x: 0, y: 0, width: 100, height: 100 };
      const rect2: Rect = { x: 50, y: 50, width: 100, height: 100 };
      const rect3: Rect = { x: 200, y: 200, width: 100, height: 100 };

      expect(rectsOverlap(rect1, rect2)).toBe(true);
      expect(rectsOverlap(rect1, rect3)).toBe(false);
    });

    it('should clamp rectangle to bounds', () => {
      const bounds: Rect = { x: 0, y: 0, width: 100, height: 100 };
      const rect: Rect = { x: 150, y: 150, width: 20, height: 20 };

      const clamped = clampRectToBounds(rect, bounds);

      expect(clamped.x).toBe(80); // 100 - 20
      expect(clamped.y).toBe(80);
      expect(clamped.width).toBe(20);
      expect(clamped.height).toBe(20);
    });

    it('should clamp with padding', () => {
      const bounds: Rect = { x: 0, y: 0, width: 100, height: 100 };
      const rect: Rect = { x: -10, y: -10, width: 20, height: 20 };

      const clamped = clampRectToBounds(rect, bounds, 5);

      expect(clamped.x).toBe(5);
      expect(clamped.y).toBe(5);
    });
  });

  describe('Container Constraints', () => {
    it('should validate element within container', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 200, 200);
      const child = createElement('child', 50, 50, 50, 50);

      setElement(engine, container);
      setElement(engine, child);

      const constraint = createContainerConstraint('c1', 'child', 'container');
      const violation = validateContainerConstraint(constraint, child, engine.elements);

      expect(violation).toBeNull();
    });

    it('should detect element exceeding container bounds', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 100, 100);
      const child = createElement('child', 50, 50, 100, 100);

      setElement(engine, container);
      setElement(engine, child);

      const constraint = createContainerConstraint('c1', 'child', 'container');
      const violation = validateContainerConstraint(constraint, child, engine.elements);

      expect(violation).not.toBeNull();
      expect(violation?.severity).toBe(ViolationSeverity.ERROR);
      expect(violation?.suggestedFix).toBeDefined();
    });

    it('should respect padding in container constraint', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 100, 100);
      const child = createElement('child', 3, 3, 94, 94); // Exceeds padding bounds

      setElement(engine, container);
      setElement(engine, child);

      const constraint = createContainerConstraint('c1', 'child', 'container', {
        padding: 5,
      });
      const violation = validateContainerConstraint(constraint, child, engine.elements);

      expect(violation).not.toBeNull(); // Should violate due to padding
    });

    it('should allow partial overlap if configured', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 100, 100);
      const child = createElement('child', 50, 50, 100, 100);

      setElement(engine, container);
      setElement(engine, child);

      const constraint = createContainerConstraint('c1', 'child', 'container', {
        allowPartialOverlap: true,
      });
      const violation = validateContainerConstraint(constraint, child, engine.elements);

      // Should pass or be warning since there's overlap
      expect(violation === null || violation.severity === ViolationSeverity.WARNING).toBe(
        true
      );
    });

    it('should report error if container not found', () => {
      const engine = createConstraintEngine();
      const child = createElement('child', 50, 50, 50, 50);

      setElement(engine, child);

      const constraint = createContainerConstraint('c1', 'child', 'missing-container');
      const violation = validateContainerConstraint(constraint, child, engine.elements);

      expect(violation).not.toBeNull();
      expect(violation?.severity).toBe(ViolationSeverity.ERROR);
      expect(violation?.message).toContain('not found');
    });
  });

  describe('Sticky Constraints', () => {
    it('should validate sticky element in correct position', () => {
      const engine = createConstraintEngine();
      const anchor = createElement('anchor', 100, 100, 50, 50);
      const sticky = createElement('sticky', 120, 120, 30, 30);

      setElement(engine, anchor);
      setElement(engine, sticky);

      const constraint = createStickyConstraint('c1', 'sticky', 'anchor', {
        x: 20,
        y: 20,
      });
      const violation = validateStickyConstraint(constraint, sticky, engine.elements);

      expect(violation).toBeNull();
    });

    it('should detect sticky element not in position', () => {
      const engine = createConstraintEngine();
      const anchor = createElement('anchor', 100, 100, 50, 50);
      const sticky = createElement('sticky', 200, 200, 30, 30);

      setElement(engine, anchor);
      setElement(engine, sticky);

      const constraint = createStickyConstraint('c1', 'sticky', 'anchor', {
        x: 20,
        y: 20,
      });
      const violation = validateStickyConstraint(constraint, sticky, engine.elements);

      expect(violation).not.toBeNull();
      expect(violation?.severity).toBe(ViolationSeverity.WARNING);
      expect(violation?.suggestedFix).toEqual({ x: 120, y: 120 });
    });

    it('should update sticky elements when anchor moves', () => {
      const engine = createConstraintEngine();
      const anchor = createElement('anchor', 100, 100, 50, 50);
      const sticky = createElement('sticky', 120, 120, 30, 30);

      setElement(engine, anchor);
      setElement(engine, sticky);

      const constraint = createStickyConstraint('c1', 'sticky', 'anchor', { x: 20, y: 20 }, {
        maintainRelativePosition: true,
      });
      addConstraint(engine, constraint);

      updateStickyForAnchorMove(engine, 'anchor', 50, 50);

      expect(engine.elements.get('sticky')?.x).toBe(170);
      expect(engine.elements.get('sticky')?.y).toBe(170);
    });

    it('should not update sticky if maintainRelativePosition is false', () => {
      const engine = createConstraintEngine();
      const anchor = createElement('anchor', 100, 100, 50, 50);
      const sticky = createElement('sticky', 120, 120, 30, 30);

      setElement(engine, anchor);
      setElement(engine, sticky);

      const constraint = createStickyConstraint('c1', 'sticky', 'anchor', { x: 20, y: 20 }, {
        maintainRelativePosition: false,
      });
      addConstraint(engine, constraint);

      updateStickyForAnchorMove(engine, 'anchor', 50, 50);

      expect(engine.elements.get('sticky')?.x).toBe(120);
      expect(engine.elements.get('sticky')?.y).toBe(120);
    });

    it('should report error if anchor not found', () => {
      const engine = createConstraintEngine();
      const sticky = createElement('sticky', 120, 120, 30, 30);

      setElement(engine, sticky);

      const constraint = createStickyConstraint('c1', 'sticky', 'missing-anchor', {
        x: 20,
        y: 20,
      });
      const violation = validateStickyConstraint(constraint, sticky, engine.elements);

      expect(violation).not.toBeNull();
      expect(violation?.severity).toBe(ViolationSeverity.ERROR);
    });
  });

  describe('Size Constraints', () => {
    it('should validate element within size limits', () => {
      const element = createElement('e1', 100, 100, 150, 100);

      const constraint = createSizeConstraint('c1', 'e1', {
        minWidth: 100,
        maxWidth: 200,
        minHeight: 50,
        maxHeight: 150,
      });
      const violation = validateSizeConstraint(constraint, element);

      expect(violation).toBeNull();
    });

    it('should detect width below minimum', () => {
      const element = createElement('e1', 100, 100, 50, 100);

      const constraint = createSizeConstraint('c1', 'e1', { minWidth: 100 });
      const violation = validateSizeConstraint(constraint, element);

      expect(violation).not.toBeNull();
      expect(violation?.suggestedFix?.width).toBe(100);
    });

    it('should detect width above maximum', () => {
      const element = createElement('e1', 100, 100, 250, 100);

      const constraint = createSizeConstraint('c1', 'e1', { maxWidth: 200 });
      const violation = validateSizeConstraint(constraint, element);

      expect(violation).not.toBeNull();
      expect(violation?.suggestedFix?.width).toBe(200);
    });

    it('should detect height violations', () => {
      const element = createElement('e1', 100, 100, 100, 200);

      const constraint = createSizeConstraint('c1', 'e1', {
        minHeight: 50,
        maxHeight: 150,
      });
      const violation = validateSizeConstraint(constraint, element);

      expect(violation).not.toBeNull();
      expect(violation?.suggestedFix?.height).toBe(150);
    });

    it('should enforce aspect ratio', () => {
      const element = createElement('e1', 100, 100, 200, 100);

      const constraint = createSizeConstraint('c1', 'e1', { aspectRatio: 1 });
      const violation = validateSizeConstraint(constraint, element);

      expect(violation).not.toBeNull();
      expect(violation?.suggestedFix?.height).toBe(200); // Width/aspectRatio
    });

    it('should allow aspect ratio within tolerance', () => {
      const element = createElement('e1', 100, 100, 200, 100);

      const constraint = createSizeConstraint('c1', 'e1', { aspectRatio: 2 });
      const violation = validateSizeConstraint(constraint, element);

      expect(violation).toBeNull();
    });
  });

  describe('Position Constraints', () => {
    it('should validate grid snapping', () => {
      const engine = createConstraintEngine();
      const element = createElement('e1', 100, 100, 50, 50);

      setElement(engine, element);

      const constraint = createPositionConstraint('c1', 'e1', { gridSize: 10 });
      const violation = validatePositionConstraint(constraint, element, engine.elements);

      expect(violation).toBeNull();
    });

    it('should detect position not snapped to grid', () => {
      const engine = createConstraintEngine();
      const element = createElement('e1', 105, 107, 50, 50);

      setElement(engine, element);

      const constraint = createPositionConstraint('c1', 'e1', { gridSize: 10 });
      const violation = validatePositionConstraint(constraint, element, engine.elements);

      expect(violation).not.toBeNull();
      expect(violation?.suggestedFix?.x).toBe(110);
      expect(violation?.suggestedFix?.y).toBe(110);
    });

    it('should validate bounds constraint', () => {
      const engine = createConstraintEngine();
      const element = createElement('e1', 50, 50, 50, 50);

      setElement(engine, element);

      const constraint = createPositionConstraint('c1', 'e1', {
        bounds: { x: 0, y: 0, width: 200, height: 200 },
      });
      const violation = validatePositionConstraint(constraint, element, engine.elements);

      expect(violation).toBeNull();
    });

    it('should detect element outside bounds', () => {
      const engine = createConstraintEngine();
      const element = createElement('e1', 250, 250, 50, 50);

      setElement(engine, element);

      const constraint = createPositionConstraint('c1', 'e1', {
        bounds: { x: 0, y: 0, width: 200, height: 200 },
      });
      const violation = validatePositionConstraint(constraint, element, engine.elements);

      expect(violation).not.toBeNull();
      expect(violation?.suggestedFix?.x).toBe(150); // 200 - 50
      expect(violation?.suggestedFix?.y).toBe(150);
    });

    it('should validate alignment constraint', () => {
      const engine = createConstraintEngine();
      const target = createElement('target', 100, 50, 50, 50);
      const element = createElement('e1', 100, 100, 50, 50);

      setElement(engine, target);
      setElement(engine, element);

      const constraint = createPositionConstraint('c1', 'e1', { alignTo: 'target' });
      const violation = validatePositionConstraint(constraint, element, engine.elements);

      expect(violation).toBeNull();
    });

    it('should detect misalignment', () => {
      const engine = createConstraintEngine();
      const target = createElement('target', 100, 50, 50, 50);
      const element = createElement('e1', 150, 100, 50, 50);

      setElement(engine, target);
      setElement(engine, element);

      const constraint = createPositionConstraint('c1', 'e1', { alignTo: 'target' });
      const violation = validatePositionConstraint(constraint, element, engine.elements);

      expect(violation).not.toBeNull();
      expect(violation?.suggestedFix?.x).toBe(100);
    });
  });

  describe('Validation and Fixing', () => {
    it('should validate element against all constraints', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 200, 200);
      const element = createElement('e1', 250, 250, 50, 50);

      setElement(engine, container);
      setElement(engine, element);

      addConstraint(
        engine,
        createContainerConstraint('c1', 'e1', 'container')
      );
      addConstraint(engine, createSizeConstraint('c2', 'e1', { minWidth: 100 }));

      const violations = validateElement(engine, 'e1');

      expect(violations).toHaveLength(2);
    });

    it('should apply constraint fixes when autoFix enabled', () => {
      const engine = createConstraintEngine({ autoFix: true });
      const container = createElement('container', 0, 0, 200, 200);
      const element = createElement('e1', 250, 250, 50, 50);

      setElement(engine, container);
      setElement(engine, element);

      addConstraint(engine, createContainerConstraint('c1', 'e1', 'container'));

      const result = applyConstraints(engine, element);

      expect(result.fixed).toBe(true);
      expect(result.element.x).toBe(150); // Clamped to container
      expect(result.element.y).toBe(150);
      expect(result.violations).toHaveLength(1);
    });

    it('should not apply fixes when autoFix disabled', () => {
      const engine = createConstraintEngine({ autoFix: false });
      const container = createElement('container', 0, 0, 200, 200);
      const element = createElement('e1', 250, 250, 50, 50);

      setElement(engine, container);
      setElement(engine, element);

      addConstraint(engine, createContainerConstraint('c1', 'e1', 'container'));

      const result = applyConstraints(engine, element);

      expect(result.fixed).toBe(false);
      expect(result.element.x).toBe(250); // Unchanged
      expect(result.violations).toHaveLength(1);
    });

    it('should respect constraint priority order', () => {
      const engine = createConstraintEngine();
      const element = createElement('e1', 100, 100, 50, 50);

      setElement(engine, element);

      addConstraint(
        engine,
        createPositionConstraint('c1', 'e1', { gridSize: 10, priority: 100 })
      );
      addConstraint(
        engine,
        createPositionConstraint('c2', 'e1', {
          bounds: { x: 0, y: 0, width: 50, height: 50 },
          priority: 50,
        })
      );

      const constraints = getConstraintsForElement(engine, 'e1');

      expect(constraints[0].id).toBe('c1'); // Higher priority first
    });
  });

  describe('Violation Management', () => {
    it('should validate all elements', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 200, 200);
      const e1 = createElement('e1', 250, 250, 50, 50);
      const e2 = createElement('e2', 50, 50, 50, 50);

      setElement(engine, container);
      setElement(engine, e1);
      setElement(engine, e2);

      addConstraint(engine, createContainerConstraint('c1', 'e1', 'container'));
      addConstraint(engine, createContainerConstraint('c2', 'e2', 'container'));

      validateAll(engine);

      expect(engine.violations).toHaveLength(1); // Only e1 violates
    });

    it('should get violations for specific element', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 200, 200);
      const element = createElement('e1', 250, 250, 50, 50);

      setElement(engine, container);
      setElement(engine, element);

      addConstraint(engine, createContainerConstraint('c1', 'e1', 'container'));
      addConstraint(engine, createSizeConstraint('c2', 'e1', { minWidth: 100 }));

      validateAll(engine);

      const violations = getViolationsForElement(engine, 'e1');

      expect(violations).toHaveLength(2);
    });

    it('should filter violations by severity', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 200, 200);
      const element = createElement('e1', 250, 250, 50, 50);

      setElement(engine, container);
      setElement(engine, element);

      addConstraint(engine, createContainerConstraint('c1', 'e1', 'container'));

      validateAll(engine);

      const errors = getViolationsBySeverity(engine, ViolationSeverity.ERROR);

      expect(errors.length).toBeGreaterThan(0);
    });

    it('should clear violations', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 200, 200);
      const element = createElement('e1', 250, 250, 50, 50);

      setElement(engine, container);
      setElement(engine, element);

      addConstraint(engine, createContainerConstraint('c1', 'e1', 'container'));

      validateAll(engine);
      expect(engine.violations.length).toBeGreaterThan(0);

      clearViolations(engine);
      expect(engine.violations).toHaveLength(0);
    });

    it('should remove violations when constraint is removed', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 200, 200);
      const element = createElement('e1', 250, 250, 50, 50);

      setElement(engine, container);
      setElement(engine, element);

      addConstraint(engine, createContainerConstraint('c1', 'e1', 'container'));

      validateAll(engine);
      expect(engine.violations.length).toBeGreaterThan(0);

      removeConstraint(engine, 'c1');
      expect(engine.violations).toHaveLength(0);
    });
  });

  describe('Statistics', () => {
    it('should calculate constraint statistics', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 200, 200);
      const element = createElement('e1', 250, 250, 50, 50);

      setElement(engine, container);
      setElement(engine, element);

      addConstraint(engine, createContainerConstraint('c1', 'e1', 'container'));
      addConstraint(engine, createSizeConstraint('c2', 'e1', { minWidth: 100 }));
      addConstraint(
        engine,
        createPositionConstraint('c3', 'e1', { gridSize: 10 })
      );

      validateAll(engine);

      const stats = getConstraintStatistics(engine);

      expect(stats.totalConstraints).toBe(3);
      expect(stats.enabledConstraints).toBe(3);
      expect(stats.constraintsByType[ConstraintType.CONTAINER]).toBe(1);
      expect(stats.constraintsByType[ConstraintType.SIZE]).toBe(1);
      expect(stats.constraintsByType[ConstraintType.POSITION]).toBe(1);
      expect(stats.totalViolations).toBeGreaterThan(0);
    });

    it('should count disabled constraints separately', () => {
      const engine = createConstraintEngine();
      const element = createElement('e1', 100, 100, 50, 50);

      setElement(engine, element);

      const c1 = createContainerConstraint('c1', 'e1', 'container');
      c1.enabled = false;

      addConstraint(engine, c1);
      addConstraint(engine, createSizeConstraint('c2', 'e1', { minWidth: 100 }));

      const stats = getConstraintStatistics(engine);

      expect(stats.totalConstraints).toBe(2);
      expect(stats.enabledConstraints).toBe(1);
    });

    it('should categorize violations by severity', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 200, 200);
      const anchor = createElement('anchor', 100, 100, 50, 50);
      const element = createElement('e1', 250, 250, 50, 50);

      setElement(engine, container);
      setElement(engine, anchor);
      setElement(engine, element);

      addConstraint(engine, createContainerConstraint('c1', 'e1', 'container'));
      addConstraint(
        engine,
        createStickyConstraint('c2', 'e1', 'anchor', { x: 0, y: 0 })
      );

      validateAll(engine);

      const stats = getConstraintStatistics(engine);

      expect(stats.violationsBySeverity[ViolationSeverity.ERROR]).toBeGreaterThan(0);
    });
  });

  describe('Edge Cases', () => {
    it('should handle element with no constraints', () => {
      const engine = createConstraintEngine();
      const element = createElement('e1', 100, 100, 50, 50);

      setElement(engine, element);

      const violations = validateElement(engine, 'e1');

      expect(violations).toHaveLength(0);
    });

    it('should handle constraint referencing non-existent element', () => {
      const engine = createConstraintEngine();
      const element = createElement('e1', 100, 100, 50, 50);

      setElement(engine, element);

      addConstraint(
        engine,
        createContainerConstraint('c1', 'e1', 'non-existent')
      );

      const violations = validateElement(engine, 'e1');

      expect(violations).toHaveLength(1);
      expect(violations[0].message).toContain('not found');
    });

    it('should handle very small elements and containers', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', 0, 0, 1, 1);
      const element = createElement('e1', 0.1, 0.1, 0.5, 0.5);

      setElement(engine, container);
      setElement(engine, element);

      addConstraint(engine, createContainerConstraint('c1', 'e1', 'container'));

      const violations = validateElement(engine, 'e1');

      expect(violations.length).toBe(0); // Should work with small numbers
    });

    it('should handle negative coordinates', () => {
      const engine = createConstraintEngine();
      const container = createElement('container', -100, -100, 200, 200);
      const element = createElement('e1', -50, -50, 50, 50);

      setElement(engine, container);
      setElement(engine, element);

      addConstraint(engine, createContainerConstraint('c1', 'e1', 'container'));

      const violations = validateElement(engine, 'e1');

      expect(violations).toHaveLength(0);
    });

    it('should handle zero-size constraints', () => {
      const element = createElement('e1', 100, 100, 0, 0);

      const constraint = createSizeConstraint('c1', 'e1', {
        minWidth: 0,
        minHeight: 0,
      });
      const violation = validateSizeConstraint(constraint, element);

      expect(violation).toBeNull();
    });

    it('should handle multiple constraints with same priority', () => {
      const engine = createConstraintEngine();
      const element = createElement('e1', 100, 100, 50, 50);

      setElement(engine, element);

      addConstraint(engine, createSizeConstraint('c1', 'e1', { priority: 100 }));
      addConstraint(
        engine,
        createPositionConstraint('c2', 'e1', { priority: 100 })
      );

      const constraints = getConstraintsForElement(engine, 'e1');

      expect(constraints).toHaveLength(2);
    });
  });
});
