/**
 * @fileoverview Tests for semantic zoom functionality.
 */

import { describe, it, expect, beforeEach } from "vitest";
import {
  resolveZoomLevel,
  validateZoomBands,
  getDefaultScaleForLevel,
  snapToSemanticLevel,
  createFocusPath,
  pushFocusSegment,
  popFocusSegment,
  truncateFocusPath,
  getCurrentFocusSegment,
  createViewportContext,
  serializeViewportContext,
  deserializeViewportContext,
  DEFAULT_ZOOM_BANDS,
  SemanticZoomManager,
  type SemanticZoomBand,
  type FocusPathSegment,
  type ContextShiftPolicy,
  type ViewportContext,
} from "../semantic-zoom.js";

describe("Semantic Zoom", () => {
  describe("resolveZoomLevel", () => {
    it("should resolve overview level for low scale", () => {
      expect(resolveZoomLevel(0.15)).toBe("overview");
    });

    it("should resolve group level for medium-low scale", () => {
      expect(resolveZoomLevel(0.4)).toBe("group");
    });

    it("should resolve node level for standard scale", () => {
      expect(resolveZoomLevel(0.8)).toBe("node");
    });

    it("should resolve detail level for high scale", () => {
      expect(resolveZoomLevel(1.2)).toBe("detail");
    });

    it("should resolve evidence level for very high scale", () => {
      expect(resolveZoomLevel(2.0)).toBe("evidence");
    });

    it("should resolve source level for extreme scale", () => {
      expect(resolveZoomLevel(3.0)).toBe("source");
    });

    it("should clamp to first band for very low scale", () => {
      expect(resolveZoomLevel(0.05)).toBe("overview");
    });

    it("should clamp to last band for very high scale", () => {
      expect(resolveZoomLevel(10.0)).toBe("source");
    });
  });

  describe("validateZoomBands", () => {
    it("should validate correct band configuration", () => {
      const result = validateZoomBands(DEFAULT_ZOOM_BANDS);
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it("should reject empty bands", () => {
      const result = validateZoomBands([]);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain("At least one zoom band is required");
    });

    it("should detect overlapping bands", () => {
      const overlappingBands: SemanticZoomBand[] = [
        {
          level: "overview",
          minScale: 0.1,
          maxScale: 0.5,
          label: "Overview",
          description: "Test",
          defaultScale: 0.2,
          showLabels: false,
          showDetails: false,
          showConnections: true,
        },
        {
          level: "group",
          minScale: 0.3,
          maxScale: 0.6,
          label: "Groups",
          description: "Test",
          defaultScale: 0.5,
          showLabels: true,
          showDetails: false,
          showConnections: true,
        },
      ];
      const result = validateZoomBands(overlappingBands);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes("overlap"))).toBe(true);
    });

    it("should detect gaps between bands", () => {
      const gappedBands: SemanticZoomBand[] = [
        {
          level: "overview",
          minScale: 0.1,
          maxScale: 0.3,
          label: "Overview",
          description: "Test",
          defaultScale: 0.2,
          showLabels: false,
          showDetails: false,
          showConnections: true,
        },
        {
          level: "group",
          minScale: 0.5,
          maxScale: 0.8,
          label: "Groups",
          description: "Test",
          defaultScale: 0.6,
          showLabels: true,
          showDetails: false,
          showConnections: true,
        },
      ];
      const result = validateZoomBands(gappedBands);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes("Gap"))).toBe(true);
    });

    it("should detect invalid range (max < min)", () => {
      const invalidBands: SemanticZoomBand[] = [
        {
          level: "overview",
          minScale: 0.5,
          maxScale: 0.1,
          label: "Overview",
          description: "Test",
          defaultScale: 0.2,
          showLabels: false,
          showDetails: false,
          showConnections: true,
        },
      ];
      const result = validateZoomBands(invalidBands);
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.includes("invalid range"))).toBe(true);
    });

    it("should detect duplicate levels", () => {
      const duplicateBands: SemanticZoomBand[] = [
        {
          level: "overview",
          minScale: 0.1,
          maxScale: 0.3,
          label: "Overview 1",
          description: "Test",
          defaultScale: 0.2,
          showLabels: false,
          showDetails: false,
          showConnections: true,
        },
        {
          level: "overview",
          minScale: 0.3,
          maxScale: 0.5,
          label: "Overview 2",
          description: "Test",
          defaultScale: 0.4,
          showLabels: false,
          showDetails: false,
          showConnections: true,
        },
      ];
      const result = validateZoomBands(duplicateBands);
      expect(result.valid).toBe(false);
      expect(result.errors).toContain("Duplicate zoom levels detected");
    });
  });

  describe("getDefaultScaleForLevel", () => {
    it("should return correct default scale for overview", () => {
      expect(getDefaultScaleForLevel("overview")).toBe(0.2);
    });

    it("should return correct default scale for group", () => {
      expect(getDefaultScaleForLevel("group")).toBe(0.5);
    });

    it("should return correct default scale for node", () => {
      expect(getDefaultScaleForLevel("node")).toBe(1.0);
    });

    it("should return correct default scale for detail", () => {
      expect(getDefaultScaleForLevel("detail")).toBe(1.2);
    });

    it("should return correct default scale for evidence", () => {
      expect(getDefaultScaleForLevel("evidence")).toBe(2.0);
    });

    it("should return correct default scale for source", () => {
      expect(getDefaultScaleForLevel("source")).toBe(3.0);
    });
  });

  describe("snapToSemanticLevel", () => {
    it("should snap to nearest semantic level default", () => {
      expect(snapToSemanticLevel(0.15)).toBe(0.2); // overview default
      expect(snapToSemanticLevel(0.4)).toBe(0.5); // group default
      expect(snapToSemanticLevel(0.9)).toBe(1.0); // node default
    });
  });

  describe("FocusPath", () => {
    it("should create empty focus path", () => {
      const path = createFocusPath();
      expect(path.segments).toHaveLength(0);
      expect(path.depth).toBe(0);
    });

    it("should push segment to path", () => {
      let path = createFocusPath();
      const segment: FocusPathSegment = {
        id: "node-1",
        type: "node",
        label: "Node 1",
      };
      path = pushFocusSegment(path, segment);
      expect(path.segments).toHaveLength(1);
      expect(path.depth).toBe(1);
      expect(path.segments[0].id).toBe("node-1");
    });

    it("should push multiple segments", () => {
      let path = createFocusPath();
      path = pushFocusSegment(path, { id: "group-1", type: "group", label: "Group 1" });
      path = pushFocusSegment(path, { id: "node-1", type: "node", label: "Node 1" });
      path = pushFocusSegment(path, { id: "detail-1", type: "detail", label: "Detail 1" });

      expect(path.segments).toHaveLength(3);
      expect(path.depth).toBe(3);
    });

    it("should pop segment from path", () => {
      let path = createFocusPath();
      path = pushFocusSegment(path, { id: "group-1", type: "group", label: "Group 1" });
      path = pushFocusSegment(path, { id: "node-1", type: "node", label: "Node 1" });
      path = popFocusSegment(path);

      expect(path.segments).toHaveLength(1);
      expect(path.segments[0].id).toBe("group-1");
    });

    it("should handle pop on empty path", () => {
      const path = createFocusPath();
      const popped = popFocusSegment(path);
      expect(popped.segments).toHaveLength(0);
    });

    it("should truncate path to depth", () => {
      let path = createFocusPath();
      path = pushFocusSegment(path, { id: "group-1", type: "group", label: "Group 1" });
      path = pushFocusSegment(path, { id: "node-1", type: "node", label: "Node 1" });
      path = pushFocusSegment(path, { id: "detail-1", type: "detail", label: "Detail 1" });

      path = truncateFocusPath(path, 1);
      expect(path.segments).toHaveLength(1);
      expect(path.segments[0].id).toBe("group-1");
    });

    it("should get current focus segment", () => {
      let path = createFocusPath();
      expect(getCurrentFocusSegment(path)).toBeUndefined();

      path = pushFocusSegment(path, { id: "node-1", type: "node", label: "Node 1" });
      expect(getCurrentFocusSegment(path)?.id).toBe("node-1");
    });
  });

  describe("ViewportContext", () => {
    it("should create default viewport context", () => {
      const context = createViewportContext();
      expect(context.centerX).toBe(0);
      expect(context.centerY).toBe(0);
      expect(context.scale).toBe(1.0);
      expect(context.semanticLevel).toBe("node");
      expect(context.focusPath.depth).toBe(0);
    });

    it("should create viewport context with overrides", () => {
      const context = createViewportContext({
        centerX: 100,
        centerY: 200,
        scale: 2.0,
        semanticLevel: "detail",
      });
      expect(context.centerX).toBe(100);
      expect(context.centerY).toBe(200);
      expect(context.scale).toBe(2.0);
      expect(context.semanticLevel).toBe("detail");
    });

    it("should serialize and deserialize viewport context", () => {
      const context = createViewportContext({
        centerX: 100,
        centerY: 200,
        scale: 2.0,
        semanticLevel: "detail",
      });

      const serialized = serializeViewportContext(context);
      const deserialized = deserializeViewportContext(serialized);

      expect(deserialized.centerX).toBe(100);
      expect(deserialized.centerY).toBe(200);
      expect(deserialized.scale).toBe(2.0);
      expect(deserialized.semanticLevel).toBe("detail");
    });

    it("should handle invalid JSON during deserialization", () => {
      const deserialized = deserializeViewportContext("invalid json");
      expect(deserialized.centerX).toBe(0);
      expect(deserialized.scale).toBe(1.0);
    });
  });

  describe("SemanticZoomManager", () => {
    let manager: SemanticZoomManager;

    beforeEach(() => {
      manager = new SemanticZoomManager();
    });

    it("should initialize with default level", () => {
      expect(manager.getCurrentLevel()).toBe("node");
    });

    it("should reject invalid zoom bands in constructor", () => {
      const invalidBands: SemanticZoomBand[] = [
        {
          level: "overview",
          minScale: 0.5,
          maxScale: 0.1,
          label: "Invalid",
          description: "Test",
          defaultScale: 0.2,
          showLabels: false,
          showDetails: false,
          showConnections: true,
        },
      ];
      expect(() => new SemanticZoomManager(invalidBands)).toThrow();
    });

    it("should resolve level from scale", () => {
      expect(manager.resolveLevel(0.2)).toBe("overview");
      expect(manager.resolveLevel(1.0)).toBe("node");
      expect(manager.resolveLevel(3.0)).toBe("source");
    });

    it("should set level directly", () => {
      manager.setLevel("detail");
      expect(manager.getCurrentLevel()).toBe("detail");
    });

    it("should reject unknown level", () => {
      expect(() => manager.setLevel("unknown" as never)).toThrow();
    });

    it("should zoom in to next level", () => {
      manager.setLevel("overview");
      expect(manager.zoomIn()).toBe("group");
      expect(manager.zoomIn()).toBe("node");
    });

    it("should not zoom past source level", () => {
      manager.setLevel("source");
      expect(manager.zoomIn()).toBe("source");
    });

    it("should zoom out to previous level", () => {
      manager.setLevel("detail");
      expect(manager.zoomOut()).toBe("node");
      expect(manager.zoomOut()).toBe("group");
    });

    it("should not zoom past overview level", () => {
      manager.setLevel("overview");
      expect(manager.zoomOut()).toBe("overview");
    });

    it("should get default scale for current level", () => {
      manager.setLevel("overview");
      expect(manager.getDefaultScale()).toBe(0.2);

      manager.setLevel("source");
      expect(manager.getDefaultScale()).toBe(3.0);
    });

    it("should manage focus path", () => {
      expect(manager.getFocusPath().depth).toBe(0);

      manager.pushFocus({ id: "node-1", type: "node", label: "Node 1" });
      expect(manager.getFocusPath().depth).toBe(1);

      manager.pushFocus({ id: "detail-1", type: "detail", label: "Detail 1" });
      expect(manager.getFocusPath().depth).toBe(2);

      manager.popFocus();
      expect(manager.getFocusPath().depth).toBe(1);
    });

    it("should navigate to specific depth", () => {
      manager.pushFocus({ id: "group-1", type: "group", label: "Group 1" });
      manager.pushFocus({ id: "node-1", type: "node", label: "Node 1" });
      manager.pushFocus({ id: "detail-1", type: "detail", label: "Detail 1" });

      manager.navigateToDepth(1);
      expect(manager.getFocusPath().depth).toBe(1);
    });

    it("should create viewport context", () => {
      manager.setLevel("detail");
      manager.pushFocus({ id: "node-1", type: "node", label: "Node 1" });

      const context = manager.createViewportContext(100, 200, 1.5);
      expect(context.centerX).toBe(100);
      expect(context.centerY).toBe(200);
      expect(context.scale).toBe(1.5);
      expect(context.semanticLevel).toBe("detail");
      expect(context.focusPath.depth).toBe(1);
    });

    it("should get and set policy", () => {
      const defaultPolicy = manager.getPolicy();
      expect(defaultPolicy.id).toBe("default");

      const newPolicy: ContextShiftPolicy = {
        id: "custom",
        preserveViewportOnFocusChange: true,
        autoZoomToFit: false,
        animateTransitions: false,
        snapToSemanticLevels: false,
        minTransitionDuration: 100,
        maxTransitionDuration: 300,
        keyboardNavigationEnabled: false,
      };

      manager.setPolicy(newPolicy);
      expect(manager.getPolicy().id).toBe("custom");
      expect(manager.getPolicy().keyboardNavigationEnabled).toBe(false);
    });
  });
});
