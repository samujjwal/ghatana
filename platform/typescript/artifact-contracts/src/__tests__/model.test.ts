/**
 * @test.type unit
 * @test.execution <50ms
 * @test.infra none
 */

import { describe, it, expect } from "vitest";
import {
  ArtifactNodeSchema,
  ArtifactEdgeSchema,
  LogicalArtifactModelSchema,
  createLogicalArtifactModel,
  LOGICAL_ARTIFACT_MODEL_SCHEMA_VERSION,
} from "../model.js";

const validNode = {
  id: "node-1",
  displayName: "Button",
  kind: "component",
};

describe("ArtifactNodeSchema", () => {
  it("accepts valid node with defaults", () => {
    const result = ArtifactNodeSchema.safeParse(validNode);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.exportedSymbols).toEqual([]);
      expect(result.data.inferredProps).toEqual({});
      expect(result.data.classificationConfidence).toBe(1);
    }
  });

  it("accepts node with all optional fields", () => {
    const result = ArtifactNodeSchema.safeParse({
      ...validNode,
      exportedSymbols: ["Button", "ButtonProps"],
      inferredProps: { label: "string", onClick: "() => void" },
      usesDesignSystem: true,
      classificationConfidence: 0.9,
      metadata: { framework: "react" },
    });
    expect(result.success).toBe(true);
  });

  it("rejects invalid kind", () => {
    const result = ArtifactNodeSchema.safeParse({ ...validNode, kind: "widget" });
    expect(result.success).toBe(false);
  });

  it("rejects confidence out of range", () => {
    const result = ArtifactNodeSchema.safeParse({ ...validNode, classificationConfidence: 1.5 });
    expect(result.success).toBe(false);
  });
});

describe("ArtifactEdgeSchema", () => {
  it("accepts valid edge", () => {
    const result = ArtifactEdgeSchema.safeParse({
      id: "e-1",
      fromId: "node-1",
      toId: "node-2",
      kind: "import",
      importSpecifier: "@ghatana/design-system",
    });
    expect(result.success).toBe(true);
  });

  it("rejects invalid edge kind", () => {
    const result = ArtifactEdgeSchema.safeParse({
      id: "e-1",
      fromId: "n1",
      toId: "n2",
      kind: "references",
    });
    expect(result.success).toBe(false);
  });
});

describe("createLogicalArtifactModel", () => {
  it("creates a model with correct schema version", () => {
    const model = createLogicalArtifactModel("model-1", "My Repo");
    expect(model.schemaVersion).toBe(LOGICAL_ARTIFACT_MODEL_SCHEMA_VERSION);
    expect(model.modelId).toBe("model-1");
    expect(model.label).toBe("My Repo");
    expect(model.nodes).toEqual({});
    expect(model.edges).toEqual([]);
    expect(model.entryNodeIds).toEqual([]);
  });

  it("validates against schema", () => {
    const model = createLogicalArtifactModel("m2", "Test");
    const result = LogicalArtifactModelSchema.safeParse(model);
    expect(result.success).toBe(true);
  });

  it("rejects model with wrong schema version", () => {
    const model = createLogicalArtifactModel("m3", "Bad");
    const result = LogicalArtifactModelSchema.safeParse({
      ...model,
      schemaVersion: "2.0.0",
    });
    expect(result.success).toBe(false);
  });
});
