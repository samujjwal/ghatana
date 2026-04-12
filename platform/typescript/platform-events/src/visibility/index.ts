/**
 * @fileoverview Visibility types barrel export.
 */

export type {
  VisibilityContract,
  ChangeRecord,
  SuggestionRecord,
  AppliedRecord,
  ActionRequiredRecord,
  UndoableRecord,
  SyncingRecord,
  ProvenanceRecord,
  OperationRecord,
  SyncStatus,
  CodeOwnership,
  OwnershipRegion,
  RoundTripFidelity,
} from './types';

export {
  createProvenanceRecord,
  createOperationRecord,
  createDefaultSyncStatus,
} from './types';
