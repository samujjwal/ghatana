/**
 * @fileoverview @ghatana/ui-builder/testing — helpers for testing BuilderDocument operations.
 *
 * Provides document factories, operation helpers, and persistence stubs for unit tests.
 * Import from this subpath in tests only. Never import in production code.
 */

export { InMemoryPersistenceAdapter } from '../core/persistence.js';
export type { PersistenceAdapter, DocumentVersion, SerializedDocument } from '../core/persistence.js';
export { captureSnapshot, RollbackHistory } from '../core/telemetry.js';
export { importSource } from '../core/import.js';
export { projectToScene, reconcileSceneDeltas } from '../core/scene-projection.js';
export { validateDocument } from '../core/validation.js';
export {
  createNodeId,
  createDocumentId,
} from '../core/types.js';
export type {
  ComponentInstance,
  NodeId,
  DocumentId,
} from '../core/types.js';
export type { BuilderDocument } from '../core/builder-document.js';
