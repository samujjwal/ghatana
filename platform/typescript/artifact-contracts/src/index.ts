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

// Structure
export type {
  JsxTreeNode,
  DetectedRoute,
  ComponentUsageRecord,
  ExtractedProtectedRegion,
  ExtractedStructureEnvelope,
  SourceImportRecord,
} from "./structure.js";
export {
  EXTRACTED_STRUCTURE_SCHEMA_VERSION,
  JsxTreeNodeSchema,
  DetectedRouteSchema,
  ComponentUsageRecordSchema,
  ExtractedProtectedRegionSchema,
  ExtractedStructureEnvelopeSchema,
  migrateExtractedStructureEnvelope,
  SourceImportRecordSchema,
} from "./structure.js";

// Provenance
export type {
  ProvenanceRecord,
  OwnershipKind,
  OwnershipRegion,
  FileOwnershipMap,
  WorkspaceOwnershipMap,
  ProtectedRegion,
} from "./provenance.js";
export {
  ProvenanceRecordSchema,
  OwnershipKindSchema,
  OwnershipRegionSchema,
  FileOwnershipMapSchema,
  ProtectedRegionMarkerSchema,
  ProtectedRegionSchema,
  PROTECTED_REGION_MARKER_PREFIX,
  PROTECTED_REGION_MARKER_RE,
  createProtectedRegion,
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
  EvidenceArtifactRef,
  EvidenceGateStatus,
  GeneratedArtifactValidationEvidence,
  PreviewValidationEvidence,
  SourceAcquisitionEvidence,
  ValidationEvidenceStageId,
  ValidationEvidenceStageResult,
} from "./evidence.js";
export {
  CompileResultSchema,
  DecompileResultSchema,
  EvidencePackSchema,
  EvidenceArtifactRefSchema,
  EvidenceGateStatusSchema,
  GeneratedArtifactValidationEvidenceSchema,
  PreviewValidationEvidenceSchema,
  SourceAcquisitionEvidenceSchema,
  ValidationEvidenceStageIdSchema,
  ValidationEvidenceStageResultSchema,
} from "./evidence.js";

// Scan / Acquisition / Validation / Diff
export type {
  AcquisitionJobStatus,
  AcquisitionJobScope,
  AcquisitionJob,
  ScanJobStatus,
  ScanJob,
  FileScanResult,
  ScanResult,
  ValidationFindingSeverity,
  ValidationFinding,
  ValidationPipelineResult,
  DiffHunkKind,
  DiffHunk,
  DiffRecord,
  RoundTripParitySection,
  RoundTripDiffReport,
} from "./scan.js";
export {
  AcquisitionJobStatusSchema,
  AcquisitionJobScopeSchema,
  AcquisitionJobSchema,
  ScanJobStatusSchema,
  ScanJobSchema,
  FileScanResultSchema,
  ScanResultSchema,
  ValidationFindingSeveritySchema,
  ValidationFindingSchema,
  ValidationPipelineResultSchema,
  DiffHunkKindSchema,
  DiffHunkSchema,
  DiffRecordSchema,
  RoundTripParitySectionSchema,
  RoundTripDiffReportSchema,
} from "./scan.js";
