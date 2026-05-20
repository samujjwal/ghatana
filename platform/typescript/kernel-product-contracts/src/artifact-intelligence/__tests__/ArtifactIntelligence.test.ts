import { describe, expect, it } from "vitest";
import {
  ARTIFACT_INTELLIGENCE_SCHEMA_VERSION,
  ArtifactGraphSummarySchema,
  ArtifactEvidenceEnvelopeSchema,
  ArtifactIntelligenceEvidenceEnvelopeSchema,
  DependencyGraphEvidenceSchema,
  GeneratedChangeSetSummarySchema,
  ProductShapeEvidenceSchema,
  ResidualIslandReportSchema,
  RiskHotspotReportSchema,
  SemanticArtifactEvidenceBundleSchema,
  SemanticArtifactReferenceSchema,
  isArtifactGraphSummary,
  isArtifactEvidenceEnvelope,
  isArtifactIntelligenceEvidenceEnvelope,
  isDependencyGraphEvidence,
  isGeneratedChangeSetSummary,
  isProductShapeEvidence,
  isResidualIslandReport,
  isRiskHotspotReport,
  isSemanticArtifactEvidenceBundle,
  isSemanticArtifactReference,
  type ArtifactGraphSummary,
  type ArtifactEvidenceEnvelope,
  type ArtifactIntelligenceEvidenceEnvelope,
  type DependencyGraphEvidence,
  type GeneratedChangeSetSummary,
  type ProductShapeEvidence,
  type ResidualIslandReport,
  type RiskHotspotReport,
  type SemanticArtifactEvidenceBundle,
  type SemanticArtifactReference,
} from "../ArtifactIntelligence";

const evidenceBase = {
  schemaVersion: ARTIFACT_INTELLIGENCE_SCHEMA_VERSION,
  evidenceId: "evidence-1",
  tenantId: "tenant-1",
  workspaceId: "workspace-1",
  projectId: "project-1",
  confidence: 0.92,
  provenanceRefs: ["prov:yappc:scan-1"],
  createdAt: "2026-05-14T00:00:00.000Z",
  createdBy: "yappc-artifact-intelligence",
  correlationId: "corr-1",
  privacyClassification: "internal",
  retention: {
    expiresAt: "2026-06-14T00:00:00.000Z",
  },
} as const;

const semanticArtifact: SemanticArtifactReference = {
  ...evidenceBase,
  evidenceType: "semantic-artifact-reference",
  productUnitId: "product-1",
  artifactId: "artifact:web-route",
  artifactKind: "ui-route",
  displayName: "Ideas route",
  artifactRef: "artifact://product-1/routes/ideas",
  path: "src/routes/IdeasPage.tsx",
  language: "tsx",
  semanticTags: ["idea-capture", "studio"],
  riskLevel: "medium",
};

const graphSummary: ArtifactGraphSummary = {
  ...evidenceBase,
  evidenceId: "evidence-graph-1",
  evidenceType: "artifact-graph-summary",
  productUnitId: "product-1",
  nodeCount: 2,
  edgeCount: 1,
  nodes: [
    {
      artifactId: "artifact:web-route",
      artifactKind: "ui-route",
      label: "Ideas route",
    },
    {
      artifactId: "artifact:api-client",
      artifactKind: "api",
      label: "Kernel client",
    },
  ],
  edges: [
    {
      fromArtifactId: "artifact:web-route",
      toArtifactId: "artifact:api-client",
      relationship: "uses",
    },
  ],
  rootArtifactIds: ["artifact:web-route"],
  orphanArtifactIds: [],
};

const productShape: ProductShapeEvidence = {
  ...evidenceBase,
  evidenceId: "evidence-shape-1",
  evidenceType: "product-shape-evidence",
  productUnitId: "product-1",
  shapeKind: "web-app",
  lifecycleReadiness: "partial",
  detectedSurfaces: ["web"],
  requiredAdapters: ["pnpm-vite-react"],
  missingEvidenceRefs: ["artifact-manifest"],
};

const dependencyGraph: DependencyGraphEvidence = {
  ...evidenceBase,
  evidenceId: "evidence-dependencies-1",
  evidenceType: "dependency-graph-evidence",
  productUnitId: "product-1",
  dependencyCount: 8,
  internalDependencyCount: 3,
  externalDependencyCount: 5,
  cycleCount: 0,
  cycleRefs: [],
  vulnerableDependencyRefs: [],
};

const residualIslandReport: ResidualIslandReport = {
  ...evidenceBase,
  evidenceId: "evidence-islands-1",
  evidenceType: "residual-island-report",
  productUnitId: "product-1",
  islandCount: 1,
  residualArtifactRefs: ["artifact:unused-helper"],
  recommendedActions: ["attach artifact to ProductUnit surface or remove it"],
};

const riskHotspotReport: RiskHotspotReport = {
  ...evidenceBase,
  evidenceId: "evidence-risk-1",
  evidenceType: "risk-hotspot-report",
  productUnitId: "product-1",
  hotspotCount: 1,
  highestRiskLevel: "high",
  hotspots: [
    {
      artifactId: "artifact:deploy-script",
      riskLevel: "high",
      reason: "deployment path lacks approval evidence",
      evidenceRefs: ["prov:yappc:scan-1"],
    },
  ],
};

const generatedChangeSet: GeneratedChangeSetSummary = {
  ...evidenceBase,
  evidenceId: "evidence-changes-1",
  evidenceType: "generated-change-set-summary",
  productUnitId: "product-1",
  changeSetId: "changeset-1",
  changeCount: 2,
  affectedArtifactRefs: ["artifact:web-route"],
  generatedArtifactRefs: ["artifact:test-route"],
  validationEvidenceRefs: ["prov:test:route"],
};

const semanticBundleFixture: SemanticArtifactEvidenceBundle = {
  bundleId: "bundle-1",
  productUnitId: "product-1",
  semanticArtifactRefs: ["artifact://product-1/routes/ideas"],
  graphSummaryRef: "evidence://artifact-graph/evidence-graph-1",
  dependencyGraphRef: "evidence://dependency-graph/evidence-dependencies-1",
  productShapeRef: "evidence://product-shape/evidence-shape-1",
  residualIslandRef: "evidence://residual-islands/evidence-islands-1",
  riskHotspotRef: "evidence://risk-hotspots/evidence-risk-1",
  generatedChangeSetRef: "evidence://change-set/evidence-changes-1",
  bundleCreatedAt: "2026-05-14T00:00:00.000Z",
  correlationId: "corr-1",
};

const envelopeFixture: ArtifactIntelligenceEvidenceEnvelope = {
  envelopeId: "envelope-1",
  tenantId: "tenant-1",
  workspaceId: "workspace-1",
  projectId: "project-1",
  productUnitId: "product-1",
  evidenceType: "artifact-graph-summary",
  evidenceId: "evidence-graph-1",
  evidenceRef: "evidence://artifact-graph/evidence-graph-1",
  envelopeCreatedAt: "2026-05-14T00:00:00.000Z",
  correlationId: "corr-1",
};

const artifactEvidenceEnvelopeFixture: ArtifactEvidenceEnvelope = {
  schemaVersion: ARTIFACT_INTELLIGENCE_SCHEMA_VERSION,
  envelopeId: "artifact-envelope-1",
  tenantId: "tenant-1",
  workspaceId: "workspace-1",
  projectId: "project-1",
  productUnitId: "product-1",
  runId: "run-1",
  correlationId: "corr-1",
  sourceRef: "source://monorepo/products/product-1",
  artifactType: "semantic-source",
  artifactRefs: ["artifact://product-1/build/web-dist"],
  semanticArtifactRefs: ["artifact://product-1/routes/ideas"],
  residualIslandRefs: ["evidence://residual-islands/evidence-islands-1"],
  fidelityRefs: ["evidence://fidelity/report-1"],
  roundTripDiffRefs: ["evidence://round-trip/diff-1"],
  generatedChangeSetRefs: ["evidence://change-set/evidence-changes-1"],
  riskHotspotRefs: ["evidence://risk-hotspots/evidence-risk-1"],
  trustState: "degraded",
  createdAt: "2026-05-14T00:00:00.000Z",
};

describe("artifact intelligence contracts", () => {
  it("accepts all required evidence contract families", () => {
    expect(isSemanticArtifactReference(semanticArtifact)).toBe(true);
    expect(isArtifactGraphSummary(graphSummary)).toBe(true);
    expect(isProductShapeEvidence(productShape)).toBe(true);
    expect(isDependencyGraphEvidence(dependencyGraph)).toBe(true);
    expect(isResidualIslandReport(residualIslandReport)).toBe(true);
    expect(isRiskHotspotReport(riskHotspotReport)).toBe(true);
    expect(isGeneratedChangeSetSummary(generatedChangeSet)).toBe(true);
    expect(isSemanticArtifactEvidenceBundle(semanticBundleFixture)).toBe(true);
    expect(isArtifactIntelligenceEvidenceEnvelope(envelopeFixture)).toBe(true);
    expect(isArtifactEvidenceEnvelope(artifactEvidenceEnvelopeFixture)).toBe(true);
  });

  it("accepts generated evidence fixtures for bundle and envelope schemas", () => {
    expect(SemanticArtifactEvidenceBundleSchema.safeParse(semanticBundleFixture).success).toBe(
      true
    );
    expect(
      ArtifactIntelligenceEvidenceEnvelopeSchema.safeParse(envelopeFixture).success
    ).toBe(true);
    expect(
      ArtifactEvidenceEnvelopeSchema.safeParse(artifactEvidenceEnvelopeFixture).success
    ).toBe(true);
  });

  it("rejects evidence with missing common identity fields", () => {
    const { evidenceId: _evidenceId, ...missingEvidenceId } = semanticArtifact;

    expect(isSemanticArtifactReference(missingEvidenceId)).toBe(false);
    expect(SemanticArtifactReferenceSchema.safeParse(missingEvidenceId).success).toBe(
      false
    );
  });

  it("rejects out-of-range confidence and empty provenance refs", () => {
    const invalidEvidence = {
      ...semanticArtifact,
      confidence: 1.5,
      provenanceRefs: [],
    };

    const result = SemanticArtifactReferenceSchema.safeParse(invalidEvidence);

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.map((issue) => issue.path.join("."))).toEqual(
        expect.arrayContaining(["confidence", "provenanceRefs"])
      );
    }
  });

  it("rejects unsupported semantic artifact kinds and unknown fields", () => {
    const invalidEvidence = {
      ...semanticArtifact,
      artifactKind: "unknown-kind",
      dangling: true,
    };

    expect(SemanticArtifactReferenceSchema.safeParse(invalidEvidence).success).toBe(
      false
    );
  });

  it("rejects invalid graph counts and malformed edges", () => {
    const invalidGraph = {
      ...graphSummary,
      nodeCount: -1,
      edges: [
        {
          fromArtifactId: "",
          toArtifactId: "artifact:api-client",
          relationship: "uses",
        },
      ],
    };

    expect(ArtifactGraphSummarySchema.safeParse(invalidGraph).success).toBe(false);
  });

  it("rejects graph summaries when counts do not match node and edge arrays", () => {
    const invalidGraph = {
      ...graphSummary,
      nodeCount: 99,
      edgeCount: 77,
    };

    const result = ArtifactGraphSummarySchema.safeParse(invalidGraph);
    expect(result.success).toBe(false);
  });

  it("rejects graph summaries when edge references unknown node ids", () => {
    const invalidGraph = {
      ...graphSummary,
      edges: [
        {
          fromArtifactId: "artifact:missing-from",
          toArtifactId: "artifact:api-client",
          relationship: "uses",
        },
      ],
    };

    const result = ArtifactGraphSummarySchema.safeParse(invalidGraph);
    expect(result.success).toBe(false);
  });

  it("rejects unsupported product shape and lifecycle readiness values", () => {
    const invalidShape = {
      ...productShape,
      shapeKind: "spreadsheet",
      lifecycleReadiness: "mostly-ready",
    };

    expect(ProductShapeEvidenceSchema.safeParse(invalidShape).success).toBe(false);
  });

  it("rejects invalid dependency graph counts", () => {
    const invalidDependencyGraph = {
      ...dependencyGraph,
      dependencyCount: -2,
      cycleCount: 0.5,
    };

    expect(DependencyGraphEvidenceSchema.safeParse(invalidDependencyGraph).success).toBe(
      false
    );
  });

  it("rejects dependency graph evidence when aggregate counts are inconsistent", () => {
    const invalidDependencyGraph = {
      ...dependencyGraph,
      dependencyCount: 100,
      cycleCount: 2,
      cycleRefs: ["cycle://a"],
    };

    const result = DependencyGraphEvidenceSchema.safeParse(invalidDependencyGraph);
    expect(result.success).toBe(false);
  });

  it("rejects invalid residual island reports", () => {
    const invalidReport = {
      ...residualIslandReport,
      islandCount: -1,
      recommendedActions: [""],
    };

    expect(ResidualIslandReportSchema.safeParse(invalidReport).success).toBe(false);
  });

  it("rejects invalid risk hotspot reports", () => {
    const invalidReport = {
      ...riskHotspotReport,
      highestRiskLevel: "urgent",
      hotspots: [
        {
          artifactId: "artifact:deploy-script",
          riskLevel: "high",
          reason: "",
          evidenceRefs: [],
        },
      ],
    };

    expect(RiskHotspotReportSchema.safeParse(invalidReport).success).toBe(false);
  });

  it("rejects risk hotspot reports when highestRiskLevel does not match hotspots", () => {
    const invalidReport = {
      ...riskHotspotReport,
      highestRiskLevel: "critical",
      hotspotCount: 2,
    };

    const result = RiskHotspotReportSchema.safeParse(invalidReport);
    expect(result.success).toBe(false);
  });

  it("rejects invalid generated change-set summaries", () => {
    const invalidSummary = {
      ...generatedChangeSet,
      changeCount: -1,
      validationEvidenceRefs: [""],
    };

    expect(GeneratedChangeSetSummarySchema.safeParse(invalidSummary).success).toBe(
      false
    );
  });

  it("keeps each type guard narrow for other evidence families", () => {
    expect(isSemanticArtifactReference(graphSummary)).toBe(false);
    expect(isArtifactGraphSummary(semanticArtifact)).toBe(false);
    expect(isProductShapeEvidence(dependencyGraph)).toBe(false);
    expect(isDependencyGraphEvidence(productShape)).toBe(false);
    expect(isResidualIslandReport(riskHotspotReport)).toBe(false);
    expect(isRiskHotspotReport(residualIslandReport)).toBe(false);
    expect(isGeneratedChangeSetSummary(residualIslandReport)).toBe(false);
    expect(isSemanticArtifactEvidenceBundle(riskHotspotReport)).toBe(false);
    expect(isArtifactIntelligenceEvidenceEnvelope(semanticArtifact)).toBe(false);
  });
});
