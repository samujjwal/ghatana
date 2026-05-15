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

export type ArtifactKind = (typeof ARTIFACT_KINDS)[number];
export type ProductShapeKind = (typeof PRODUCT_SHAPE_KINDS)[number];
export type LifecycleReadinessState = (typeof LIFECYCLE_READINESS_STATES)[number];
export type RiskLevel = (typeof RISK_LEVELS)[number];

const NonEmptyStringSchema = z.string().trim().min(1);

export const ArtifactIntelligenceEvidenceBaseSchema = z
  .object({
    schemaVersion: z.literal(ARTIFACT_INTELLIGENCE_SCHEMA_VERSION),
    evidenceId: NonEmptyStringSchema,
    source: NonEmptyStringSchema,
    confidence: z.number().min(0).max(1),
    provenanceRefs: z.array(NonEmptyStringSchema).min(1),
    createdAt: z.string().datetime({ offset: true }),
    correlationId: NonEmptyStringSchema,
  })
  .strict();

export const SemanticArtifactReferenceSchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("semantic-artifact-reference"),
  productUnitId: NonEmptyStringSchema,
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
  productUnitId: NonEmptyStringSchema,
  nodeCount: z.number().int().nonnegative(),
  edgeCount: z.number().int().nonnegative(),
  nodes: z.array(ArtifactGraphNodeSchema),
  edges: z.array(ArtifactGraphEdgeSchema),
  rootArtifactIds: z.array(NonEmptyStringSchema),
  orphanArtifactIds: z.array(NonEmptyStringSchema),
}).strict();

export const ProductShapeEvidenceSchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("product-shape-evidence"),
  productUnitId: NonEmptyStringSchema,
  shapeKind: z.enum(PRODUCT_SHAPE_KINDS),
  lifecycleReadiness: z.enum(LIFECYCLE_READINESS_STATES),
  detectedSurfaces: z.array(NonEmptyStringSchema),
  requiredAdapters: z.array(NonEmptyStringSchema),
  missingEvidenceRefs: z.array(NonEmptyStringSchema),
}).strict();

export const DependencyGraphEvidenceSchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("dependency-graph-evidence"),
  productUnitId: NonEmptyStringSchema,
  dependencyCount: z.number().int().nonnegative(),
  internalDependencyCount: z.number().int().nonnegative(),
  externalDependencyCount: z.number().int().nonnegative(),
  cycleCount: z.number().int().nonnegative(),
  cycleRefs: z.array(NonEmptyStringSchema),
  vulnerableDependencyRefs: z.array(NonEmptyStringSchema),
}).strict();

export const ResidualIslandReportSchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("residual-island-report"),
  productUnitId: NonEmptyStringSchema,
  islandCount: z.number().int().nonnegative(),
  residualArtifactRefs: z.array(NonEmptyStringSchema),
  recommendedActions: z.array(NonEmptyStringSchema),
}).strict();

export const RiskHotspotReportSchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("risk-hotspot-report"),
  productUnitId: NonEmptyStringSchema,
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
}).strict();

export const GeneratedChangeSetSummarySchema = ArtifactIntelligenceEvidenceBaseSchema.extend({
  evidenceType: z.literal("generated-change-set-summary"),
  productUnitId: NonEmptyStringSchema,
  changeSetId: NonEmptyStringSchema,
  changeCount: z.number().int().nonnegative(),
  affectedArtifactRefs: z.array(NonEmptyStringSchema),
  generatedArtifactRefs: z.array(NonEmptyStringSchema),
  validationEvidenceRefs: z.array(NonEmptyStringSchema),
}).strict();

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
