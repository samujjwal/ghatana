/**
 * @file keyboard-graph-navigation.test.ts
 * Tests for keyboard-driven semantic zoom and focus-path navigation.
 *
 * Covers:
 * - handleZoomKeyboardEvent dispatching to caller-provided handlers
 * - SemanticZoomManager zoom-in/out traversal through all six semantic levels
 * - Focus path drill-down and back navigation via pushFocusSegment / popFocusSegment
 * - Context shift policies controlling transition behaviour
 *
 * @doc.type module
 * @doc.purpose Keyboard graph navigation tests
 * @doc.layer platform
 * @doc.pattern Test
 */

import { describe, it, expect, vi } from "vitest";
import {
  SemanticZoomManager,
  handleZoomKeyboardEvent,
  ZOOM_KEYBOARD_SHORTCUTS,
  createFocusPath,
  pushFocusSegment,
  popFocusSegment,
  truncateFocusPath,
  getCurrentFocusSegment,
  DEFAULT_CONTEXT_SHIFT_POLICY,
  KEYBOARD_CONTEXT_SHIFT_POLICY,
  REDUCED_MOTION_CONTEXT_SHIFT_POLICY,
  calculateTransitionDuration,
  createViewportContext,
} from "../public/index.js";

// ── ZOOM_KEYBOARD_SHORTCUTS coverage ────────────────────────────────────────

describe("ZOOM_KEYBOARD_SHORTCUTS", () => {
  const requiredCommands = [
    "zoom-in",
    "zoom-out",
    "zoom-reset",
    "focus-up",
    "focus-down",
    "focus-back",
    "pan-left",
    "pan-right",
    "pan-up",
    "pan-down",
  ] as const;

  for (const cmd of requiredCommands) {
    it(`should define at least one shortcut for "${cmd}"`, () => {
      const shortcuts = ZOOM_KEYBOARD_SHORTCUTS.get(cmd);
      expect(shortcuts).toBeDefined();
      expect(shortcuts!.length).toBeGreaterThan(0);
    });
  }

  it("should cover exactly ten commands", () => {
    expect(ZOOM_KEYBOARD_SHORTCUTS.size).toBe(10);
  });
});

// ── handleZoomKeyboardEvent ──────────────────────────────────────────────────

describe("handleZoomKeyboardEvent", () => {
  it("should call zoom-in handler when Ctrl+= is pressed", () => {
    const handler = vi.fn();
    const event = new KeyboardEvent("keydown", { key: "=", ctrlKey: true });
    const handled = handleZoomKeyboardEvent(event, { "zoom-in": handler });
    expect(handled).toBe(true);
    expect(handler).toHaveBeenCalledOnce();
  });

  it("should call zoom-out handler when Minus key is pressed", () => {
    const handler = vi.fn();
    const event = new KeyboardEvent("keydown", { key: "Minus" });
    const handled = handleZoomKeyboardEvent(event, { "zoom-out": handler });
    expect(handled).toBe(true);
    expect(handler).toHaveBeenCalledOnce();
  });

  it("should call pan-up handler when ArrowUp is pressed", () => {
    const handler = vi.fn();
    const event = new KeyboardEvent("keydown", { key: "ArrowUp" });
    const handled = handleZoomKeyboardEvent(event, { "pan-up": handler });
    expect(handled).toBe(true);
    expect(handler).toHaveBeenCalledOnce();
  });

  it("should call pan-down handler when ArrowDown is pressed", () => {
    const handler = vi.fn();
    const event = new KeyboardEvent("keydown", { key: "ArrowDown" });
    const handled = handleZoomKeyboardEvent(event, { "pan-down": handler });
    expect(handled).toBe(true);
    expect(handler).toHaveBeenCalledOnce();
  });

  it("should call pan-left handler when ArrowLeft is pressed", () => {
    const handler = vi.fn();
    const event = new KeyboardEvent("keydown", { key: "ArrowLeft" });
    const handled = handleZoomKeyboardEvent(event, { "pan-left": handler });
    expect(handled).toBe(true);
    expect(handler).toHaveBeenCalledOnce();
  });

  it("should call pan-right handler when ArrowRight is pressed", () => {
    const handler = vi.fn();
    const event = new KeyboardEvent("keydown", { key: "ArrowRight" });
    const handled = handleZoomKeyboardEvent(event, { "pan-right": handler });
    expect(handled).toBe(true);
    expect(handler).toHaveBeenCalledOnce();
  });

  it("should call focus-back handler when Escape is pressed", () => {
    const handler = vi.fn();
    const event = new KeyboardEvent("keydown", { key: "Escape" });
    const handled = handleZoomKeyboardEvent(event, { "focus-back": handler });
    expect(handled).toBe(true);
    expect(handler).toHaveBeenCalledOnce();
  });

  it("should call zoom-reset handler when Ctrl+0 is pressed", () => {
    const handler = vi.fn();
    const event = new KeyboardEvent("keydown", { key: "0", ctrlKey: true });
    const handled = handleZoomKeyboardEvent(event, { "zoom-reset": handler });
    expect(handled).toBe(true);
    expect(handler).toHaveBeenCalledOnce();
  });

  it("should return false when the pressed key matches no shortcut", () => {
    const handler = vi.fn();
    const event = new KeyboardEvent("keydown", { key: "a" });
    const handled = handleZoomKeyboardEvent(event, { "zoom-in": handler });
    expect(handled).toBe(false);
    expect(handler).not.toHaveBeenCalled();
  });

  it("should return false when no handler is provided for a matching shortcut", () => {
    // ArrowUp maps to pan-up, but we only provide a zoom-in handler
    const event = new KeyboardEvent("keydown", { key: "ArrowUp" });
    const handled = handleZoomKeyboardEvent(event, { "zoom-in": vi.fn() });
    expect(handled).toBe(false);
  });

  it("should integrate with SemanticZoomManager.zoomIn via Ctrl+= handler", () => {
    const manager = new SemanticZoomManager();
    expect(manager.getCurrentLevel()).toBe("node");

    const event = new KeyboardEvent("keydown", { key: "=", ctrlKey: true });
    handleZoomKeyboardEvent(event, {
      "zoom-in": () => { manager.zoomIn(); },
    });

    expect(manager.getCurrentLevel()).toBe("detail");
  });

  it("should integrate with SemanticZoomManager.zoomOut via Minus key handler", () => {
    const manager = new SemanticZoomManager();
    expect(manager.getCurrentLevel()).toBe("node");

    const event = new KeyboardEvent("keydown", { key: "Minus" });
    handleZoomKeyboardEvent(event, {
      "zoom-out": () => { manager.zoomOut(); },
    });

    expect(manager.getCurrentLevel()).toBe("group");
  });

  it("should integrate with focus path navigation via Escape handler", () => {
    const manager = new SemanticZoomManager();
    manager.pushFocus({ id: "node-1", type: "node", label: "Node 1" });
    expect(manager.getFocusPath().depth).toBe(1);

    const event = new KeyboardEvent("keydown", { key: "Escape" });
    handleZoomKeyboardEvent(event, {
      "focus-back": () => { manager.popFocus(); },
    });

    expect(manager.getFocusPath().depth).toBe(0);
  });
});

// ── SemanticZoomManager zoom traversal ──────────────────────────────────────

describe("SemanticZoomManager zoom traversal", () => {
  it("should default to node level", () => {
    const manager = new SemanticZoomManager();
    expect(manager.getCurrentLevel()).toBe("node");
  });

  it("should zoom in through all levels from overview to source", () => {
    const manager = new SemanticZoomManager();
    manager.setLevel("overview");

    const expectedSequence = [
      "group",
      "node",
      "detail",
      "evidence",
      "source",
    ] as const;
    for (const expected of expectedSequence) {
      manager.zoomIn();
      expect(manager.getCurrentLevel()).toBe(expected);
    }
  });

  it("should clamp zoom-in at source level and not overflow", () => {
    const manager = new SemanticZoomManager();
    manager.setLevel("source");
    manager.zoomIn();
    expect(manager.getCurrentLevel()).toBe("source");
  });

  it("should zoom out through all levels from source to overview", () => {
    const manager = new SemanticZoomManager();
    manager.setLevel("source");

    const expectedSequence = [
      "evidence",
      "detail",
      "node",
      "group",
      "overview",
    ] as const;
    for (const expected of expectedSequence) {
      manager.zoomOut();
      expect(manager.getCurrentLevel()).toBe(expected);
    }
  });

  it("should clamp zoom-out at overview level and not underflow", () => {
    const manager = new SemanticZoomManager();
    manager.setLevel("overview");
    manager.zoomOut();
    expect(manager.getCurrentLevel()).toBe("overview");
  });

  it("should resolve the default scale for a level from the current bands", () => {
    const manager = new SemanticZoomManager();
    manager.setLevel("overview");
    const scale = manager.getDefaultScale();
    expect(scale).toBe(0.2);
  });

  it("should allow policy switching at runtime", () => {
    const manager = new SemanticZoomManager();
    expect(manager.getPolicy().id).toBe("default");

    manager.setPolicy(KEYBOARD_CONTEXT_SHIFT_POLICY);
    expect(manager.getPolicy().id).toBe("keyboard");
    expect(manager.getPolicy().animateTransitions).toBe(false);
  });

  it("should build a viewport context from current manager state", () => {
    const manager = new SemanticZoomManager();
    manager.setLevel("detail");
    const ctx = manager.createViewportContext(100, 200, 1.2);
    expect(ctx.semanticLevel).toBe("detail");
    expect(ctx.centerX).toBe(100);
    expect(ctx.centerY).toBe(200);
    expect(ctx.scale).toBe(1.2);
  });
});

// ── Focus path keyboard navigation ──────────────────────────────────────────

describe("Focus path keyboard navigation", () => {
  it("should start with an empty path at depth 0", () => {
    const path = createFocusPath();
    expect(path.depth).toBe(0);
    expect(path.segments).toHaveLength(0);
    expect(getCurrentFocusSegment(path)).toBeUndefined();
  });

  it("should build a focus path through sequential drill-down", () => {
    let path = createFocusPath();

    path = pushFocusSegment(path, {
      id: "group-1",
      type: "group",
      label: "Group 1",
    });
    expect(path.depth).toBe(1);
    expect(getCurrentFocusSegment(path)?.id).toBe("group-1");

    path = pushFocusSegment(path, {
      id: "node-1",
      type: "node",
      label: "Node 1",
    });
    expect(path.depth).toBe(2);
    expect(getCurrentFocusSegment(path)?.id).toBe("node-1");

    path = pushFocusSegment(path, {
      id: "detail-1",
      type: "detail",
      label: "Details",
    });
    expect(path.depth).toBe(3);
    expect(getCurrentFocusSegment(path)?.id).toBe("detail-1");
  });

  it("should pop focus segments correctly on back navigation", () => {
    let path = createFocusPath();
    path = pushFocusSegment(path, {
      id: "group-1",
      type: "group",
      label: "Group 1",
    });
    path = pushFocusSegment(path, {
      id: "node-1",
      type: "node",
      label: "Node 1",
    });

    path = popFocusSegment(path);
    expect(path.depth).toBe(1);
    expect(getCurrentFocusSegment(path)?.id).toBe("group-1");

    path = popFocusSegment(path);
    expect(path.depth).toBe(0);
    expect(getCurrentFocusSegment(path)).toBeUndefined();
  });

  it("should not underflow when popping an empty path", () => {
    const empty = createFocusPath();
    const stillEmpty = popFocusSegment(empty);
    expect(stillEmpty.depth).toBe(0);
  });

  it("should truncate a focus path to a specific depth", () => {
    let path = createFocusPath();
    path = pushFocusSegment(path, { id: "g", type: "group", label: "G" });
    path = pushFocusSegment(path, { id: "n", type: "node", label: "N" });
    path = pushFocusSegment(path, {
      id: "d",
      type: "detail",
      label: "D",
    });
    expect(path.depth).toBe(3);

    path = truncateFocusPath(path, 1);
    expect(path.depth).toBe(1);
    expect(path.segments[0].id).toBe("g");
  });

  it("should be immutable — push does not mutate the original path", () => {
    const original = createFocusPath();
    pushFocusSegment(original, { id: "s", type: "node", label: "S" });
    expect(original.depth).toBe(0);
  });

  it("should support full navigation workflow via SemanticZoomManager", () => {
    const manager = new SemanticZoomManager();

    manager.pushFocus({
      id: "service-a",
      type: "group",
      label: "Service A",
    });
    manager.pushFocus({
      id: "endpoint-1",
      type: "node",
      label: "POST /api/data",
    });

    expect(manager.getFocusPath().depth).toBe(2);
    expect(getCurrentFocusSegment(manager.getFocusPath())?.id).toBe(
      "endpoint-1",
    );

    manager.popFocus();
    expect(manager.getFocusPath().depth).toBe(1);
    expect(getCurrentFocusSegment(manager.getFocusPath())?.id).toBe(
      "service-a",
    );

    manager.navigateToDepth(0);
    expect(manager.getFocusPath().depth).toBe(0);
  });

  it("should support metadata attachment on focus segments", () => {
    let path = createFocusPath();
    path = pushFocusSegment(path, {
      id: "node-x",
      type: "node",
      label: "Node X",
      metadata: { tenant: "acme", phase: "planning" },
    });
    const segment = getCurrentFocusSegment(path);
    expect(segment?.metadata?.["tenant"]).toBe("acme");
    expect(segment?.metadata?.["phase"]).toBe("planning");
  });
});

// ── Context shift policy ─────────────────────────────────────────────────────

describe("Context shift policy for keyboard navigation", () => {
  it("should have keyboard navigation enabled in default policy", () => {
    expect(DEFAULT_CONTEXT_SHIFT_POLICY.keyboardNavigationEnabled).toBe(true);
  });

  it("should have no animation in keyboard-optimised policy", () => {
    expect(KEYBOARD_CONTEXT_SHIFT_POLICY.keyboardNavigationEnabled).toBe(true);
    expect(KEYBOARD_CONTEXT_SHIFT_POLICY.animateTransitions).toBe(false);
  });

  it("should have zero-duration transitions in reduced-motion policy", () => {
    expect(REDUCED_MOTION_CONTEXT_SHIFT_POLICY.animateTransitions).toBe(false);
    expect(REDUCED_MOTION_CONTEXT_SHIFT_POLICY.minTransitionDuration).toBe(0);
    expect(REDUCED_MOTION_CONTEXT_SHIFT_POLICY.maxTransitionDuration).toBe(0);
  });

  it("should return 0 duration for reduced-motion policy regardless of viewport distance", () => {
    const from = createViewportContext({ centerX: 0, centerY: 0, scale: 1.0 });
    const to = createViewportContext({
      centerX: 1000,
      centerY: 1000,
      scale: 3.0,
    });
    const duration = calculateTransitionDuration(
      from,
      to,
      REDUCED_MOTION_CONTEXT_SHIFT_POLICY,
    );
    expect(duration).toBe(0);
  });

  it("should return a positive capped duration for the default policy on a large viewport shift", () => {
    const from = createViewportContext({ centerX: 0, centerY: 0, scale: 1.0 });
    const to = createViewportContext({
      centerX: 5000,
      centerY: 5000,
      scale: 2.0,
    });
    const duration = calculateTransitionDuration(
      from,
      to,
      DEFAULT_CONTEXT_SHIFT_POLICY,
    );
    expect(duration).toBeGreaterThan(0);
    expect(duration).toBeLessThanOrEqual(
      DEFAULT_CONTEXT_SHIFT_POLICY.maxTransitionDuration,
    );
  });

  it("should return 0 duration for non-animated policies even across large distances", () => {
    const from = createViewportContext({ centerX: 0, centerY: 0, scale: 0.5 });
    const to = createViewportContext({
      centerX: 2000,
      centerY: 2000,
      scale: 2.0,
    });
    expect(
      calculateTransitionDuration(from, to, KEYBOARD_CONTEXT_SHIFT_POLICY),
    ).toBe(0);
  });
});
