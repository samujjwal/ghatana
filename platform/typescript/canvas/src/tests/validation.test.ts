import { describe, it, expect } from "vitest";
import {
  validateDiagram,
  validatePattern,
  validatePipeline,
} from "../topology/builder/validation.js";
import type { Node, Edge } from "@xyflow/react";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeNode(id: string, type?: string, label?: string): Node {
  return {
    id,
    position: { x: 0, y: 0 },
    type,
    data: { label: label ?? id },
  } as Node;
}

function makeEdge(id: string, source: string, target: string): Edge {
  return { id, source, target } as Edge;
}

// ---------------------------------------------------------------------------
// validateDiagram
// ---------------------------------------------------------------------------

describe("validateDiagram", () => {
  it("returns no errors for a single node (no disconnected-node check)", () => {
    const errors = validateDiagram([makeNode("n1")], []);
    // Single node: disconnected check is skipped when nodes.length <= 1
    const disconnected = errors.filter((e) => e.id.startsWith("disconnected-"));
    expect(disconnected).toHaveLength(0);
  });

  it("reports disconnected warning for unconnected nodes in multi-node graph", () => {
    const nodes = [makeNode("n1"), makeNode("n2"), makeNode("n3")];
    const edges = [makeEdge("e1", "n1", "n2")]; // n3 is isolated
    const errors = validateDiagram(nodes, edges);
    const ids = errors.map((e) => e.id);
    expect(ids).toContain("disconnected-n3");
  });

  it("does not report disconnected for connected nodes", () => {
    const nodes = [makeNode("n1"), makeNode("n2")];
    const edges = [makeEdge("e1", "n1", "n2")];
    const errors = validateDiagram(nodes, edges);
    const disconnected = errors.filter((e) => e.id.startsWith("disconnected-"));
    expect(disconnected).toHaveLength(0);
  });

  it("reports cycle-detected error for a circular graph", () => {
    const nodes = [makeNode("a"), makeNode("b"), makeNode("c")];
    const edges = [
      makeEdge("e1", "a", "b"),
      makeEdge("e2", "b", "c"),
      makeEdge("e3", "c", "a"),
    ];
    const errors = validateDiagram(nodes, edges);
    expect(errors.some((e) => e.id === "cycle-detected")).toBe(true);
  });

  it("does not report cycle for a DAG", () => {
    const nodes = [makeNode("a"), makeNode("b"), makeNode("c")];
    const edges = [
      makeEdge("e1", "a", "b"),
      makeEdge("e2", "b", "c"),
    ];
    const errors = validateDiagram(nodes, edges);
    expect(errors.every((e) => e.id !== "cycle-detected")).toBe(true);
  });

  it("reports missing-label error for node with no label", () => {
    const node: Node = {
      id: "n1",
      position: { x: 0, y: 0 },
      data: { label: "" },
    } as Node;
    const errors = validateDiagram([node], []);
    expect(errors.some((e) => e.id === "missing-label-n1")).toBe(true);
  });

  it("reports missing-label error with severity 'error'", () => {
    const node: Node = {
      id: "n1",
      position: { x: 0, y: 0 },
      data: {},
    } as Node;
    const errors = validateDiagram([node], []);
    const labelErr = errors.find((e) => e.id === "missing-label-n1");
    expect(labelErr?.severity).toBe("error");
  });

  it("returns empty error list for a fully connected valid graph", () => {
    const nodes = [makeNode("a"), makeNode("b")];
    const edges = [makeEdge("e1", "a", "b")];
    const errors = validateDiagram(nodes, edges);
    expect(errors).toHaveLength(0);
  });

  it("self-loop is detected as a cycle", () => {
    const nodes = [makeNode("a")];
    const edges = [makeEdge("e1", "a", "a")];
    const errors = validateDiagram(nodes, edges);
    expect(errors.some((e) => e.id === "cycle-detected")).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// validatePattern
// ---------------------------------------------------------------------------

describe("validatePattern", () => {
  it("requires at least one eventType node", () => {
    const nodes = [makeNode("a", "action")];
    const errors = validatePattern(nodes, []);
    expect(errors.some((e) => e.id === "no-event-type")).toBe(true);
  });

  it("requires at least one action node", () => {
    const nodes = [makeNode("a", "eventType")];
    const errors = validatePattern(nodes, []);
    expect(errors.some((e) => e.id === "no-action")).toBe(true);
  });

  it("passes when both eventType and action nodes are present", () => {
    const nodes = [makeNode("et", "eventType"), makeNode("ac", "action")];
    const edges = [makeEdge("e1", "et", "ac")];
    const errors = validatePattern(nodes, edges);
    const patternErrors = errors.filter((e) =>
      ["no-event-type", "no-action"].includes(e.id)
    );
    expect(patternErrors).toHaveLength(0);
  });
});

// ---------------------------------------------------------------------------
// validatePipeline
// ---------------------------------------------------------------------------

describe("validatePipeline", () => {
  it("requires at least one source node", () => {
    const nodes = [makeNode("s", "sink")];
    const errors = validatePipeline(nodes, []);
    expect(errors.some((e) => e.id === "no-source")).toBe(true);
  });

  it("requires at least one sink node", () => {
    const nodes = [makeNode("s", "source")];
    const errors = validatePipeline(nodes, []);
    expect(errors.some((e) => e.id === "no-sink")).toBe(true);
  });

  it("reports error when no path exists from source to sink", () => {
    const nodes = [
      makeNode("src", "source"),
      makeNode("snk", "sink"),
    ];
    // No edges connecting them
    const errors = validatePipeline(nodes, []);
    expect(errors.some((e) => e.id === "no-source-to-sink-path")).toBe(true);
  });

  it("passes when source is connected to sink", () => {
    const nodes = [
      makeNode("src", "source"),
      makeNode("snk", "sink"),
    ];
    const edges = [makeEdge("e1", "src", "snk")];
    const errors = validatePipeline(nodes, edges);
    const pipelineErrors = errors.filter((e) =>
      ["no-source", "no-sink", "no-source-to-sink-path"].includes(e.id)
    );
    expect(pipelineErrors).toHaveLength(0);
  });

  it("passes with an intermediate transform node between source and sink", () => {
    const nodes = [
      makeNode("src", "source"),
      makeNode("t", "transform"),
      makeNode("snk", "sink"),
    ];
    const edges = [
      makeEdge("e1", "src", "t"),
      makeEdge("e2", "t", "snk"),
    ];
    const errors = validatePipeline(nodes, edges);
    const pipelineErrors = errors.filter((e) =>
      ["no-source-to-sink-path"].includes(e.id)
    );
    expect(pipelineErrors).toHaveLength(0);
  });
});
