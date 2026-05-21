/**
 * Tests for PhaseGraph contracts.
 */

import { describe, it, expect } from "vitest";
import type { PhaseGraph, PhaseGraphNode } from "../PhaseGraph";
import type { ProductLifecyclePhase } from "../ProductLifecyclePhase";

describe("PhaseGraph", () => {
  describe("PhaseGraphNode", () => {
    it("should create a valid node", () => {
      const node: PhaseGraphNode = {
        nodeId: "test-node",
        phase: "build" as ProductLifecyclePhase,
        dependsOn: [],
        state: "pending",
      };

      expect(node.nodeId).toBe("test-node");
      expect(node.phase).toBe("build");
      expect(node.state).toBe("pending");
    });

    it("should support different node states", () => {
      const states: PhaseGraphNode["state"][] = [
        "pending",
        "running",
        "succeeded",
        "failed",
        "skipped",
        "blocked",
      ];

      for (const state of states) {
        const node: PhaseGraphNode = {
          nodeId: `node-${state}`,
          phase: "build" as ProductLifecyclePhase,
          dependsOn: [],
          state,
        };
        expect(node.state).toBe(state);
      }
    });
  });

  describe("PhaseGraph", () => {
    it("should create a valid phase graph", () => {
      const graph: PhaseGraph = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        nodes: [
          {
            nodeId: "node-1",
            phase: "build" as ProductLifecyclePhase,
            dependsOn: [],
            state: "pending",
          },
        ],
        state: "pending",
        createdAt: "2024-01-01T00:00:00.000Z",
        updatedAt: "2024-01-01T00:00:00.000Z",
      };

      expect(graph.schemaVersion).toBe("1.0.0");
      expect(graph.productId).toBe("test-product");
      expect(graph.runId).toBe("test-run-1");
      expect(graph.nodes).toHaveLength(1);
      expect(graph.state).toBe("pending");
    });

    it("should support optional correlationId", () => {
      const graph: PhaseGraph = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        correlationId: "correlation-123",
        nodes: [],
        state: "pending",
        createdAt: "2024-01-01T00:00:00.000Z",
        updatedAt: "2024-01-01T00:00:00.000Z",
      };

      expect(graph.correlationId).toBe("correlation-123");
    });

    it("should support optional metadata", () => {
      const graph: PhaseGraph = {
        schemaVersion: "1.0.0",
        productId: "test-product",
        runId: "test-run-1",
        nodes: [],
        state: "pending",
        createdAt: "2024-01-01T00:00:00.000Z",
        updatedAt: "2024-01-01T00:00:00.000Z",
        metadata: {
          lifecycleProfile: "standard-web-api-product",
          providerMode: "platform",
          environment: "local",
        },
      };

      expect(graph.metadata).toBeDefined();
      expect(graph.metadata?.lifecycleProfile).toBe("standard-web-api-product");
      expect(graph.metadata?.providerMode).toBe("platform");
      expect(graph.metadata?.environment).toBe("local");
    });

    it("should support different graph states", () => {
      const states: PhaseGraph["state"][] = [
        "pending",
        "running",
        "succeeded",
        "failed",
        "partially-succeeded",
        "blocked",
      ];

      for (const state of states) {
        const graph: PhaseGraph = {
          schemaVersion: "1.0.0",
          productId: "test-product",
          runId: "test-run-1",
          nodes: [],
          state,
          createdAt: "2024-01-01T00:00:00.000Z",
          updatedAt: "2024-01-01T00:00:00.000Z",
        };
        expect(graph.state).toBe(state);
      }
    });
  });
});
