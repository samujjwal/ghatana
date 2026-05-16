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
export { PatchCoordinator } from './patch-coordinator';
export type { PatchCoordinatorOptions } from './patch-coordinator';
export { preserveResidual, buildResidualIndex } from './residual-preserver';
export type { PreservationResult } from './residual-preserver';
