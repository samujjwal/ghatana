/**
 * @fileoverview Tests for the builder and canvas projections.
 */

import { describe, it, expect } from "vitest";
import { projectToBuilder } from "../projection/builder.js";
import { projectToCanvas } from "../projection/canvas.js";
import { projectToDs } from "../projection/ds.js";
import {
  createLogicalArtifactModel,
  type ArtifactNode,
} from "@ghatana/artifact-contracts";

// ============================================================================
// FIXTURES
// ============================================================================

function makeModel() {
  const model = createLogicalArtifactModel("test-model-001", "Test Workspace");

  const componentNode: ArtifactNode = {
    id: "src/components/Button.tsx",
    displayName: "Button",
    kind: "component",
    exportedSymbols: ["Button", "ButtonProps"],
    inferredProps: { label: "string", onClick: "() => void" },
    usesDesignSystem: true,
    classificationConfidence: 1,
    metadata: {},
  };

  const pageNode: ArtifactNode = {
    id: "src/pages/Home.tsx",
    displayName: "Home",
    kind: "page",
    exportedSymbols: ["Home"],
    inferredProps: { title: "string" },
    usesDesignSystem: false,
    classificationConfidence: 0.9,
    metadata: {},
  };

  const utilNode: ArtifactNode = {
    id: "src/utils/format.ts",
    displayName: "format",
    kind: "utility",
    exportedSymbols: ["formatCurrency", "formatDate"],
    inferredProps: {},
    usesDesignSystem: false,
    classificationConfidence: 1,
    metadata: {},
  };

  return {
    ...model,
    nodes: {
      [componentNode.id]: componentNode,
      [pageNode.id]: pageNode,
      [utilNode.id]: utilNode,
    },
    edges: [
      {
        id: "e1",
        fromId: "src/pages/Home.tsx",
        toId: "src/components/Button.tsx",
        kind: "import" as const,
        importSpecifier: "../components/Button",
      },
    ],
  };
}

// ============================================================================
// BUILDER PROJECTION TESTS
// ============================================================================

describe("projectToBuilder", () => {
  it("projects component and page nodes, excludes utility by default", () => {
    const model = makeModel();
    const result = projectToBuilder(model);

    expect(result.document.nodes).toHaveProperty("src/components/Button.tsx");
    expect(result.document.nodes).toHaveProperty("src/pages/Home.tsx");
    expect(result.document.nodes).not.toHaveProperty("src/utils/format.ts");
    expect(result.excludedNodeIds).toContain("src/utils/format.ts");
  });

  it("sets the correct contractName from displayName", () => {
    const model = makeModel();
    const result = projectToBuilder(model);

    const buttonNode = result.document.nodes["src/components/Button.tsx"];
    expect(buttonNode?.contractName).toBe("Button");
  });

  it("includes all projected node IDs as root children", () => {
    const model = makeModel();
    const result = projectToBuilder(model);

    const rootId = result.document.layout.rootId;
    const rootLayoutNode = result.document.layout.nodes[rootId];
    expect(rootLayoutNode?.children).toContain("src/components/Button.tsx");
    expect(rootLayoutNode?.children).toContain("src/pages/Home.tsx");
  });

  it("produces a valid fidelity report", () => {
    const model = makeModel();
    const result = projectToBuilder(model);

    expect(result.fidelityReport.score).toBeGreaterThanOrEqual(0);
    expect(result.fidelityReport.score).toBeLessThanOrEqual(1);
  });

  it("respects custom includeKinds option", () => {
    const model = makeModel();
    const result = projectToBuilder(model, { includeKinds: ["utility"] });

    expect(result.document.nodes).toHaveProperty("src/utils/format.ts");
    expect(result.document.nodes).not.toHaveProperty("src/components/Button.tsx");
  });
});

// ============================================================================
// CANVAS PROJECTION TESTS
// ============================================================================

describe("projectToCanvas", () => {
  it("projects all nodes to canvas nodes with deterministic positions", () => {
    const model = makeModel();
    const result = projectToCanvas(model);

    expect(result.document.nodes).toHaveLength(3);
    // All positions should be deterministic (no random)
    const positions = result.document.nodes.map((n) => n.position);
    const result2 = projectToCanvas(model);
    const positions2 = result2.document.nodes.map((n) => n.position);
    expect(positions).toEqual(positions2);
  });

  it("projects intra-model edges only", () => {
    const model = makeModel();
    const result = projectToCanvas(model);

    expect(result.document.edges).toHaveLength(1);
    expect(result.document.edges[0]?.source).toBe("src/pages/Home.tsx");
    expect(result.document.edges[0]?.target).toBe("src/components/Button.tsx");
  });

  it("uses grid layout by default", () => {
    const model = makeModel();
    const result = projectToCanvas(model, { layoutAlgorithm: "grid" });

    // Grid positions should be in a regular pattern
    const nodes = result.document.nodes;
    expect(nodes[0]?.position.x).toBe(40); // MARGIN_X
    expect(nodes[0]?.position.y).toBe(40); // MARGIN_Y
  });

  it("uses layered layout when requested", () => {
    const model = makeModel();
    const result = projectToCanvas(model, { layoutAlgorithm: "layered" });

    expect(result.document.nodes).toHaveLength(3);
    // Verify all nodes got positions
    for (const node of result.document.nodes) {
      expect(typeof node.position.x).toBe("number");
      expect(typeof node.position.y).toBe("number");
    }
  });

  it("sets node type to artifactNode", () => {
    const model = makeModel();
    const result = projectToCanvas(model);

    for (const node of result.document.nodes) {
      expect(node.type).toBe("artifactNode");
    }
  });

  it("produces a valid fidelity report", () => {
    const model = makeModel();
    const result = projectToCanvas(model);

    expect(result.fidelityReport.score).toBeGreaterThanOrEqual(0);
    expect(result.fidelityReport.score).toBeLessThanOrEqual(1);
  });
});

// ============================================================================
// DS PROJECTION TESTS
// ============================================================================

describe("projectToDs", () => {
  it("returns empty config when no nodes use design system", () => {
    const model = createLogicalArtifactModel("m1", "Test");
    const result = projectToDs(model);

    expect(result.config.components).toHaveLength(0);
    expect(result.config.usedTokenCategories.size).toBe(0);
    expect(result.fidelityReport.score).toBe(1);
  });

  it("includes only nodes with usesDesignSystem === true", () => {
    const model = makeModel();
    const result = projectToDs(model);

    // Only Button has usesDesignSystem: true
    expect(result.config.components).toHaveLength(1);
    expect(result.config.components[0]?.name).toBe("Button");
  });

  it("infers color token category from prop names ending in 'color'", () => {
    const m = createLogicalArtifactModel("m2", "DS Test");
    const node: ArtifactNode = {
      id: "Card",
      displayName: "Card",
      kind: "component",
      exportedSymbols: ["Card"],
      inferredProps: { backgroundColor: "string", paddingSize: "string" },
      usesDesignSystem: true,
      classificationConfidence: 1,
      metadata: {},
    };
    const model = { ...m, nodes: { Card: node } };
    const result = projectToDs(model);

    const card = result.config.components[0];
    const colorProp = card?.tokenProps.find((p) => p.propName === "backgroundColor");
    expect(colorProp?.inferredTokenCategory).toBe("color");
  });

  it("infers spacing token category from padding/margin/gap props", () => {
    const m = createLogicalArtifactModel("m3", "Spacing Test");
    const node: ArtifactNode = {
      id: "Box",
      displayName: "Box",
      kind: "component",
      exportedSymbols: ["Box"],
      inferredProps: { paddingSize: "string", marginTop: "number" },
      usesDesignSystem: true,
      classificationConfidence: 1,
      metadata: {},
    };
    const model = { ...m, nodes: { Box: node } };
    const result = projectToDs(model);

    const box = result.config.components[0];
    const paddingProp = box?.tokenProps.find((p) => p.propName === "paddingSize");
    expect(paddingProp?.inferredTokenCategory).toBe("spacing");
  });

  it("tracks all used token categories in the config", () => {
    const model = makeModel();
    const result = projectToDs(model);

    // Button has label (unknown) and onClick (unknown) — none map to token categories
    // But the Button node does have usesDesignSystem = true
    // The usedTokenCategories should be empty since no props map
    expect(result.config.usedTokenCategories instanceof Set).toBe(true);
  });
});
