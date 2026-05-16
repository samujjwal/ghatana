/**
 * @fileoverview Core types and operations barrel export.
 */

export type {
  NodeId,
  DocumentId,
  ComponentInstance,
  InstanceMetadata,
  Binding,
  BindingType,
  DesignSystemModel,
  BuilderDocument,
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
} from './operations';

export type {
  OperationEventBus,
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

export type {
  ValidationIssue,
  ValidationIssueSeverity,
  DocumentValidationResult,
  MigrationFunction,
} from './builder-document';

export {
  CURRENT_SCHEMA_VERSION,
  SCHEMA_VERSIONS,
  BuilderDocumentSchema,
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
