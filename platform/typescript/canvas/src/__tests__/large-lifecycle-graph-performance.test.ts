/**
 * @file large-lifecycle-graph-performance.test.ts
 * Performance fixtures for large lifecycle-domain graphs.
 *
 * These tests verify that the DiagramBuilder can construct large, realistic
 * graphs within acceptable time budgets, ensuring the canvas platform remains
 * viable for product graphs with hundreds of nodes.
 *
 * Time budgets are intentionally generous (10× the realistic expected time) to
 * remain stable on CI runners with variable load, while still catching
 * pathological O(n²) regressions.
 *
 * @doc.type module
 * @doc.purpose Performance tests for large graph construction
 * @doc.layer platform
 * @doc.pattern Test
 */

import { describe, it, expect } from "vitest";
import {
  createDiagram,
  getDiagramPreset,
} from "../public/index.js";
import type {
  DependencyNode,
  DependencyEdge,
  DagNode,
  DagEdge,
  FlowNode,
  FlowEdge,
} from "../public/index.js";

// ── 500-node dependency graph ────────────────────────────────────────────────

describe("Large lifecycle graph performance — dependency graph", () => {
  it("should build a linear dependency chain of 500 nodes within 1000ms", () => {
    const preset = getDiagramPreset("dependency-graph");
    const start = performance.now();
    const builder = createDiagram("perf-dep-500", preset.diagramType);

    for (let i = 0; i < 500; i++) {
      const node: DependencyNode = {
        id: `dep-node-${i}`,
        type: "node",
        diagramType: "dependency-graph",
        x: (i % 25) * 60,
        y: Math.floor(i / 25) * 60,
        width: 50,
        height: 30,
        shape: "rectangle",
        label: `Module-${i}`,
        dependencies: i > 0 ? [`dep-node-${i - 1}`] : [],
        dependents: i < 499 ? [`dep-node-${i + 1}`] : [],
        dependencyType: "depends-on",
        circular: false,
      };
      builder.addNode(node);
    }

    for (let i = 0; i < 499; i++) {
      const edge: DependencyEdge = {
        id: `dep-edge-${i}`,
        type: "edge",
        diagramType: "dependency-graph",
        source: `dep-node-${i}`,
        target: `dep-node-${i + 1}`,
        routing: "straight",
        sourceArrow: "none",
        targetArrow: "arrow",
        dependencyType: "depends-on",
        optional: false,
      };
      builder.addEdge(edge);
    }

    const diagram = builder.build();
    const elapsed = performance.now() - start;

    expect(diagram.nodes.size).toBe(500);
    expect(diagram.edges.size).toBe(499);
    expect(diagram.diagramType).toBe("dependency-graph");
    // Time budget: 1000ms (generous for CI environments)
    expect(elapsed).toBeLessThan(1000);
  });

  it("should build a 100-node optional-dependency graph within 200ms", () => {
    const start = performance.now();
    const builder = createDiagram("perf-dep-optional", "dependency-graph");

    // Fan-out topology: node 0 depends on nothing; nodes 1–99 depend on node 0
    const root: DependencyNode = {
      id: "root",
      type: "node",
      diagramType: "dependency-graph",
      x: 0,
      y: 0,
      width: 60,
      height: 40,
      shape: "hexagon",
      label: "Root Module",
      dependencies: [],
      dependents: Array.from({ length: 99 }, (_, i) => `dep-leaf-${i}`),
      dependencyType: "depends-on",
      circular: false,
    };
    builder.addNode(root);

    for (let i = 0; i < 99; i++) {
      const leaf: DependencyNode = {
        id: `dep-leaf-${i}`,
        type: "node",
        diagramType: "dependency-graph",
        x: (i % 10) * 80,
        y: Math.floor(i / 10) * 60 + 100,
        width: 50,
        height: 30,
        shape: "rectangle",
        label: `Leaf-${i}`,
        dependencies: ["root"],
        dependents: [],
        dependencyType: "uses",
        circular: false,
      };
      builder.addNode(leaf);

      const edge: DependencyEdge = {
        id: `dep-leaf-edge-${i}`,
        type: "edge",
        diagramType: "dependency-graph",
        source: "root",
        target: `dep-leaf-${i}`,
        routing: "straight",
        sourceArrow: "none",
        targetArrow: "arrow",
        dependencyType: "uses",
        optional: true,
      };
      builder.addEdge(edge);
    }

    const diagram = builder.build();
    const elapsed = performance.now() - start;

    expect(diagram.nodes.size).toBe(100);
    expect(diagram.edges.size).toBe(99);
    expect(elapsed).toBeLessThan(200);
  });
});

// ── 200-node DAG (pipeline stages) ──────────────────────────────────────────

describe("Large lifecycle graph performance — DAG", () => {
  it("should build a DAG pipeline with 200 nodes within 500ms", () => {
    const start = performance.now();
    const builder = createDiagram("perf-dag-200", "dag");

    // Build a two-column DAG: 100 rows × 2 columns
    for (let i = 0; i < 200; i++) {
      const col = i % 2;
      const row = Math.floor(i / 2);
      const node: DagNode = {
        id: `dag-node-${i}`,
        type: "node",
        diagramType: "dag",
        x: col * 120,
        y: row * 60,
        width: 100,
        height: 40,
        shape: "rounded-rectangle",
        label: `Stage-${row}-${col}`,
        depth: row,
        parents: row > 0 ? [`dag-node-${i - 2}`] : [],
        children: i + 2 < 200 ? [`dag-node-${i + 2}`] : [],
      };
      builder.addNode(node);
    }

    // Chain each node to the next node in the same column
    let edgeCount = 0;
    for (let i = 0; i < 198; i += 2) {
      for (const offset of [0, 1]) {
        const sourceIdx = i + offset;
        const targetIdx = sourceIdx + 2;
        if (targetIdx >= 200) continue;
        const edge: DagEdge = {
          id: `dag-edge-${edgeCount++}`,
          type: "edge",
          diagramType: "dag",
          source: `dag-node-${sourceIdx}`,
          target: `dag-node-${targetIdx}`,
          routing: "orthogonal",
          sourceArrow: "none",
          targetArrow: "arrow",
          weight: 1.0,
          critical: sourceIdx === 0,
        };
        builder.addEdge(edge);
      }
    }

    const diagram = builder.build();
    const elapsed = performance.now() - start;

    expect(diagram.nodes.size).toBe(200);
    expect(diagram.edges.size).toBe(edgeCount);
    expect(elapsed).toBeLessThan(500);
  });
});

// ── 150-node gate-flow diagram ───────────────────────────────────────────────

describe("Large lifecycle graph performance — gate flow", () => {
  it("should build a gate flow with 150 nodes within 300ms using the gate-flow preset", () => {
    const preset = getDiagramPreset("gate-flow");
    const start = performance.now();
    const builder = createDiagram("perf-gate-flow-150", preset.diagramType);

    // Pattern: process → decision → process → decision … (alternating every 2)
    for (let i = 0; i < 150; i++) {
      const isGate = i % 3 === 2;
      const node: FlowNode = {
        id: `gf-node-${i}`,
        type: "node",
        diagramType: "flow",
        x: i * 40,
        y: isGate ? 80 : 0,
        width: isGate ? 50 : 80,
        height: isGate ? 50 : 40,
        shape: isGate ? "diamond" : "rounded-rectangle",
        label: isGate ? `Gate-${Math.floor(i / 3)}` : `Task-${i}`,
        flowType: isGate ? "decision" : "process",
        incomingEdges: i > 0 ? [`gf-edge-${i - 1}`] : [],
        outgoingEdges: i < 149 ? [`gf-edge-${i}`] : [],
      };
      builder.addNode(node);
    }

    for (let i = 0; i < 149; i++) {
      const edge: FlowEdge = {
        id: `gf-edge-${i}`,
        type: "edge",
        diagramType: "flow",
        source: `gf-node-${i}`,
        target: `gf-node-${i + 1}`,
        routing: "orthogonal",
        sourceArrow: "none",
        targetArrow: "arrow",
        flowType: i % 3 === 1 ? "true" : "default",
      };
      builder.addEdge(edge);
    }

    const diagram = builder.build();
    const elapsed = performance.now() - start;

    expect(diagram.nodes.size).toBe(150);
    expect(diagram.edges.size).toBe(149);
    expect(diagram.diagramType).toBe("flow");
    // Verify the preset's diagramType is consistent
    expect(preset.diagramType).toBe(diagram.diagramType);
    expect(elapsed).toBeLessThan(300);
  });
});

// ── Node / edge count correctness ────────────────────────────────────────────

describe("Graph construction correctness at scale", () => {
  it("should maintain exact node count for a 250-node dependency graph", () => {
    const builder = createDiagram("correctness-250", "dependency-graph");
    for (let i = 0; i < 250; i++) {
      const node: DependencyNode = {
        id: `cn-${i}`,
        type: "node",
        diagramType: "dependency-graph",
        x: i * 10,
        y: 0,
        width: 40,
        height: 20,
        shape: "rectangle",
        label: `N${i}`,
        dependencies: [],
        dependents: [],
        dependencyType: "depends-on",
        circular: false,
      };
      builder.addNode(node);
    }
    const diagram = builder.build();
    expect(diagram.nodes.size).toBe(250);
  });

  it("should reject duplicate node IDs in a large graph", () => {
    const builder = createDiagram("dup-check", "dependency-graph");
    const node: DependencyNode = {
      id: "dup-id",
      type: "node",
      diagramType: "dependency-graph",
      x: 0,
      y: 0,
      width: 40,
      height: 20,
      shape: "rectangle",
      label: "First",
      dependencies: [],
      dependents: [],
      dependencyType: "depends-on",
      circular: false,
    };
    builder.addNode(node);
    expect(() => builder.addNode({ ...node, label: "Duplicate" })).toThrow(
      /already exists/i,
    );
  });
});
