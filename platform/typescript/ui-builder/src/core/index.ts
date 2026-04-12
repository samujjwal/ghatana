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
} from './operations';

export { validateDocument } from './validation';

export {
  generateReactCode,
  type GenerateOptions,
} from './codegen';

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
