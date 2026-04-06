/**
 * CRDT primitives — absorbed from @yappc/crdt.
 *
 * Re-exports vector-clock, conflict-resolution, and IDE-integration
 * CRDT utilities. Consumers that previously imported from @yappc/crdt
 * should migrate to @yappc/collab/crdt.
 *
 * @deprecated @yappc/crdt — Use @yappc/collab/crdt instead
 */
export { CRDTCore } from './core/index.js';
export type {
	VectorClock,
	CRDTOperation,
	OperationLogEntry,
	StateVector,
	CRDTValue,
	MergeResult,
	Conflict as CoreConflict,
	ConflictMetadata,
	CRDTState,
	CRDTOperationResult,
	SyncMessage,
	MergeStrategy,
	CRDTConfig,
	Snapshot,
	CRDTStatistics,
} from './core/index.js';
export { ConflictResolutionEngine, autoResolveOperations } from './conflict-resolution/index.js';
export type {
	Conflict,
	ConflictType,
	ResolutionStrategy,
	ConflictSeverity,
	ConflictAnalysis,
	ResolutionSuggestion,
	MergeStrategyConfig,
	ThreeWayMergeInput,
	ThreeWayMergeResult,
	ConflictResolutionResult,
	ConflictReport,
	ResolutionHistoryEntry,
	ConflictDetectorConfig,
	ConflictResolutionEngineConfig,
	OperationResult,
	ConflictStatistics,
	AutoResolutionResult,
	AutoResolutionRule,
} from './conflict-resolution/index.js';
export { recordToYMap, yMapToRecord } from './ide/index.js';
export type {
	CanvasNodeLite,
	IDEFileCRDT,
	IDEFolderCRDT,
	IDEPresenceCRDT,
	IDECRDTState,
	IDECRDTStateWrapper,
} from './ide/index.js';
