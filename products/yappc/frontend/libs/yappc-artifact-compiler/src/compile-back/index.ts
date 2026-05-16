/**
 * @fileoverview Compile-back layer barrel export.
 */

export {
  ChangeOpKindSchema,
  ChangeOpSchema,
  TextPatchSchema,
  PatchSetSchema,
  buildChangePlan,
} from './types';

export type {
  ChangeOpKind,
  ChangeOp,
  TextPatch,
  PatchSet,
  PatchEmitter,
  PatchContext,
} from './types';

export { ReactPatchEmitter } from './react-patch-emitter';
export * from './types';
export * from './patch-coordinator';
export * from './react-patch-emitter';
export * from './prisma-patch-emitter';
export * from './workflow-patch-emitter';
export * from './residual-preserver';
// P1-15: Export apply-patch module with explicit re-exports to avoid conflicts
export type {
  ApplyPatchOptions,
  ValidationResult as ApplyPatchValidationResult,
  ApplyPatchResult,
  RollbackMetadata as ApplyPatchRollbackMetadata,
  FileSystem,
} from './apply-patch';
export {
  defaultValidationHook,
  checkResidualOverlap,
  verifyChecksum,
  applyPatch,
  rollbackPatch,
} from './apply-patch';
export type { PreservationResult } from './residual-preserver';
