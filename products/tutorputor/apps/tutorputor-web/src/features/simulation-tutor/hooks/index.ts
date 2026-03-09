/**
 * Simulation Tutor Hooks
 *
 * @doc.type barrel
 * @doc.purpose Export all simulation tutor hooks
 * @doc.layer product
 * @doc.pattern BarrelExport
 */

export {
  useSimulationTutor,
  type SimulationState,
  type TutorContext,
  type TutorResponse,
  type UseSimulationTutorOptions,
} from "./useSimulationTutor";

export {
  useConversationContext,
  type ConversationMessage,
  type MessageMetadata,
  type LearningObjective,
  type MisconceptionRecord,
  type ConversationContext,
  type UseConversationContextOptions,
  type UseConversationContextReturn,
} from "./useConversationContext";

export {
  useSimulationStateSync,
  type StateSnapshot,
  type StateChangeEvent,
  type StateDiff,
  type SyncOptions,
  type UseSimulationStateSyncReturn,
} from "./useSimulationStateSync";
