/**
 * @fileoverview Tests for diagram types and validation.
 */

import { describe, it, expect } from "vitest";
import {
  isValidDiagramType,
  getValidDiagramTypes,
  validateDiagram,
  validateLayoutConfig,
  createDiagram,
  type FlowNode,
  type FlowEdge,
  type DagNode,
  type DagEdge,
  type TopologyNode,
  type DependencyNode,
  type ProvenanceNode,
  type Diagram,
} from "../types.js";

describe("Diagram Types", () => {
  describe("isValidDiagramType", () => {
    it("should validate flow diagram type", () => {
      expect(isValidDiagramType("flow")).toBe(true);
    });

    it("should validate dag diagram type", () => {
      expect(isValidDiagramType("dag")).toBe(true);
    });

    it("should validate topology diagram type", () => {
      expect(isValidDiagramType("topology")).toBe(true);
    });

    it("should validate swimlane diagram type", () => {
      expect(isValidDiagramType("swimlane")).toBe(true);
    });

    it("should validate dependency-graph diagram type", () => {
      expect(isValidDiagramType("dependency-graph")).toBe(true);
    });

    it("should validate provenance-graph diagram type", () => {
      expect(isValidDiagramType("provenance-graph")).toBe(true);
    });

    it("should reject invalid diagram type", () => {
      expect(isValidDiagramType("invalid")).toBe(false);
    });

    it("should reject empty string", () => {
      expect(isValidDiagramType("")).toBe(false);
    });
  });

  describe("getValidDiagramTypes", () => {
    it("should return all valid diagram types", () => {
      const types = getValidDiagramTypes();
      expect(types).toContain("flow");
      expect(types).toContain("dag");
      expect(types).toContain("topology");
      expect(types).toContain("swimlane");
      expect(types).toContain("dependency-graph");
      expect(types).toContain("provenance-graph");
      expect(types).toHaveLength(6);
    });
  });

  describe("validateLayoutConfig", () => {
    it("should validate hierarchical layout", () => {
      const result = validateLayoutConfig({
        algorithm: "hierarchical",
        direction: "vertical",
      });
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it("should validate force-directed layout", () => {
      const result = validateLayoutConfig({
        algorithm: "force-directed",
      });
      expect(result.valid).toBe(true);
    });

    it("should reject invalid algorithm", () => {
      const result = validateLayoutConfig({
        algorithm: "invalid" as never,
      });
      expect(result.valid).toBe(false);
      expect(result.errors[0]).toContain("Invalid layout algorithm");
    });

    it("should reject negative nodeSpacing", () => {
      const result = validateLayoutConfig({
        algorithm: "grid",
        nodeSpacing: -10,
      });
      expect(result.valid).toBe(false);
      expect(result.errors[0]).toContain("nodeSpacing");
    });

    it("should reject negative edgeSpacing", () => {
      const result = validateLayoutConfig({
        algorithm: "grid",
        edgeSpacing: -5,
      });
      expect(result.valid).toBe(false);
      expect(result.errors[0]).toContain("edgeSpacing");
    });

    it("should reject negative rankSpacing", () => {
      const result = validateLayoutConfig({
        algorithm: "hierarchical",
        rankSpacing: -20,
      });
      expect(result.valid).toBe(false);
      expect(result.errors[0]).toContain("rankSpacing");
    });

    it("should reject negative padding", () => {
      const result = validateLayoutConfig({
        algorithm: "grid",
        padding: -10,
      });
      expect(result.valid).toBe(false);
      expect(result.errors[0]).toContain("padding");
    });

    it("should reject animationDuration greater than 5000", () => {
      const result = validateLayoutConfig({
        algorithm: "hierarchical",
        animationDuration: 6000,
      });
      expect(result.valid).toBe(false);
      expect(result.errors[0]).toContain("animationDuration");
    });
  });

  describe("validateDiagram", () => {
    it("should validate minimal valid diagram", () => {
      const node: FlowNode = {
        id: "node-1",
        type: "node",
        diagramType: "flow",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        shape: "rectangle",
        flowType: "process",
        incomingEdges: [],
        outgoingEdges: [],
      };

      const diagram: Diagram = {
        id: "test-diagram",
        diagramType: "flow",
        nodes: new Map([["node-1", node]]),
        edges: new Map(),
        layout: { algorithm: "hierarchical" },
      };

      const result = validateDiagram(diagram);
      expect(result.valid).toBe(true);
    });

    it("should reject unknown diagram type", () => {
      const diagram: Diagram = {
        id: "test-diagram",
        diagramType: "unknown" as never,
        nodes: new Map(),
        edges: new Map(),
        layout: { algorithm: "hierarchical" },
      };

      const result = validateDiagram(diagram);
      expect(result.valid).toBe(false);
      expect(result.errors[0].code).toBe("INVALID_DIAGRAM_TYPE");
    });

    it("should warn about empty diagram", () => {
      const diagram: Diagram = {
        id: "test-diagram",
        diagramType: "flow",
        nodes: new Map(),
        edges: new Map(),
        layout: { algorithm: "hierarchical" },
      };

      const result = validateDiagram(diagram);
      expect(result.valid).toBe(true);
      expect(result.warnings[0].code).toBe("EMPTY_DIAGRAM");
    });

    it("should detect node-diagram type mismatch", () => {
      const dagNode: DagNode = {
        id: "node-1",
        type: "node",
        diagramType: "dag",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        shape: "rectangle",
        depth: 0,
        parents: [],
        children: [],
      };

      const diagram: Diagram = {
        id: "test-diagram",
        diagramType: "flow",
        nodes: new Map([["node-1", dagNode as never]]),
        edges: new Map(),
        layout: { algorithm: "hierarchical" },
      };

      const result = validateDiagram(diagram);
      expect(result.valid).toBe(false);
      expect(result.errors[0].code).toBe("NODE_TYPE_MISMATCH");
    });

    it("should detect invalid node dimensions", () => {
      const node: FlowNode = {
        id: "node-1",
        type: "node",
        diagramType: "flow",
        x: 0,
        y: 0,
        width: -100,
        height: 0,
        shape: "rectangle",
        flowType: "process",
        incomingEdges: [],
        outgoingEdges: [],
      };

      const diagram: Diagram = {
        id: "test-diagram",
        diagramType: "flow",
        nodes: new Map([["node-1", node]]),
        edges: new Map(),
        layout: { algorithm: "hierarchical" },
      };

      const result = validateDiagram(diagram);
      expect(result.valid).toBe(false);
      expect(result.errors[0].code).toBe("INVALID_NODE_DIMENSIONS");
    });

    it("should detect missing source node for edge", () => {
      const node: FlowNode = {
        id: "node-1",
        type: "node",
        diagramType: "flow",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        shape: "rectangle",
        flowType: "process",
        incomingEdges: [],
        outgoingEdges: [],
      };

      const edge: FlowEdge = {
        id: "edge-1",
        type: "edge",
        diagramType: "flow",
        source: "missing-node",
        target: "node-1",
        routing: "straight",
        sourceArrow: "none",
        targetArrow: "arrow",
        flowType: "default",
      };

      const diagram: Diagram = {
        id: "test-diagram",
        diagramType: "flow",
        nodes: new Map([["node-1", node]]),
        edges: new Map([["edge-1", edge]]),
        layout: { algorithm: "hierarchical" },
      };

      const result = validateDiagram(diagram);
      expect(result.valid).toBe(false);
      expect(result.errors[0].code).toBe("MISSING_SOURCE_NODE");
    });

    it("should detect missing target node for edge", () => {
      const node: FlowNode = {
        id: "node-1",
        type: "node",
        diagramType: "flow",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        shape: "rectangle",
        flowType: "process",
        incomingEdges: [],
        outgoingEdges: [],
      };

      const edge: FlowEdge = {
        id: "edge-1",
        type: "edge",
        diagramType: "flow",
        source: "node-1",
        target: "missing-node",
        routing: "straight",
        sourceArrow: "none",
        targetArrow: "arrow",
        flowType: "default",
      };

      const diagram: Diagram = {
        id: "test-diagram",
        diagramType: "flow",
        nodes: new Map([["node-1", node]]),
        edges: new Map([["edge-1", edge]]),
        layout: { algorithm: "hierarchical" },
      };

      const result = validateDiagram(diagram);
      expect(result.valid).toBe(false);
      expect(result.errors[0].code).toBe("MISSING_TARGET_NODE");
    });

    it("should warn about self-loop edges", () => {
      const node: FlowNode = {
        id: "node-1",
        type: "node",
        diagramType: "flow",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        shape: "rectangle",
        flowType: "process",
        incomingEdges: [],
        outgoingEdges: [],
      };

      const edge: FlowEdge = {
        id: "edge-1",
        type: "edge",
        diagramType: "flow",
        source: "node-1",
        target: "node-1",
        routing: "straight",
        sourceArrow: "none",
        targetArrow: "arrow",
        flowType: "default",
      };

      const diagram: Diagram = {
        id: "test-diagram",
        diagramType: "flow",
        nodes: new Map([["node-1", node]]),
        edges: new Map([["edge-1", edge]]),
        layout: { algorithm: "hierarchical" },
      };

      const result = validateDiagram(diagram);
      expect(result.valid).toBe(true);
      expect(result.warnings[0].code).toBe("SELF_LOOP");
    });

    it("should detect edge-diagram type mismatch", () => {
      const node: FlowNode = {
        id: "node-1",
        type: "node",
        diagramType: "flow",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        shape: "rectangle",
        flowType: "process",
        incomingEdges: [],
        outgoingEdges: [],
      };

      const dagEdge: DagEdge = {
        id: "edge-1",
        type: "edge",
        diagramType: "dag",
        source: "node-1",
        target: "node-1",
        routing: "straight",
        sourceArrow: "none",
        targetArrow: "arrow",
        weight: 1,
        critical: false,
      };

      const diagram: Diagram = {
        id: "test-diagram",
        diagramType: "flow",
        nodes: new Map([["node-1", node]]),
        edges: new Map([["edge-1", dagEdge as never]]),
        layout: { algorithm: "hierarchical" },
      };

      const result = validateDiagram(diagram);
      expect(result.valid).toBe(false);
      expect(result.errors[0].code).toBe("EDGE_TYPE_MISMATCH");
    });

    it("should detect missing swimlanes for swimlane diagram", () => {
      const node: TopologyNode = {
        id: "node-1",
        type: "node",
        diagramType: "topology",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        shape: "rectangle",
        topologyType: "device",
        connectedTo: [],
        layer: 0,
      };

      const diagram: Diagram = {
        id: "test-diagram",
        diagramType: "swimlane",
        nodes: new Map([["node-1", node as never]]),
        edges: new Map(),
        layout: { algorithm: "hierarchical" },
      };

      const result = validateDiagram(diagram);
      expect(result.valid).toBe(false);
      expect(result.errors[0].code).toBe("MISSING_SWIMLANES");
    });
  });

  describe("createDiagram", () => {
    it("should create flow diagram builder", () => {
      const builder = createDiagram("flow-1", "flow");
      expect(builder).toBeDefined();
    });

    it("should reject invalid diagram type in builder", () => {
      expect(() => createDiagram("test", "invalid" as never)).toThrow();
    });

    it("should build valid flow diagram", () => {
      const diagram = createDiagram("flow-1", "flow")
        .addNode({
          id: "node-1",
          type: "node",
          diagramType: "flow",
          x: 0,
          y: 0,
          width: 100,
          height: 50,
          shape: "rectangle",
          flowType: "process",
          incomingEdges: [],
          outgoingEdges: [],
        })
        .build();

      expect(diagram.id).toBe("flow-1");
      expect(diagram.diagramType).toBe("flow");
      expect(diagram.nodes.size).toBe(1);
    });

    it("should reject adding node with wrong diagram type", () => {
      const dagNode: DagNode = {
        id: "node-1",
        type: "node",
        diagramType: "dag",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        shape: "rectangle",
        depth: 0,
        parents: [],
        children: [],
      };

      expect(() =>
        createDiagram("flow-1", "flow").addNode(dagNode as never),
      ).toThrow();
    });

    it("should reject duplicate node IDs", () => {
      const node: FlowNode = {
        id: "node-1",
        type: "node",
        diagramType: "flow",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        shape: "rectangle",
        flowType: "process",
        incomingEdges: [],
        outgoingEdges: [],
      };

      expect(() =>
        createDiagram("flow-1", "flow")
          .addNode(node)
          .addNode(node),
      ).toThrow();
    });

    it("should reject invalid layout config", () => {
      expect(() =>
        createDiagram("flow-1", "flow").setLayout({
          algorithm: "invalid" as never,
        }),
      ).toThrow();
    });

    it("should build valid dependency graph", () => {
      const depNode1: DependencyNode = {
        id: "dep-1",
        type: "node",
        diagramType: "dependency-graph",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        shape: "rectangle",
        dependencies: [],
        dependents: ["dep-2"],
        dependencyType: "depends-on",
        circular: false,
      };

      const depNode2: DependencyNode = {
        id: "dep-2",
        type: "node",
        diagramType: "dependency-graph",
        x: 200,
        y: 0,
        width: 100,
        height: 50,
        shape: "rectangle",
        dependencies: ["dep-1"],
        dependents: [],
        dependencyType: "required-by",
        circular: false,
      };

      const diagram = createDiagram("deps-1", "dependency-graph")
        .addNode(depNode1)
        .addNode(depNode2)
        .build();

      expect(diagram.nodes.size).toBe(2);
    });

    it("should build valid provenance graph", () => {
      const node: ProvenanceNode = {
        id: "prov-1",
        type: "node",
        diagramType: "provenance-graph",
        x: 0,
        y: 0,
        width: 100,
        height: 50,
        shape: "rectangle",
        provenanceType: "source",
        sources: [],
        outputs: ["prov-2"],
        timestamp: new Date().toISOString(),
      };

      const diagram = createDiagram("prov-1", "provenance-graph")
        .addNode(node)
        .build();

      expect(diagram.diagramType).toBe("provenance-graph");
    });
  });
});
