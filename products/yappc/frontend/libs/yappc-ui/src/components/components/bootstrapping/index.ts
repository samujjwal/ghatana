/**
 * Bootstrapping Components Barrel Export
 *
 * @description Exports all bootstrapping phase components.
 * These components wrap and extend existing UI components for
 * the specific needs of the bootstrapping workflow.
 *
 * @doc.type barrel
 * @doc.purpose Bootstrapping component exports
 * @doc.layer presentation
 * @doc.phase bootstrapping
 */

// Phase Progress
export { PhaseProgressBar } from './PhaseProgressBar';
export type { PhaseProgressBarProps, PhaseConfig } from './PhaseProgressBar';

// Conversation (wraps AIChatInterface)
export { BootstrapConversation } from './BootstrapConversation';
export type { BootstrapConversationProps } from './BootstrapConversation';

// Canvas (wraps ProjectCanvas)
export { BootstrapCanvas } from './BootstrapCanvas';
export type { BootstrapCanvasProps, PhaseLane } from './BootstrapCanvas';

// Question Options
export { QuestionOptionsGroup } from './QuestionOptionsGroup';
export type {
  QuestionOptionsGroupProps,
  QuestionOption,
  QuestionType,
  QuestionValidation,
} from './QuestionOptionsGroup';

// Agent Status
export { AgentStatusIndicator } from './AgentStatusIndicator';
export type {
  AgentStatusIndicatorProps,
  AgentStatusType,
} from './AgentStatusIndicator';

// Saved Session Card
export { SavedSessionCard } from './SavedSessionCard';
export type {
  SavedSessionCardProps,
  SavedSession,
  SessionStatus,
  SessionCollaborator,
} from './SavedSessionCard';

// Template Card
export { TemplateCard } from './TemplateCard';
export type {
  TemplateCardProps,
  Template,
  TemplateCategory,
  TemplateAuthor,
} from './TemplateCard';

// Voice Input Button
export { VoiceInputButton } from './VoiceInputButton';
export type {
  VoiceInputButtonProps,
  VoiceInputStatus,
} from './VoiceInputButton';

// Canvas Export Dialog
export { CanvasExportDialog } from './CanvasExportDialog';
export type {
  CanvasExportDialogProps,
  ExportFormat,
  ExportQuality,
  ExportStatus,
  ExportOptions,
} from './CanvasExportDialog';
