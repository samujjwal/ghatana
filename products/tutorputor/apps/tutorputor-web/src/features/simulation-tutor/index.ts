/**
 * Simulation Tutor Feature - Barrel Export
 *
 * Provides AI tutoring functionality during simulations with Q&A,
 * hints, and pedagogical scaffolds.
 *
 * @doc.type module
 * @doc.purpose Export simulation tutor components and hooks
 * @doc.layer product
 * @doc.pattern Barrel
 */

// Components
export { SimulationTutorPanel } from "./components/SimulationTutorPanel";
export type { SimulationTutorPanelProps } from "./components/SimulationTutorPanel";

// Hooks - Core Tutor
export {
  useSimulationTutor,
  useStepExplanation,
  useSimulationHint,
} from "./hooks/useSimulationTutor";
export type {
  TutorMessage,
  UseSimulationTutorOptions,
  UseSimulationTutorResult,
} from "./hooks/useSimulationTutor";

// Hooks - Conversation Context
export {
  useConversationContext,
  type ConversationMessage,
  type MessageMetadata,
  type LearningObjective,
  type MisconceptionRecord,
  type ConversationContext,
  type UseConversationContextOptions,
  type UseConversationContextReturn,
} from "./hooks/useConversationContext";

// Hooks - State Sync
export {
  useSimulationStateSync,
  type StateSnapshot,
  type StateChangeEvent,
  type StateDiff,
  type SyncOptions,
  type UseSimulationStateSyncReturn,
} from "./hooks/useSimulationStateSync";

// Utils - Guardrails
export {
  TutorGuardrails,
  validateContent,
  validateTopic,
  applyPedagogicalFilters,
  ContentCategory,
  type ContentGuardrail,
  type TopicGuardrail,
  type ContentFilterResult,
  type TopicValidationResult,
  type PedagogicalFilter,
  type GuardrailConfig,
} from "./utils/guardrails";
