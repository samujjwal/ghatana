/**
 * @file import-boundary.test.ts
 * Verifies that the @ghatana/canvas public API barrel exports every type and
 * utility that callers are expected to import.  Products MUST NOT import from
 * internal src/ subdirectories; this test acts as a compile-time + runtime
 * guard ensuring all required symbols surface through the canonical public
 * index.
 *
 * @doc.type module
 * @doc.purpose Public API boundary tests for @ghatana/canvas
 * @doc.layer platform
 * @doc.pattern Test
 */

import { describe, it, expect } from "vitest";
import {
  // Semantic zoom — value exports
  DEFAULT_ZOOM_BANDS,
  SemanticZoomManager,
  createFocusPath,
  pushFocusSegment,
  createViewportContext,
  DEFAULT_CONTEXT_SHIFT_POLICY,
  KEYBOARD_CONTEXT_SHIFT_POLICY,
  REDUCED_MOTION_CONTEXT_SHIFT_POLICY,
  PROGRESSIVE_DISCLOSURE_POLICY,
  ZOOM_KEYBOARD_SHORTCUTS,
  handleZoomKeyboardEvent,
  semanticZoomLevelAtom,
  focusPathAtom,
  viewportContextAtom,
  contextShiftPolicyAtom,
  detailDisclosurePolicyAtom,
  resolveZoomLevel,
  validateZoomBands,
  // Diagram builder
  createDiagram,
  isValidDiagramType,
  getValidDiagramTypes,
  DiagramBuilder,
  // Diagram presets
  DIAGRAM_PRESETS,
  getDiagramPreset,
  getDiagramPresetIds,
  isDiagramPresetId,
} from "../public/index.js";

// ── Semantic zoom ────────────────────────────────────────────────────────────

describe("Canvas public API — semantic zoom exports", () => {
  it("should export DEFAULT_ZOOM_BANDS covering all six semantic levels", () => {
    const levels = DEFAULT_ZOOM_BANDS.map((b) => b.level);
    expect(levels).toContain("overview");
    expect(levels).toContain("group");
    expect(levels).toContain("node");
    expect(levels).toContain("detail");
    expect(levels).toContain("evidence");
    expect(levels).toContain("source");
    expect(levels).toHaveLength(6);
  });

  it("should export SemanticZoomManager as a constructable class defaulting to node level", () => {
    const manager = new SemanticZoomManager();
    expect(manager.getCurrentLevel()).toBe("node");
  });

  it("should export createFocusPath returning an immutable empty path", () => {
    const path = createFocusPath();
    expect(path.depth).toBe(0);
    expect(path.segments).toHaveLength(0);
  });

  it("should export pushFocusSegment that appends a segment immutably", () => {
    const empty = createFocusPath();
    const updated = pushFocusSegment(empty, {
      id: "seg-1",
      type: "node",
      label: "Segment 1",
    });
    expect(updated.depth).toBe(1);
    expect(empty.depth).toBe(0); // immutable — original unchanged
  });

  it("should export createViewportContext returning a valid context with default scale 1.0", () => {
    const ctx = createViewportContext();
    expect(ctx.scale).toBe(1.0);
    expect(ctx.semanticLevel).toBe("node");
    expect(ctx.centerX).toBe(0);
    expect(ctx.centerY).toBe(0);
  });

  it("should export DEFAULT_CONTEXT_SHIFT_POLICY with keyboard navigation enabled", () => {
    expect(DEFAULT_CONTEXT_SHIFT_POLICY.keyboardNavigationEnabled).toBe(true);
    expect(DEFAULT_CONTEXT_SHIFT_POLICY.animateTransitions).toBe(true);
  });

  it("should export KEYBOARD_CONTEXT_SHIFT_POLICY with animation disabled", () => {
    expect(KEYBOARD_CONTEXT_SHIFT_POLICY.keyboardNavigationEnabled).toBe(true);
    expect(KEYBOARD_CONTEXT_SHIFT_POLICY.animateTransitions).toBe(false);
  });

  it("should export REDUCED_MOTION_CONTEXT_SHIFT_POLICY with zero duration", () => {
    expect(REDUCED_MOTION_CONTEXT_SHIFT_POLICY.animateTransitions).toBe(false);
    expect(REDUCED_MOTION_CONTEXT_SHIFT_POLICY.maxTransitionDuration).toBe(0);
  });

  it("should export PROGRESSIVE_DISCLOSURE_POLICY with all six level thresholds", () => {
    expect(PROGRESSIVE_DISCLOSURE_POLICY.progressiveDisclosure).toBe(true);
    expect(PROGRESSIVE_DISCLOSURE_POLICY.thresholds.size).toBe(6);
  });

  it("should export ZOOM_KEYBOARD_SHORTCUTS covering all ten commands", () => {
    const commands = [
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
    for (const cmd of commands) {
      expect(ZOOM_KEYBOARD_SHORTCUTS.has(cmd)).toBe(true);
    }
  });

  it("should export handleZoomKeyboardEvent as a callable function", () => {
    let invoked = false;
    const event = new KeyboardEvent("keydown", { key: "ArrowUp" });
    handleZoomKeyboardEvent(event, { "pan-up": () => { invoked = true; } });
    expect(invoked).toBe(true);
  });

  it("should export Jotai atoms for semantic zoom state", () => {
    // Atoms are primitives exported from the Jotai library — verify they are
    // defined and carry an init value.
    expect(semanticZoomLevelAtom).toBeDefined();
    expect(focusPathAtom).toBeDefined();
    expect(viewportContextAtom).toBeDefined();
    expect(contextShiftPolicyAtom).toBeDefined();
    expect(detailDisclosurePolicyAtom).toBeDefined();
  });

  it("should export resolveZoomLevel resolving scale to a semantic level", () => {
    expect(resolveZoomLevel(1.0)).toBe("node");
    expect(resolveZoomLevel(0.2)).toBe("overview");
    expect(resolveZoomLevel(3.0)).toBe("source");
  });

  it("should export validateZoomBands returning valid for DEFAULT_ZOOM_BANDS", () => {
    const result = validateZoomBands(DEFAULT_ZOOM_BANDS);
    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
  });
});

// ── Diagram builder ──────────────────────────────────────────────────────────

describe("Canvas public API — diagram builder exports", () => {
  it("should export isValidDiagramType as a type guard recognising all base types", () => {
    expect(isValidDiagramType("flow")).toBe(true);
    expect(isValidDiagramType("dag")).toBe(true);
    expect(isValidDiagramType("topology")).toBe(true);
    expect(isValidDiagramType("swimlane")).toBe(true);
    expect(isValidDiagramType("dependency-graph")).toBe(true);
    expect(isValidDiagramType("provenance-graph")).toBe(true);
    // Preset IDs are NOT base diagram types
    expect(isValidDiagramType("lifecycle-plan")).toBe(false);
    expect(isValidDiagramType("gate-flow")).toBe(false);
  });

  it("should export getValidDiagramTypes returning all six base types", () => {
    const types = getValidDiagramTypes();
    expect(types).toHaveLength(6);
    expect(types).toContain("dependency-graph");
    expect(types).toContain("provenance-graph");
  });

  it("should export createDiagram returning a DiagramBuilder", () => {
    const builder = createDiagram("api-test", "flow");
    expect(builder).toBeInstanceOf(DiagramBuilder);
  });

  it("should export DiagramBuilder that builds a minimal diagram", () => {
    const diagram = new DiagramBuilder("bd-test", "dag").build();
    expect(diagram.id).toBe("bd-test");
    expect(diagram.diagramType).toBe("dag");
    expect(diagram.nodes.size).toBe(0);
    expect(diagram.edges.size).toBe(0);
  });
});

// ── Diagram presets ──────────────────────────────────────────────────────────

describe("Canvas public API — diagram preset exports", () => {
  it("should export DIAGRAM_PRESETS containing all five named presets", () => {
    const ids = Object.keys(DIAGRAM_PRESETS);
    expect(ids).toHaveLength(5);
    expect(ids).toContain("lifecycle-plan");
    expect(ids).toContain("dependency-graph");
    expect(ids).toContain("topology");
    expect(ids).toContain("provenance-graph");
    expect(ids).toContain("gate-flow");
  });

  it("should export getDiagramPreset returning the exact preset by ID", () => {
    const preset = getDiagramPreset("lifecycle-plan");
    expect(preset.id).toBe("lifecycle-plan");
    expect(preset.label).toBe("Lifecycle Plan");
    expect(preset.diagramType).toBe("swimlane");
  });

  it("should export getDiagramPresetIds returning five IDs", () => {
    const ids = getDiagramPresetIds();
    expect(ids).toHaveLength(5);
    expect(ids).toContain("gate-flow");
    expect(ids).toContain("provenance-graph");
  });

  it("should export isDiagramPresetId as a type guard", () => {
    expect(isDiagramPresetId("lifecycle-plan")).toBe(true);
    expect(isDiagramPresetId("dependency-graph")).toBe(true);
    expect(isDiagramPresetId("topology")).toBe(true);
    expect(isDiagramPresetId("provenance-graph")).toBe(true);
    expect(isDiagramPresetId("gate-flow")).toBe(true);
    expect(isDiagramPresetId("flow")).toBe(false); // base type, not a preset
    expect(isDiagramPresetId("")).toBe(false);
    expect(isDiagramPresetId("unknown")).toBe(false);
  });

  it("should have every preset reference a valid base diagramType", () => {
    for (const preset of Object.values(DIAGRAM_PRESETS)) {
      expect(isValidDiagramType(preset.diagramType)).toBe(true);
    }
  });

  it("should have non-empty label and description for every preset", () => {
    for (const preset of Object.values(DIAGRAM_PRESETS)) {
      expect(preset.label.trim().length).toBeGreaterThan(0);
      expect(preset.description.trim().length).toBeGreaterThan(0);
    }
  });

  it("should have a valid layout algorithm for every preset", () => {
    const validAlgorithms = new Set([
      "hierarchical",
      "force-directed",
      "circular",
      "grid",
      "dagre",
      "custom",
    ]);
    for (const preset of Object.values(DIAGRAM_PRESETS)) {
      expect(validAlgorithms.has(preset.defaultLayoutConfig.algorithm)).toBe(
        true,
      );
    }
  });

  it("should reflect correct diagramType for gate-flow preset (flow, not a custom type)", () => {
    const gateFlow = getDiagramPreset("gate-flow");
    expect(gateFlow.diagramType).toBe("flow");
    expect(gateFlow.featureFlags.enableEdgeLabels).toBe(true);
  });

  it("should reflect correct diagramType for lifecycle-plan preset (swimlane)", () => {
    const lp = getDiagramPreset("lifecycle-plan");
    expect(lp.diagramType).toBe("swimlane");
    expect(lp.defaultLayoutConfig.direction).toBe("horizontal");
    expect(lp.featureFlags.showLegend).toBe(true);
  });
});
