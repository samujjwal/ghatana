/**
 * @fileoverview Core types and operations barrel export.
 *
 * IMPORTANT: The canonical BuilderDocument is defined in builder-document.ts
 * with full Zod schema validation, migrations, and serialization. This is the
 * single source of truth for BuilderDocument across the platform.
 *
 * The Map-based BuilderDocument from types.ts is DEPRECATED. It is no longer
 * exported from this barrel. All new code must use the canonical BuilderDocument.
 */

// ============================================================================
// CANONICAL BUILDER DOCUMENT (from builder-document.ts)
// ============================================================================

export type {
  BuilderDocument,
} from './builder-document';

export {
  CURRENT_SCHEMA_VERSION,
  SCHEMA_VERSIONS,
  BuilderDocumentSchema,
  // NOTE: attachBuilderDocumentCompatibility and normalizeBuilderDocument are
  // intentionally NOT exported from the canonical barrel. Import them from
  // `./legacy-builder-document-adapter` for migration purposes only.
  createBuilderDocument,
  validateBuilderDocument,
  MIGRATIONS,
  detectSchemaVersion,
  migrateBuilderDocument,
  serializeBuilderDocument,
  deserializeBuilderDocument,
  getNode,
  getRootNodes,
  getNodeBindings,
  hasPrivacySensitiveData,
  requiresAccessibility,
} from './builder-document';

export type {
  ValidationIssue,
  ValidationIssueSeverity,
  DocumentValidationResult,
  MigrationFunction,
} from './builder-document';

// ============================================================================
// SUPPORTING TYPES (from types.ts) - BuilderDocument NOT included
// ============================================================================

export type {
  NodeId,
  DocumentId,
  ComponentInstance,
  InstanceMetadata,
  Binding,
  BindingType,
  DesignSystemModel,
  DocumentMetadata,
  CodeProjection,
  CodeFile,
  RoundTripFidelity,
  LossPoint,
  PreviewConfig,
  PreviewHost,
  ValidationResult,
  ValidationError,
  ValidationWarning,
  ActionDefinition,
  ActionTargetKind,
  ReviewStatus,
  ReviewStatusKind,
  AIChangeRecord,
  ResponsiveVariant,
  StateVariant,
  LayoutConstraints,
  CodeRegionOwnership,
} from './types';

export {
  createNodeId,
  createDocumentId,
} from './types';

export {
  insertNode,
  moveNode,
  deleteNode,
  updateNodeProps,
  addBinding,
  removeBinding,
  reorderNode,
  resizeNode,
  repositionNode,
  setResponsiveVariant,
  removeResponsiveVariant,
  upsertAction,
  removeAction,
  batchUpdate,
  createUndoStack,
  mergeDocuments,
  lastWriteWins,
  noopEventBus,
  createTestOperationContext,
} from './operations';

export type {
  OperationEventBus,
  OperationContext,
  NodeInsertedPayload,
  NodeMovedPayload,
  NodeDeletedPayload,
  NodePropsUpdatedPayload,
  BindingAddedPayload,
  BindingRemovedPayload,
  UndoStack,
  UndoStackOptions,
  DocumentConflict,
  ConflictResolution,
  ConflictResolver,
} from './operations';

export { validateDocument } from './validation';

export {
  generateReactCode,
  type GenerateOptions,
} from './codegen';

export type {
  BuilderPlatformTarget,
  BuilderPlatformSemantics,
  BuilderSerializedProps,
  BuilderPlatformSlotPlan,
  BuilderPlatformNodePlan,
  BuilderPlatformDocumentPlan,
  ContractLookup,
  ManifestLookup,
} from './platform-plan';

export {
  projectInstanceToPlatformPlan,
  projectDocumentToPlatformPlan,
} from './platform-plan';

export type {
  BuilderOperationKind,
  BuilderOperationEvent,
  RollbackSnapshot,
  BuilderTelemetrySink,
  BuilderTelemetryEvent,
} from './telemetry';

export {
  captureSnapshot,
  RollbackHistory,
  noopTelemetrySink,
  withTelemetry,
  toBuilderTelemetryEvent,
} from './telemetry';

export type {
  SceneNode,
  SceneViewport,
  SceneProjection,
  SceneDelta,
  SceneDeltaKind,
  SceneDeltaPayload,
  MoveDeltaPayload,
  ResizeDeltaPayload,
  ReorderDeltaPayload,
  DeleteDeltaPayload,
  UpdatePropsDeltaPayload,
} from './scene-projection';

export { projectToScene, reconcileSceneDeltas } from './scene-projection';

export type {
  ImportSourceFormat,
  ImportSource,
  ImportResultStatus,
  ImportConflict,
  ImportResult,
} from './import';

export { importSource, importFromJson, importFromTsx, importFromHtml } from './import';

export type {
  SerializedDocument,
  DocumentVersion,
  PersistenceAdapter,
  AutosaveConfig,
} from './persistence';

export {
  serializeDocument,
  deserializeDocument,
  LocalStoragePersistenceAdapter,
  InMemoryPersistenceAdapter,
  AutosaveOrchestrator,
  recoverSession,
} from './persistence';

export type {
  DSBindingViolation,
  DSViolationKind,
  DSBindingValidationResult,
  StoryParityReport,
} from './ds-binding';

export {
  validateDocumentAgainstDS,
  dsViolationsToValidationResult,
  checkStoryContractParity,
} from './ds-binding';
