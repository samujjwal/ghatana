/**
 * Artifact intelligence contracts shared by YAPPC, Kernel, and Studio.
 *
 * @doc.type module
 * @doc.purpose Schema-backed artifact intelligence evidence contracts
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";

export const ARTIFACT_INTELLIGENCE_SCHEMA_VERSION = "1.0.0" as const;

export const ARTIFACT_KINDS = [
  "source-file",
  "api",
  "database",
  "service",
  "ui-route",
  "workflow",
  "configuration",
  "test",
  "documentation",
] as const;

export const PRODUCT_SHAPE_KINDS = [
  "web-app",
  "api-service",
  "worker-service",
  "mobile-app",
  "data-pipeline",
  "plugin",
  "documentation-site",
  "mixed-product",
] as const;

export const LIFECYCLE_READINESS_STATES = [
  "disabled",
  "planned",
  "partial",
  "enabled",
] as const;

export const RISK_LEVELS = ["low", "medium", "high", "critical"] as const;
const PRIVACY_CLASSIFICATIONS = ["public", "internal", "confidential", "restricted"] as const;

export type ArtifactKind = (typeof ARTIFACT_KINDS)[number];
export type ProductShapeKind = (typeof PRODUCT_SHAPE_KINDS)[number];
export type LifecycleReadinessState = (typeof LIFECYCLE_READINESS_STATES)[number];
export type RiskLevel = (typeof RISK_LEVELS)[number];
export type PrivacyClassification = (typeof PRIVACY_CLASSIFICATIONS)[number];

export const EVIDENCE_TYPES = [
  "semantic-artifact-reference",
  "artifact-graph-summary",
  "dependency-graph-evidence",
  "product-shape-evidence",
  "residual-island-report",
  "risk-hotspot-report",
  "generated-change-set-summary",
] as const;

export type EvidenceType = (typeof EVIDENCE_TYPES)[number];

const NonEmptyStringSchema = z.string().trim().min(1);

export const ArtifactIntelligenceEvidenceBaseSchema = z
  .object({
    schemaVersion: z.literal(ARTIFACT_INTELLIGENCE_SCHEMA_VERSION),
    evidenceId: NonEmptyStringSchema,
    tenantId: NonEmptyStringSchema,
    workspaceId: NonEmptyStringSchema,
    projectId: NonEmptyStringSchema,
    productUnitId: NonEmptyStringSchema,
    createdAt: z.string().datetime({ offset: true }),
    createdBy: NonEmptyStringSchema,
    correlationId: NonEmptyStringSchema,
    confidence: z.number().min(0).max(1),
    provenanceRefs: z.array(NonEmptyStringSchema).min(1),
    privacyClassification: z.enum(PRIVACY_CLASSIFICATIONS),
    retention: z.object({
      expiresAt: z.string().datetime({ offset: true }),
    }),
  })
  .strict();

export const SemanticArtifactReferenceSchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("semantic-artifact-reference"),
  artifactId: NonEmptyStringSchema,
  artifactKind: z.enum(ARTIFACT_KINDS),
  displayName: NonEmptyStringSchema,
  artifactRef: NonEmptyStringSchema.optional(),
  path: NonEmptyStringSchema.optional(),
  language: NonEmptyStringSchema.optional(),
  semanticTags: z.array(NonEmptyStringSchema),
  riskLevel: z.enum(RISK_LEVELS).optional(),
}).strict();

export const ArtifactGraphNodeSchema = z
  .object({
    artifactId: NonEmptyStringSchema,
    artifactKind: z.enum(ARTIFACT_KINDS),
    label: NonEmptyStringSchema,
  })
  .strict();

export const ArtifactGraphEdgeSchema = z
  .object({
    fromArtifactId: NonEmptyStringSchema,
    toArtifactId: NonEmptyStringSchema,
    relationship: NonEmptyStringSchema,
  })
  .strict();

export const ArtifactGraphSummarySchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("artifact-graph-summary"),
  nodeCount: z.number().int().nonnegative(),
  edgeCount: z.number().int().nonnegative(),
  nodes: z.array(ArtifactGraphNodeSchema),
  edges: z.array(ArtifactGraphEdgeSchema),
  rootArtifactIds: z.array(NonEmptyStringSchema),
  orphanArtifactIds: z.array(NonEmptyStringSchema),
})
  .strict()
  .superRefine((value, ctx) => {
    if (value.nodeCount !== value.nodes.length) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["nodeCount"],
        message: `nodeCount must equal nodes.length (${value.nodes.length})`,
      });
    }
    if (value.edgeCount !== value.edges.length) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["edgeCount"],
        message: `edgeCount must equal edges.length (${value.edges.length})`,
      });
    }

    const nodeIds = new Set(value.nodes.map((node) => node.artifactId));
    for (let index = 0; index < value.edges.length; index += 1) {
      const edge = value.edges[index];
      if (!nodeIds.has(edge.fromArtifactId)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["edges", index, "fromArtifactId"],
          message: `fromArtifactId '${edge.fromArtifactId}' must reference an existing node`,
        });
      }
      if (!nodeIds.has(edge.toArtifactId)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["edges", index, "toArtifactId"],
          message: `toArtifactId '${edge.toArtifactId}' must reference an existing node`,
        });
      }
    }

    for (let index = 0; index < value.rootArtifactIds.length; index += 1) {
      const rootArtifactId = value.rootArtifactIds[index];
      if (!nodeIds.has(rootArtifactId)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["rootArtifactIds", index],
          message: `rootArtifactId '${rootArtifactId}' must reference an existing node`,
        });
      }
    }

    for (let index = 0; index < value.orphanArtifactIds.length; index += 1) {
      const orphanArtifactId = value.orphanArtifactIds[index];
      if (!nodeIds.has(orphanArtifactId)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["orphanArtifactIds", index],
          message: `orphanArtifactId '${orphanArtifactId}' must reference an existing node`,
        });
      }
    }
  });

export const ProductShapeEvidenceSchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("product-shape-evidence"),
  shapeKind: z.enum(PRODUCT_SHAPE_KINDS),
  lifecycleReadiness: z.enum(LIFECYCLE_READINESS_STATES),
  detectedSurfaces: z.array(NonEmptyStringSchema),
  requiredAdapters: z.array(NonEmptyStringSchema),
  missingEvidenceRefs: z.array(NonEmptyStringSchema),
}).strict();

export const DependencyGraphEvidenceSchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("dependency-graph-evidence"),
  dependencyCount: z.number().int().nonnegative(),
  internalDependencyCount: z.number().int().nonnegative(),
  externalDependencyCount: z.number().int().nonnegative(),
  cycleCount: z.number().int().nonnegative(),
  cycleRefs: z.array(NonEmptyStringSchema),
  vulnerableDependencyRefs: z.array(NonEmptyStringSchema),
})
  .strict()
  .superRefine((value, ctx) => {
    const expectedDependencyCount =
      value.internalDependencyCount + value.externalDependencyCount;
    if (value.dependencyCount !== expectedDependencyCount) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["dependencyCount"],
        message: `dependencyCount must equal internalDependencyCount + externalDependencyCount (${expectedDependencyCount})`,
      });
    }
    if (value.cycleCount !== value.cycleRefs.length) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["cycleCount"],
        message: `cycleCount must equal cycleRefs.length (${value.cycleRefs.length})`,
      });
    }
  });

export const ResidualIslandReportSchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("residual-island-report"),
  islandCount: z.number().int().nonnegative(),
  residualArtifactRefs: z.array(NonEmptyStringSchema),
  recommendedActions: z.array(NonEmptyStringSchema),
}).strict();

export const RiskHotspotReportSchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("risk-hotspot-report"),
  hotspotCount: z.number().int().nonnegative(),
  highestRiskLevel: z.enum(RISK_LEVELS),
  hotspots: z.array(
    z
      .object({
        artifactId: NonEmptyStringSchema,
        riskLevel: z.enum(RISK_LEVELS),
        reason: NonEmptyStringSchema,
        evidenceRefs: z.array(NonEmptyStringSchema),
      })
      .strict()
  ),
})
  .strict()
  .superRefine((value, ctx) => {
    if (value.hotspotCount !== value.hotspots.length) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["hotspotCount"],
        message: `hotspotCount must equal hotspots.length (${value.hotspots.length})`,
      });
    }
    if (value.hotspots.length === 0) {
      return;
    }
    const highestRisk = value.hotspots.reduce<RiskLevel>((current, hotspot) => {
      return compareRiskLevel(hotspot.riskLevel, current) > 0
        ? hotspot.riskLevel
        : current;
    }, value.hotspots[0].riskLevel);
    if (value.highestRiskLevel !== highestRisk) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["highestRiskLevel"],
        message: `highestRiskLevel must match max hotspot risk level (${highestRisk})`,
      });
    }
  });

export const GeneratedChangeSetSummarySchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("generated-change-set-summary"),
  changeSetId: NonEmptyStringSchema,
  changeCount: z.number().int().nonnegative(),
  affectedArtifactRefs: z.array(NonEmptyStringSchema),
  generatedArtifactRefs: z.array(NonEmptyStringSchema),
  validationEvidenceRefs: z.array(NonEmptyStringSchema),
}).strict();

// Evidence bundle and envelope schemas
export const SemanticArtifactEvidenceBundleSchema = z
  .object({
    bundleId: NonEmptyStringSchema,
    productUnitId: NonEmptyStringSchema,
    semanticArtifactRefs: z.array(NonEmptyStringSchema),
    graphSummaryRef: NonEmptyStringSchema,
    dependencyGraphRef: NonEmptyStringSchema,
    productShapeRef: NonEmptyStringSchema,
    residualIslandRef: NonEmptyStringSchema.optional(),
    riskHotspotRef: NonEmptyStringSchema.optional(),
    generatedChangeSetRef: NonEmptyStringSchema.optional(),
    bundleCreatedAt: z.string().datetime({ offset: true }),
    correlationId: NonEmptyStringSchema,
  })
  .strict();

export const ArtifactIntelligenceEvidenceEnvelopeSchema = z
  .object({
    envelopeId: NonEmptyStringSchema,
    tenantId: NonEmptyStringSchema,
    workspaceId: NonEmptyStringSchema,
    projectId: NonEmptyStringSchema,
    productUnitId: NonEmptyStringSchema,
    evidenceType: z.enum(EVIDENCE_TYPES),
    evidenceId: NonEmptyStringSchema,
    evidenceRef: NonEmptyStringSchema,
    envelopeCreatedAt: z.string().datetime({ offset: true }),
    correlationId: NonEmptyStringSchema,
  })
  .strict();

export type ArtifactIntelligenceEvidenceBase = z.infer<
  typeof ArtifactIntelligenceEvidenceBaseSchema
>;
export type SemanticArtifactReference = z.infer<typeof SemanticArtifactReferenceSchema>;
export type ArtifactGraphNode = z.infer<typeof ArtifactGraphNodeSchema>;
export type ArtifactGraphEdge = z.infer<typeof ArtifactGraphEdgeSchema>;
export type ArtifactGraphSummary = z.infer<typeof ArtifactGraphSummarySchema>;
export type ProductShapeEvidence = z.infer<typeof ProductShapeEvidenceSchema>;
export type DependencyGraphEvidence = z.infer<typeof DependencyGraphEvidenceSchema>;
export type ResidualIslandReport = z.infer<typeof ResidualIslandReportSchema>;
export type RiskHotspotReport = z.infer<typeof RiskHotspotReportSchema>;
export type GeneratedChangeSetSummary = z.infer<typeof GeneratedChangeSetSummarySchema>;
export type SemanticArtifactEvidenceBundle = z.infer<typeof SemanticArtifactEvidenceBundleSchema>;
export type ArtifactIntelligenceEvidenceEnvelope = z.infer<typeof ArtifactIntelligenceEvidenceEnvelopeSchema>;

export function isSemanticArtifactReference(
  value: unknown
): value is SemanticArtifactReference {
  return SemanticArtifactReferenceSchema.safeParse(value).success;
}

export function isArtifactGraphSummary(value: unknown): value is ArtifactGraphSummary {
  return ArtifactGraphSummarySchema.safeParse(value).success;
}

export function isProductShapeEvidence(value: unknown): value is ProductShapeEvidence {
  return ProductShapeEvidenceSchema.safeParse(value).success;
}

export function isDependencyGraphEvidence(
  value: unknown
): value is DependencyGraphEvidence {
  return DependencyGraphEvidenceSchema.safeParse(value).success;
}

export function isResidualIslandReport(value: unknown): value is ResidualIslandReport {
  return ResidualIslandReportSchema.safeParse(value).success;
}

export function isRiskHotspotReport(value: unknown): value is RiskHotspotReport {
  return RiskHotspotReportSchema.safeParse(value).success;
}

export function isGeneratedChangeSetSummary(
  value: unknown
): value is GeneratedChangeSetSummary {
  return GeneratedChangeSetSummarySchema.safeParse(value).success;
}

export function isSemanticArtifactEvidenceBundle(
  value: unknown
): value is SemanticArtifactEvidenceBundle {
  return SemanticArtifactEvidenceBundleSchema.safeParse(value).success;
}

export function isArtifactIntelligenceEvidenceEnvelope(
  value: unknown
): value is ArtifactIntelligenceEvidenceEnvelope {
  return ArtifactIntelligenceEvidenceEnvelopeSchema.safeParse(value).success;
}

const RISK_LEVEL_ORDER: Readonly<Record<RiskLevel, number>> = {
  low: 0,
  medium: 1,
  high: 2,
  critical: 3,
};

function compareRiskLevel(left: RiskLevel, right: RiskLevel): number {
  return RISK_LEVEL_ORDER[left] - RISK_LEVEL_ORDER[right];
}
