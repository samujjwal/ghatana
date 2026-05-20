/**
 * @fileoverview @ghatana/artifact-contracts — canonical barrel export.
 *
 * Shared contracts for the Ghatana artifact compiler/decompiler pipeline.
 * All platform packages, Studio, and backend services that participate in the
 * compile/decompile workflow must depend on this package rather than defining
 * their own local models.
 *
 * @doc.type package
 * @doc.purpose Canonical artifact pipeline contracts
 * @doc.layer platform
 */

// Source
export type {
  SourceSpan,
  SourceFile,
  SourceFileKind,
  SourceRef,
  SourceAcquisitionKind,
  SourceAcquisitionDescriptor,
} from "./source.js";
export {
  SourceSpanSchema,
  SourceFileSchema,
  SourceFileKindSchema,
  SourceRefSchema,
  SourceAcquisitionKindSchema,
  SourceAcquisitionDescriptorSchema,
} from "./source.js";

// Model
export type {
  ArtifactKind,
  ArtifactDependencyKind,
  ArtifactEdge,
  ArtifactNode,
  LogicalArtifactModel,
} from "./model.js";
export {
  ArtifactKindSchema,
  ArtifactDependencyKindSchema,
  ArtifactEdgeSchema,
  ArtifactNodeSchema,
  LogicalArtifactModelSchema,
  LOGICAL_ARTIFACT_MODEL_SCHEMA_VERSION,
  createLogicalArtifactModel,
} from "./model.js";

// Provenance
export type {
  ProvenanceRecord,
  OwnershipKind,
  OwnershipRegion,
  FileOwnershipMap,
  WorkspaceOwnershipMap,
} from "./provenance.js";
export {
  ProvenanceRecordSchema,
  OwnershipKindSchema,
  OwnershipRegionSchema,
  FileOwnershipMapSchema,
} from "./provenance.js";

// Fidelity
export type {
  LossPointSeverity,
  LossPoint,
  FidelityReport,
  ResidualIsland,
  ResidualIslandReport,
} from "./fidelity.js";
export {
  LossPointSeveritySchema,
  LossPointSchema,
  FidelityReportSchema,
  ResidualIslandSchema,
  ResidualIslandReportSchema,
  computeFidelityReport,
  createPerfectFidelityReport,
  createResidualIslandReport,
} from "./fidelity.js";

// Evidence
export type {
  CompileResult,
  DecompileResult,
  EvidencePack,
} from "./evidence.js";
export {
  CompileResultSchema,
  DecompileResultSchema,
  EvidencePackSchema,
} from "./evidence.js";
